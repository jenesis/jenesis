package build.jenesis.test.maven;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.maven.MavenRepositoryRelease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenRepositoryReleaseTest {

    private static final String FOLDER = "/repository/releases/demo/greeter/greeter/";

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
            byte[] body = exchange.getRequestBody().readAllBytes();
            String path = exchange.getRequestURI().getPath();
            requests.add(exchange.getRequestMethod() + " " + path + " " + exchange.getRequestHeaders().getFirst("Authorization"));
            Integer status = statuses.poll();
            if (status != null) {
                exchange.sendResponseHeaders(status, -1);
            } else if (exchange.getRequestMethod().equals("GET")) {
                String content = received.get(path);
                if (content == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } else {
                received.put(path, new String(body, StandardCharsets.UTF_8));
                exchange.sendResponseHeaders(201, -1);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void puts_every_staged_file_with_its_checksums_in_the_maven_layout() throws Exception {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");
        stage("1.0.0", "greeter-1.0.0.pom", "pom");
        stage("1.0.0", "greeter-1.0.0-sources.jar", "sources");

        BuildStepResult result = run(release());

        assertThat(result.next()).isTrue();
        assertThat(requests.stream().filter(request -> request.startsWith("PUT ") && request.contains("/1.0.0/")))
                .map(request -> request.substring(4, request.lastIndexOf(' ')))
                .containsExactly(
                        FOLDER + "1.0.0/greeter-1.0.0-sources.jar",
                        FOLDER + "1.0.0/greeter-1.0.0-sources.jar.md5",
                        FOLDER + "1.0.0/greeter-1.0.0-sources.jar.sha1",
                        FOLDER + "1.0.0/greeter-1.0.0-sources.jar.sha256",
                        FOLDER + "1.0.0/greeter-1.0.0-sources.jar.sha512",
                        FOLDER + "1.0.0/greeter-1.0.0.jar",
                        FOLDER + "1.0.0/greeter-1.0.0.jar.md5",
                        FOLDER + "1.0.0/greeter-1.0.0.jar.sha1",
                        FOLDER + "1.0.0/greeter-1.0.0.jar.sha256",
                        FOLDER + "1.0.0/greeter-1.0.0.jar.sha512",
                        FOLDER + "1.0.0/greeter-1.0.0.pom",
                        FOLDER + "1.0.0/greeter-1.0.0.pom.md5",
                        FOLDER + "1.0.0/greeter-1.0.0.pom.sha1",
                        FOLDER + "1.0.0/greeter-1.0.0.pom.sha256",
                        FOLDER + "1.0.0/greeter-1.0.0.pom.sha512");
        assertThat(received)
                .containsEntry(FOLDER + "1.0.0/greeter-1.0.0.jar", "classes")
                .containsEntry(FOLDER + "1.0.0/greeter-1.0.0.jar.md5", digest("MD5", "classes"))
                .containsEntry(FOLDER + "1.0.0/greeter-1.0.0.jar.sha1", digest("SHA-1", "classes"))
                .containsEntry(FOLDER + "1.0.0/greeter-1.0.0.jar.sha256", digest("SHA-256", "classes"))
                .containsEntry(FOLDER + "1.0.0/greeter-1.0.0.jar.sha512", digest("SHA-512", "classes"));
    }

    @Test
    public void writes_the_metadata_of_an_artifact_the_repository_does_not_hold_yet() throws Exception {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");

        run(release());

        assertThat(requests).contains("GET " + FOLDER + "maven-metadata.xml null");
        String metadata = received.get(FOLDER + "maven-metadata.xml");
        assertThat(metadata)
                .contains("<groupId>demo.greeter</groupId>")
                .contains("<artifactId>greeter</artifactId>")
                .contains("<latest>1.0.0</latest>")
                .contains("<release>1.0.0</release>")
                .contains("<version>1.0.0</version>")
                .containsPattern("<lastUpdated>\\d{14}</lastUpdated>");
        assertThat(received).containsEntry(FOLDER + "maven-metadata.xml.sha1", digest("SHA-1", metadata));
        assertThat(requests.getLast()).startsWith("PUT " + FOLDER + "maven-metadata.xml.sha512");
    }

    @Test
    public void merges_the_version_into_the_metadata_the_repository_holds() throws IOException {
        received.put(FOLDER + "maven-metadata.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata xmlns="http://maven.apache.org/METADATA/1.1.0">
                  <groupId>demo.greeter</groupId>
                  <artifactId>greeter</artifactId>
                  <versioning>
                    <latest>1.1.0</latest>
                    <release>1.1.0</release>
                    <versions>
                      <version>0.9.0</version>
                      <version>1.1.0</version>
                    </versions>
                    <lastUpdated>20240101000000</lastUpdated>
                  </versioning>
                </metadata>
                """);
        stage("1.0.0", "greeter-1.0.0.jar", "classes");

        run(release());

        assertThat(received.get(FOLDER + "maven-metadata.xml"))
                .contains("<latest>1.1.0</latest>")
                .contains("<release>1.1.0</release>")
                .containsSubsequence("<version>0.9.0</version>", "<version>1.0.0</version>", "<version>1.1.0</version>")
                .doesNotContain("20240101000000");
    }

    @Test
    public void deploys_a_snapshot_under_a_unique_timestamped_name_beside_its_version_metadata() throws Exception {
        stage("1.0-SNAPSHOT", "greeter-1.0-SNAPSHOT.jar", "classes");
        stage("1.0-SNAPSHOT", "greeter-1.0-SNAPSHOT.pom", "pom");

        run(release());

        String jar = received.keySet().stream()
                .filter(path -> path.matches(Pattern.quote(FOLDER) + "1\\.0-SNAPSHOT/greeter-1\\.0-\\d{8}\\.\\d{6}-1\\.jar"))
                .findFirst()
                .orElseThrow();
        String value = jar.substring((FOLDER + "1.0-SNAPSHOT/greeter-").length(), jar.length() - ".jar".length());
        assertThat(received)
                .containsEntry(jar, "classes")
                .containsEntry(FOLDER + "1.0-SNAPSHOT/greeter-" + value + ".pom", "pom")
                .containsEntry(jar + ".sha1", digest("SHA-1", "classes"))
                .doesNotContainKey(FOLDER + "1.0-SNAPSHOT/greeter-1.0-SNAPSHOT.jar");
        assertThat(received.get(FOLDER + "1.0-SNAPSHOT/maven-metadata.xml"))
                .contains("<version>1.0-SNAPSHOT</version>")
                .contains("<timestamp>" + value.substring("1.0-".length(), value.lastIndexOf('-')) + "</timestamp>")
                .contains("<buildNumber>1</buildNumber>")
                .containsSubsequence("<extension>jar</extension>", "<value>" + value + "</value>")
                .containsSubsequence("<extension>pom</extension>", "<value>" + value + "</value>");
        assertThat(received).containsKey(FOLDER + "1.0-SNAPSHOT/maven-metadata.xml.sha1");
        assertThat(received.get(FOLDER + "maven-metadata.xml"))
                .contains("<latest>1.0-SNAPSHOT</latest>")
                .contains("<version>1.0-SNAPSHOT</version>")
                .doesNotContain("<release>");
    }

    @Test
    public void counts_on_from_the_build_number_of_the_snapshot_the_repository_holds() throws IOException {
        received.put(FOLDER + "1.0-SNAPSHOT/maven-metadata.xml", """
                <metadata modelVersion="1.1.0">
                  <groupId>demo.greeter</groupId>
                  <artifactId>greeter</artifactId>
                  <version>1.0-SNAPSHOT</version>
                  <versioning>
                    <snapshot>
                      <timestamp>20240101.000000</timestamp>
                      <buildNumber>4</buildNumber>
                    </snapshot>
                    <lastUpdated>20240101000000</lastUpdated>
                    <snapshotVersions>
                      <snapshotVersion>
                        <classifier>javadoc</classifier>
                        <extension>jar</extension>
                        <value>1.0-20240101.000000-4</value>
                        <updated>20240101000000</updated>
                      </snapshotVersion>
                      <snapshotVersion>
                        <extension>jar</extension>
                        <value>1.0-20240101.000000-4</value>
                        <updated>20240101000000</updated>
                      </snapshotVersion>
                    </snapshotVersions>
                  </versioning>
                </metadata>
                """);
        stage("1.0-SNAPSHOT", "greeter-1.0-SNAPSHOT.jar", "classes");

        run(release());

        assertThat(received.keySet()).anyMatch(path -> path.matches(
                Pattern.quote(FOLDER) + "1\\.0-SNAPSHOT/greeter-1\\.0-\\d{8}\\.\\d{6}-5\\.jar"));
        assertThat(received.get(FOLDER + "1.0-SNAPSHOT/maven-metadata.xml"))
                .contains("<buildNumber>5</buildNumber>")
                .containsSubsequence("<classifier>javadoc</classifier>", "<value>1.0-20240101.000000-4</value>")
                .containsPattern("<value>1\\.0-\\d{8}\\.\\d{6}-5</value>");
        assertThat(received.get(FOLDER + "1.0-SNAPSHOT/maven-metadata.xml").split("1.0-20240101.000000-4", -1))
                .as("the javadoc jar keeps the snapshot it was deployed as, the main jar takes the new one")
                .hasSize(2);
    }

    @Test
    public void sends_the_authorization_header_value_as_given() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");

        run(release().token("Basic ZGVwbG95OnNlY3JldA=="));

        assertThat(requests).isNotEmpty().allMatch(request -> request.endsWith(" Basic ZGVwbG95OnNlY3JldA=="));
    }

    @Test
    public void sends_the_token_to_a_repository_the_command_line_named() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");
        Environment environment = new Environment(Map.of("release.maven.uri", uri(),
                "release.maven.token", "Bearer secret",
                "repository.insecure", "true"));

        run(MavenRepositoryRelease.ofEnvironment(environment, MavenRepositoryRelease.configured(environment)).printing(null, Palette.ANSI));

        assertThat(requests).isNotEmpty().allMatch(request -> request.endsWith(" Bearer secret"));
    }

    @Test
    public void sends_no_token_to_a_repository_the_project_named_itself() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");
        Environment environment = new Environment(Map.of("release.maven.uri", uri(),
                "release.maven.token", "Bearer secret",
                "repository.insecure", "true",
                "make.provided", "release.maven.uri"));

        run(MavenRepositoryRelease.ofEnvironment(environment, MavenRepositoryRelease.configured(environment)).printing(null, Palette.ANSI));

        assertThat(requests).isNotEmpty().allMatch(request -> request.endsWith(" null"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://repo1.maven.org/maven2/",
            "https://repo.maven.apache.org/maven2/",
            "https://central.sonatype.com/api/v1/publisher/",
            "https://oss.sonatype.org/service/local/staging/deploy/maven2/",
            "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"})
    public void refuses_maven_central_and_names_jreleaser_as_the_way_there(String location) {
        assertThatThrownBy(() -> new MavenRepositoryRelease(URI.create(location)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(location)
                .hasMessageContaining("JReleaser")
                .hasMessageContaining("jreleaser.yml");
    }

    @Test
    public void names_the_address_and_status_when_the_repository_refuses_a_file() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");
        statuses.add(400);

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("PUT " + uri() + "demo/greeter/greeter/1.0.0/greeter-1.0.0.jar")
                .hasMessageContaining("status 400");
        assertThat(requests).hasSize(1);
    }

    @Test
    public void names_the_address_and_status_when_the_repository_cannot_serve_its_metadata() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");
        statuses.addAll(List.of(201, 201, 201, 201, 201, 500));

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("GET " + uri() + "demo/greeter/greeter/maven-metadata.xml")
                .hasMessageContaining("status 500");
    }

    @Test
    public void names_the_token_when_the_repository_refuses_to_accept_a_release() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");
        statuses.add(401);

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status 401")
                .hasMessageContaining("jenesis.release.maven.token");
    }

    @Test
    public void retries_a_file_the_repository_could_not_take_at_once() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");
        statuses.add(503);

        run(release().connection(new Repository.Connection().insecure(true).retries(1).backoff(Duration.ZERO)));

        assertThat(received).containsEntry(FOLDER + "1.0.0/greeter-1.0.0.jar", "classes");
    }

    @Test
    public void refuses_a_file_outside_the_maven_layout_before_it_puts_anything() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");
        Files.writeString(staged.resolve("stray.jar"), "stray");

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stray.jar")
                .hasMessageContaining("<group path>/<artifactId>/<version>/<file>");
        assertThat(requests).isEmpty();
    }

    @Test
    public void refuses_a_file_not_named_after_its_artifact_and_version() throws IOException {
        stage("1.0.0", "other-1.0.0.jar", "classes");

        assertThatThrownBy(() -> run(release()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("greeter-1.0.0[-<classifier>].<extension>");
        assertThat(requests).isEmpty();
    }

    @Test
    public void refuses_a_plaintext_repository_unless_it_is_allowed() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");

        assertThatThrownBy(() -> run(release().connection(new Repository.Connection())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.repository.insecure");
        assertThat(requests).isEmpty();
    }

    @Test
    public void refuses_a_repository_that_is_not_addressed_by_http() {
        assertThatThrownBy(() -> new MavenRepositoryRelease(root.toUri()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(root.toUri().toString());
    }

    @Test
    public void prints_each_released_file_once() throws IOException {
        stage("1.0.0", "greeter-1.0.0.jar", "classes");
        stage("1.0.0", "greeter-1.0.0.pom", "pom");
        List<String> printed = new ArrayList<>();

        run(release().printing(printed::add, Palette.ANSI));

        assertThat(printed).hasSize(2).allMatch(line -> line.contains("[RELEASED]"));
        assertThat(printed.getFirst()).endsWith("demo/greeter/greeter/1.0.0/greeter-1.0.0.jar");
        assertThat(printed.getLast()).endsWith("demo/greeter/greeter/1.0.0/greeter-1.0.0.pom");
    }

    private String uri() {
        return "http://localhost:" + server.getAddress().getPort() + "/repository/releases/";
    }

    private MavenRepositoryRelease release() {
        return new MavenRepositoryRelease(URI.create("http://localhost:" + server.getAddress().getPort() + "/repository/releases"))
                .connection(new Repository.Connection().insecure(true).retries(0))
                .printing(null, Palette.ANSI);
    }

    private void stage(String version, String name, String content) throws IOException {
        Path folder = Files.createDirectories(staged.resolve("demo/greeter/greeter").resolve(version));
        Files.writeString(folder.resolve(name), content);
    }

    private static String digest(String algorithm, String content) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(content.getBytes(StandardCharsets.UTF_8)));
    }

    private BuildStepResult run(MavenRepositoryRelease release) throws IOException {
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
