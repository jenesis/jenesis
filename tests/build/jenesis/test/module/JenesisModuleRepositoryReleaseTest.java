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
    private Path staged;
    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<String, String> received = new ConcurrentHashMap<>();
    private final Queue<Integer> statuses = new ConcurrentLinkedQueue<>();

    @BeforeEach
    public void setUp() throws IOException {
        staged = Files.createDirectory(root.resolve("staged"));
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            if (exchange.getRequestMethod().equals("GET")) {
                String content = received.get(exchange.getRequestURI().getPath());
                if (content == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
                exchange.close();
                return;
            }
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
    public void puts_only_the_jar_of_a_module_under_its_version() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        stage("demo.greeter", "1.0.0", "demo.greeter-sources.jar", "sources");
        stage("demo.greeter", "1.0.0", "demo.greeter.pom", "pom");

        BuildStepResult result = run(release());

        assertThat(result.next()).isTrue();
        assertThat(requests).containsExactly("PUT /repository/releases/module/demo.greeter/1.0.0/demo.greeter.jar null");
        assertThat(received).containsEntry("/repository/releases/module/demo.greeter/1.0.0/demo.greeter.jar", "classes");
    }

    @Test
    public void releases_what_a_module_repository_then_resolves_by_version() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");

        run(release());

        JenesisModuleRepository resolved = new JenesisModuleRepository(
                URI.create("http://localhost:" + server.getAddress().getPort() + "/repository/releases/module/"))
                .connection(new Repository.Connection().insecure(true));
        try (InputStream versioned = resolved.fetch(Runnable::run, "demo.greeter", null, "1.0.0", "jar")
                .orElseThrow()
                .toInputStream()) {
            assertThat(versioned).hasContent("classes");
        }
    }

    @Test
    public void refuses_a_module_staged_without_a_version_before_it_puts_anything() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        Files.createDirectories(staged.resolve("demo.app"));
        Files.writeString(staged.resolve("demo.app/demo.app.jar"), "app");

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.project.version");
        assertThat(requests).isEmpty();
    }

    @Test
    public void refuses_a_module_staged_at_more_than_one_version() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        stage("demo.greeter", "1.0.1", "demo.greeter.jar", "classes");

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one version");
        assertThat(requests).isEmpty();
    }

    @Test
    public void refuses_a_module_whose_jar_is_not_staged() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.pom", "pom");

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no demo.greeter.jar");
        assertThat(requests).isEmpty();
    }

    @Test
    public void puts_one_jar_per_module_with_the_token() throws IOException {
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
        stage("demo.app", "2.0.0", "demo.app.jar", "app");
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
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
    public void refuses_a_repository_that_is_not_addressed_by_http() {
        assertThatThrownBy(() -> new JenesisModuleRepositoryRelease(root.toUri()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(root.toUri().toString());
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
    public void prints_each_released_module_once() throws IOException {
        stage("demo.greeter", "1.0.0", "demo.greeter.jar", "classes");
        List<String> printed = new ArrayList<>();

        run(release().printing(printed::add));

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
