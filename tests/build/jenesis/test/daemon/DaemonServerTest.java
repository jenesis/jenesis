package build.jenesis.test.daemon;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.daemon.DaemonServer;

import static org.assertj.core.api.Assertions.assertThat;

@Isolated
@Execution(ExecutionMode.SAME_THREAD)
public class DaemonServerTest {

    @TempDir
    private Path root;

    private Thread daemon;

    private Properties properties;

    @BeforeEach
    public void startDaemon() throws IOException {
        properties = new Properties();
        properties.putAll(System.getProperties());
        Files.createDirectories(root.resolve("sources"));
        Files.writeString(root.resolve("sources").resolve("module-info.java"), "module daemon.sample {}\n");
        System.setProperty("jenesis.daemon.idle", "60");
        daemon = Thread.ofPlatform().daemon().start(() -> {
            try {
                DaemonServer.main(root.toString(), "fingerprint");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @AfterEach
    public void stopDaemon() throws InterruptedException {
        daemon.interrupt();
        daemon.join(Duration.ofSeconds(10));
        System.setProperties(properties);
    }

    @Test
    public void serves_a_build_and_reports_its_exit_code() throws IOException {
        Exchange exchange = request(token(), "fingerprint", false, Map.of(), "help");
        assertThat(exchange.code()).isEqualTo(0);
        assertThat(exchange.out()).contains("a Java build tool");
    }

    @Test
    public void hands_back_what_the_build_produced() throws IOException {
        assertThat(request(token(), "fingerprint", false, Map.of(), "build").code()).isEqualTo(0);
        assertThat(outputs)
                .as("the caller can carry on with the folders the build wrote")
                .isNotEmpty();
        assertThat(outputs.values()).allSatisfy(output -> assertThat(root.resolve(output)).exists());
    }

    @Test
    public void reports_a_failing_build_without_dying() throws IOException {
        assertThat(request(token(), "fingerprint", false, Map.of(), "no-such-selector").code()).isEqualTo(1);
        assertThat(request(token(), "fingerprint", false, Map.of(), "help").code())
                .as("the daemon keeps serving after a failed build")
                .isEqualTo(0);
    }

    @Test
    public void applies_the_properties_of_one_request_only() throws IOException {
        assertThat(request(token(), "fingerprint", false, Map.of("jenesis.project.layout", "maven"), "help").out())
                .contains("maven");
        assertThat(System.getProperty("jenesis.project.layout"))
                .as("a request leaves no property behind in the daemon")
                .isNull();
    }

    @Test
    public void rejects_a_client_that_presents_a_wrong_token() throws IOException {
        assertThat(request("00".repeat(32), "fingerprint", false, Map.of(), "help").rejected()).isTrue();
        assertThat(request(token(), "fingerprint", false, Map.of(), "help").code())
                .as("a rejected client does not stop the daemon")
                .isEqualTo(0);
    }

    @Test
    public void shuts_down_when_the_engine_fingerprint_moved() throws IOException, InterruptedException {
        assertThat(request(token(), "a-different-fingerprint", false, Map.of(), "help").stale()).isTrue();
        daemon.join(Duration.ofSeconds(10));
        assertThat(Files.exists(root.resolve(".jenesis").resolve("daemon.port")))
                .as("a retiring daemon takes its port file with it")
                .isFalse();
    }

    @Test
    public void shuts_down_on_request() throws IOException, InterruptedException {
        assertThat(request(token(), "fingerprint", true, Map.of()).code()).isEqualTo(0);
        daemon.join(Duration.ofSeconds(10));
        assertThat(daemon.isAlive()).isFalse();
    }

    @Test
    public void runs_the_entry_point_the_request_names() throws IOException {
        Exchange exchange = request(token(), "fingerprint", "build.jenesis.test.daemon.DaemonEntry", false, Map.of(), "one");
        assertThat(exchange.code()).isEqualTo(0);
        assertThat(exchange.out()).contains("DaemonEntry saw [one]");
    }

    private record Exchange(Integer code, String out, boolean rejected, boolean stale) {
    }

    private String token() throws IOException {
        return Files.readString(awaited("daemon.token")).trim();
    }

    private final SequencedMap<String, String> outputs = new LinkedHashMap<>();

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

    private Exchange request(String token, String digest, boolean stop, Map<String, String> properties,
                             String... selectors) throws IOException {
        return request(token, digest, "build.jenesis.Project", stop, properties, selectors);
    }

    private Exchange request(String token, String digest, String mainClass, boolean stop,
                             Map<String, String> properties, String... selectors) throws IOException {
        int port = Integer.parseInt(Files.readString(awaited("daemon.port")).trim());
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            out.writeUTF(token);
            out.writeUTF(digest);
            out.writeUTF(mainClass);
            out.writeBoolean(stop);
            out.writeInt(properties.size());
            for (Map.Entry<String, String> property : properties.entrySet()) {
                out.writeUTF(property.getKey());
                out.writeUTF(property.getValue());
            }
            out.writeInt(selectors.length);
            for (String selector : selectors) {
                out.writeUTF(selector);
            }
            out.flush();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            outputs.clear();
            while (true) {
                switch (in.readByte()) {
                    case 0 -> {
                        return new Exchange(in.readInt(), received.toString(StandardCharsets.UTF_8), false, false);
                    }
                    case 1, 2 -> {
                        byte[] bytes = new byte[in.readInt()];
                        in.readFully(bytes);
                        received.write(bytes);
                    }
                    case 3 -> {
                        return new Exchange(null, received.toString(StandardCharsets.UTF_8), true, false);
                    }
                    case 4 -> {
                        return new Exchange(null, received.toString(StandardCharsets.UTF_8), false, true);
                    }
                    case 6 -> {
                        for (int index = in.readInt(); index > 0; index--) {
                            outputs.put(in.readUTF(), in.readUTF());
                        }
                    }
                    default -> throw new AssertionError("Unexpected frame from the daemon");
                }
            }
        }
    }
}
