package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.DiscoverySources;
import build.jenesis.RepositoryItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DiscoverySourcesTest {

    private DiscoveryServer server;

    @BeforeEach
    public void start() throws IOException {
        server = new DiscoveryServer();
    }

    @AfterEach
    public void stop() {
        server.close();
    }

    @Test
    public void names_the_source_archive_of_a_maven_artifact_and_of_a_module() throws IOException {
        server.domain("jenesis.build",
                "sources[build.jenesis]=https://github.com/jenesis/jenesis/archive/refs/tags/v{version}.zip",
                "sources[build.jenesis.launcher]=https://github.com/jenesis/jenesis-launcher/archive/v{version}.zip");
        DiscoverySources sources = sources();

        assertThat(link(sources.fetch(Runnable::run, "maven/build.jenesis/build.jenesis/0.15.4")))
                .isEqualTo("https://github.com/jenesis/jenesis/archive/refs/tags/v0.15.4.zip");
        assertThat(link(sources.fetch(Runnable::run, "maven/build.jenesis/build.jenesis.launcher/0.5.3")))
                .isEqualTo("https://github.com/jenesis/jenesis-launcher/archive/v0.5.3.zip");
        assertThat(link(sources.fetch(Runnable::run, "module/build.jenesis.launcher/0.5.3")))
                .isEqualTo("https://github.com/jenesis/jenesis-launcher/archive/v0.5.3.zip");
    }

    @Test
    public void fills_in_the_placeholders_of_the_kind_of_dependency_asked_for() throws IOException {
        server.domain("bytebuddy.net",
                "sources=https://example.org/{artifactId}{module}-{version}.zip");
        DiscoverySources sources = sources();

        assertThat(link(sources.fetch(Runnable::run, "maven/net.bytebuddy/byte-buddy/1.0")))
                .as("a placeholder of the other kind names no archive")
                .isNull();
        server.domain("bytebuddy.net", "sources=https://example.org/{groupPath}/{artifactId}-{version}.zip");
        assertThat(link(sources().fetch(Runnable::run, "maven/net.bytebuddy/byte-buddy-agent/1.0")))
                .isEqualTo("https://example.org/net/bytebuddy/byte-buddy-agent-1.0.zip");
        assertThat(link(sources().fetch(Runnable::run, "module/net.bytebuddy.agent/1.0")))
                .isNull();
    }

    @Test
    public void names_nothing_for_a_dependency_its_domain_does_not_answer_for() throws IOException {
        server.domain("jenesis.build", "sources[build.jenesis]=https://example.org/v{version}.zip");

        assertThat(sources().fetch(Runnable::run, "maven/build.jenesis/build.jenesis.crawler/1.0")).isEmpty();
        assertThat(sources().fetch(Runnable::run, "maven/org.example/lib/1.0")).isEmpty();
        assertThat(sources().fetch(Runnable::run, "maven/build.jenesis/build.jenesis")).isEmpty();
    }

    @Test
    public void refuses_a_location_that_names_no_version() {
        server.domain("jenesis.build", "sources=https://github.com/jenesis/jenesis/");

        assertThatThrownBy(() -> sources().fetch(Runnable::run, "maven/build.jenesis/build.jenesis/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sources in ")
                .hasMessageContaining("{version}");
    }

    private DiscoverySources sources() {
        return new DiscoverySources(server.discovery(), server.connection());
    }

    private static String link(Optional<RepositoryItem> item) throws IOException {
        if (item.isEmpty()) {
            return null;
        }
        try (InputStream inputStream = item.get().toInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
