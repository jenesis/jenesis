package build.jenesis.test.module;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.RepositoryItem;
import build.jenesis.module.JenesisRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JenesisRepositoryTest {

    @TempDir
    private Path index;

    @TempDir
    private Path maven;

    @TempDir
    private Path local;

    @Test
    public void reads_the_published_index_where_git_is_named() throws IOException {
        writeIndex("widget", "2.0\tcom.example\twidget-core\t2.0");
        writeArtifact("com.example", "widget-core", "2.0", "fromIndex");

        assertThat(content(JenesisRepository.ofEnvironment(environment(Map.of(
                "module.source", "git",
                "module.index", index.toUri().toString(),
                "maven.uri", maven.toUri().toString())), JenesisRepository.Scope.MODULE)
                .fetch(Runnable::run, "widget", null, null, "jar"))).isEqualTo("fromIndex");
    }

    @Test
    public void asks_the_module_repository_where_no_source_is_named() throws IOException {
        List<String> requested = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requested.add(exchange.getRequestURI().getPath());
            byte[] body = "fromService".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            assertThat(content(JenesisRepository.ofEnvironment(environment(Map.of(
                    "repository.insecure", "true",
                    "module.uri", "http://localhost:" + server.getAddress().getPort() + "/")),
                    JenesisRepository.Scope.MODULE)
                    .fetch(Runnable::run, "widget", null, null, "jar"))).isEqualTo("fromService");
            assertThat(requested)
                    .as("the service resolves a module's coordinates itself, so the index is never read")
                    .isNotEmpty();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void refuses_a_source_that_is_neither() {
        assertThatThrownBy(() -> JenesisRepository.ofEnvironment(
                environment(Map.of("module.source", "cloudflare")), JenesisRepository.Scope.MODULE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown jenesis.module.source 'cloudflare'")
                .hasMessageContaining("'service'")
                .hasMessageContaining("'git'");
    }

    private Environment environment(Map<String, String> settings) {
        Map<String, String> all = new HashMap<>(settings);
        all.put("module.local", local.toString());
        return new Environment(all::get);
    }

    private void writeIndex(String module, String... rows) throws IOException {
        Path folder = Files.createDirectories(index.resolve(module.replace('.', '/')));
        Files.writeString(folder.resolve("modules.tsv"), String.join("\n", rows) + "\n");
    }

    private void writeArtifact(String groupId, String artifactId, String version, String content) throws IOException {
        Path folder = Files.createDirectories(maven
                .resolve(groupId.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version));
        Files.writeString(folder.resolve(artifactId + "-" + version + ".jar"), content);
    }

    private static String content(Optional<RepositoryItem> item) throws IOException {
        assertThat(item).isPresent();
        try (InputStream stream = item.orElseThrow().toInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
