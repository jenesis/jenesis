package build.jenesis.test.daemon;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.SequencedProperties;
import build.jenesis.daemon.DaemonBuild;
import build.jenesis.daemon.Worker;

import org.assertj.core.api.InstanceOfAssertFactories;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;

@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class DaemonTest {

    @TempDir
    private Path root;

    private Thread daemon;

    @BeforeEach
    public void startDaemon() throws IOException {
        Files.createDirectories(root.resolve("sources"));
        Files.writeString(root.resolve("sources").resolve("module-info.java"), "module daemon.sample {}\n");
        daemon = Thread.ofPlatform().daemon().start(() -> {
            try {
                Worker.main("listen", root.resolve(".jenesis").toString(), "fingerprint", "60");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    @AfterEach
    public void stopDaemon() throws IOException, InterruptedException {
        if (daemon.isAlive()) {
            request(token(), "fingerprint", true, null, Map.of());
        }
        daemon.join(Duration.ofSeconds(10));
    }

    @Test
    public void serves_a_build_and_reports_its_exit_code() throws IOException {
        Exchange exchange = build(Map.of(), "help");
        assertThat(exchange.code()).isEqualTo(0);
        assertThat(exchange.out()).contains("a Java build tool");
    }

    @Test
    public void hands_back_what_the_build_produced() throws IOException {
        Path outputs = Files.createTempFile(root, "daemon", ".outputs");
        assertThat(request(token(), "fingerprint", false, outputs, Map.of(), "build").code()).isEqualTo(0);
        SequencedProperties produced = SequencedProperties.ofFiles(outputs);
        assertThat(produced.stringPropertyNames())
                .as("the caller can carry on with the folders the build wrote")
                .isNotEmpty();
        assertThat(produced.stringPropertyNames())
                .allSatisfy(identity -> assertThat(root.resolve(produced.getProperty(identity))).exists());
    }

    @Test
    public void reports_a_failing_build_without_dying() throws IOException {
        assertThat(build(Map.of(), "no-such-selector").code()).isEqualTo(1);
        assertThat(build(Map.of(), "help").code())
                .as("the daemon keeps serving after a failed build")
                .isEqualTo(0);
    }

    @Test
    public void applies_the_properties_of_one_request_only() throws IOException {
        assertThat(build(Map.of("jenesis.project.layout", "maven"), "help").out()).contains("maven");
        assertThat(System.getProperty("jenesis.project.layout"))
                .as("a request leaves no property behind in the daemon")
                .isNull();
    }

    @Test
    public void rejects_a_client_that_presents_a_wrong_token() throws IOException {
        assertThat(request("00".repeat(32), "fingerprint", false, null, Map.of(), "help").status())
                .isEqualTo(Worker.REJECTED);
        assertThat(build(Map.of(), "help").code())
                .as("a rejected client does not stop the daemon")
                .isEqualTo(0);
    }

    @Test
    public void shuts_down_when_the_engine_fingerprint_moved() throws IOException, InterruptedException {
        assertThat(request(token(), "a-different-fingerprint", false, null, Map.of(), "help").status())
                .isEqualTo(Worker.STALE);
        daemon.join(Duration.ofSeconds(10));
        assertThat(Files.exists(root.resolve(".jenesis").resolve(Worker.PORT)))
                .as("a retiring daemon takes its port file with it")
                .isFalse();
    }

    @Test
    public void shuts_down_on_request() throws IOException, InterruptedException {
        assertThat(request(token(), "fingerprint", true, null, Map.of()).code()).isEqualTo(0);
        daemon.join(Duration.ofSeconds(10));
        assertThat(daemon.isAlive()).isFalse();
    }

    @Test
    public void runs_the_entry_point_the_request_names() throws IOException {
        Exchange exchange = request(token(),
                "fingerprint",
                false,
                null,
                DaemonEntry.class.getName(),
                Map.of(),
                "one");
        assertThat(exchange.code()).isEqualTo(0);
        assertThat(exchange.out()).contains("DaemonEntry saw [one]");
    }

    @Test
    public void refuses_a_second_build_while_one_runs() throws Exception {
        BlockingEntry.ENTERED.drainPermits();
        BlockingEntry.RELEASED.drainPermits();
        Future<Exchange> first = Executors.newVirtualThreadPerTaskExecutor().submit(() -> request(token(),
                "fingerprint",
                false,
                null,
                BlockingEntry.class.getName(),
                Map.of()));
        assertThat(BlockingEntry.ENTERED.tryAcquire(30, TimeUnit.SECONDS)).isTrue();
        try {
            assertThat(build(Map.of(), "help").status())
                    .as("two builds over one target folder would interfere with each other")
                    .isEqualTo(Worker.BUSY);
        } finally {
            BlockingEntry.RELEASED.release();
        }
        assertThat(first.get(30, TimeUnit.SECONDS).code()).isEqualTo(0);
    }

    @Test
    public void lists_every_setting_with_the_value_in_force() throws IOException {
        Exchange exchange = build(Map.of("jenesis.test.skip", "true"), "configuration");
        assertThat(exchange.code()).isEqualTo(0);
        assertThat(exchange.out().lines().filter(line -> line.startsWith("jenesis.")).count())
                .as("the catalogue lists every setting, not only the ones that are set")
                .isGreaterThan(100);
        assertThat(exchange.out().lines().filter(line -> line.startsWith("jenesis.project.digest=")).toList())
                .singleElement(as(InstanceOfAssertFactories.STRING))
                .contains("SHA-256")
                .contains("[default]");
        assertThat(exchange.out().lines().filter(line -> line.startsWith("jenesis.test.skip=")).toList())
                .singleElement(as(InstanceOfAssertFactories.STRING))
                .as("a property the request set is reported as set")
                .contains("[set]");
    }

    public static final class BlockingEntry {

        static final Semaphore ENTERED = new Semaphore(0), RELEASED = new Semaphore(0);

        public static void main(String... selectors) throws InterruptedException {
            ENTERED.release();
            RELEASED.acquire();
        }
    }

    private record Exchange(byte status, Integer code, String out) {
    }

    private String token() throws IOException {
        awaited(Worker.PORT);
        return Files.readString(awaited(Worker.TOKEN)).trim();
    }

    private Path awaited(String name) throws IOException {
        Path file = root.resolve(".jenesis").resolve(name);
        for (int attempt = 0; attempt < 400 && !Files.isRegularFile(file); attempt++) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }
        assertThat(Files.isRegularFile(file)).as("the daemon announced %s", name).isTrue();
        return file;
    }

    private Exchange build(Map<String, String> properties, String... selectors) throws IOException {
        return request(token(), "fingerprint", false, null, properties, selectors);
    }

    private Exchange request(String token,
                             String fingerprint,
                             boolean stop,
                             Path outputs,
                             Map<String, String> properties,
                             String... selectors) throws IOException {
        return request(token, fingerprint, stop, outputs, "build.jenesis.Project", properties, selectors);
    }

    private Exchange request(String token,
                             String fingerprint,
                             boolean stop,
                             Path outputs,
                             String mainClass,
                             Map<String, String> properties,
                             String... selectors) throws IOException {
        int port = Integer.parseInt(Files.readString(awaited(Worker.PORT)).trim());
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            out.writeUTF(token);
            out.writeUTF(fingerprint);
            out.writeBoolean(stop);
            if (!stop) {
                List<String> request = new ArrayList<>(List.of(DaemonBuild.class.getName(),
                        root.toString(),
                        mainClass,
                        outputs == null ? "" : outputs.toString()));
                properties.forEach((key, value) -> request.add("-D" + key + "=" + value));
                request.add("--");
                request.addAll(List.of(selectors));
                Worker.request(out, 0, request);
            }
            out.flush();
            byte status = in.readByte();
            if (status != Worker.ACCEPTED) {
                return new Exchange(status, null, "");
            }
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            int code = Worker.receive(in, received, received);
            return new Exchange(status, code, received.toString(StandardCharsets.UTF_8));
        }
    }
}
