package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.DiscoveredLocation;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DiscoveredLocationTest {

    private static final Repository.Connection CONNECTION = new Repository.Connection().insecure(true).retries(0);

    @Test
    public void reads_a_location_without_placeholders_as_a_root() {
        DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"),
                "maven",
                "https://example.com/maven",
                null,
                null,
                null);

        assertThat(location.template()).isFalse();
        assertThat(location.root(CONNECTION)).isEqualTo(URI.create("https://example.com/maven/"));
    }

    @Test
    public void fills_in_every_placeholder_of_a_template() {
        DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"), "maven",
                "https://example.com/{groupPath}/{artifactId}/{version}/{artifactId}-{version}{-classifier}.{type}",
                null,
                null,
                null);

        assertThat(location.template()).isTrue();
        assertThat(location.resolve(values("sources", "jar"), CONNECTION))
                .contains(URI.create("https://example.com/com/example/tool/1.0/tool-1.0-sources.jar"));
        assertThatThrownBy(() -> location.root(CONNECTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("template");
    }

    @Test
    public void serves_a_classifier_or_another_type_only_where_the_template_names_it() {
        DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"),
                "maven",
                "https://example.com/{artifactId}.jar",
                null,
                null,
                null);

        assertThat(location.resolve(values(null, "jar"), CONNECTION))
                .contains(URI.create("https://example.com/tool.jar"));
        assertThat(location.resolve(values("sources", "jar"), CONNECTION)).isEmpty();
        assertThat(location.resolve(values(null, "pom"), CONNECTION)).isEmpty();
    }

    @Test
    public void resolves_nothing_where_a_placeholder_it_uses_has_no_value() {
        DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"),
                "maven",
                "https://example.com/{version}.jar",
                null,
                null,
                null);
        Map<String, String> values = values(null, "jar");
        values.put("version", null);

        assertThat(location.resolve(values, CONNECTION)).isEmpty();
    }

    @Test
    public void refuses_a_placeholder_the_kind_does_not_know() {
        DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"),
                "maven",
                "https://example.com/{module}.jar",
                null,
                null,
                null);

        assertThatThrownBy(() -> location.resolve(values(null, "jar"), CONNECTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{module}")
                .hasMessageContaining("but maven knows only")
                .hasMessageContaining("{artifactId}");
    }

    @Test
    public void refuses_a_location_that_is_not_read_over_http() {
        assertThatThrownBy(() -> location("file:///etc/").root(CONNECTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file:///etc/");
        assertThatThrownBy(() -> location("http://example.com/").root(new Repository.Connection()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.repository.insecure");
    }

    @Test
    public void validates_a_fetched_file_against_the_strongest_checksum_published_beside_it() throws Exception {
        try (DiscoveryServer server = new DiscoveryServer()) {
            server.file("tool-1.0.jar", "jar")
                    .file("tool-1.0.jar.sha256", HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest("jar".getBytes(StandardCharsets.UTF_8)))
                            + "  tool-1.0.jar")
                    .file("tool-1.0.jar.sha1", "0".repeat(40));
            DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"), "maven",
                    server.files() + "{artifactId}-{version}.{type}",
                    null,
                    null,
                    null);

            assertThat(content(location.fetch(values(null, "jar"), true, CONNECTION, null, Palette.NONE)))
                    .as("SHA-256 is preferred to SHA-1, so the wrong SHA-1 is never consulted")
                    .isEqualTo("jar");
        }
    }

    @Test
    public void refuses_a_fetched_file_whose_checksum_does_not_match() throws IOException {
        try (DiscoveryServer server = new DiscoveryServer()) {
            server.file("tool-1.0.jar", "jar").file("tool-1.0.jar.sha1", "0".repeat(40));
            DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"), "maven",
                    server.files() + "{artifactId}-{version}.{type}",
                    null,
                    null,
                    null);

            assertThatThrownBy(() -> location.fetch(values(null, "jar"), true, CONNECTION, null, Palette.NONE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-1")
                    .hasMessageContaining("example.com/.well-known/java-repository.properties");
            assertThat(content(location.fetch(values(null, "jar"), false, CONNECTION, null, Palette.NONE)))
                    .as("a signature or checksum is fetched as it stands")
                    .isEqualTo("jar");
        }
    }

    @Test
    public void reads_the_newest_version_where_the_latest_link_redirects_to_with_a_head_request() throws IOException {
        try (DiscoveryServer server = new DiscoveryServer()) {
            server.answer("tool.jar", 302, "Location", "/releases/v1.2.3-rc.1/tool.jar")
                    .answer("pom", 302, "Location", server.root() + "/releases/v2.0/tool-2.0.pom?signed=true");
            DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"), "maven",
                    server.root() + "/releases/v{version}/{artifactId}-{version}{-classifier}.{type}",
                    null,
                    null,
                    server.latest() + "{artifactId}.jar");

            assertThat(location.latest(Map.of("artifactId", "tool"), CONNECTION)).contains("1.2.3-rc.1");
            assertThat(location.latest(Map.of("artifactId", "missing"), CONNECTION))
                    .as("a link that leads nowhere names no version")
                    .isEmpty();
            assertThat(server.asked()).containsExactly("HEAD tool.jar", "HEAD missing.jar");
            assertThat(new DiscoveredLocation("example.com", source("example.com"), "maven",
                    server.root() + "/releases/v{version}/{artifactId}-{version}.{type}",
                    null,
                    null,
                    server.latest() + "pom").latest(Map.of("artifactId", "tool"), CONNECTION))
                    .as("the version ends where the template's next character does")
                    .contains("2.0");
        }
    }

    @Test
    public void reads_the_newest_version_a_jenesis_module_service_names_in_its_header() throws IOException {
        try (DiscoveryServer server = new DiscoveryServer()) {
            server.answer("net.bytebuddy/net.bytebuddy.jar", 302,
                    "Location", "https://storage.example.com/byte-buddy-1.17.0.jar",
                    "Jenesis-ModuleVersion", "1.17.0");
            DiscoveredLocation location = new DiscoveredLocation("bytebuddy.net", source("bytebuddy.net"), "module",
                    server.root() + "/files/{module}/{version}/{module}{-classifier}.{type}",
                    null,
                    null,
                    server.latest() + "{module}/{module}.jar");

            assertThat(location.latest(Map.of("module", "net.bytebuddy", "-suffix", ""), CONNECTION))
                    .contains("1.17.0");
        }
    }

    @Test
    public void refuses_a_latest_link_that_leads_elsewhere_or_names_no_version() throws IOException {
        try (DiscoveryServer server = new DiscoveryServer()) {
            server.answer("elsewhere", 302, "Location", "https://example.com/login")
                    .answer("plain", 200)
                    .answer("garbage", 302, "Location", "/releases/v1.0;rm/tool.jar");
            DiscoveredLocation elsewhere = location(server, "elsewhere"),
                    plain = location(server, "plain"),
                    garbage = location(server, "garbage");

            assertThatThrownBy(() -> elsewhere.latest(Map.of(), CONNECTION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maven.latest in " + source("example.com"))
                    .hasMessageContaining("https://example.com/login")
                    .hasMessageContaining("/releases/v{version}");
            assertThatThrownBy(() -> plain.latest(Map.of(), CONNECTION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("answered 200");
            assertThatThrownBy(() -> garbage.latest(Map.of(), CONNECTION))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("/releases/v1.0;rm/tool.jar");
        }
    }

    @Test
    public void reads_the_newest_release_from_the_maven_metadata_a_latest_link_names() throws IOException {
        try (DiscoveryServer server = new DiscoveryServer()) {
            server.file("meta/tool/maven-metadata.xml", "<metadata><versioning><release>1.5</release><versions><version>1.0</version><version>1.5</version>"
                    + "<version>2.0-SNAPSHOT</version></versions></versioning></metadata>");
            DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"), "maven",
                    server.files() + "{artifactId}-{version}.{type}",
                    null,
                    null,
                    server.files() + "meta/{artifactId}/maven-metadata.xml"),
                    restricted = new DiscoveredLocation("example.com", source("example.com"), "maven",
                            server.files() + "{artifactId}-{version}.{type}",
                            "2.0",
                            null,
                            server.files() + "meta/{artifactId}/maven-metadata.xml");

            assertThat(location.listsVersions()).isTrue();
            assertThat(location.latest(Map.of("artifactId", "tool"), CONNECTION)).contains("1.5");
            assertThat(location.latest(Map.of("artifactId", "missing"), CONNECTION)).isEmpty();
            assertThat(restricted.latest(Map.of("artifactId", "tool"), CONNECTION))
                    .as("a snapshot is no release, and the release is older than the key serves")
                    .isEmpty();
        }
    }

    @Test
    public void accepts_a_fetched_file_that_publishes_no_checksum() throws IOException {
        try (DiscoveryServer server = new DiscoveryServer()) {
            server.file("tool-1.0.jar", "jar");
            DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"), "maven",
                    server.files() + "{artifactId}-{version}.{type}",
                    null,
                    null,
                    null);

            assertThat(content(location.fetch(values(null, "jar"), true, CONNECTION, null, Palette.NONE)))
                    .isEqualTo("jar");
        }
    }

    @Test
    public void names_the_labels_of_a_namespace_below_its_domain_as_a_dashed_suffix() {
        DiscoveredLocation location = new DiscoveredLocation("bytebuddy.net", source("bytebuddy.net"),
                "moduletomaven",
                "net.bytebuddy:byte-buddy{-suffix}",
                null,
                null,
                null);

        assertThat(location.suffix("net.bytebuddy")).isEmpty();
        assertThat(location.suffix("net.bytebuddy.agent")).isEqualTo("-agent");
        assertThat(location.suffix("net.bytebuddy.agent.jvm")).isEqualTo("-agent-jvm");
        assertThatThrownBy(() -> location.suffix("com.example.tool"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bytebuddy.net");
    }

    @Test
    public void admits_a_version_of_any_suffix_where_the_entry_lists_none() {
        DiscoveredLocation location = location("https://example.com/");

        assertThat(location.admits("1.0")).isTrue();
        assertThat(location.admits("1.0-SNAPSHOT")).isTrue();
        assertThat(location.admits("1.0-rc.1")).isTrue();
        assertThat(location.admits(null)).isTrue();
    }

    @Test
    public void admits_only_the_suffixes_an_entry_lists_with_none_naming_a_version_without_one() {
        DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"),
                "maven",
                "https://example.com/",
                null,
                List.of("none", "rc"),
                null);

        assertThat(location.admits("1.0")).isTrue();
        assertThat(location.admits("1.0-rc")).isTrue();
        assertThat(location.admits("1.0-rc.1")).isTrue();
        assertThat(location.admits("1.0-RC2")).as("a suffix is matched ignoring case").isTrue();
        assertThat(location.admits("1.0-rc-3")).isTrue();
        assertThat(location.admits("1.0-SNAPSHOT")).isFalse();
        assertThat(location.admits("1.0-rcx")).as("a suffix matches a whole leading word only").isFalse();
        assertThat(location.admits(null))
                .as("a request for no version in particular cannot be held to the suffixes")
                .isFalse();
    }

    @Test
    public void admits_a_snapshot_alone_where_an_entry_lists_only_its_suffix() {
        DiscoveredLocation location = new DiscoveredLocation("example.com", source("example.com"),
                "maven",
                "https://example.com/",
                "2.0",
                List.of("snapshot"),
                null);

        assertThat(location.admits("2.1-SNAPSHOT")).isTrue();
        assertThat(location.admits("2.1")).isFalse();
        assertThat(location.admits("1.9-SNAPSHOT")).as("the floor still applies").isFalse();
    }

    private static DiscoveredLocation location(DiscoveryServer server, String latest) {
        return new DiscoveredLocation("example.com", source("example.com"), "maven",
                server.root() + "/releases/v{version}/tool-{version}.{type}",
                null,
                null,
                server.latest() + latest);
    }

    private static DiscoveredLocation location(String target) {
        return new DiscoveredLocation("example.com", source("example.com"), "maven", target, null, null, null);
    }

    private static URI source(String domain) {
        return URI.create("https://" + domain + "/.well-known/java-repository.properties");
    }

    private static Map<String, String> values(String classifier, String type) {
        Map<String, String> values = new HashMap<>();
        values.put("groupId", "com.example");
        values.put("groupPath", "com/example");
        values.put("artifactId", "tool");
        values.put("version", "1.0");
        values.put("-classifier", classifier == null ? "" : "-" + classifier);
        values.put("type", type);
        return values;
    }

    private static String content(Optional<RepositoryItem> item) throws IOException {
        assertThat(item).isPresent();
        try (InputStream inputStream = item.orElseThrow().toInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
