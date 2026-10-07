package build.jenesis.test.maven;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.DiscoveryMavenRepository;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenRepository;
import build.jenesis.test.DiscoveryServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DiscoveryMavenRepositoryTest {

    @TempDir
    private Path local;

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
    public void resolves_an_artifact_from_the_maven_repository_its_group_domain_names() throws IOException {
        server.domain("bytebuddy.net", "maven=" + server.files() + "maven")
                .file("maven/net/bytebuddy/agent/byte-buddy-agent/1.0/byte-buddy-agent-1.0.jar", "agent");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy.agent/byte-buddy-agent/1.0")))
                .isEqualTo("agent");
    }

    @Test
    public void reads_the_metadata_of_an_artifact_from_that_repository() throws IOException {
        server.domain("bytebuddy.net", "maven=" + server.files() + "maven/")
                .file("maven/net/bytebuddy/byte-buddy/maven-metadata.xml", "<metadata/>");

        assertThat(content(repository().fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null)))
                .isEqualTo("<metadata/>");
    }

    @Test
    public void validates_an_artifact_against_the_checksum_that_repository_publishes() {
        server.domain("bytebuddy.net", "maven=" + server.files() + "maven/")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "jar")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar.sha1", "0".repeat(40));

        assertThatThrownBy(() -> content(repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    public void leaves_a_version_below_the_floor_of_the_record_to_the_maven_remotes() throws IOException {
        server.domain("bytebuddy.net", "maven=" + server.files() + "maven/", "maven.since=1.2.3")
                .file("maven/net/bytebuddy/byte-buddy/1.2.2/byte-buddy-1.2.2.jar", "old")
                .file("maven/net/bytebuddy/byte-buddy/1.2.3/byte-buddy-1.2.3.jar", "floor");

        DiscoveryMavenRepository repository = repository();

        assertThat(repository.fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.2.2")).isEmpty();
        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.2.3"))).isEqualTo("floor");
    }

    @Test
    public void resolves_an_artifact_and_its_pom_from_release_assets_a_template_names() throws IOException {
        server.domain("jenesis.build",
                        "maven=" + server.files() + "v{version}/{artifactId}-{version}{-classifier}.{type}")
                .file("v1.0/build.jenesis-1.0.jar", "jar")
                .file("v1.0/build.jenesis-1.0.jar.asc", "signature")
                .file("v1.0/build.jenesis-1.0-sources.jar", "sources")
                .file("v1.0/build.jenesis-1.0.pom", "<project/>");

        DiscoveryMavenRepository repository = repository();

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
        server.domain("bytebuddy.net", "maven=" + server.files() + "{artifactId}-{version}.{type}")
                .file("byte-buddy-1.0.jar", "jar")
                .file("byte-buddy-1.0.jar.sha256", "0".repeat(64));

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    public void refuses_the_suffix_of_a_module_in_a_template_of_a_group() {
        server.domain("bytebuddy.net", "maven=" + server.files() + "byte-buddy{-suffix}-{version}.{type}");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{-suffix}")
                .hasMessageContaining("{artifactId}");
    }

    @Test
    public void leaves_a_version_whose_suffix_the_record_does_not_list_to_the_maven_remotes() throws IOException {
        server.domain("bytebuddy.net",
                        "maven=" + server.files() + "{artifactId}-{version}.{type}",
                        "maven.suffixes=none")
                .file("byte-buddy-1.0.jar", "release")
                .file("byte-buddy-1.1-SNAPSHOT.jar", "snapshot");

        DiscoveryMavenRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0"))).isEqualTo("release");
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.1-SNAPSHOT"))
                .as("a release location that lists no SNAPSHOT suffix is not asked for a snapshot")
                .isEmpty();
    }

    @Test
    public void merges_the_metadata_of_a_location_with_that_of_the_maven_remotes() throws IOException {
        server.domain("bytebuddy.net", "maven=" + server.files() + "domain/")
                .file("domain/net/bytebuddy/byte-buddy/maven-metadata.xml", metadata("2.0", "2.0", "20260201000000",
                        "2.0"))
                .file("central/net/bytebuddy/byte-buddy/maven-metadata.xml", metadata("1.1", "1.1", "20260101000000",
                        "1.0", "1.1"));
        Map<String, String> settings = Map.of("repository.insecure", "true",
                "maven.uri", server.files() + "central/",
                "maven.local", local.toString());

        String merged = content(MavenDefaultRepository.ofEnvironment(new Environment(settings), server.discovery())
                .fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null));

        assertThat(merged)
                .as("the versions of both are listed, in Maven's order, and the newest of each is the newest")
                .contains("<versions><version>1.0</version><version>1.1</version><version>2.0</version></versions>")
                .contains("<latest>2.0</latest>")
                .contains("<release>2.0</release>")
                .contains("<lastUpdated>20260201000000</lastUpdated>");
    }

    @Test
    public void answers_the_metadata_of_a_template_from_its_latest_link_merged_with_the_maven_remotes()
            throws IOException {
        server.domain("bytebuddy.net",
                        "maven=" + server.files() + "release/{artifactId}-{version}{-classifier}.{type}",
                        "maven.latest=" + server.latest() + "{artifactId}.pom")
                .answer("byte-buddy.pom", 302, "Location", server.files() + "release/byte-buddy-2.0.pom")
                .file("central/net/bytebuddy/byte-buddy/maven-metadata.xml", metadata("1.1", "1.1", "20260101000000",
                        "1.0", "1.1"));
        Map<String, String> settings = Map.of("repository.insecure", "true",
                "maven.uri", server.files() + "central/",
                "maven.local", local.toString());

        String merged = content(MavenDefaultRepository.ofEnvironment(new Environment(settings), server.discovery())
                .fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null));

        assertThat(merged)
                .as("the newest version the domain names joins those of the remotes")
                .contains("<versions><version>1.0</version><version>1.1</version><version>2.0</version></versions>")
                .contains("<release>2.0</release>");
        assertThat(repository().fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", "sha1"))
                .as("a metadata file made from the latest link has no checksum of its own")
                .isEmpty();
    }

    @Test
    public void names_a_newest_prerelease_as_the_latest_version_but_not_as_a_release() throws IOException {
        server.domain("bytebuddy.net",
                        "maven=" + server.files() + "release/{artifactId}-{version}{-classifier}.{type}",
                        "maven.latest=" + server.latest() + "{artifactId}.pom")
                .answer("byte-buddy.pom", 302, "Location", server.files() + "release/byte-buddy-2.0-rc1.pom");

        String metadata = content(repository().fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null));

        assertThat(metadata)
                .contains("<groupId>net.bytebuddy</groupId>")
                .contains("<latest>2.0-rc1</latest>")
                .contains("<versions><version>2.0-rc1</version></versions>")
                .doesNotContain("<release>");
    }

    @Test
    public void answers_the_metadata_a_template_names_as_its_latest_link_with_the_versions_it_serves()
            throws IOException {
        server.domain("bytebuddy.net",
                        "maven=" + server.files() + "release/{artifactId}-{version}{-classifier}.{type}",
                        "maven.latest=" + server.files() + "meta/{groupPath}/{artifactId}/maven-metadata.xml",
                        "maven.since=1.5")
                .file("meta/net/bytebuddy/byte-buddy/maven-metadata.xml", metadata("2.0", "2.0", null,
                        "1.0", "1.5", "2.0"));

        String listed = content(repository().fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null));

        assertThat(listed)
                .contains("<versions><version>1.5</version><version>2.0</version></versions>")
                .contains("<release>2.0</release>");
        assertThat(repository().fetchMetadata(Runnable::run, "net.bytebuddy", "other", null))
                .as("a metadata file that does not exist lists nothing")
                .isEmpty();
    }

    @Test
    public void lists_only_the_versions_a_record_admits_in_the_metadata_of_its_location() throws IOException {
        server.domain("bytebuddy.net", "maven=" + server.files() + "domain/", "maven.since=1.5")
                .file("domain/net/bytebuddy/byte-buddy/maven-metadata.xml", metadata("2.0", "2.0", null,
                        "1.0", "2.0"));

        String admitted = content(repository().fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null));

        assertThat(admitted).contains("<version>2.0</version>").doesNotContain("<version>1.0</version>");
    }

    @Test
    public void takes_the_metadata_of_the_overlay_alone_where_a_checksum_is_asked_for() throws IOException {
        MavenRepository overlay = new MetadataRepository("overlay"), underlying = new MetadataRepository("underlying");

        assertThat(content(underlying.overlay(overlay).fetchMetadata(Runnable::run, "g", "a", "sha1")))
                .isEqualTo("overlay");
    }

    @Test
    public void returns_empty_where_no_record_exists() throws IOException {
        assertThat(repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0")).isEmpty();
        assertThat(repository().fetchMetadata(Runnable::run, "net.bytebuddy", "byte-buddy", null)).isEmpty();
    }

    @Test
    public void refuses_a_repository_that_is_not_read_over_http() {
        server.domain("bytebuddy.net", "maven=file:///etc/");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file:///etc/");
    }

    @Test
    public void resolves_through_discovery_alone_where_the_maven_remotes_are_empty() throws IOException {
        server.domain("bytebuddy.net", "maven=" + server.files() + "maven/")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "fromDomain");
        Environment environment = new Environment(Map.of("repository.insecure", "true", "maven.uri", ""));

        MavenRepository repository = MavenDefaultRepository.ofEnvironment(environment, server.discovery());

        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0"))).isEqualTo("fromDomain");
        assertThat(repository.fetch(Runnable::run, "com.example/tool/1.0"))
                .as("an empty jenesis.maven.uri leaves no remote to fall back to")
                .isEmpty();
        assertThat(MavenDefaultRepository.ofEnvironment(environment)
                .fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0")).isEmpty();
    }

    @Test
    public void asks_the_domain_before_the_maven_repository_where_discovery_is_given() throws IOException {
        server.domain("bytebuddy.net", "maven=" + server.files() + "maven/")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "fromDomain")
                .file("central/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "fromCentral");
        Map<String, String> settings = Map.of("repository.insecure", "true",
                "maven.uri", server.files() + "central/",
                "maven.local", Files.createDirectory(local.resolve("disabled")).toString());

        assertThat(content(MavenDefaultRepository.ofEnvironment(new Environment(settings))
                .fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0")))
                .as("a domain is asked only where discovery is switched on")
                .isEqualTo("fromCentral");
        Map<String, String> enabled = new HashMap<>(settings);
        enabled.put("maven.local", Files.createDirectory(local.resolve("enabled")).toString());
        assertThat(content(MavenDefaultRepository.ofEnvironment(new Environment(enabled), server.discovery())
                .fetch(Runnable::run, "net.bytebuddy/byte-buddy/1.0")))
                .isEqualTo("fromDomain");
    }

    private DiscoveryMavenRepository repository() {
        return new DiscoveryMavenRepository().discovery(server.discovery()).connection(server.connection());
    }

    private static String content(Optional<RepositoryItem> item) throws IOException {
        assertThat(item).isPresent();
        try (InputStream inputStream = item.orElseThrow().toInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String metadata(String latest, String release, String lastUpdated, String... versions) {
        return "<metadata><groupId>net.bytebuddy</groupId><artifactId>byte-buddy</artifactId><versioning>"
                + "<latest>" + latest + "</latest><release>" + release + "</release><versions>"
                + Arrays.stream(versions)
                        .map(version -> "<version>" + version + "</version>")
                        .collect(Collectors.joining())
                + "</versions>" + (lastUpdated == null ? "" : "<lastUpdated>" + lastUpdated + "</lastUpdated>")
                + "</versioning></metadata>";
    }

    private record MetadataRepository(String content) implements MavenRepository {

        @Override
        public Optional<RepositoryItem> fetch(Executor executor,
                                              String groupId,
                                              String artifactId,
                                              String version,
                                              String type,
                                              String classifier,
                                              String checksum) {
            return Optional.empty();
        }

        @Override
        public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                      String groupId,
                                                      String artifactId,
                                                      String checksum) {
            return Optional.of(() -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        }
    }
}
