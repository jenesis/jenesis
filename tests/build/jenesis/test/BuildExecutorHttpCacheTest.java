package build.jenesis.test;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutorFileCache;
import build.jenesis.BuildExecutorHttpCache;
import build.jenesis.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BuildExecutorHttpCacheTest {

    @TempDir
    private Path output, target, local, elsewhere;
    private HttpServer server;
    private URI uri;
    private final Map<String, byte[]> manifests = new ConcurrentHashMap<>(), blobs = new ConcurrentHashMap<>();
    private final List<String> keys = new CopyOnWriteArrayList<>(),
            projects = new CopyOnWriteArrayList<>(),
            digests = new CopyOnWriteArrayList<>(),
            requests = new CopyOnWriteArrayList<>(),
            uploaded = new CopyOnWriteArrayList<>();

    @BeforeEach
    public void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            keys.add(String.valueOf(exchange.getRequestHeaders().getFirst(BuildExecutorHttpCache.KEY)));
            projects.add(String.valueOf(exchange.getRequestHeaders().getFirst(BuildExecutorHttpCache.PROJECT)));
            digests.add(String.valueOf(exchange.getRequestHeaders().getFirst(BuildExecutorHttpCache.DIGEST)));
            String path = exchange.getRequestURI().getPath();
            requests.add(exchange.getRequestMethod() + " " + path);
            try {
                switch (exchange.getRequestMethod() + (path.equals("/blobs") ? " blobs" : " step")) {
                    case "GET step" -> {
                        byte[] manifest = manifests.get(path);
                        if (manifest == null) {
                            exchange.sendResponseHeaders(404, -1);
                        } else {
                            exchange.sendResponseHeaders(200, manifest.length == 0 ? -1 : manifest.length);
                            exchange.getResponseBody().write(manifest);
                        }
                    }
                    case "HEAD step" -> exchange.sendResponseHeaders(manifests.containsKey(path) ? 200 : 404, -1);
                    case "PUT step" -> {
                        byte[] manifest = exchange.getRequestBody().readAllBytes();
                        String missing = new String(manifest, StandardCharsets.UTF_8).lines()
                                .map(line -> line.substring(0, line.indexOf(' ')))
                                .filter(hex -> !hex.equals("-") && !blobs.containsKey(hex))
                                .distinct()
                                .collect(Collectors.joining("\n"));
                        if (missing.isEmpty()) {
                            manifests.put(path, manifest);
                            exchange.sendResponseHeaders(201, -1);
                        } else {
                            byte[] answer = missing.getBytes(StandardCharsets.UTF_8);
                            exchange.sendResponseHeaders(409, answer.length);
                            exchange.getResponseBody().write(answer);
                        }
                    }
                    case "POST blobs" -> {
                        List<String> wanted = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                                .lines()
                                .toList();
                        exchange.sendResponseHeaders(200, 0);
                        try (ZipOutputStream zip = new ZipOutputStream(exchange.getResponseBody())) {
                            for (String hex : wanted) {
                                if (blobs.containsKey(hex)) {
                                    zip.putNextEntry(new ZipEntry(hex));
                                    zip.write(blobs.get(hex));
                                    zip.closeEntry();
                                }
                            }
                        }
                    }
                    case "PUT blobs" -> {
                        try (ZipInputStream zip = new ZipInputStream(exchange.getRequestBody())) {
                            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                                blobs.put(entry.getName(), zip.readAllBytes());
                                uploaded.add(entry.getName());
                            }
                        }
                        exchange.sendResponseHeaders(204, -1);
                    }
                    default -> exchange.sendResponseHeaders(405, -1);
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
    public void stores_and_fetches_round_trip() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        Files.createDirectories(output.resolve("nested").resolve("empty"));
        Files.writeString(output.resolve("nested").resolve("inner"), "deep");
        cache(local).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        assertThat(manifests).hasSize(1);
        assertThat(new String(manifests.values().iterator().next(), StandardCharsets.UTF_8)).isEqualTo(
                hex("result") + " 6 file\n- 0 nested/empty\n" + hex("deep") + " 4 nested/inner\n");
        assertThat(blobs).containsOnlyKeys(hex("result"), hex("deep"));
        Optional<Map<Path, byte[]>> fetched = cache(elsewhere).fetch(Runnable::run, "step", step, in, target, "SHA-256");
        assertThat(fetched).isPresent();
        assertThat(fetched.get()).containsOnlyKeys(Path.of("file"), Path.of("nested", "inner"));
        assertThat(target.resolve("file")).content().isEqualTo("result");
        assertThat(target.resolve("nested").resolve("inner")).content().isEqualTo("deep");
        assertThat(target.resolve("nested").resolve("empty")).isEmptyDirectory();
        assertThat(digests).containsOnly("SHA-256");
    }

    @Test
    public void a_store_asks_before_it_uploads_and_uploads_only_what_the_server_lacks() throws IOException {
        Files.writeString(output.resolve("shared"), "shared");
        Files.writeString(output.resolve("first"), "first");
        cache(local).store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(requests).containsExactly(
                "PUT /step/01/" + fold(new byte[]{9}),
                "PUT /blobs",
                "PUT /step/01/" + fold(new byte[]{9}));
        assertThat(uploaded).containsExactlyInAnyOrder(hex("shared"), hex("first"));
        requests.clear();
        uploaded.clear();
        Files.delete(output.resolve("first"));
        Files.writeString(output.resolve("second"), "second");
        cache(local).store(Runnable::run, "step", new byte[]{2}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(uploaded).as("a file an earlier step uploaded is not sent again").containsExactly(hex("second"));
        requests.clear();
        uploaded.clear();
        cache(elsewhere).store(Runnable::run, "step", new byte[]{3}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(requests).as("a step of files the server holds is one request").containsExactly(
                "PUT /step/03/" + fold(new byte[]{9}));
        assertThat(uploaded).isEmpty();
    }

    @Test
    public void a_fetch_downloads_only_the_files_this_machine_lacks() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        Files.writeString(output.resolve("other"), "other");
        cache(local).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        requests.clear();
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, target, "SHA-256")).isPresent();
        assertThat(requests).as("with every file on this machine a fetch is one request").containsExactly(
                "GET /step/01/" + fold(new byte[]{9}));
        assertThat(target.resolve("file")).content().isEqualTo("result");
        requests.clear();
        assertThat(cache(elsewhere).fetch(Runnable::run, "step", step, in, Files.createDirectory(elsewhere.resolve("target")), "SHA-256"))
                .isPresent();
        assertThat(requests).containsExactly("GET /step/01/" + fold(new byte[]{9}), "POST /blobs");
        assertThat(elsewhere.resolve("target").resolve("other")).content().isEqualTo("other");
        assertThat(elsewhere.resolve("sha-256").resolve("blobs").resolve(hex("other").substring(0, 2)).resolve(hex("other").substring(2)))
                .as("a downloaded file is kept for the next build of any project")
                .content()
                .isEqualTo("other");
    }

    @Test
    public void a_file_the_server_answers_with_other_content_is_a_miss() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        cache(local).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        blobs.put(hex("result"), "tampered".getBytes(StandardCharsets.UTF_8));
        assertThat(cache(elsewhere).fetch(Runnable::run, "step", step, in, target, "SHA-256")).isEmpty();
        try (Stream<Path> contents = Files.list(target)) {
            assertThat(contents.findAny()).isEmpty();
        }
        try (Stream<Path> files = Files.walk(elsewhere)) {
            assertThat(files.filter(Files::isRegularFile).filter(file -> !file.getFileName().toString().startsWith(".")).toList())
                    .as("nothing that is not what its name says is kept")
                    .isEmpty();
        }
    }

    @Test
    public void a_file_the_server_no_longer_holds_is_a_miss() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        Files.writeString(output.resolve("file"), "result");
        cache(local).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        blobs.clear();
        assertThat(cache(elsewhere).fetch(Runnable::run, "step", step, in, target, "SHA-256")).isEmpty();
        try (Stream<Path> contents = Files.list(target)) {
            assertThat(contents.findAny()).isEmpty();
        }
    }

    @Test
    public void a_manifest_naming_a_path_outside_the_target_is_a_miss() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        blobs.put(hex("result"), "result".getBytes(StandardCharsets.UTF_8));
        manifests.put("/step/01/" + fold(new byte[]{9}), (hex("result") + " 6 ../escape\n").getBytes(StandardCharsets.UTF_8));
        assertThat(cache(local).fetch(Runnable::run, "step", step, in, target, "SHA-256")).isEmpty();
        assertThat(target.resolveSibling("escape")).doesNotExist();
    }

    @Test
    public void an_answer_that_is_no_manifest_is_a_miss() throws IOException {
        manifests.put("/step/01/" + fold(new byte[]{9}), "<html>Sign in</html>".getBytes(StandardCharsets.UTF_8));
        assertThat(cache(local).fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), target, "SHA-256"))
                .isEmpty();
        assertThat(requests).hasSize(1);
    }

    @Test
    public void a_step_that_wrote_nothing_is_fetched_as_such() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        cache(local).store(Runnable::run, "step", step, in, output, "SHA-256", Map.of());
        Optional<Map<Path, byte[]>> fetched = cache(local).fetch(Runnable::run, "step", step, in, target, "SHA-256");
        assertThat(fetched).isPresent();
        assertThat(fetched.get()).isEmpty();
    }

    @Test
    public void reads_the_token_of_the_settings_and_refuses_the_name_it_had() throws IOException {
        BuildExecutorHttpCache cache = BuildExecutorHttpCache.ofEnvironment(
                new Environment(Map.of("cache.token", "team-alpha", "cache.local", local.toString())),
                uri);
        cache.fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), target, "SHA-256");
        assertThat(keys).containsExactly("team-alpha");
        assertThatThrownBy(() -> BuildExecutorHttpCache.ofEnvironment(new Environment(Map.of("cache.key", "team-alpha")), uri))
                .as("a setting under its former name would leave the cache server without a credential")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.cache.token");
    }

    @Test
    public void keeps_the_files_of_a_server_in_the_folder_the_settings_name() throws IOException {
        BuildExecutorHttpCache cache = BuildExecutorHttpCache.ofEnvironment(
                new Environment(Map.of("cache.local", local.toString())),
                uri);
        Files.writeString(output.resolve("file"), "result");
        cache.store(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), output, "SHA-256", Map.of());
        assertThat(local.resolve("sha-256").resolve("blobs").resolve(hex("result").substring(0, 2)).resolve(hex("result").substring(2)))
                .content()
                .isEqualTo("result");
    }

    @Test
    public void fetch_returns_empty_on_miss() throws IOException {
        assertThat(cache(local).fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), target, "SHA-256"))
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
        assertThat(cache(local).read(false).fetch(Runnable::run, "step", step, in, target, "SHA-256")).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    public void a_cache_that_hangs_does_not_hang_the_build() throws IOException {
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
            assertThat(cache.fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), target, "SHA-256"))
                    .isEmpty();
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(20));
            accepting.interrupt();
        }
    }

    @Test
    public void sends_the_token_and_the_project_as_headers_and_omits_them_when_unset() throws IOException {
        cache(local).fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), target, "SHA-256");
        assertThat(keys).containsExactly("team-alpha");
        assertThat(projects).containsExactly("demo");
        keys.clear();
        projects.clear();
        new BuildExecutorHttpCache(uri).local(new BuildExecutorFileCache(local))
                .fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), target, "SHA-256");
        assertThat(keys).containsExactly("null");
        assertThat(projects).containsExactly("null");
    }

    @Test
    public void refuses_to_send_the_token_over_plaintext_off_loopback() {
        BuildExecutorHttpCache cache = new BuildExecutorHttpCache(URI.create("http://cache.example.test/"))
                .local(new BuildExecutorFileCache(local))
                .token("team-alpha");
        assertThatThrownBy(() -> cache.fetch(Runnable::run, "step", new byte[]{1}, inputs("source", "file", new byte[]{9}), target, "SHA-256"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.cache.insecure");
    }

    @Test
    public void touch_sends_a_head_request_for_the_step() throws IOException {
        byte[] step = {1};
        SequencedMap<String, Map<Path, byte[]>> in = inputs("source", "file", new byte[]{9});
        cache(local).touch(Runnable::run, "step", step, in, "SHA-256");
        assertThat(requests).containsExactly("HEAD /step/01/" + fold(new byte[]{9}));
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

    private static String fold(byte[] hash) {
        try {
            MessageDigest message = MessageDigest.getInstance("SHA-256");
            message.update("source".getBytes(StandardCharsets.UTF_8));
            message.update((byte) 0);
            message.update("file".getBytes(StandardCharsets.UTF_8));
            message.update((byte) 0);
            message.update(hash);
            return HexFormat.of().formatHex(message.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static String hex(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private static SequencedMap<String, Map<Path, byte[]>> inputs(String argument, String file, byte[] hash) {
        Map<Path, byte[]> files = new LinkedHashMap<>();
        files.put(Path.of(file), hash);
        SequencedMap<String, Map<Path, byte[]>> inputs = new LinkedHashMap<>();
        inputs.put(argument, files);
        return inputs;
    }
}
