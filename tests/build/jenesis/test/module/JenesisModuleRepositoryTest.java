package build.jenesis.test.module;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JenesisModuleRepositoryTest {

    private final Map<String, String> settings = new HashMap<>();

    @TempDir
    private Path root;

    @BeforeEach
    public void setUp() throws IOException {
        settings.put("module.local", Files.createDirectories(root.resolve("home")).toString());
    }

    @AfterEach
    public void tearDown() {
        settings.remove("module.local");
    }

    @Test
    public void credential_is_not_leaked_to_a_fallback_repository() throws IOException {
        settings.put("repository.insecure", "true");
        settings.put("module.token", "Bearer secret");
        List<String> firstAuth = new ArrayList<>();
        List<String> secondAuth = new ArrayList<>();
        HttpServer first = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        first.createContext("/", exchange -> {
            firstAuth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        HttpServer second = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        second.createContext("/", exchange -> {
            secondAuth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "classes".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        first.start();
        second.start();
        try {
            settings.put("module.uri",
                    "http://localhost:" + first.getAddress().getPort() + "/,"
                            + "http://localhost:" + second.getAddress().getPort() + "/");
            RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(read(item)).isEqualTo("classes");
            assertThat(firstAuth).containsOnly("Bearer secret");
            assertThat(secondAuth)
                    .as("the private token must not reach the fallback mirror")
                    .containsOnlyNulls();
        } finally {
            first.stop(0);
            second.stop(0);
            settings.remove("repository.insecure");
            settings.remove("module.token");
            settings.remove("module.uri");
        }
    }

    @Test
    public void network_item_can_be_read_more_than_once() throws IOException {
        settings.put("repository.insecure", "true");
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "classes".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            URI base = URI.create("http://localhost:" + server.getAddress().getPort() + "/");
            RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), base)
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(read(item)).isEqualTo("classes");
            assertThat(read(item))
                    .as("a second read re-opens the connection instead of returning an exhausted stream")
                    .isEqualTo("classes");
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
        }
    }

    @Test
    public void names_the_configured_maven_repository_to_the_module_index() throws IOException {
        settings.put("repository.insecure", "true");
        settings.put("maven.uri", "https://repo.example.com/maven2/");
        List<Map<String, String>> requests = new ArrayList<>();
        HttpServer server = serving(requests);
        server.start();
        try {
            RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), 
                    URI.create("http://localhost:" + server.getAddress().getPort() + "/"))
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(read(item)).isEqualTo("classes");
            assertThat(requests).containsExactly(Map.of("Jenesis-Repository", "https://repo.example.com/maven2/"));
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
            settings.remove("maven.uri");
        }
    }

    @Test
    public void states_no_preference_when_no_property_is_set() throws IOException {
        settings.put("repository.insecure", "true");
        List<Map<String, String>> requests = new ArrayList<>();
        HttpServer server = serving(requests);
        server.start();
        try {
            JenesisModuleRepository.ofEnvironment(new Environment(settings::get), URI.create("http://localhost:" + server.getAddress().getPort() + "/"))
                    .maven(null)
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(requests)
                    .as("an unconfigured build leaves every choice to the module index")
                    .containsExactly(Map.of());
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
        }
    }

    @Test
    public void names_the_first_repository_that_serves_every_module() throws IOException {
        settings.put("repository.insecure", "true");
        settings.put("maven.uri", "https://internal.example.com/maven2/|com.example,"
                + "https://repo.example.com/maven2/");
        List<Map<String, String>> requests = new ArrayList<>();
        HttpServer server = serving(requests);
        server.start();
        try {
            JenesisModuleRepository.ofEnvironment(new Environment(settings::get), URI.create("http://localhost:" + server.getAddress().getPort() + "/"))
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(requests)
                    .as("a repository restricted to some modules cannot stand for the redirect of any")
                    .containsExactly(Map.of("Jenesis-Repository", "https://repo.example.com/maven2/"));
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
            settings.remove("maven.uri");
        }
    }

    @Test
    public void names_no_repository_that_the_module_index_could_not_reach() throws IOException {
        settings.put("repository.insecure", "true");
        settings.put("maven.uri", root.resolve("maven").toUri().toString());
        List<Map<String, String>> requests = new ArrayList<>();
        HttpServer server = serving(requests);
        server.start();
        try {
            JenesisModuleRepository.ofEnvironment(new Environment(settings::get), URI.create("http://localhost:" + server.getAddress().getPort() + "/"))
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(requests).containsExactly(Map.of());
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
            settings.remove("maven.uri");
        }
    }

    @Test
    public void accepts_a_prerelease_when_the_property_says_so() throws IOException {
        settings.put("repository.insecure", "true");
        settings.put("module.prerelease", "true");
        List<Map<String, String>> requests = new ArrayList<>();
        HttpServer server = serving(requests);
        server.start();
        try {
            JenesisModuleRepository.ofEnvironment(new Environment(settings::get), URI.create("http://localhost:" + server.getAddress().getPort() + "/"))
                    .maven(null)
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(requests)
                    .as("a build that names no Maven repository sends only what the prerelease property asked for")
                    .containsExactly(Map.of("Jenesis-Prerelease", "true"));
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
            settings.remove("module.prerelease");
        }
    }

    @Test
    public void refuses_a_speculative_version_when_the_property_says_so() throws IOException {
        settings.put("repository.insecure", "true");
        settings.put("module.speculative", "false");
        List<Map<String, String>> requests = new ArrayList<>();
        HttpServer server = serving(requests);
        server.start();
        try {
            JenesisModuleRepository.ofEnvironment(new Environment(settings::get), URI.create("http://localhost:" + server.getAddress().getPort() + "/"))
                    .maven(null)
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(requests)
                    .as("a build that names no Maven repository sends only what the speculative property asked for")
                    .containsExactly(Map.of("Jenesis-BestEffort", "false"));
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
            settings.remove("module.speculative");
        }
    }

    @Test
    public void a_wither_states_what_no_property_did() throws IOException {
        settings.put("repository.insecure", "true");
        List<Map<String, String>> requests = new ArrayList<>();
        HttpServer server = serving(requests);
        server.start();
        try {
            JenesisModuleRepository.ofEnvironment(new Environment(settings::get), URI.create("http://localhost:" + server.getAddress().getPort() + "/"))
                    .maven(URI.create("https://repo.example.com/maven2/"))
                    .prerelease(true)
                    .speculative(false)
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(requests).containsExactly(Map.of(
                    "Jenesis-Repository", "https://repo.example.com/maven2/",
                    "Jenesis-Prerelease", "true",
                    "Jenesis-BestEffort", "false"));
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
        }
    }

    @Test
    public void a_wither_takes_back_what_a_property_stated() throws IOException {
        settings.put("repository.insecure", "true");
        settings.put("maven.uri", "https://repo.example.com/maven2/");
        settings.put("module.prerelease", "true");
        List<Map<String, String>> requests = new ArrayList<>();
        HttpServer server = serving(requests);
        server.start();
        try {
            JenesisModuleRepository.ofEnvironment(new Environment(settings::get), URI.create("http://localhost:" + server.getAddress().getPort() + "/"))
                    .maven(null)
                    .prerelease(null)
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(requests).containsExactly(Map.of());
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
            settings.remove("maven.uri");
            settings.remove("module.prerelease");
        }
    }

    @Test
    public void states_nothing_to_a_redirect_target() throws IOException {
        settings.put("repository.insecure", "true");
        settings.put("maven.uri", "https://repo.example.com/maven2/");
        List<Map<String, String>> requests = new ArrayList<>();
        HttpServer target = serving(requests);
        HttpServer index = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        target.start();
        index.createContext("/", exchange -> {
            requests.add(Map.of("Jenesis-Repository",
                    exchange.getRequestHeaders().getFirst("Jenesis-Repository")));
            exchange.getResponseHeaders().add("Location",
                    "http://localhost:" + target.getAddress().getPort() + "/build.jenesis.jar");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        index.start();
        try {
            RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), 
                    URI.create("http://localhost:" + index.getAddress().getPort() + "/"))
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(read(item)).isEqualTo("classes");
            assertThat(requests)
                    .as("only the index decides the redirect, so only the index is told")
                    .containsExactly(Map.of("Jenesis-Repository", "https://repo.example.com/maven2/"), Map.of());
        } finally {
            index.stop(0);
            target.stop(0);
            settings.remove("repository.insecure");
            settings.remove("maven.uri");
        }
    }

    private static HttpServer serving(List<Map<String, String>> requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            SequencedMap<String, String> headers = new LinkedHashMap<>();
            for (String name : List.of("Jenesis-Repository", "Jenesis-Prerelease", "Jenesis-BestEffort")) {
                String value = exchange.getRequestHeaders().getFirst(name);
                if (value != null) {
                    headers.put(name, value);
                }
            }
            requests.add(headers);
            byte[] bytes = "classes".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        return server;
    }

    @Test
    public void local_repository_is_consulted_before_the_remote() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("home/build.jenesis/1.0"))
                .resolve("build.jenesis.jar"), "local");
        Files.writeString(Files
                .createDirectories(root.resolve("remote/module/build.jenesis/1.0"))
                .resolve("build.jenesis.jar"), "remote");
        settings.put("module.uri", root.resolve("remote").toUri().toString());
        try {
            RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "build.jenesis/1.0")
                    .orElseThrow();

            assertThat(read(item))
                    .as("the local repository must be consulted without an explicit prepend")
                    .isEqualTo("local");
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void remote_repository_serves_what_the_local_one_lacks() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("remote/module/build.jenesis/1.0"))
                .resolve("build.jenesis.jar"), "remote");
        settings.put("module.uri", root.resolve("remote").toUri().toString());
        try {
            RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "build.jenesis/1.0")
                    .orElseThrow();

            assertThat(read(item)).isEqualTo("remote");
        } finally {
            settings.remove("module.uri");
        }
    }

    private static String read(RepositoryItem item) throws IOException {
        try (InputStream stream = item.toInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void fetches_unversioned_module_from_root_module_directory() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jar"), "classes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri()).fetch(Runnable::run, "build.jenesis");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("classes");
        }
    }

    @Test
    public void fetches_versioned_module_from_version_subdirectory() throws IOException {
        Path versionDir = Files.createDirectories(root.resolve("build.jenesis/1.0.0"));
        Files.writeString(versionDir.resolve("build.jenesis.jar"), "v1-classes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis/1.0.0");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("v1-classes");
        }
    }

    @Test
    public void exposes_underlying_path_for_file_uri() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Path jar = Files.writeString(moduleDir.resolve("build.jenesis.jar"), "classes");

        RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis")
                .orElseThrow();

        assertThat(item.file()).hasValue(jar);
    }

    @Test
    public void returns_empty_when_unversioned_module_is_missing() throws IOException {
        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri()).fetch(Runnable::run, "build.jenesis");

        assertThat(item).isEmpty();
    }

    @Test
    public void returns_empty_when_versioned_module_is_missing() throws IOException {
        Files.createDirectories(root.resolve("build.jenesis"));

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis/1.0.0");

        assertThat(item).isEmpty();
    }

    @Test
    public void normalises_uri_without_trailing_slash() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jar"), "classes");
        URI rootWithoutSlash = URI.create(root.toUri().toString().replaceAll("/$", ""));

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), rootWithoutSlash).fetch(Runnable::run, "build.jenesis");

        assertThat(item).isPresent();
    }

    @Test
    public void does_not_confuse_module_with_a_prefix_relationship() throws IOException {
        Files.createDirectories(root.resolve("build.jenesis"));
        Path otherDir = Files.createDirectories(root.resolve("build.jenesis.extras"));
        Files.writeString(otherDir.resolve("build.jenesis.extras.jar"), "extras");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri()).fetch(Runnable::run, "build.jenesis");

        assertThat(item).isEmpty();
    }

    @Test
    public void resolves_an_unversioned_pom_request_to_the_module_pom() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.pom"), "pom-bytes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis:pom");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("pom-bytes");
        }
    }

    @Test
    public void resolves_a_versioned_pom_request_to_the_module_pom() throws IOException {
        Path versionDir = Files.createDirectories(root.resolve("build.jenesis/1.0.0"));
        Files.writeString(versionDir.resolve("build.jenesis.pom"), "v1-pom-bytes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis/1.0.0:pom");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("v1-pom-bytes");
        }
    }

    @Test
    public void does_not_serve_a_legacy_pom_xml_for_a_pom_request() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("pom.xml"), "legacy-pom");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis:pom");

        assertThat(item).isEmpty();
    }

    @Test
    public void does_not_serve_jar_when_pom_is_requested() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jar"), "classes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis:pom");

        assertThat(item).isEmpty();
    }

    @Test
    public void fetches_jmod_with_explicit_type() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jmod"), "jmod-bytes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis:jmod");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("jmod-bytes");
        }
    }

    @Test
    public void fetches_classified_module_from_version_subdirectory() throws IOException {
        Path versionDir = Files.createDirectories(root.resolve("build.jenesis/1.0.0"));
        Files.writeString(versionDir.resolve("build.jenesis-windows-x86_64.jar"), "native-classes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis-windows-x86_64/1.0.0");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("native-classes");
        }
    }

    @Test
    public void fetches_unversioned_classified_module() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis-win.jar"), "native-classes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis-win");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("native-classes");
        }
    }

    @Test
    public void resolves_a_classified_pom_request_with_its_classifier() throws IOException {
        Path versionDir = Files.createDirectories(root.resolve("build.jenesis/1.0.0"));
        Files.writeString(versionDir.resolve("build.jenesis-win.pom"), "classified-pom");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis-win/1.0.0:pom");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("classified-pom");
        }
    }

    @Test
    public void does_not_serve_unclassified_jar_for_classified_request() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jar"), "classes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis-win");

        assertThat(item).isEmpty();
    }

    @Test
    public void rejects_blank_classifier() {
        assertThatThrownBy(() -> JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis-"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("classifier");
    }

    @Test
    public void rejects_version_segment_containing_path_traversal() throws IOException {
        Path outside = Files.writeString(root.resolve("secret.jar"), "secret");

        assertThatThrownBy(() -> JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.resolve("module").toUri())
                .fetch(Runnable::run, "build.jenesis/../../secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traversal");

        assertThat(Files.exists(outside)).isTrue();
    }

    @Test
    public void rejects_version_with_unsafe_character() {
        assertThatThrownBy(() -> JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis/..%2f..%2fsecret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejects_module_name_with_path_separator() {
        assertThatThrownBy(() -> JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build\\jenesis"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void jmod_request_falls_back_to_jar_when_no_jmod_is_published() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jar"), "classes");

        Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), root.toUri())
                .fetch(Runnable::run, "build.jenesis:jmod");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("classes");
        }
    }

    @Test
    public void factory_queries_comma_separated_repositories_in_declared_order() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/build.jenesis"))
                .resolve("build.jenesis.jar"), "first-classes");
        Files.writeString(Files
                .createDirectories(root.resolve("second/module/build.jenesis"))
                .resolve("build.jenesis.jar"), "second-classes");
        settings.put("module.uri",
                root.resolve("first").toUri() + "," + root.resolve("second").toUri());
        try {
            Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE).fetch(Runnable::run, "build.jenesis");

            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("first-classes");
            }
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_falls_back_to_later_repository_on_miss() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("second/module/build.jenesis"))
                .resolve("build.jenesis.jar"), "second-classes");
        settings.put("module.uri",
                root.resolve("first").toUri() + "," + root.resolve("second").toUri());
        try {
            Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE).fetch(Runnable::run, "build.jenesis");

            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second-classes");
            }
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_filter_argument_restricts_repository_to_matching_module_ids() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/corp.mod"))
                .resolve("corp.mod.jar"), "first-corp");
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/other.mod"))
                .resolve("other.mod.jar"), "first-other");
        Files.writeString(Files
                .createDirectories(root.resolve("second/module/corp.mod"))
                .resolve("corp.mod.jar"), "second-corp");
        Files.writeString(Files
                .createDirectories(root.resolve("second/module/other.mod"))
                .resolve("other.mod.jar"), "second-other");
        settings.put("module.uri",
                root.resolve("first").toUri() + "|corp.mod," + root.resolve("second").toUri());
        try {
            Repository merged = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE);
            try (InputStream stream = merged.fetch(Runnable::run, "corp.mod").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("first-corp");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "other.mod").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second-other");
            }
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_filter_argument_accepts_several_module_prefixes() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/corp.mod.inner"))
                .resolve("corp.mod.inner.jar"), "first-inner");
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/team.mod"))
                .resolve("team.mod.jar"), "first-team");
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/other.mod"))
                .resolve("other.mod.jar"), "first-other");
        Files.writeString(Files
                .createDirectories(root.resolve("second/module/other.mod"))
                .resolve("other.mod.jar"), "second-other");
        settings.put("module.uri",
                root.resolve("first").toUri() + "|corp|team.mod," + root.resolve("second").toUri());
        try {
            Repository merged = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE);
            try (InputStream stream = merged.fetch(Runnable::run, "corp.mod.inner").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("first-inner");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "team.mod").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("first-team");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "other.mod").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .as("a module no prefix covers is left to the next remote")
                        .isEqualTo("second-other");
            }
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_maven_type_resolves_a_module_by_the_publishing_convention() throws IOException {
        writeMavenArtifact("com.corp", "com.corp.mod", "company-classes");
        writeMavenArtifact("other.mod", "other.mod", "company-other");
        Files.writeString(Files
                .createDirectories(root.resolve("public/module/other.mod/1.0.0"))
                .resolve("other.mod.jar"), "public-classes");
        settings.put("module.uri",
                "maven:" + root.resolve("company").toUri() + "|com.corp," + root.resolve("public").toUri());
        try {
            Repository merged = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE);
            try (InputStream stream = merged.fetch(Runnable::run, "com.corp.mod/1.0.0").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("company-classes");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "other.mod/1.0.0").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .as("the Maven remote is asked only for the modules its prefix covers")
                        .isEqualTo("public-classes");
            }
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_maven_type_carries_into_a_referenced_chain() throws IOException {
        writeMavenArtifact("com.corp", "com.corp.mod", "company-classes");
        settings.put("corp.test.modules", root.resolve("company").toUri().toString());
        settings.put("module.uri", "maven:@corp.test.modules");
        try {
            Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "com.corp.mod/1.0.0");

            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("company-classes");
            }
        } finally {
            settings.remove("module.uri");
            settings.remove("corp.test.modules");
        }
    }

    @Test
    public void factory_maven_type_takes_a_group_id_segment_count_per_entry() throws IOException {
        writeMavenArtifact("deep", "com.corp.deep", "com.corp.deep.mod", "deep-classes");
        writeMavenArtifact("flat", "org.tools", "org.tools.mod", "flat-classes");
        settings.put("module.uri", "maven:3:"
                + root.resolve("deep").toUri()
                + "|com.corp,maven:"
                + root.resolve("flat").toUri()
                + "|org.tools");
        try {
            Repository merged = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE);
            try (InputStream stream = merged.fetch(Runnable::run, "com.corp.deep.mod/1.0.0")
                    .orElseThrow()
                    .toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .as("three segments of the name form the group id of this remote")
                        .isEqualTo("deep-classes");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "org.tools.mod/1.0.0")
                    .orElseThrow()
                    .toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .as("a remote without a count keeps the default of two")
                        .isEqualTo("flat-classes");
            }
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_rejects_a_segment_count_below_one() {
        settings.put("module.uri", "maven:0:https://repo.example.com/");
        try {
            assertThatThrownBy(() -> JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one group id segment");
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_rejects_a_segment_count_on_a_module_entry() {
        settings.put("module.uri", "module:3:https://repo.example.com/");
        try {
            assertThatThrownBy(() -> JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("applies only to a 'maven' entry");
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_rejects_an_unknown_repository_type() {
        settings.put("module.uri", "nexus:https://repo.example.com/");
        try {
            assertThatThrownBy(() -> JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("nexus:https://repo.example.com/")
                    .hasMessageContaining("expected 'module' or 'maven'");
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_rejects_a_typed_entry_without_a_uri() {
        settings.put("module.uri", "maven:");
        try {
            assertThatThrownBy(() -> JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No URI in Jenesis module repository entry: maven:");
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_withholds_the_credential_from_a_remote_a_project_file_named() throws IOException {
        List<String> authorizations = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] body = "classes".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
            exchange.close();
        });
        server.start();
        settings.put("repository.insecure", "true");
        settings.put("module.token", "Bearer secret");
        settings.put("module.uri", "http://localhost:" + server.getAddress().getPort() + "/");
        settings.put("make.provided", "module.uri");
        try {
            RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();

            assertThat(read(item)).isEqualTo("classes");
            assertThat(authorizations)
                    .as("a url a file the project provides named never receives the credential")
                    .containsOnlyNulls();
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
            settings.remove("module.token");
            settings.remove("module.uri");
            settings.remove("make.provided");
        }
    }

    @Test
    public void factory_maven_type_authenticates_the_first_remote_of_the_chain() throws IOException {
        List<String> authorizations = new ArrayList<>();
        HttpServer server = mavenServer(authorizations);
        settings.put("repository.insecure", "true");
        settings.put("maven.local", Files.createDirectories(root.resolve("m2")).toString());
        settings.put("maven.token", "Bearer secret");
        settings.put("module.uri", "maven:http://localhost:" + server.getAddress().getPort() + "/");
        try {
            RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "com.corp.mod/1.0.0")
                    .orElseThrow();

            assertThat(read(item)).isEqualTo("classes");
            assertThat(authorizations).contains("Bearer secret");
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
            settings.remove("maven.local");
            settings.remove("maven.token");
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_maven_type_is_not_handed_the_credential_of_an_earlier_remote() throws IOException {
        List<String> authorizations = new ArrayList<>();
        HttpServer server = mavenServer(authorizations);
        settings.put("repository.insecure", "true");
        settings.put("maven.local", Files.createDirectories(root.resolve("m2")).toString());
        settings.put("maven.token", "Bearer secret");
        settings.put("module.token", "Bearer other");
        settings.put("module.uri", Files.createDirectories(root.resolve("empty")).toUri()
                + ",maven:http://localhost:" + server.getAddress().getPort() + "/");
        try {
            RepositoryItem item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "com.corp.mod/1.0.0")
                    .orElseThrow();

            assertThat(read(item)).isEqualTo("classes");
            assertThat(authorizations)
                    .as("a fallback remote is never handed the credential of the chain")
                    .containsOnlyNulls();
        } finally {
            server.stop(0);
            settings.remove("repository.insecure");
            settings.remove("maven.local");
            settings.remove("maven.token");
            settings.remove("module.token");
            settings.remove("module.uri");
        }
    }

    private HttpServer mavenServer(List<String> authorizations) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (exchange.getRequestURI().getPath().equals("/com/corp/com.corp.mod/1.0.0/com.corp.mod-1.0.0.jar")) {
                byte[] body = "classes".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private void writeMavenArtifact(String groupId, String artifactId, String content) throws IOException {
        writeMavenArtifact("company", groupId, artifactId, content);
    }

    private void writeMavenArtifact(String repository, String groupId, String artifactId, String content)
            throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve(repository)
                        .resolve(Path.of(groupId.replace('.', File.separatorChar)))
                        .resolve(artifactId)
                        .resolve("1.0.0"))
                .resolve(artifactId + "-1.0.0.jar"), content);
    }

    @Test
    public void factory_artifact_scope_reads_the_artifact_subtree() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("first/artifact/build.jenesis"))
                .resolve("build.jenesis.jar"), "artifact-classes");
        settings.put("module.uri", root.resolve("first").toUri().toString());
        try {
            Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.ARTIFACT)
                    .fetch(Runnable::run, "build.jenesis");

            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("artifact-classes");
            }
        } finally {
            settings.remove("module.uri");
        }
    }

    @Test
    public void factory_resolves_named_reference_entries() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/build.jenesis"))
                .resolve("build.jenesis.jar"), "referenced-classes");
        settings.put("module.uri", "@corp.test.modules");
        settings.put("corp.test.modules", root.resolve("first").toUri().toString());
        try {
            Optional<RepositoryItem> item = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "build.jenesis");

            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("referenced-classes");
            }
        } finally {
            settings.remove("module.uri");
            settings.remove("corp.test.modules");
        }
    }

    @Test
    public void factory_filter_matches_versioned_sub_modules_on_dot_boundary() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/corp.mod/1.0"))
                .resolve("corp.mod.jar"), "first-corp");
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/corporate.mod/1.0"))
                .resolve("corporate.mod.jar"), "first-corporate");
        Files.writeString(Files
                .createDirectories(root.resolve("second/module/corp.mod/1.0"))
                .resolve("corp.mod.jar"), "second-corp");
        Files.writeString(Files
                .createDirectories(root.resolve("second/module/corporate.mod/1.0"))
                .resolve("corporate.mod.jar"), "second-corporate");
        settings.put("module.uri",
                root.resolve("first").toUri() + "|corp," + root.resolve("second").toUri());
        try {
            Repository merged = JenesisModuleRepository.ofEnvironment(new Environment(settings::get), JenesisRepository.Scope.MODULE);
            try (InputStream stream = merged.fetch(Runnable::run, "corp.mod/1.0").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("first-corp");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "corporate.mod/1.0").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second-corporate");
            }
        } finally {
            settings.remove("module.uri");
        }
    }
}
