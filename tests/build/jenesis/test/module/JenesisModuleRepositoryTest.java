package build.jenesis.test.module;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JenesisModuleRepositoryTest {

    @TempDir
    private Path root;

    @BeforeEach
    public void setUp() throws IOException {
        System.setProperty("jenesis.module.local", Files.createDirectories(root.resolve("home")).toString());
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty("jenesis.module.local");
    }

    @Test
    public void credential_is_not_leaked_to_a_fallback_repository() throws IOException {
        System.setProperty("jenesis.repository.insecure", "true");
        System.setProperty("jenesis.module.token", "Bearer secret");
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
            System.setProperty("jenesis.module.uri",
                    "http://localhost:" + first.getAddress().getPort() + "/,"
                            + "http://localhost:" + second.getAddress().getPort() + "/");
            RepositoryItem item = JenesisModuleRepository.of(JenesisRepository.Scope.MODULE)
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
            System.clearProperty("jenesis.repository.insecure");
            System.clearProperty("jenesis.module.token");
            System.clearProperty("jenesis.module.uri");
        }
    }

    @Test
    public void network_item_can_be_read_more_than_once() throws IOException {
        System.setProperty("jenesis.repository.insecure", "true");
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
            RepositoryItem item = new JenesisModuleRepository(base)
                    .fetch(Runnable::run, "build.jenesis")
                    .orElseThrow();
            assertThat(read(item)).isEqualTo("classes");
            assertThat(read(item))
                    .as("a second read re-opens the connection instead of returning an exhausted stream")
                    .isEqualTo("classes");
        } finally {
            server.stop(0);
            System.clearProperty("jenesis.repository.insecure");
        }
    }

    @Test
    public void local_repository_is_consulted_before_the_remote() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("home/build.jenesis/1.0"))
                .resolve("build.jenesis.jar"), "local");
        Files.writeString(Files
                .createDirectories(root.resolve("remote/module/build.jenesis/1.0"))
                .resolve("build.jenesis.jar"), "remote");
        System.setProperty("jenesis.module.uri", root.resolve("remote").toUri().toString());
        try {
            RepositoryItem item = JenesisModuleRepository.of(JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "build.jenesis/1.0")
                    .orElseThrow();

            assertThat(read(item))
                    .as("the local repository must be consulted without an explicit prepend")
                    .isEqualTo("local");
        } finally {
            System.clearProperty("jenesis.module.uri");
        }
    }

    @Test
    public void remote_repository_serves_what_the_local_one_lacks() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("remote/module/build.jenesis/1.0"))
                .resolve("build.jenesis.jar"), "remote");
        System.setProperty("jenesis.module.uri", root.resolve("remote").toUri().toString());
        try {
            RepositoryItem item = JenesisModuleRepository.of(JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "build.jenesis/1.0")
                    .orElseThrow();

            assertThat(read(item)).isEqualTo("remote");
        } finally {
            System.clearProperty("jenesis.module.uri");
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

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri()).fetch(Runnable::run, "build.jenesis");

        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("classes");
        }
    }

    @Test
    public void fetches_versioned_module_from_version_subdirectory() throws IOException {
        Path versionDir = Files.createDirectories(root.resolve("build.jenesis/1.0.0"));
        Files.writeString(versionDir.resolve("build.jenesis.jar"), "v1-classes");

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
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

        RepositoryItem item = new JenesisModuleRepository(root.toUri())
                .fetch(Runnable::run, "build.jenesis")
                .orElseThrow();

        assertThat(item.file()).hasValue(jar);
    }

    @Test
    public void returns_empty_when_unversioned_module_is_missing() throws IOException {
        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri()).fetch(Runnable::run, "build.jenesis");

        assertThat(item).isEmpty();
    }

    @Test
    public void returns_empty_when_versioned_module_is_missing() throws IOException {
        Files.createDirectories(root.resolve("build.jenesis"));

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
                .fetch(Runnable::run, "build.jenesis/1.0.0");

        assertThat(item).isEmpty();
    }

    @Test
    public void normalises_uri_without_trailing_slash() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jar"), "classes");
        URI rootWithoutSlash = URI.create(root.toUri().toString().replaceAll("/$", ""));

        Optional<RepositoryItem> item = new JenesisModuleRepository(rootWithoutSlash).fetch(Runnable::run, "build.jenesis");

        assertThat(item).isPresent();
    }

    @Test
    public void does_not_confuse_module_with_a_prefix_relationship() throws IOException {
        Files.createDirectories(root.resolve("build.jenesis"));
        Path otherDir = Files.createDirectories(root.resolve("build.jenesis.extras"));
        Files.writeString(otherDir.resolve("build.jenesis.extras.jar"), "extras");

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri()).fetch(Runnable::run, "build.jenesis");

        assertThat(item).isEmpty();
    }

    @Test
    public void resolves_an_unversioned_pom_request_to_the_module_pom() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.pom"), "pom-bytes");

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
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

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
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

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
                .fetch(Runnable::run, "build.jenesis:pom");

        assertThat(item).isEmpty();
    }

    @Test
    public void does_not_serve_jar_when_pom_is_requested() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jar"), "classes");

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
                .fetch(Runnable::run, "build.jenesis:pom");

        assertThat(item).isEmpty();
    }

    @Test
    public void fetches_jmod_with_explicit_type() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jmod"), "jmod-bytes");

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
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

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
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

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
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

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
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

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
                .fetch(Runnable::run, "build.jenesis-win");

        assertThat(item).isEmpty();
    }

    @Test
    public void rejects_blank_classifier() {
        assertThatThrownBy(() -> new JenesisModuleRepository(root.toUri())
                .fetch(Runnable::run, "build.jenesis-"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("classifier");
    }

    @Test
    public void rejects_version_segment_containing_path_traversal() throws IOException {
        Path outside = Files.writeString(root.resolve("secret.jar"), "secret");

        assertThatThrownBy(() -> new JenesisModuleRepository(root.resolve("module").toUri())
                .fetch(Runnable::run, "build.jenesis/../../secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("traversal");

        assertThat(Files.exists(outside)).isTrue();
    }

    @Test
    public void rejects_version_with_unsafe_character() {
        assertThatThrownBy(() -> new JenesisModuleRepository(root.toUri())
                .fetch(Runnable::run, "build.jenesis/..%2f..%2fsecret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejects_module_name_with_path_separator() {
        assertThatThrownBy(() -> new JenesisModuleRepository(root.toUri())
                .fetch(Runnable::run, "build\\jenesis"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void jmod_request_falls_back_to_jar_when_no_jmod_is_published() throws IOException {
        Path moduleDir = Files.createDirectories(root.resolve("build.jenesis"));
        Files.writeString(moduleDir.resolve("build.jenesis.jar"), "classes");

        Optional<RepositoryItem> item = new JenesisModuleRepository(root.toUri())
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
        System.setProperty("jenesis.module.uri",
                root.resolve("first").toUri() + "," + root.resolve("second").toUri());
        try {
            Optional<RepositoryItem> item = JenesisModuleRepository.of(JenesisRepository.Scope.MODULE).fetch(Runnable::run, "build.jenesis");

            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("first-classes");
            }
        } finally {
            System.clearProperty("jenesis.module.uri");
        }
    }

    @Test
    public void factory_falls_back_to_later_repository_on_miss() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("second/module/build.jenesis"))
                .resolve("build.jenesis.jar"), "second-classes");
        System.setProperty("jenesis.module.uri",
                root.resolve("first").toUri() + "," + root.resolve("second").toUri());
        try {
            Optional<RepositoryItem> item = JenesisModuleRepository.of(JenesisRepository.Scope.MODULE).fetch(Runnable::run, "build.jenesis");

            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second-classes");
            }
        } finally {
            System.clearProperty("jenesis.module.uri");
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
        System.setProperty("jenesis.module.uri",
                root.resolve("first").toUri() + "|corp.mod," + root.resolve("second").toUri());
        try {
            Repository merged = JenesisModuleRepository.of(JenesisRepository.Scope.MODULE);
            try (InputStream stream = merged.fetch(Runnable::run, "corp.mod").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("first-corp");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "other.mod").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second-other");
            }
        } finally {
            System.clearProperty("jenesis.module.uri");
        }
    }

    @Test
    public void factory_artifact_scope_reads_the_artifact_subtree() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("first/artifact/build.jenesis"))
                .resolve("build.jenesis.jar"), "artifact-classes");
        System.setProperty("jenesis.module.uri", root.resolve("first").toUri().toString());
        try {
            Optional<RepositoryItem> item = JenesisModuleRepository.of(JenesisRepository.Scope.ARTIFACT)
                    .fetch(Runnable::run, "build.jenesis");

            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("artifact-classes");
            }
        } finally {
            System.clearProperty("jenesis.module.uri");
        }
    }

    @Test
    public void factory_resolves_named_reference_entries() throws IOException {
        Files.writeString(Files
                .createDirectories(root.resolve("first/module/build.jenesis"))
                .resolve("build.jenesis.jar"), "referenced-classes");
        System.setProperty("jenesis.module.uri", "@corp.test.modules");
        System.setProperty("corp.test.modules", root.resolve("first").toUri().toString());
        try {
            Optional<RepositoryItem> item = JenesisModuleRepository.of(JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "build.jenesis");

            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("referenced-classes");
            }
        } finally {
            System.clearProperty("jenesis.module.uri");
            System.clearProperty("corp.test.modules");
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
        System.setProperty("jenesis.module.uri",
                root.resolve("first").toUri() + "|corp," + root.resolve("second").toUri());
        try {
            Repository merged = JenesisModuleRepository.of(JenesisRepository.Scope.MODULE);
            try (InputStream stream = merged.fetch(Runnable::run, "corp.mod/1.0").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("first-corp");
            }
            try (InputStream stream = merged.fetch(Runnable::run, "corporate.mod/1.0").orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("second-corporate");
            }
        } finally {
            System.clearProperty("jenesis.module.uri");
        }
    }
}
