package build.jenesis.test.step;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Json;
import build.jenesis.SequencedProperties;
import build.jenesis.step.OsvDownload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OsvDownloadTest {

    @TempDir
    private Path root;

    @Test
    public void refuses_to_query_an_insecure_endpoint() throws IOException {
        Path next = Files.createDirectory(root.resolve("next"));
        Path argument = Files.createDirectory(root.resolve("argument"));
        SequencedProperties dependencies = new SequencedProperties();
        dependencies.setProperty("main/compile/maven/org.example/lib/1.2.3", "resolved/lib.jar");
        dependencies.store(argument.resolve(BuildStep.DEPENDENCIES));
        OsvDownload step = new OsvDownload().endpoint(URI.create("http://osv.invalid"));
        assertThatThrownBy(() -> step.apply(Runnable::run,
                new BuildStepContext(root.resolve("previous"), next, root.resolve("supplement")),
                new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                        argument,
                        Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED)))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insecure scheme");
    }

    @Test
    public void retries_a_transient_server_error_and_drains_the_error_stream() throws IOException {
        System.setProperty("jenesis.repository.insecure", "true");
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/querybatch", exchange -> {
            if (hits.incrementAndGet() < 2) {
                exchange.getResponseHeaders().set("Retry-After", "0");
                byte[] body = "rate limited".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(503, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
                return;
            }
            byte[] body = "{\"results\":[{}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            Path next = Files.createDirectory(root.resolve("retry-next"));
            Path argument = Files.createDirectory(root.resolve("retry-argument"));
            SequencedProperties dependencies = new SequencedProperties();
            dependencies.setProperty("main/compile/maven/org.example/lib/1.2.3", "resolved/lib.jar");
            dependencies.store(argument.resolve(BuildStep.DEPENDENCIES));
            URI endpoint = URI.create("http://localhost:" + server.getAddress().getPort());
            BuildStepResult result = new OsvDownload().endpoint(endpoint).apply(Runnable::run,
                    new BuildStepContext(root.resolve("retry-previous"), next, root.resolve("retry-supplement")),
                    new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                            argument,
                            Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED))))))
                    .toCompletableFuture()
                    .join();
            assertThat(result.next()).isTrue();
            assertThat(hits.get()).isEqualTo(2);
            assertThat(next.resolve("advisories.properties")).exists();
        } finally {
            server.stop(0);
            System.clearProperty("jenesis.repository.insecure");
        }
    }

    @Test
    public void query_batch_escapes_json_metacharacters_in_coordinates() {
        String body = OsvDownload.queryBatch(List.of("org.example/lib/1.0\",\"x\":\"y"));
        Object parsed = Json.parse(body);
        Object query = ((List<?>) ((Map<?, ?>) parsed).get("queries")).getFirst();
        assertThat(((Map<?, ?>) query).get("version"))
                .as("a quote in the version stays inside the version string instead of reshaping the query")
                .isEqualTo("1.0\",\"x\":\"y");
    }

    @Test
    public void parses_query_batch_results_positionally() {
        String response = "{\"results\":[{\"vulns\":[{\"id\":\"GHSA-jfh8-c2jp-5v3q\",\"modified\":\"2025-10-22T19:37:02Z\"},"
                + "{\"id\":\"GHSA-7rjr-3q55-vv33\"}]},{}]}";
        List<List<String>> identifiers = OsvDownload.identifiers(response);
        assertThat(identifiers).hasSize(2);
        assertThat(identifiers.get(0)).containsExactly("GHSA-jfh8-c2jp-5v3q", "GHSA-7rjr-3q55-vv33");
        assertThat(identifiers.get(1)).isEmpty();
    }

    @Test
    public void maps_the_github_severity_word() {
        assertThat(OsvDownload.severity("{\"database_specific\":{\"severity\":\"MODERATE\",\"cwe_ids\":[\"CWE-116\"]}}"))
                .isEqualTo("MEDIUM");
        assertThat(OsvDownload.severity("{\"database_specific\":{\"severity\":\"CRITICAL\"}}"))
                .isEqualTo("CRITICAL");
    }

    @Test
    public void returns_empty_severity_when_absent_or_unparseable() {
        assertThat(OsvDownload.severity("{\"severity\":[{\"type\":\"CVSS_V3\",\"score\":\"CVSS:3.1/AV:N\"}]}")).isEmpty();
        assertThat(OsvDownload.severity("not json")).isEmpty();
    }

    @Test
    public void scores_a_cvss_v3_vector_when_there_is_no_github_word() {
        assertThat(OsvDownload.severity("{\"severity\":[{\"type\":\"CVSS_V3\",\"score\":\"CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H\"}]}"))
                .as("9.8 base score").isEqualTo("CRITICAL");
        assertThat(OsvDownload.severity("{\"severity\":[{\"type\":\"CVSS_V3\",\"score\":\"CVSS:3.1/AV:L/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N\"}]}"))
                .as("5.5 base score").isEqualTo("MEDIUM");
    }

    @Test
    public void scores_a_cvss_v2_vector() {
        assertThat(OsvDownload.severity("{\"severity\":[{\"type\":\"CVSS_V2\",\"score\":\"AV:N/AC:L/Au:N/C:C/I:C/A:C\"}]}"))
                .as("10.0 base score").isEqualTo("CRITICAL");
    }

    @Test
    public void prefers_the_github_word_over_a_cvss_vector() {
        assertThat(OsvDownload.severity("{\"database_specific\":{\"severity\":\"MODERATE\"},\"severity\":[{\"type\":\"CVSS_V3\",\"score\":\"CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H\"}]}"))
                .isEqualTo("MEDIUM");
    }

    @Test
    public void leaves_a_cvss_v4_only_advisory_unscored() {
        assertThat(OsvDownload.severity("{\"severity\":[{\"type\":\"CVSS_V4\",\"score\":\"CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N\"}]}"))
                .isEmpty();
    }
}
