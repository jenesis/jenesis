package build.jenesis.test.maven;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDnsRepository;
import build.jenesis.test.DnsServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenDnsRepositoryTest {

    @TempDir
    private Path local;

    private DnsServer dns;

    @BeforeEach
    public void start() throws IOException {
        dns = new DnsServer();
    }

    @AfterEach
    public void stop() {
        dns.close();
    }

    @Test
    public void resolves_an_artifact_from_the_maven_repository_its_group_domain_names() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=" + dns.files() + "maven\"")
                .file("maven/net/bytebuddy/agent/byte-buddy-agent/1.0/byte-buddy-agent-1.0.jar", "agent");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy.agent/byte-buddy-agent/1.0")))
                .isEqualTo("agent");
    }

    @Test
    public void reads_the_metadata_of_an_artifact_from_that_repository() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=" + dns.files() + "maven/\"")
                .file("maven/net/bytebuddy/byte-buddy/maven-metadata.xml", "<metadata/>");

        assertThat(content(repository().fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null)))
                .isEqualTo("<metadata/>");
    }

    @Test
    public void validates_an_artifact_against_the_checksum_that_repository_publishes() {
        dns.record("_java.bytebuddy.net", "\"maven=" + dns.files() + "maven/\"")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "jar")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar.sha1", "0".repeat(40));

        assertThatThrownBy(() -> content(repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    public void leaves_a_version_below_the_floor_of_the_record_to_the_maven_remotes() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=" + dns.files() + "maven/ since=1.2.3\"")
                .file("maven/net/bytebuddy/byte-buddy/1.2.2/byte-buddy-1.2.2.jar", "old")
                .file("maven/net/bytebuddy/byte-buddy/1.2.3/byte-buddy-1.2.3.jar", "floor")
                .file("maven/net/bytebuddy/byte-buddy/maven-metadata.xml", "<metadata/>");

        MavenDnsRepository repository = repository();

        assertThat(repository.fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.2.2")).isEmpty();
        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.2.3"))).isEqualTo("floor");
        assertThat(content(repository.fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null)))
                .as("the metadata names no single version, so the floor does not withhold it")
                .isEqualTo("<metadata/>");
    }

    @Test
    public void resolves_an_artifact_and_its_pom_from_release_assets_a_template_names() throws IOException {
        dns.record("_java.jenesis.build",
                        "\"maven=" + dns.files() + "v{version}/{artifactId}-{version}{-classifier}.{type}\"")
                .file("v1.0/build.jenesis-1.0.jar", "jar")
                .file("v1.0/build.jenesis-1.0.jar.asc", "signature")
                .file("v1.0/build.jenesis-1.0-sources.jar", "sources")
                .file("v1.0/build.jenesis-1.0.pom", "<project/>");

        MavenDnsRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "build.jenesis/build.jenesis/1.0"))).isEqualTo("jar");
        assertThat(content(repository.fetch(Runnable::run, "build.jenesis/build.jenesis/jar/sources/1.0")))
                .isEqualTo("sources");
        assertThat(content(repository.fetch(Runnable::run, "build.jenesis", "build.jenesis", "1.0", "pom", null, null)))
                .isEqualTo("<project/>");
        assertThat(content(repository.fetch(Runnable::run, "build.jenesis", "build.jenesis", "1.0", "jar", null,
                "asc")))
                .isEqualTo("signature");
        assertThat(repository.fetchMetadata(Runnable::run, "build.jenesis", "build.jenesis", null))
                .as("a template names files of a version, so it offers no metadata to choose a version from")
                .isEmpty();
    }

    @Test
    public void validates_an_artifact_from_a_template_against_the_checksum_beside_it() {
        dns.record("_java.bytebuddy.net", "\"maven=" + dns.files() + "{artifactId}-{version}.{type}\"")
                .file("byte-buddy-1.0.jar", "jar")
                .file("byte-buddy-1.0.jar.sha256", "0".repeat(64));

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    public void refuses_the_suffix_of_a_module_in_a_template_of_a_group() {
        dns.record("_java.bytebuddy.net", "\"maven=" + dns.files() + "byte-buddy{-suffix}-{version}.{type}\"");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{-suffix}")
                .hasMessageContaining("{artifactId}");
    }

    @Test
    public void leaves_a_version_whose_suffix_the_record_does_not_list_to_the_maven_remotes() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=" + dns.files() + "{artifactId}-{version}.{type} suffixes=none\"")
                .file("byte-buddy-1.0.jar", "release")
                .file("byte-buddy-1.1-SNAPSHOT.jar", "snapshot");

        MavenDnsRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0"))).isEqualTo("release");
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.1-SNAPSHOT"))
                .as("a release location that lists no SNAPSHOT suffix is not asked for a snapshot")
                .isEmpty();
    }

    @Test
    public void returns_empty_where_no_record_exists() throws IOException {
        assertThat(repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0")).isEmpty();
        assertThat(repository().fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null)).isEmpty();
    }

    @Test
    public void refuses_a_repository_that_is_not_read_over_http() {
        dns.record("_java.bytebuddy.net", "\"maven=file:///etc/\"");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file:///etc/");
    }

    @Test
    public void asks_dns_before_the_maven_repository_where_enabled() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=" + dns.files() + "maven/\"")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "fromDns")
                .file("central/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "fromCentral");
        Map<String, String> settings = Map.of("repository.insecure", "true",
                "maven.uri", dns.files() + "central/",
                "maven.local", Files.createDirectory(local.resolve("disabled")).toString(),
                "dns.uri", dns.resolver().toString());

        assertThat(content(MavenDefaultRepository.ofEnvironment(new Environment(settings))
                .fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0")))
                .as("DNS is asked only where it is switched on")
                .isEqualTo("fromCentral");
        Map<String, String> enabled = new HashMap<>(settings);
        enabled.put("dns.enabled", "true");
        enabled.put("maven.local", Files.createDirectory(local.resolve("enabled")).toString());
        assertThat(content(MavenDefaultRepository.ofEnvironment(new Environment(enabled))
                .fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0")))
                .isEqualTo("fromDns");
    }

    private MavenDnsRepository repository() {
        return new MavenDnsRepository().dns(dns.lookup()).connection(dns.connection());
    }

    private static String content(Optional<RepositoryItem> item) throws IOException {
        assertThat(item).isPresent();
        try (InputStream inputStream = item.orElseThrow().toInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
