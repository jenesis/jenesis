package build.jenesis.test;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutorFileCache;
import build.jenesis.BuildExecutorHttpCache;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Environment;
import build.jenesis.Json;
import build.jenesis.Repository;
import build.jenesis.Repository.Credential;
import build.jenesis.Repository.Credential.Identity;
import build.jenesis.Repository.Credential.Source;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenRepositoryRelease;
import build.jenesis.module.JenesisModuleRepositoryRelease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RepositoryCredentialTest {

    private static final String EXCHANGE = "/api/token",
            DEVIATING = "/access/api/v1/oidc/token",
            IDENTITY = "/identity",
            REPOSITORY = "/repository/default/maven/",
            CACHE = "/build/default/";

    @TempDir
    private Path folder;

    private final List<String> exchanged = new CopyOnWriteArrayList<>(),
            encodings = new CopyOnWriteArrayList<>(),
            requested = new CopyOnWriteArrayList<>(),
            authorizations = new CopyOnWriteArrayList<>(),
            everything = new CopyOnWriteArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200), lifetime = new AtomicInteger(3600);
    private HttpServer server;
    private Path identity;

    @BeforeEach
    public void setUp() throws IOException {
        identity = Files.writeString(folder.resolve("identity"), "job.identity.token\n");
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                everything.add(exchange.getRequestURI() + " " + exchange.getRequestHeaders() + " " + body);
                byte[] answer;
                int code = 200;
                if (path.equals(EXCHANGE) || path.equals(DEVIATING)) {
                    exchanged.add(body);
                    encodings.add(exchange.getRequestHeaders().getFirst("Content-Type"));
                    code = status.get();
                    answer = (code == 200
                            ? "{\"access_token\":\"issued-" + exchanged.size() + "\",\"issued_token_type\":"
                                    + "\"urn:ietf:params:oauth:token-type:access_token\",\"token_type\":\"Bearer\""
                                    + (lifetime.get() < 0 ? "" : ",\"expires_in\":" + lifetime.get()) + "}"
                            : "{\"error\":\"invalid_request\",\"error_description\":\"no trust admits the token\"}")
                            .getBytes(StandardCharsets.UTF_8);
                } else if (path.equals(IDENTITY)) {
                    requested.add(exchange.getRequestHeaders().getFirst("Authorization")
                            + " " + exchange.getRequestURI().getRawQuery());
                    answer = "{\"count\":1,\"value\":\"github.identity.token\"}".getBytes(StandardCharsets.UTF_8);
                } else {
                    authorizations.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
                    answer = "classes".getBytes(StandardCharsets.UTF_8);
                }
                exchange.sendResponseHeaders(code, answer.length);
                exchange.getResponseBody().write(answer);
            }
        });
        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void exchanges_the_identity_token_as_rfc_8693_lays_the_request_out() throws IOException {
        fetch(credential(new Source.Exchange(EXCHANGE, new Identity.File(identity))));

        assertThat(encodings).containsExactly("application/x-www-form-urlencoded");
        assertThat(form(exchanged.getFirst())).containsExactly(
                Map.entry("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange"),
                Map.entry("subject_token", "job.identity.token"),
                Map.entry("subject_token_type", "urn:ietf:params:oauth:token-type:id_token"),
                Map.entry("resource", origin() + REPOSITORY));
        assertThat(authorizations)
                .as("the token the server issued is what the repository is sent, as a bearer token")
                .containsExactly("Bearer issued-1");
    }

    @Test
    public void accepts_the_exchange_as_a_url_on_the_host_of_the_repository() throws IOException {
        fetch(credential(new Source.Exchange(origin() + EXCHANGE, new Identity.File(identity))));

        assertThat(authorizations).containsExactly("Bearer issued-1");
    }

    @Test
    public void refuses_an_exchange_on_another_host_port_or_scheme() {
        URI repository = URI.create("https://repository.example/maven/");
        for (String location : List.of("https://tokens.example/api/token",
                "https://repository.example:8443/api/token",
                "http://repository.example/api/token",
                "//tokens.example/api/token")) {
            Credential credential = new Credential(new Source.Exchange(location, new Identity.File(identity)),
                    Repository.Origin.USER);
            assertThatThrownBy(() -> credential.target(repository))
                    .as("an identity token is exchanged only with the server the issued token is sent to")
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Refusing to exchange an identity token at")
                    .hasMessageContaining("https://repository.example/maven/");
            assertThatThrownBy(() -> MavenDefaultRepository.of(repository, credential))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new MavenRepositoryRelease(repository).credential(credential))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new JenesisModuleRepositoryRelease(repository).credential(credential))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BuildExecutorHttpCache(repository).credential(credential))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(everything).isEmpty();
    }

    @Test
    public void refuses_to_exchange_over_plaintext_without_the_opt_in() {
        Credential credential = credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)));

        assertThatThrownBy(() -> credential.authorization(new Repository.Connection()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insecure scheme 'http'");
        assertThat(everything).isEmpty();
    }

    @Test
    public void requests_the_identity_token_of_github_actions_for_the_origin_of_the_exchange() throws IOException {
        Identity github = new Identity.GitHub(URI.create(origin() + IDENTITY + "?api-version=2.0"), "request.token");

        fetch(credential(new Source.Exchange(EXCHANGE, github)));

        assertThat(requested).containsExactly("Bearer request.token api-version=2.0&audience="
                + URLEncoder.encode(origin(), StandardCharsets.UTF_8));
        assertThat(form(exchanged.getFirst())).containsEntry("subject_token", "github.identity.token");
        assertThat(authorizations).containsExactly("Bearer issued-1");
    }

    @Test
    public void requests_the_identity_token_of_github_actions_for_the_audience_it_is_given() throws IOException {
        Identity github = new Identity.GitHub(URI.create(origin() + IDENTITY + "?api-version=2.0"), "request.token");

        fetch(credential(new Source.Exchange(EXCHANGE, github).audience("jenesis repository")));

        assertThat(requested).containsExactly("Bearer request.token api-version=2.0&audience=jenesis+repository");
    }

    @Test
    public void writes_the_request_of_a_deviating_server_as_its_settings_describe_it() throws IOException {
        Map<String, String> settings = Map.of("maven.exchange", DEVIATING,
                "maven.encoding", "json",
                "maven.parameters", "provider_name=github-oidc, resource=",
                "maven.identity", "file " + identity);

        fetch(Credential.of(new Environment(settings), "maven.token", "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET")
                .target(URI.create(origin() + REPOSITORY)));

        assertThat(encodings).containsExactly("application/json");
        assertThat(Json.parse(exchanged.getFirst())).isEqualTo(Map.of(
                "grant_type", "urn:ietf:params:oauth:grant-type:token-exchange",
                "subject_token", "job.identity.token",
                "subject_token_type", "urn:ietf:params:oauth:token-type:id_token",
                "provider_name", "github-oidc"));
        assertThat(authorizations).containsExactly("Bearer issued-1");
    }

    @Test
    public void reads_the_identity_token_from_where_the_shared_setting_names_it() throws IOException {
        fetch(Credential.of(new Environment(Map.of("deploy.exchange", EXCHANGE, "exchange.identity", "file " + identity)),
                        "deploy.token",
                        "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET")
                .target(URI.create(origin() + REPOSITORY)));

        assertThat(form(exchanged.getFirst())).containsEntry("subject_token", "job.identity.token");
    }

    @Test
    public void reads_the_identity_token_from_an_environment_variable() throws IOException {
        fetch(credential(new Source.Exchange(EXCHANGE, new Identity.Variable("PATH"))));

        assertThat(form(exchanged.getFirst())).containsEntry("subject_token", System.getenv("PATH").strip());
    }

    @Test
    public void fails_naming_the_fix_where_no_identity_token_is_found() {
        Credential credential = credential(new Source.Exchange(EXCHANGE,
                new Identity.Variable("JENESIS_TEST_TOKEN_THAT_IS_NOT_SET")));

        assertThatThrownBy(() -> fetch(credential))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("JENESIS_TEST_TOKEN_THAT_IS_NOT_SET holds no identity token")
                .hasMessageContaining("jenesis.exchange.identity");
        assertThat(everything).isEmpty();
    }

    @Test
    public void refuses_an_identity_that_is_written_as_a_value() {
        assertThatThrownBy(() -> Credential.of(
                new Environment(Map.of("maven.exchange", EXCHANGE, "maven.identity", "job.identity.token")),
                "maven.token",
                "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected 'env <variable>' or 'file <path>'");
    }

    @Test
    public void refuses_two_sources_of_one_credential() {
        assertThatThrownBy(() -> Credential.of(
                new Environment(Map.of("maven.token", "Bearer secret", "maven.exchange", EXCHANGE)),
                "maven.token",
                "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.maven.token and jenesis.maven.exchange");
        assertThatThrownBy(() -> Credential.of(
                new Environment(Map.of("cache.helper", "issue-token", "cache.exchange", EXCHANGE)),
                "cache.token",
                "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.cache.exchange and jenesis.cache.helper");
        assertThatThrownBy(() -> Credential.of(new Environment(Map.of("release.exchange", EXCHANGE)),
                "release.token",
                "PATH"))
                .as("a token of the environment is a source like any other")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("the environment variable PATH and jenesis.release.exchange");
    }

    @Test
    public void each_credential_reads_the_settings_of_its_own_group() {
        Environment environment = new Environment(Map.of("maven.exchange", EXCHANGE,
                "maven.identity", "env MAVEN_IDENTITY",
                "cache.helper", "issue-token --cache",
                "deploy.token", "Basic secret"));

        assertThat(Credential.of(environment, "maven.token", "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET").source())
                .isEqualTo(new Source.Exchange(EXCHANGE, new Identity.Variable("MAVEN_IDENTITY")));
        assertThat(Credential.of(environment, "cache.token", "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET").source())
                .isEqualTo(new Source.Helper(List.of("issue-token", "--cache")));
        assertThat(Credential.of(environment, "deploy.token", "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET").source())
                .isEqualTo(new Source.Token("Basic secret"));
        assertThat(Credential.of(environment, "release.token", "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET"))
                .isEqualTo(Credential.NONE);
    }

    @Test
    public void no_file_of_a_project_names_an_exchange_an_identity_or_a_helper() {
        for (String key : List.of("maven.exchange", "maven.helper", "maven.identity", "exchange.identity")) {
            Map<String, String> settings = new HashMap<>(Map.of("maven.exchange", EXCHANGE, "make.provided", key));
            settings.putIfAbsent(key, key.endsWith("identity") ? "file " + identity : "issue-token");
            if (key.equals("maven.helper")) {
                settings.remove("maven.exchange");
            }
            assertThatThrownBy(() -> Credential.of(new Environment(settings),
                    "maven.token",
                    "JENESIS_TEST_TOKEN_THAT_IS_NOT_SET"))
                    .as("jenesis." + key + " decides where an identity token is sent")
                    .hasMessageContaining("jenesis." + key);
        }
    }

    @Test
    public void exchanges_nothing_for_a_repository_that_a_project_named() throws IOException {
        Map<String, String> settings = Map.of("maven.uri", origin() + REPOSITORY,
                "maven.exchange", EXCHANGE,
                "maven.identity", "file " + identity,
                "maven.local", "",
                "repository.insecure", "true",
                "make.provided", "maven.uri");

        MavenDefaultRepository.ofEnvironment(new Environment(settings)).fetch(Runnable::run, "sample/sample/1", "pom");

        assertThat(exchanged)
                .as("an identity token is not exchanged with a server a project's own file chose")
                .isEmpty();
        assertThat(authorizations).isNotEmpty().containsOnly("null");
    }

    @Test
    public void exchanges_for_a_repository_that_the_command_line_named() throws IOException {
        Map<String, String> settings = Map.of("maven.uri", origin() + REPOSITORY,
                "maven.exchange", EXCHANGE,
                "maven.identity", "file " + identity,
                "maven.local", "",
                "repository.insecure", "true");

        MavenDefaultRepository.ofEnvironment(new Environment(settings)).fetch(Runnable::run, "sample/sample/1", "pom");

        assertThat(exchanged).hasSize(1);
        assertThat(authorizations).isNotEmpty().containsOnly("Bearer issued-1");
    }

    @Test
    public void sends_the_identity_token_to_the_exchange_alone_and_the_issued_token_to_its_host_alone()
            throws IOException {
        List<String> foreign = new CopyOnWriteArrayList<>();
        HttpServer other = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        other.createContext("/", exchange -> {
            try (exchange) {
                foreign.add(exchange.getRequestURI() + " " + exchange.getRequestHeaders());
                exchange.sendResponseHeaders(200, -1);
            }
        });
        other.start();
        try {
            Credential credential = credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)));
            fetch(credential);
            try (InputStream stream = Repository.open(connection(),
                    URI.create("http://localhost:" + other.getAddress().getPort() + "/sample.jar"),
                    credential)) {
                stream.readAllBytes();
            }
        } finally {
            other.stop(0);
        }

        assertThat(everything.stream().filter(request -> request.contains("job.identity.token")))
                .as("only the request of the exchange carries the identity token")
                .hasSize(1)
                .allMatch(request -> request.startsWith(EXCHANGE + " "));
        assertThat(foreign)
                .as("a credential issued for one server is no credential of another")
                .hasSize(1)
                .noneMatch(request -> request.contains("issued-") || request.contains("job.identity.token"));
    }

    @Test
    public void exchanges_at_first_use_and_once_for_a_build() throws IOException {
        Source source = new Source.Exchange(EXCHANGE, new Identity.File(identity));
        try (Closeable _ = Credential.scope()) {
            MavenDefaultRepository first = new MavenDefaultRepository(URI.create(origin() + REPOSITORY),
                    null,
                    Map.of(),
                    null,
                    new Credential(source, Repository.Origin.USER)).connection(connection());
            MavenDefaultRepository second = new MavenDefaultRepository(URI.create(origin() + REPOSITORY),
                    null,
                    Map.of(),
                    null,
                    new Credential(source, Repository.Origin.USER)).connection(connection());
            assertThat(everything).as("a credential nothing uses is never asked for").isEmpty();
            read(first.fetch(Runnable::run, "sample/sample/1"));
            read(second.fetch(Runnable::run, "sample/sample/2"));
            read(first.fetch(Runnable::run, "sample/sample/3"));
            assertThat(exchanged).as("every repository of a build shares what one exchange issued").hasSize(1);
            assertThat(authorizations).hasSize(3).containsOnly("Bearer issued-1");
        }
        fetch(credential(source));

        assertThat(exchanged).as("the next build exchanges for itself").hasSize(2);
        assertThat(authorizations).last().isEqualTo("Bearer issued-2");
    }

    @Test
    public void exchanges_again_once_the_token_has_expired() throws IOException {
        lifetime.set(0);
        Credential credential = credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)));

        fetch(credential);
        fetch(credential);

        assertThat(exchanged).hasSize(2);
        assertThat(authorizations).containsExactly("Bearer issued-1", "Bearer issued-2");
    }

    @Test
    public void keeps_a_token_that_names_no_lifetime_until_the_build_ends() throws IOException {
        lifetime.set(-1);
        Credential credential = credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)));

        fetch(credential);
        fetch(credential);

        assertThat(exchanged).hasSize(1);
    }

    @Test
    public void reads_a_rotated_identity_token_for_the_next_exchange() throws IOException {
        lifetime.set(0);
        Credential credential = credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)));

        fetch(credential);
        Files.writeString(identity, "rotated.identity.token");
        fetch(credential);

        assertThat(form(exchanged.getLast())).containsEntry("subject_token", "rotated.identity.token");
    }

    @Test
    public void exchanges_nothing_while_offline() {
        Credential credential = credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)));

        assertThatThrownBy(() -> credential.authorization(connection().offline(true)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("while offline");
        assertThatThrownBy(() -> Repository.open(connection().offline(true),
                URI.create(origin() + REPOSITORY + "sample.jar"),
                credential))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("while offline");
        assertThat(everything).isEmpty();
    }

    @Test
    public void a_refused_exchange_fails_naming_the_server_and_its_error_but_no_token() {
        status.set(400);
        Credential credential = credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)));

        assertThatThrownBy(() -> fetch(credential))
                .isInstanceOf(IOException.class)
                .hasMessageContaining(origin() + EXCHANGE)
                .hasMessageContaining(origin() + REPOSITORY)
                .hasMessageContaining("answered 400 invalid_request: no trust admits the token")
                .message()
                .doesNotContain("job.identity.token");
        assertThatThrownBy(() -> fetch(credential)).isInstanceOf(IOException.class);
        assertThat(exchanged).as("a server that refused is not asked again at once").hasSize(1);
        assertThat(authorizations).as("the repository is not asked without the credential it was to be sent").isEmpty();
    }

    @Test
    public void a_refused_exchange_fails_a_release() throws IOException {
        status.set(400);
        Path staged = Files.createDirectories(folder.resolve("staged/sample/1.0.0"));
        Files.writeString(staged.resolve("sample.jar"), "classes");
        JenesisModuleRepositoryRelease release = new JenesisModuleRepositoryRelease(URI.create(origin() + REPOSITORY))
                .publish(true)
                .connection(connection())
                .credential(new Credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)),
                        Repository.Origin.USER));

        assertThatThrownBy(() -> release.apply(Runnable::run,
                        new BuildStepContext(folder.resolve("previous"),
                                Files.createDirectories(folder.resolve("next")),
                                Files.createDirectories(folder.resolve("supplement")),
                                Files.createDirectories(folder.resolve("persistent"))),
                        new LinkedHashMap<>(Map.of("staged", new BuildStepArgument(
                                folder.resolve("staged"),
                                Map.of(Path.of("."), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join())
                .hasMessageContaining("answered 400 invalid_request");
        assertThat(authorizations).isEmpty();
    }

    @Test
    public void a_cache_server_is_sent_the_issued_token_as_a_bearer_token() throws IOException {
        BuildExecutorHttpCache cache = new BuildExecutorHttpCache(URI.create(origin() + CACHE))
                .local(new BuildExecutorFileCache(folder.resolve("local")))
                .credential(new Credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)),
                        Repository.Origin.USER));

        cache.fetch(Runnable::run,
                "step",
                new byte[] {1},
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                Files.createDirectories(folder.resolve("target")),
                "SHA-256");

        assertThat(form(exchanged.getFirst())).containsEntry("resource", origin() + CACHE);
        assertThat(authorizations).containsExactly("Bearer issued-1");
    }

    @Test
    public void a_cache_reads_its_exchange_from_its_own_settings() throws IOException {
        BuildExecutorHttpCache.ofEnvironment(new Environment(Map.of("cache.exchange", EXCHANGE,
                        "cache.identity", "file " + identity,
                        "cache.local", folder.resolve("local").toString())), URI.create(origin() + CACHE))
                .fetch(Runnable::run,
                        "step",
                        new byte[] {1},
                        new LinkedHashMap<>(),
                        new LinkedHashMap<>(),
                        Files.createDirectories(folder.resolve("target")),
                        "SHA-256");

        assertThat(authorizations).containsExactly("Bearer issued-1");
    }

    @Test
    public void a_refused_exchange_is_a_miss_of_the_cache_and_no_failure() throws IOException {
        status.set(400);
        BuildExecutorHttpCache cache = new BuildExecutorHttpCache(URI.create(origin() + CACHE))
                .local(new BuildExecutorFileCache(folder.resolve("local")))
                .credential(new Credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)),
                        Repository.Origin.USER));
        Path output = Files.createDirectories(folder.resolve("output"));
        Files.writeString(output.resolve("file"), "result");

        assertThat(cache.fetch(Runnable::run,
                "step",
                new byte[] {1},
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                Files.createDirectories(folder.resolve("target")),
                "SHA-256")).isEmpty();
        cache.store(Runnable::run, "step", new byte[] {1}, new LinkedHashMap<>(), output, "SHA-256", Map.of());

        assertThat(exchanged).hasSize(1);
    }

    @Test
    public void a_helper_issues_the_token_for_the_repository_it_is_handed() throws IOException {
        Path helper = helper("""
                System.err.println("a diagnostic that is not a token");
                System.out.println("helped-" + args[args.length - 2] + "-" + args[args.length - 1]);
                """);
        try (Closeable _ = Credential.scope()) {
            Credential credential = credential(new Source.Helper(List.of(java(), helper.toString(), "maven")));
            fetch(credential);
            fetch(credential);
        }

        assertThat(authorizations)
                .as("the helper runs once for a build and what it prints is sent as a bearer token")
                .containsExactly("Bearer helped-maven-" + origin() + REPOSITORY,
                        "Bearer helped-maven-" + origin() + REPOSITORY);
    }

    @Test
    public void a_helper_that_prints_nothing_sends_no_credential() throws IOException {
        fetch(credential(new Source.Helper(List.of(java(), helper("").toString()))));

        assertThat(authorizations).containsExactly("null");
    }

    @Test
    public void a_helper_that_fails_fails_what_asked_for_the_credential() throws IOException {
        Path helper = helper("""
                System.out.println("half.a.token");
                System.exit(3);
                """);

        assertThatThrownBy(() -> fetch(credential(new Source.Helper(List.of(java(), helper.toString())))))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("ended with 3")
                .message()
                .doesNotContain("half.a.token");
        assertThat(authorizations).isEmpty();
    }

    @Test
    public void a_token_that_differs_between_two_builds_does_not_change_the_hash_of_a_step() throws IOException {
        BuildStepHashFunction hash = BuildStepHashFunction.ofSerializationDigest("SHA-256");
        URI repository = URI.create(origin() + REPOSITORY);
        Credential exchange = new Credential(new Source.Exchange(EXCHANGE, new Identity.File(identity)),
                Repository.Origin.USER);

        assertThat(hash.hash(new MavenRepositoryRelease(repository).credential(token("Bearer first"))))
                .isEqualTo(hash.hash(new MavenRepositoryRelease(repository).credential(token("Bearer second"))))
                .isEqualTo(hash.hash(new MavenRepositoryRelease(repository).credential(exchange)))
                .isEqualTo(hash.hash(new MavenRepositoryRelease(repository)));
        assertThat(hash.hash(new JenesisModuleRepositoryRelease(repository).credential(token("Bearer first"))))
                .isEqualTo(hash.hash(new JenesisModuleRepositoryRelease(repository).credential(exchange)))
                .isEqualTo(hash.hash(new JenesisModuleRepositoryRelease(repository)));
        assertThat(hash.hash(MavenRepositoryRelease.ofEnvironment(new Environment(Map.of("deploy.exchange", EXCHANGE,
                        "deploy.identity", "file " + identity)), repository)))
                .as("how a credential is obtained is no input of what a step produces")
                .isEqualTo(hash.hash(MavenRepositoryRelease.ofEnvironment(Environment.NONE, repository)));
    }

    @Test
    public void nothing_that_holds_a_token_prints_it() {
        assertThat(List.of(token("Bearer secret.token"),
                token("Bearer secret.token").source(),
                new Identity.GitHub(URI.create("https://actions.example/token?secret.token"), "secret.token"),
                new Credential.Issued("secret.token", Instant.EPOCH)))
                .noneMatch(value -> value.toString().contains("secret.token"));
    }

    private Credential credential(Source source) {
        return new Credential(source, Repository.Origin.USER, URI.create(origin() + REPOSITORY));
    }

    private static Credential token(String value) {
        return new Credential(new Source.Token(value), Repository.Origin.USER);
    }

    private Repository.Connection connection() {
        return new Repository.Connection().insecure(true).retries(0);
    }

    private String origin() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private void fetch(Credential credential) throws IOException {
        try (InputStream stream = Repository.open(connection(),
                URI.create(origin() + REPOSITORY + "sample/sample.jar"),
                credential)) {
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("classes");
        }
    }

    private static void read(Optional<RepositoryItem> item) throws IOException {
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            stream.readAllBytes();
        }
    }

    private static SequencedMap<String, String> form(String body) {
        SequencedMap<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            int separator = pair.indexOf('=');
            form.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
        }
        return form;
    }

    private static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private Path helper(String body) throws IOException {
        return Files.writeString(Files.createTempDirectory(folder, "helper").resolve("Helper.java"),
                "public class Helper {\n    public static void main(String[] args) {\n" + body + "    }\n}\n");
    }
}
