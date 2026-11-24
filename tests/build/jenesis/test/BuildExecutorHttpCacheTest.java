package build.jenesis.test;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import java.lang.management.ManagementFactory;
import build.jenesis.BuildExecutorFileCache;
import build.jenesis.BuildExecutorHttpCache;
import build.jenesis.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BuildExecutorHttpCacheTest {

    private static final String BLOBS = "/626c6f623a7368612d323536/";

    @TempDir
    private Path output, target, local, elsewhere, argument, served;
    private HttpServer server;
    private URI uri;
    private final Map<String, Path> stored = new ConcurrentHashMap<>();
    private final Map<String, Integer> refused = new ConcurrentHashMap<>();
    private final List<String> keys = new CopyOnWriteArrayList<>(),
            projects = new CopyOnWriteArrayList<>(),
            requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String project = String.valueOf(exchange.getRequestHeaders().getFirst(BuildExecutorHttpCache.PROJECT)),
                    path = exchange.getRequestURI().getPath(),
                    method = exchange.getRequestMethod();
            keys.add(String.valueOf(exchange.getRequestHeaders().getFirst(BuildExecutorHttpCache.KEY)));
            projects.add(project);
            requests.add(method + " " + path);
            try {
                Path entry = stored.get(project + path);
                Integer refusal = refused.entrySet().stream()
                        .filter(candidate -> (method + " " + path).startsWith(candidate.getKey()))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
                if (refusal != null) {
                    exchange.sendResponseHeaders(refusal, -1);
                } else if (method.equals("PUT") && exchange.getRequestHeaders().getFirst("Content-Length") == null) {
                    exchange.sendResponseHeaders(411, -1);
                } else if (method.equals("PUT") && entry != null) {
                    exchange.sendResponseHeaders(204, -1);
                } else if (method.equals("PUT")) {
                    Path body = Files.createTempFile(served, "entry", null);
                    try (InputStream in = exchange.getRequestBody()) {
                        Files.copy(in, body, StandardCopyOption.REPLACE_EXISTING);
                    }
                    stored.put(project + path, body);
                    exchange.sendResponseHeaders(201, -1);
                } else if (entry == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else if (method.equals("HEAD")) {
                    exchange.sendResponseHeaders(200, -1);
                } else {
                    exchange.sendResponseHeaders(200, 0);
                    try (OutputStream out = exchange.getResponseBody()) {
                        Files.copy(entry, out);
                    }
                }
            } finally {
                exchange.close();
            }
        });
        server.start();
        uri = URI.create("http://localhost:" + server.getAddress().getPort());
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void restores_a_file_of_every_kind_and_an_empty_folder() throws IOException {
        byte[] step = {1};
        Files.writeString(argument.resolve("handed"), "handed on");
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "handed", sha256("handed on"));
        Files.writeString(output.resolve("small"), "small");
        Files.writeString(output.resolve("copy"), "handed on");
        Files.writeString(output.resolve("large.jar"), "x".repeat(100));
        Files.createDirectories(output.resolve("nested").resolve("empty"));
        Files.writeString(output.resolve("nested").resolve("inner"), "small");
        cache(local).inline(100).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        String address = "demo/01/" + key(in);
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(stored.get(address)))) {
            assertThat(zip.getNextEntry().getName()).isEqualTo("manifest");
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("jenesis-cache/2 SHA-256\n"
                    + "i " + hex("handed on") + " 9 copy\n"
                    + "b " + hex("x".repeat(100)) + " 100 large.jar\n"
                    + "d - 0 nested/empty\n"
                    + "f " + hex("small") + " 5 nested/inner\n"
                    + "f " + hex("small") + " 5 small\n");
            assertThat(zip.getNextEntry().getName()).as("a file two paths name travels once").isEqualTo(hex("small"));
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("small");
            assertThat(zip.getNextEntry()).as("neither the input nor the large file is in the archive").isNull();
        }
        assertThat(stored.get("demo" + BLOBS + hex("x".repeat(100)))).content().isEqualTo("x".repeat(100));
        Optional<Map<Path, byte[]>> fetched = cache(elsewhere).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256");
        assertThat(fetched).isPresent();
        assertThat(fetched.get()).containsOnlyKeys(Path.of("small"), Path.of("copy"), Path.of("large.jar"), Path.of("nested", "inner"));
        assertThat(target.resolve("small")).content().isEqualTo("small");
        assertThat(target.resolve("copy")).as("restored from the folder of the step's input").content().isEqualTo("handed on");
        assertThat(target.resolve("large.jar")).content().isEqualTo("x".repeat(100));
        assertThat(target.resolve("nested").resolve("inner")).content().isEqualTo("small");
        assertThat(target.resolve("nested").resolve("empty")).isEmptyDirectory();
    }

    @Test
    public void a_step_that_wrote_nothing_is_fetched_as_such() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        cache(local).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(stored.get("demo/01/" + key(in))))) {
            zip.getNextEntry();
            assertThat(new String(zip.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("jenesis-cache/2 SHA-256\n");
        }
        Optional<Map<Path, byte[]>> fetched = cache(elsewhere).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256");
        assertThat(fetched).isPresent();
        assertThat(fetched.get()).isEmpty();
    }

    @Test
    public void a_large_file_is_asked_for_and_uploaded_once_however_many_steps_name_it() throws IOException {
        Files.writeString(output.resolve("large"), "x".repeat(100));
        BuildExecutorHttpCache cache = cache(local).inline(100);
        cache.store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(requests).containsExactly(
                "HEAD " + BLOBS + hex("x".repeat(100)),
                "PUT " + BLOBS + hex("x".repeat(100)),
                "PUT /01/" + key(inputs("source", "file", new byte[]{9})));
        requests.clear();
        cache.store(Runnable::run, "step", new byte[]{2}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(requests).as("a second step of the build naming the file sends its own archive alone").containsExactly(
                "PUT /02/" + key(inputs("source", "file", new byte[]{9})));
        requests.clear();
        cache(elsewhere).inline(100)
                .store(Runnable::run, "step", new byte[]{3}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(requests).as("another build asks, and does not upload what the server holds").containsExactly(
                "HEAD " + BLOBS + hex("x".repeat(100)),
                "PUT /03/" + key(inputs("source", "file", new byte[]{9})));
    }

    @Test
    public void a_fetch_downloads_no_large_file_this_machine_holds() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("large"), "x".repeat(100));
        cache(local).inline(100).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        requests.clear();
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isPresent();
        assertThat(requests).as("the step, and a word to the server that the file is in use").containsExactly(
                "GET /01/" + key(in),
                "HEAD " + BLOBS + hex("x".repeat(100)));
        requests.clear();
        assertThat(cache(elsewhere).fetch(Runnable::run, "step", step, in, folders("source"), Files.createDirectory(elsewhere.resolve("target")), "SHA-256"))
                .isPresent();
        assertThat(requests).containsExactly("GET /01/" + key(in), "GET " + BLOBS + hex("x".repeat(100)));
        assertThat(elsewhere.resolve("sha-256").resolve("blobs").resolve(hex("x".repeat(100)).substring(0, 2)).resolve(hex("x".repeat(100)).substring(2)))
                .as("a downloaded file is kept for the next build of any project")
                .content()
                .isEqualTo("x".repeat(100));
    }

    @Test
    public void a_file_within_the_step_that_is_not_what_its_name_says_is_a_miss() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.write(stored.computeIfAbsent("demo/01/" + key(in), _ -> served.resolve("tampered")), archive(
                "jenesis-cache/2 SHA-256\nf " + hex("result") + " 6 file\n",
                Map.of(hex("result"), "tampered")));
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        assertThat(target).isEmptyDirectory();
        assertThat(local.resolve("sha-256").resolve("blobs").resolve(hex("result").substring(0, 2)).resolve(hex("result").substring(2)))
                .as("nothing that is not what its name says is kept")
                .doesNotExist();
    }

    @Test
    public void a_step_naming_a_file_it_does_not_carry_or_carrying_one_it_does_not_name_is_a_miss() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Path entry = stored.computeIfAbsent("demo/01/" + key(in), _ -> served.resolve("entry"));
        Files.write(entry, archive("jenesis-cache/2 SHA-256\nf " + hex("result") + " 6 file\n", Map.of()));
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        Files.write(entry, archive("jenesis-cache/2 SHA-256\n", Map.of(hex("result"), "result")));
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        Files.write(entry, archive("jenesis-cache/2 SHA-256\nf " + hex("other") + " 6 file\n", Map.of(hex("result"), "result")));
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        assertThat(target).isEmptyDirectory();
    }

    @Test
    public void a_large_file_the_server_answers_with_other_content_is_a_miss() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("large"), "x".repeat(100));
        Files.writeString(output.resolve("small"), "small");
        cache(local).inline(100).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        Files.writeString(stored.get("demo" + BLOBS + hex("x".repeat(100))), "tampered");
        assertThat(cache(elsewhere).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        assertThat(target).isEmptyDirectory();
    }

    @Test
    public void a_large_file_the_server_does_not_hold_is_a_miss_and_the_next_store_uploads_it() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("large"), "x".repeat(100));
        BuildExecutorHttpCache cache = cache(local).inline(100);
        cache.store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        stored.remove("demo" + BLOBS + hex("x".repeat(100)));
        assertThat(cache(elsewhere).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        assertThat(target).isEmptyDirectory();
        requests.clear();
        cache(elsewhere).inline(100).store(Runnable::run, "step", new byte[]{2}, in, output, "SHA-256", Map.of());
        assertThat(requests).contains("PUT " + BLOBS + hex("x".repeat(100)));
    }

    @Test
    public void a_large_file_the_server_refuses_leaves_the_step_unstored() throws IOException {
        Files.writeString(output.resolve("large"), "x".repeat(100));
        refused.put("PUT " + BLOBS, 413);
        cache(local).inline(100)
                .store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(requests).as("a step is not stored naming a file the server was not given").containsExactly(
                "HEAD " + BLOBS + hex("x".repeat(100)),
                "PUT " + BLOBS + hex("x".repeat(100)));
    }

    @Test
    public void a_server_that_refuses_is_a_miss_and_an_abandoned_store() throws IOException {
        Files.writeString(output.resolve("file"), "result");
        for (int status : new int[]{401, 403, 413, 500, 507}) {
            refused.put("", status);
            cache(local).store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
            assertThat(cache(local).fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), folders("source"), target, "SHA-256"))
                    .isEmpty();
        }
        assertThat(stored).isEmpty();
    }

    @Test
    public void changed_inputs_name_another_step_and_no_address_is_that_of_one_zip_per_step() throws IOException {
        Files.writeString(output.resolve("file"), "result");
        cache(local).store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        cache(local).store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{8}), output, "SHA-256", Map.of());
        MessageDigest message = message();
        message.update("source".getBytes(StandardCharsets.UTF_8));
        message.update((byte) 0);
        message.update("file".getBytes(StandardCharsets.UTF_8));
        message.update((byte) 0);
        message.update(new byte[]{9});
        assertThat(requests).containsExactly(
                        "PUT /01/" + key(inputs("source", "file", new byte[]{9})),
                        "PUT /01/" + key(inputs("source", "file", new byte[]{8})))
                .doesNotHaveDuplicates()
                .doesNotContain("PUT /01/" + HexFormat.of().formatHex(message.digest()));
        assertThat(cache(local).fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{7}), folders("source"), target, "SHA-256"))
                .isEmpty();
    }

    @Test
    public void a_large_file_within_a_step_and_one_apart_from_it_are_streamed() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        try (FileChannel channel = FileChannel.open(output.resolve("within"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{1}), (128L << 20) - 1);
        }
        try (FileChannel channel = FileChannel.open(output.resolve("apart"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.write(ByteBuffer.wrap(new byte[]{2}), (160L << 20) - 1);
        }
        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        cache(local).inline(144L << 20).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        Optional<Map<Path, byte[]>> fetched = cache(elsewhere).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256");
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        assertThat(fetched).isPresent();
        assertThat(Files.size(target.resolve("within"))).isEqualTo(128L << 20);
        assertThat(Files.size(target.resolve("apart"))).isEqualTo(160L << 20);
        assertThat(stored).containsKeys("demo" + BLOBS + HexFormat.of().formatHex(fetched.get().get(Path.of("apart"))));
        assertThat(allocated).as("288 MiB stored and restored without holding a file in memory").isLessThan(32L << 20);
    }

    @Test
    public void two_projects_share_no_address_on_the_server() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("large"), "x".repeat(100));
        cache(local).inline(100).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        requests.clear();
        assertThat(cache(elsewhere).project("other").fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256"))
                .isEmpty();
        cache(elsewhere).inline(100).project("other").store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        assertThat(requests).as("the file one project stored is not there for another, which uploads its own").contains(
                "PUT " + BLOBS + hex("x".repeat(100)));
        assertThat(stored).containsKeys("demo" + BLOBS + hex("x".repeat(100)), "other" + BLOBS + hex("x".repeat(100)));
        assertThat(projects).containsOnly("demo", "other");
    }

    @Test
    public void a_manifest_naming_a_path_outside_the_target_is_a_miss() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.write(stored.computeIfAbsent("demo/01/" + key(in), _ -> served.resolve("escape")), archive(
                "jenesis-cache/2 SHA-256\nf " + hex("result") + " 6 ../escape\n",
                Map.of(hex("result"), "result")));
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        assertThat(target.resolveSibling("escape")).doesNotExist();
    }

    @Test
    public void an_answer_that_is_no_step_of_this_format_is_a_miss() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Path entry = stored.computeIfAbsent("demo/01/" + key(in), _ -> served.resolve("entry"));
        Files.writeString(entry, "<html>Sign in</html>");
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        Files.write(entry, archive("jenesis-cache/3 SHA-256\n", Map.of()));
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        Files.write(entry, archive("jenesis-cache/2 SHA-256\nx " + hex("result") + " 6 file\n", Map.of()));
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-1"))
                .as("a build hashing with another digest could verify no file of it")
                .isEmpty();
        assertThat(requests).hasSize(4);
    }

    @Test
    public void reads_the_token_of_the_settings_and_refuses_the_name_it_had() throws IOException {
        BuildExecutorHttpCache cache = BuildExecutorHttpCache.ofEnvironment(
                new Environment(Map.of("cache.token", "team-alpha", "cache.local", local.toString())),
                uri);
        cache.fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), folders("source"), target, "SHA-256");
        assertThat(keys).containsExactly("team-alpha");
        assertThatThrownBy(() -> BuildExecutorHttpCache.ofEnvironment(new Environment(Map.of("cache.key", "team-alpha")), uri))
                .as("a setting under its former name would leave the cache server without a credential")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.cache.token");
    }

    @Test
    public void reads_the_folder_of_the_files_and_the_size_a_file_travels_within_its_step_up_to() throws IOException {
        BuildExecutorHttpCache cache = BuildExecutorHttpCache.ofEnvironment(
                new Environment(Map.of("cache.local", local.toString(), "cache.inline", "6")),
                uri);
        Files.writeString(output.resolve("apart"), "result");
        Files.writeString(output.resolve("within"), "small");
        cache.store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(local.resolve("sha-256").resolve("blobs").resolve(hex("result").substring(0, 2)).resolve(hex("result").substring(2)))
                .content()
                .isEqualTo("result");
        assertThat(requests).contains("PUT " + BLOBS + hex("result")).doesNotContain("PUT " + BLOBS + hex("small"));
        assertThatThrownBy(() -> BuildExecutorHttpCache.ofEnvironment(new Environment(Map.of("cache.inline", "-1")), uri))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.cache.inline");
    }

    @Test
    public void fetch_returns_empty_on_miss() throws IOException {
        assertThat(cache(local).fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), folders("source"), target, "SHA-256"))
                .isEmpty();
    }

    @Test
    public void write_disabled_does_not_store() throws IOException {
        Files.writeString(output.resolve("file"), "result");
        cache(local).write(false)
                .store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(requests).isEmpty();
    }

    @Test
    public void read_disabled_does_not_fetch() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        cache(local).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        requests.clear();
        assertThat(cache(local).read(false).fetch(Runnable::run, "step", step, in, folders("source"), target, "SHA-256")).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    public void a_cache_that_does_not_answer_or_is_not_there_does_not_hang_or_fail_the_build() throws IOException {
        Files.writeString(output.resolve("file"), "result");
        try (ServerSocket hanging = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            Thread accepting = Thread.ofVirtual().start(() -> {
                try {
                    Socket accepted = hanging.accept();
                    Thread.sleep(Duration.ofMinutes(1));
                    accepted.close();
                } catch (IOException | InterruptedException _) {
                }
            });
            BuildExecutorHttpCache cache = BuildExecutorHttpCache.ofEnvironment(
                            new Environment(Map.of("cache.read", "PT0.5S", "cache.local", local.toString())),
                            URI.create("http://localhost:" + hanging.getLocalPort() + "/cache"))
                    .token("team-alpha")
                    .project("demo");
            long started = System.nanoTime();
            assertThat(cache.fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), folders("source"), target, "SHA-256"))
                    .isEmpty();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(20));
            accepting.interrupt();
        }
        server.stop(0);
        assertThat(cache(local).fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), folders("source"), target, "SHA-256"))
                .isEmpty();
        cache(local).store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        cache(local).touch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), "SHA-256");
    }

    @Test
    public void sends_the_token_and_the_project_as_headers_and_omits_them_when_unset() throws IOException {
        cache(local).fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), folders("source"), target, "SHA-256");
        assertThat(keys).containsExactly("team-alpha");
        assertThat(projects).containsExactly("demo");
        keys.clear();
        projects.clear();
        new BuildExecutorHttpCache(uri).local(new BuildExecutorFileCache(local))
                .fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), folders("source"), target, "SHA-256");
        assertThat(keys).containsExactly("null");
        assertThat(projects).containsExactly("null");
    }

    @Test
    public void refuses_to_send_the_token_over_plaintext_off_loopback() {
        BuildExecutorHttpCache cache = new BuildExecutorHttpCache(URI.create("http://cache.example.test/"))
                .local(new BuildExecutorFileCache(local))
                .token("team-alpha");
        assertThatThrownBy(() -> cache.fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), folders("source"), target, "SHA-256"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.cache.insecure");
    }

    @Test
    public void touch_sends_a_head_request_for_the_step() throws IOException {
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        cache(local).touch(Runnable::run, "step", new byte[]{1}, in, "SHA-256");
        assertThat(requests).containsExactly("HEAD /01/" + key(in));
        assertThat(keys).contains("team-alpha");
        assertThat(projects).contains("demo");
    }

    @Test
    public void touch_is_skipped_when_reading_is_disabled() throws IOException {
        cache(local).read(false)
                .touch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), "SHA-256");
        assertThat(requests).isEmpty();
    }

    private BuildExecutorHttpCache cache(Path folder) {
        return new BuildExecutorHttpCache(uri).local(new BuildExecutorFileCache(folder)).token("team-alpha").project("demo");
    }

    private SequencedMap<String, Path> folders(String name) {
        SequencedMap<String, Path> folders = new LinkedHashMap<>();
        folders.put(name, argument);
        return folders;
    }

    private static byte[] archive(String manifest, Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("manifest"));
            zip.write(manifest.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static String key(SequencedMap<String, Map<Path, byte[]>> inputs) {
        MessageDigest message = message();
        message.update("jenesis-cache/2".getBytes(StandardCharsets.UTF_8));
        message.update((byte) 0);
        inputs.forEach((argument, files) -> {
            message.update(argument.getBytes(StandardCharsets.UTF_8));
            message.update((byte) 0);
            files.forEach((file, hash) -> {
                message.update(file.toString().getBytes(StandardCharsets.UTF_8));
                message.update((byte) 0);
                message.update(hash);
            });
        });
        return HexFormat.of().formatHex(message.digest());
    }

    private static MessageDigest message() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static byte[] sha256(String content) {
        return message().digest(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(String content) {
        return HexFormat.of().formatHex(sha256(content));
    }

    private static SequencedMap<String, Map<Path, byte[]>> inputs(String argument, String file, byte[] hash) {
        Map<Path, byte[]> files = new LinkedHashMap<>();
        files.put(Path.of(file), hash);
        SequencedMap<String, Map<Path, byte[]>> inputs = new LinkedHashMap<>();
        inputs.put(argument, files);
        return inputs;
    }
}
