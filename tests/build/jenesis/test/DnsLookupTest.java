package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.DnsLocation;
import build.jenesis.DnsLookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DnsLookupTest {

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
    public void reads_the_value_of_a_key_from_the_txt_record_of_the_reversed_name() throws IOException {
        dns.record("_java.bytebuddy.net", "\"javamodule=https://example.com/\"");

        assertThat(dns.lookup().lookup("net.bytebuddy", "javamodule").map(DnsLocation::target))
                .contains("https://example.com/");
    }

    @Test
    public void walks_up_to_the_record_of_a_shorter_prefix_of_the_name() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=https://example.com/\"");

        assertThat(dns.lookup().lookup("net.bytebuddy.agent", "maven").map(DnsLocation::target))
                .contains("https://example.com/");
        assertThat(dns.queried())
                .as("the most specific name is asked first, and a single label is never a domain of its own")
                .containsExactly("_java.agent.bytebuddy.net", "_java.bytebuddy.net");
    }

    @Test
    public void asks_each_name_once_whatever_key_is_looked_up() throws IOException {
        dns.record("_java.bytebuddy.net", "\"javamodule=https://modules/\"", "\"maven=https://maven/\"");

        DnsLookup lookup = dns.lookup();

        assertThat(lookup.lookup("net.bytebuddy", "javamodule").map(DnsLocation::target))
                .contains("https://modules/");
        assertThat(lookup.lookup("net.bytebuddy", "maven").map(DnsLocation::target)).contains("https://maven/");
        assertThat(dns.queried()).containsExactly("_java.bytebuddy.net");
    }

    @Test
    public void joins_the_character_strings_of_a_long_txt_record() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=https://exa\" \"mple.com/\\\"q\\\"\\059\"");

        assertThat(dns.lookup().lookup("net.bytebuddy", "maven").map(DnsLocation::target))
                .contains("https://example.com/\"q\";");
    }

    @Test
    public void ignores_txt_records_of_other_keys() throws IOException {
        dns.record("_java.bytebuddy.net", "\"v=spf1 -all\"", "\"javamodule=https://example.com/\"");

        assertThat(dns.lookup().lookup("net.bytebuddy", "maven")).isEmpty();
    }

    @Test
    public void returns_empty_where_no_record_exists() throws IOException {
        assertThat(dns.lookup().lookup("net.bytebuddy", "maven")).isEmpty();
    }

    @Test
    public void asks_nothing_for_a_name_that_cannot_be_a_domain() throws IOException {
        assertThat(dns.lookup().lookup("widget", "maven")).isEmpty();
        assertThat(dns.lookup().lookup("net.byte+buddy", "maven")).isEmpty();
        assertThat(dns.queried()).isEmpty();
    }

    @Test
    public void refuses_a_record_that_dnssec_did_not_authenticate() {
        dns.record("_java.bytebuddy.net", "\"maven=https://example.com/\"").authenticated(false);

        assertThatThrownBy(() -> dns.lookup().lookup("net.bytebuddy", "maven"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DNSSEC")
                .hasMessageContaining("jenesis.dns.secure=false");
    }

    @Test
    public void accepts_an_unsigned_record_where_dnssec_is_not_required() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=https://example.com/\"").authenticated(false);

        assertThat(dns.lookup().secure(false).lookup("net.bytebuddy", "maven").map(DnsLocation::target))
                .contains("https://example.com/");
    }

    @Test
    public void refuses_two_records_of_the_same_key() {
        dns.record("_java.bytebuddy.net", "\"maven=https://a/\"", "\"maven=https://b/\"");

        assertThatThrownBy(() -> dns.lookup().lookup("net.bytebuddy", "maven"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unambiguous");
    }

    @Test
    public void admits_every_version_where_the_record_names_no_floor() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=https://example.com/\"");

        DnsLocation location = dns.lookup().lookup("net.bytebuddy", "maven").orElseThrow();

        assertThat(location.since()).isNull();
        assertThat(location.admits("0.1")).isTrue();
        assertThat(location.admits(null)).isTrue();
    }

    @Test
    public void admits_only_the_version_a_record_names_as_its_floor_and_later_ones() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=https://example.com/  since=1.2.3\"");

        DnsLocation location = dns.lookup().lookup("net.bytebuddy", "maven").orElseThrow();

        assertThat(location.target()).isEqualTo("https://example.com/");
        assertThat(location.since()).isEqualTo("1.2.3");
        assertThat(location.admits("1.2.3")).isTrue();
        assertThat(location.admits("1.2.4-SNAPSHOT")).isTrue();
        assertThat(location.admits("1.10.0")).as("versions are ordered by number, not by text").isTrue();
        assertThat(location.admits("2.0.0")).isTrue();
        assertThat(location.admits("1.2.2")).isFalse();
        assertThat(location.admits("1.2.3-rc.1"))
                .as("a pre-release of the floor comes before its release, as in semantic versioning")
                .isFalse();
        assertThat(location.admits(null))
                .as("a request for no version in particular cannot be held to a floor")
                .isFalse();
    }

    @Test
    public void refuses_an_attribute_other_than_since() {
        dns.record("_java.bytebuddy.net", "\"maven=https://example.com/ until=2.0\"");

        assertThatThrownBy(() -> dns.lookup().lookup("net.bytebuddy", "maven"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'until'")
                .hasMessageContaining("since=<version>");
    }

    @Test
    public void refuses_a_floor_without_a_version_or_named_twice() {
        dns.record("_java.bytebuddy.net", "\"maven=https://example.com/ since=\"");
        dns.record("_java.example.com", "\"maven=https://example.com/ since=1 since=2\"");

        assertThatThrownBy(() -> dns.lookup().lookup("net.bytebuddy", "maven"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("since=<version>");
        assertThatThrownBy(() -> dns.lookup().lookup("com.example", "maven"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("once");
    }

    @Test
    public void names_a_namespace_reversed_below_the_java_label() {
        assertThat(DnsLookup.name("net.bytebuddy.agent")).isEqualTo("_java.agent.bytebuddy.net");
    }

    @Test
    public void reads_whichever_kind_the_most_specific_name_holds() throws IOException {
        dns.record("_java.agent.bytebuddy.net", "\"coordinate=net.bytebuddy/byte-buddy-agent\"")
                .record("_java.bytebuddy.net", "\"javamodule=https://example.com/{module}.jar\"");

        DnsLocation agent = dns.lookup().lookup("net.bytebuddy.agent", "javamodule", "coordinate").orElseThrow();
        DnsLocation other = dns.lookup().lookup("net.bytebuddy.other", "javamodule", "coordinate").orElseThrow();

        assertThat(agent.key()).isEqualTo("coordinate");
        assertThat(agent.name()).isEqualTo("_java.agent.bytebuddy.net");
        assertThat(agent.target()).isEqualTo("net.bytebuddy/byte-buddy-agent");
        assertThat(other.key()).isEqualTo("javamodule");
        assertThat(other.name()).isEqualTo("_java.bytebuddy.net");
    }

    @Test
    public void refuses_two_kinds_of_record_at_one_name() throws IOException {
        dns.record("_java.bytebuddy.net",
                "\"javamodule=https://example.com/{module}.jar\"",
                "\"coordinate=net.bytebuddy/byte-buddy\"");

        assertThatThrownBy(() -> dns.lookup().lookup("net.bytebuddy", "javamodule", "coordinate"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("javamodule= or coordinate=")
                .hasMessageContaining("unambiguous");
        assertThat(dns.lookup().lookup("net.bytebuddy", "coordinate").map(DnsLocation::target))
                .as("asked for one kind alone, the other is no rival")
                .contains("net.bytebuddy/byte-buddy");
    }

    @Test
    public void reads_the_suffixes_a_record_lists_beside_its_floor() throws IOException {
        dns.record("_java.bytebuddy.net", "\"maven=https://example.com/ suffixes=none,SNAPSHOT since=1.0\"");

        DnsLocation location = dns.lookup().lookup("net.bytebuddy", "maven").orElseThrow();

        assertThat(location.target()).isEqualTo("https://example.com/");
        assertThat(location.since()).isEqualTo("1.0");
        assertThat(location.suffixes()).containsExactly("none", "snapshot");
    }

    @Test
    public void refuses_a_suffix_that_is_not_a_word_or_a_list_with_a_blank_entry() {
        dns.record("_java.bytebuddy.net", "\"maven=https://example.com/ suffixes=rc.1\"");
        dns.record("_java.example.com", "\"maven=https://example.com/ suffixes=none,\"");

        assertThatThrownBy(() -> dns.lookup().lookup("net.bytebuddy", "maven"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'rc.1'")
                .hasMessageContaining("none for a version without one");
        assertThatThrownBy(() -> dns.lookup().lookup("com.example", "maven"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("''");
    }
}
