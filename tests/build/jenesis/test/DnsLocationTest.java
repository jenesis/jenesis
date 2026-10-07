package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.DnsLocation;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DnsLocationTest {

    private static final Repository.Connection CONNECTION = new Repository.Connection().insecure(true).retries(0);

    @Test
    public void reads_a_location_without_placeholders_as_a_root() {
        DnsLocation location = new DnsLocation("_java.example.com",
                "maven",
                "https://example.com/maven",
                null,
                null);

        assertThat(location.template()).isFalse();
        assertThat(location.root(CONNECTION)).isEqualTo(URI.create("https://example.com/maven/"));
    }

    @Test
    public void fills_in_every_placeholder_of_a_template() {
        DnsLocation location = new DnsLocation("_java.example.com", "maven",
                "https://example.com/{groupPath}/{artifactId}/{version}/{artifactId}-{version}{-classifier}.{type}",
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
        DnsLocation location = new DnsLocation("_java.example.com",
                "maven",
                "https://example.com/{artifactId}.jar",
                null,
                null);

        assertThat(location.resolve(values(null, "jar"), CONNECTION))
                .contains(URI.create("https://example.com/tool.jar"));
        assertThat(location.resolve(values("sources", "jar"), CONNECTION)).isEmpty();
        assertThat(location.resolve(values(null, "pom"), CONNECTION)).isEmpty();
    }

    @Test
    public void resolves_nothing_where_a_placeholder_it_uses_has_no_value() {
        DnsLocation location = new DnsLocation("_java.example.com",
                "maven",
                "https://example.com/{version}.jar",
                null,
                null);
        Map<String, String> values = values(null, "jar");
        values.put("version", null);

        assertThat(location.resolve(values, CONNECTION)).isEmpty();
    }

    @Test
    public void refuses_a_placeholder_the_kind_does_not_know() {
        DnsLocation location = new DnsLocation("_java.example.com",
                "maven",
                "https://example.com/{module}.jar",
                null,
                null);

        assertThatThrownBy(() -> location.resolve(values(null, "jar"), CONNECTION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{module}")
                .hasMessageContaining("a maven= record")
                .hasMessageContaining("{artifactId}");
    }

    @Test
    public void refuses_a_location_that_is_not_read_over_http() {
        assertThatThrownBy(() -> new DnsLocation("_java.example.com", "maven", "file:///etc/", null, null)
                .root(CONNECTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file:///etc/");
        assertThatThrownBy(() -> new DnsLocation("_java.example.com", "maven", "http://example.com/", null, null)
                .root(new Repository.Connection()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jenesis.repository.insecure");
    }

    @Test
    public void validates_a_fetched_file_against_the_strongest_checksum_published_beside_it() throws Exception {
        try (DnsServer dns = new DnsServer()) {
            dns.file("tool-1.0.jar", "jar")
                    .file("tool-1.0.jar.sha256", HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256").digest("jar".getBytes(StandardCharsets.UTF_8)))
                            + "  tool-1.0.jar")
                    .file("tool-1.0.jar.sha1", "0".repeat(40));
            DnsLocation location = new DnsLocation("_java.example.com", "maven",
                    dns.files() + "{artifactId}-{version}.{type}",
                    null,
                    null);

            assertThat(content(location.fetch(values(null, "jar"), true, CONNECTION, null, Palette.NONE)))
                    .as("SHA-256 is preferred to SHA-1, so the wrong SHA-1 is never consulted")
                    .isEqualTo("jar");
        }
    }

    @Test
    public void refuses_a_fetched_file_whose_checksum_does_not_match() throws IOException {
        try (DnsServer dns = new DnsServer()) {
            dns.file("tool-1.0.jar", "jar").file("tool-1.0.jar.sha1", "0".repeat(40));
            DnsLocation location = new DnsLocation("_java.example.com", "maven",
                    dns.files() + "{artifactId}-{version}.{type}",
                    null,
                    null);

            assertThatThrownBy(() -> location.fetch(values(null, "jar"), true, CONNECTION, null, Palette.NONE))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-1")
                    .hasMessageContaining("_java.example.com");
            assertThat(content(location.fetch(values(null, "jar"), false, CONNECTION, null, Palette.NONE)))
                    .as("a signature or checksum is fetched as it stands")
                    .isEqualTo("jar");
        }
    }

    @Test
    public void accepts_a_fetched_file_that_publishes_no_checksum() throws IOException {
        try (DnsServer dns = new DnsServer()) {
            dns.file("tool-1.0.jar", "jar");
            DnsLocation location = new DnsLocation("_java.example.com", "maven",
                    dns.files() + "{artifactId}-{version}.{type}",
                    null,
                    null);

            assertThat(content(location.fetch(values(null, "jar"), true, CONNECTION, null, Palette.NONE)))
                    .isEqualTo("jar");
        }
    }

    @Test
    public void names_the_labels_of_a_namespace_below_its_record_as_a_dashed_suffix() {
        DnsLocation location = new DnsLocation("_java.bytebuddy.net",
                "coordinate",
                "net.bytebuddy/byte-buddy{-suffix}",
                null,
                null);

        assertThat(location.suffix("net.bytebuddy")).isEmpty();
        assertThat(location.suffix("net.bytebuddy.agent")).isEqualTo("-agent");
        assertThat(location.suffix("net.bytebuddy.agent.jvm")).isEqualTo("-agent-jvm");
        assertThatThrownBy(() -> location.suffix("com.example.tool"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("_java.bytebuddy.net");
    }

    @Test
    public void admits_a_version_of_any_suffix_where_the_record_lists_none() {
        DnsLocation location = new DnsLocation("_java.example.com", "maven", "https://example.com/", null, null);

        assertThat(location.admits("1.0")).isTrue();
        assertThat(location.admits("1.0-SNAPSHOT")).isTrue();
        assertThat(location.admits("1.0-rc.1")).isTrue();
        assertThat(location.admits(null)).isTrue();
    }

    @Test
    public void admits_only_the_suffixes_a_record_lists_with_none_naming_a_version_without_one() {
        DnsLocation location = new DnsLocation("_java.example.com",
                "maven",
                "https://example.com/",
                null,
                List.of("none", "rc"));

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
    public void admits_a_snapshot_alone_where_a_record_lists_only_its_suffix() {
        DnsLocation location = new DnsLocation("_java.example.com",
                "maven",
                "https://example.com/",
                "2.0",
                List.of("snapshot"));

        assertThat(location.admits("2.1-SNAPSHOT")).isTrue();
        assertThat(location.admits("2.1")).isFalse();
        assertThat(location.admits("1.9-SNAPSHOT")).as("the floor still applies").isFalse();
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
