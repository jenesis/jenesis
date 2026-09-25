package build.jenesis.test.module;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisModuleRepositoryRelease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JenesisModuleRepositoryReleaseTest {

    @TempDir
    private Path root;
    private Path staged, repository;
    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private final Queue<Integer> statuses = new ConcurrentLinkedQueue<>();

    @BeforeEach
    public void setUp() throws IOException {
        staged = Files.createDirectory(root.resolve("staged"));
        repository = root.resolve("repository");
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            requests.add(exchange.getRequestMethod()
                    + " " + exchange.getRequestURI().getPath()
                    + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            Integer status = statuses.poll();
            if (status == null) {
                received.put(exchange.getRequestURI().getPath(), new String(body, StandardCharsets.UTF_8));
                status = 201;
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void releases_each_file_under_its_version_and_nothing_else() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        stage("demo.greeter", "1.0.0", "demo.greeter-sources.jar", "sources");

        BuildStepResult result = run(new JenesisModuleRepositoryRelease(repository.toUri()).printing(null));

        assertThat(result.next()).isTrue();
        assertThat(repository.resolve("module/demo.greeter/1.0.0/demo.greeter.jar")).hasContent("classes");
        assertThat(repository.resolve("module/demo.greeter/1.0.0/demo.greeter-sources.jar")).hasContent("sources");
        assertThat(repository.resolve("module/demo.greeter/demo.greeter.jar")).doesNotExist();
        assertThat(repository.resolve("module/demo.greeter/demo.greeter-sources.jar")).doesNotExist();
    }

    @Test
    public void releases_what_a_module_repository_then_resolves_by_version() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");

        run(new JenesisModuleRepositoryRelease(repository.toUri()).printing(null));

        JenesisModuleRepository resolved = new JenesisModuleRepository(repository.resolve("module").toUri());
        try (InputStream versioned = resolved.fetch(Runnable::run, "demo.greeter", null, "1.0.0", "jar")
                .orElseThrow()
                .toInputStream()) {
            assertThat(versioned).hasContent("classes");
        }
    }

    @Test
    public void leaves_a_version_released_with_the_same_content_in_place() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        run(new JenesisModuleRepositoryRelease(repository.toUri()).printing(null));

        BuildStepResult result = run(new JenesisModuleRepositoryRelease(repository.toUri()).printing(null));

        assertThat(result.next()).isTrue();
        assertThat(repository.resolve("module/demo.greeter/1.0.0/demo.greeter.jar")).hasContent("classes");
    }

    @Test
    public void refuses_to_replace_a_released_version_but_releases_a_new_one() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        run(new JenesisModuleRepositoryRelease(repository.toUri()).printing(null));
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "changed");

        assertThatThrownBy(() -> run(new JenesisModuleRepositoryRelease(repository.toUri()).printing(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("release under a new version");
        assertThat(repository.resolve("module/demo.greeter/1.0.0/demo.greeter.jar")).hasContent("classes");

        Files.move(staged.resolve("demo.greeter/1.0.0"), staged.resolve("demo.greeter/1.0.1"));
        run(new JenesisModuleRepositoryRelease(repository.toUri()).printing(null));

        assertThat(repository.resolve("module/demo.greeter/1.0.1/demo.greeter.jar")).hasContent("changed");
    }

    @Test
    public void refuses_a_module_staged_without_a_version() throws IOException {
        Files.createDirectories(staged.resolve("demo.greeter"));
        Files.writeString(staged.resolve("demo.greeter/demo.greeter.jar"), "classes");

        assertThatThrownBy(() -> run(new JenesisModuleRepositoryRelease(repository.toUri()).printing(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.project.version");
        assertThat(repository).doesNotExist();
    }

    @Test
    public void puts_each_file_under_its_version_with_the_token() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "greeter");
        stage("demo.app", "2.0.0", "demo.app.jar", "app");

        run(release().token("Bearer secret"));

        assertThat(requests).containsExactly(
                "PUT /repository/releases/module/demo.app/2.0.0/demo.app.jar Bearer secret",
                "PUT /repository/releases/module/demo.greeter/1.0.0/demo.greeter.jar Bearer secret");
        assertThat(received)
                .containsEntry("/repository/releases/module/demo.app/2.0.0/demo.app.jar", "app")
                .containsEntry("/repository/releases/module/demo.greeter/1.0.0/demo.greeter.jar", "greeter");
    }

    @Test
    public void stops_when_the_repository_holds_a_version_already() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        stage("demo.greeter", "1.0.0", "demo.greeter.pom", "pom");
        statuses.add(409);

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("release under a new version");
        assertThat(requests).hasSize(1);
    }

    @Test
    public void names_the_token_when_the_repository_refuses_to_accept_a_release() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        statuses.add(403);

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.release.token");
    }

    @Test
    public void retries_a_release_the_repository_could_not_take_at_once() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        statuses.add(503);

        run(release().connection(new Repository.Connection().insecure(true).retries(1).backoff(Duration.ZERO)));

        assertThat(requests).hasSize(2);
        assertThat(received).containsEntry("/repository/releases/module/demo.greeter/1.0.0/demo.greeter.jar", "classes");
    }

    @Test
    public void refuses_a_plaintext_repository_unless_it_is_allowed() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");

        assertThatThrownBy(() -> run(release().connection(new Repository.Connection())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.repository.insecure");
        assertThat(requests).isEmpty();
    }

    @Test
    public void releases_over_http_only_where_the_environment_allows_a_plaintext_repository() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/repository/releases");

        assertThatThrownBy(() -> run(JenesisModuleRepositoryRelease.ofEnvironment(
                new Environment(Map.of("release.uri", uri.toString())), uri).printing(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.repository.insecure");
        assertThat(requests).isEmpty();

        run(JenesisModuleRepositoryRelease.ofEnvironment(
                new Environment(Map.of("release.uri", uri.toString(), "repository.insecure", "true")), uri).printing(null));

        assertThat(received).containsEntry("/repository/releases/module/demo.greeter/1.0.0/demo.greeter.jar", "classes");
    }

    @Test
    public void refuses_a_repository_that_is_not_addressed_by_a_supported_scheme() {
        assertThatThrownBy(() -> new JenesisModuleRepositoryRelease(URI.create("ftp://example.com/releases")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ftp://example.com/releases");
    }

    @Test
    public void sends_no_token_to_a_repository_the_project_named_itself() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/repository/releases");
        Environment environment = new Environment(Map.of("release.uri", uri.toString(),
                "release.token", "Bearer secret",
                "repository.insecure", "true",
                "make.provided", "release.uri"));

        run(JenesisModuleRepositoryRelease.ofEnvironment(environment, JenesisModuleRepositoryRelease.configured(environment)).printing(null));

        assertThat(requests).allMatch(request -> request.endsWith(" null"));
    }

    @Test
    public void sends_the_token_to_a_repository_the_command_line_named() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        URI uri = URI.create("http://localhost:" + server.getAddress().getPort() + "/repository/releases");
        Environment environment = new Environment(Map.of("release.uri", uri.toString(),
                "release.token", "Bearer secret",
                "repository.insecure", "true"));

        run(JenesisModuleRepositoryRelease.ofEnvironment(environment, JenesisModuleRepositoryRelease.configured(environment)).printing(null));

        assertThat(requests).allMatch(request -> request.endsWith(" Bearer secret"));
    }

    @Test
    public void prints_each_released_file_once() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        List<String> printed = new ArrayList<>();

        run(new JenesisModuleRepositoryRelease(repository.toUri()).printing(printed::add));

        assertThat(printed).hasSize(1);
        assertThat(printed.getFirst())
                .contains("[RELEASED]")
                .endsWith("module/demo.greeter/1.0.0/demo.greeter.jar");
    }

    private JenesisModuleRepositoryRelease release() {
        return new JenesisModuleRepositoryRelease(URI.create("http://localhost:" + server.getAddress().getPort() + "/repository/releases"))
                .connection(new Repository.Connection().insecure(true).retries(0))
                .printing(null);
    }

    private void stage(String module, String version, String name, String content) throws IOException {
        Path folder = Files.createDirectories(staged.resolve(module).resolve(version));
        Files.writeString(folder.resolve(name), content);
    }

    private BuildStepResult run(JenesisModuleRepositoryRelease release) throws IOException {
        return release.apply(Runnable::run,
                        new BuildStepContext(root.resolve("previous"),
                                Files.createDirectories(root.resolve("next")),
                                Files.createDirectories(root.resolve("supplement"))),
                        new LinkedHashMap<>(Map.of("staged", new BuildStepArgument(
                                staged,
                                Map.of(Path.of("."), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
    }
}
