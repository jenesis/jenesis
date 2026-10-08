package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.DiscoveredLocation;
import build.jenesis.Discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DiscoveryTest {

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
    public void names_a_namespace_as_the_domain_it_reverses() {
        assertThat(Discovery.domain("net.bytebuddy.agent")).isEqualTo("agent.bytebuddy.net");
    }

    @Test
    public void reads_the_value_of_a_key_from_the_file_of_the_domain_a_name_reverses() throws IOException {
        server.domain("bytebuddy.net", "maven=https://example.com/");

        DiscoveredLocation location = server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy").orElseThrow();

        assertThat(location.domain()).isEqualTo("bytebuddy.net");
        assertThat(location.key()).isEqualTo("maven");
        assertThat(location.target()).isEqualTo("https://example.com/");
        assertThat(location.source().toString()).endsWith("/bytebuddy.net/.well-known/java-repository.properties");
    }

    @Test
    public void lets_the_domain_a_vendor_owns_answer_for_its_subdomains_without_asking_them() throws IOException {
        server.domain("bytebuddy.net", "maven=https://example.com/")
                .domain("agent.bytebuddy.net", "maven=https://agent/");

        assertThat(server.discovery()
                .lookup("net.bytebuddy.agent", "maven", "byte-buddy")
                .map(DiscoveredLocation::target))
                .contains("https://example.com/");
        assertThat(server.queried())
                .as("the shortest domain is asked first, and a single label is never a domain of its own")
                .containsExactly("bytebuddy.net");
    }

    @Test
    public void lets_the_files_of_subdomains_answer_first_where_a_file_delegates_to_them() throws IOException {
        server.domain("bytebuddy.net", "maven=https://root/", "delegate=true")
                .domain("agent.bytebuddy.net", "maven=https://agent/")
                .domain("dep.bytebuddy.net", "module=https://dep/");

        Discovery discovery = server.discovery();

        assertThat(discovery.lookup("net.bytebuddy.agent", "maven", "byte-buddy").map(DiscoveredLocation::target))
                .contains("https://agent/");
        assertThat(discovery.lookup("net.bytebuddy.dep", "maven", "byte-buddy").map(DiscoveredLocation::target))
                .as("a subdomain's file without the key leaves the entry of its domain in force")
                .contains("https://root/");
        assertThat(discovery.lookup("net.bytebuddy.other", "maven", "byte-buddy").map(DiscoveredLocation::target))
                .contains("https://root/");
        assertThat(server.queried())
                .containsExactly("bytebuddy.net", "agent.bytebuddy.net", "dep.bytebuddy.net", "other.bytebuddy.net");
    }

    @Test
    public void refuses_a_delegate_that_is_neither_true_nor_false() {
        server.domain("bytebuddy.net", "maven=https://root/", "delegate=no");

        assertThatThrownBy(() -> server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delegate=no")
                .hasMessageContaining("false, the default");
    }

    @Test
    public void walks_on_to_the_file_of_a_subdomain_where_the_domain_publishes_none() throws IOException {
        server.domain("agent.bytebuddy.net", "maven=https://agent/");

        assertThat(server.discovery()
                .lookup("net.bytebuddy.agent", "maven", "byte-buddy")
                .map(DiscoveredLocation::target))
                .contains("https://agent/");
        assertThat(server.queried()).containsExactly("bytebuddy.net", "agent.bytebuddy.net");
    }

    @Test
    public void asks_each_domain_once_whatever_key_is_looked_up() throws IOException {
        server.domain("bytebuddy.net", "module=https://modules/", "maven=https://maven/");

        Discovery lookup = server.discovery();

        assertThat(lookup.lookup("net.bytebuddy", "module", "net.bytebuddy").map(DiscoveredLocation::target))
                .contains("https://modules/");
        assertThat(lookup.lookup("net.bytebuddy", "maven", "byte-buddy").map(DiscoveredLocation::target))
                .contains("https://maven/");
        assertThat(server.queried()).containsExactly("bytebuddy.net");
    }

    @Test
    public void fetches_the_file_of_a_domain_once_for_lookups_running_at_the_same_time() throws Exception {
        server.domain("agent.bytebuddy.net", "maven=https://example.com/");
        Discovery discovery = server.discovery();
        List<Callable<Optional<DiscoveredLocation>>> lookups = new ArrayList<>();
        for (int index = 0; index < 16; index++) {
            lookups.add(() -> discovery.lookup("net.bytebuddy.agent", "maven", "byte-buddy"));
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<Optional<DiscoveredLocation>> result : executor.invokeAll(lookups)) {
                assertThat(result.get().map(DiscoveredLocation::target)).contains("https://example.com/");
            }
        }
        assertThat(server.queried())
                .as("a domain without a file is remembered as one just as a domain with one")
                .containsExactly("bytebuddy.net", "agent.bytebuddy.net");
    }

    @Test
    public void reads_a_file_as_java_util_properties_and_ignores_keys_it_does_not_know() throws IOException {
        server.domain("bytebuddy.net",
                "# where Byte Buddy is published",
                "future.kind=anything",
                "maven = https://example.com/\\",
                "    releases/");

        assertThat(server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy").map(DiscoveredLocation::target))
                .contains("https://example.com/releases/");
    }

    @Test
    public void asks_no_domain_while_offline() throws IOException {
        server.domain("bytebuddy.net", "maven=https://maven/");

        assertThat(server.discovery()
                .connection(server.connection().offline(true))
                .lookup("net.bytebuddy", "maven", "byte-buddy")).isEmpty();
        assertThat(server.queried()).isEmpty();
    }

    @Test
    public void refuses_a_latest_file_beside_a_root_or_a_coordinate() {
        server.domain("bytebuddy.net", "maven=https://maven/", "maven.latest=https://maven/latest")
                .domain("jenesis.build", "moduletomaven=build.jenesis:{module}", "moduletomaven.latest=https://x/v");

        assertThatThrownBy(() -> server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maven.latest")
                .hasMessageContaining("only a template");
        assertThatThrownBy(() -> server.discovery().lookup("build.jenesis", "moduletomaven", "build.jenesis"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("moduletomaven.latest");
    }

    @Test
    public void answers_from_the_first_file_alone_even_where_it_holds_no_entry_for_the_key() throws IOException {
        server.domain("bytebuddy.net", "maven=https://maven/")
                .domain("agent.bytebuddy.net", "moduletomaven=net.bytebuddy:byte-buddy-agent");

        assertThat(server.discovery().lookup("net.bytebuddy.agent", "moduletomaven", "net.bytebuddy.agent"))
                .as("the domain that publishes a file speaks for every name below it")
                .isEmpty();
        assertThat(server.queried()).containsExactly("bytebuddy.net");
    }

    @Test
    public void returns_empty_where_no_file_exists() throws IOException {
        assertThat(server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy")).isEmpty();
    }

    @Test
    public void asks_nothing_for_a_name_that_cannot_be_a_domain() throws IOException {
        assertThat(server.discovery().lookup("widget", "maven", "byte-buddy")).isEmpty();
        assertThat(server.discovery().lookup("net.byte+buddy", "maven", "byte-buddy")).isEmpty();
        assertThat(server.queried()).isEmpty();
    }

    @Test
    public void counts_a_file_that_cannot_be_fetched_as_absent() throws IOException {
        assertThat(new Discovery().uri("http://localhost:1/{domain}").connection(server.connection())
                .lookup("net.bytebuddy", "maven", "byte-buddy"))
                .as("a domain whose host is unknown or unreachable publishes nothing, as one behind a proxy appears")
                .isEmpty();
    }

    @Test
    public void refuses_a_location_of_files_that_does_not_name_the_domain() {
        assertThatThrownBy(() -> new Discovery().uri("https://mirror.example.com/java-repository.properties"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("{domain}");
    }

    @Test
    public void takes_the_last_value_of_a_key_named_twice_as_java_util_properties_does() throws IOException {
        server.domain("bytebuddy.net", "maven=https://first/", "maven=https://last/");

        assertThat(server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy").map(DiscoveredLocation::target))
                .contains("https://last/");
    }

    @Test
    public void refuses_a_key_without_a_value() {
        server.domain("bytebuddy.net", "maven=");

        assertThatThrownBy(() -> server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maven without a value");
    }

    @Test
    public void reads_the_versions_an_entry_serves_from_keys_beside_it() throws IOException {
        server.domain("bytebuddy.net",
                "maven=https://example.com/",
                "maven.since=1.0",
                "maven.suffixes=none, SNAPSHOT");

        DiscoveredLocation location = server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy").orElseThrow();

        assertThat(location.since()).isEqualTo("1.0");
        assertThat(location.suffixes()).containsExactly("none", "snapshot");
        assertThat(location.admits("1.1")).isTrue();
        assertThat(location.admits("1.1-SNAPSHOT")).isTrue();
        assertThat(location.admits("1.1-rc1")).isFalse();
        assertThat(location.admits("0.9")).isFalse();
    }

    @Test
    public void reads_no_attribute_of_another_key() throws IOException {
        server.domain("bytebuddy.net", "maven=https://example.com/", "module.since=1.0");

        DiscoveredLocation location = server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy").orElseThrow();

        assertThat(location.since()).isNull();
        assertThat(location.suffixes()).isNull();
    }

    @Test
    public void refuses_a_suffix_that_is_not_a_word_or_a_list_with_a_blank_entry() {
        server.domain("bytebuddy.net", "maven=https://example.com/", "maven.suffixes=rc.1")
                .domain("example.com", "maven=https://example.com/", "maven.suffixes=none,");

        assertThatThrownBy(() -> server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'rc.1'")
                .hasMessageContaining("none for a version without one");
        assertThatThrownBy(() -> server.discovery().lookup("com.example", "maven", "byte-buddy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("''");
    }

    @Test
    public void tells_a_coordinate_from_a_location_by_the_form_of_its_value() throws IOException {
        server.domain("bytebuddy.net",
                "moduletomaven=net.bytebuddy:byte-buddy{-suffix}",
                "module=https://example.com/{module}.jar");

        DiscoveredLocation agent = server.discovery()
                .lookup("net.bytebuddy.agent", "moduletomaven", "net.bytebuddy.agent")
                .orElseThrow();
        DiscoveredLocation other = server.discovery()
                .lookup("net.bytebuddy.other", "module", "net.bytebuddy.other")
                .orElseThrow();

        assertThat(agent.coordinate()).as("a value without :// is a Maven coordinate").isTrue();
        assertThat(agent.domain()).isEqualTo("bytebuddy.net");
        assertThat(agent.target()).isEqualTo("net.bytebuddy:byte-buddy{-suffix}");
        assertThat(other.coordinate()).as("a value naming :// is a location").isFalse();
        assertThat(other.domain()).isEqualTo("bytebuddy.net");
    }

    @Test
    public void selects_the_key_that_names_an_artifact_over_a_prefix_and_over_the_key_for_all() throws IOException {
        server.domain("bytebuddy.net",
                "maven=https://all/",
                "maven[byte-buddy-*]=https://prefix/",
                "maven[byte-buddy-agent]=https://agent/{artifactId}-{version}.{type}",
                "maven[byte-buddy-agent].latest=https://agent/latest/{artifactId}.jar",
                "maven[byte-buddy-agent].since=1.0");
        Discovery discovery = server.discovery();

        DiscoveredLocation agent = discovery.lookup("net.bytebuddy", "maven", "byte-buddy-agent").orElseThrow();

        assertThat(agent.key()).isEqualTo("maven[byte-buddy-agent]");
        assertThat(agent.target()).isEqualTo("https://agent/{artifactId}-{version}.{type}");
        assertThat(agent.latest())
                .as("the keys beside a selected key are read with its selector")
                .isEqualTo("https://agent/latest/{artifactId}.jar");
        assertThat(agent.since()).isEqualTo("1.0");
        assertThat(discovery.lookup("net.bytebuddy", "maven", "byte-buddy-dep").map(DiscoveredLocation::target))
                .contains("https://prefix/");
        assertThat(discovery.lookup("net.bytebuddy", "maven", "byte-buddy").map(DiscoveredLocation::target))
                .contains("https://all/");
        assertThat(server.queried()).containsExactly("bytebuddy.net");
    }

    @Test
    public void prefers_the_longest_prefix_a_name_starts_with() throws IOException {
        server.domain("bytebuddy.net",
                "maven[byte-*]=https://short/",
                "maven[byte-buddy-*]=https://long/");

        assertThat(server.discovery()
                .lookup("net.bytebuddy", "maven", "byte-buddy-agent")
                .map(DiscoveredLocation::target))
                .contains("https://long/");
    }

    @Test
    public void answers_nothing_for_a_name_no_selector_matches_without_a_key_for_all() throws IOException {
        server.domain("bytebuddy.net", "maven[byte-buddy-agent]=https://agent/");

        assertThat(server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy")).isEmpty();
    }

    @Test
    public void maps_a_selected_module_to_a_coordinate_without_placeholders_below_the_domain() throws IOException {
        server.domain("bytebuddy.net",
                "moduletomaven=net.bytebuddy:byte-buddy",
                "moduletomaven[net.bytebuddy.utility]=net.bytebuddy:byte-buddy-utility");
        Discovery discovery = server.discovery();

        assertThat(discovery.lookup("net.bytebuddy.utility", "moduletomaven", "net.bytebuddy.utility")
                .map(DiscoveredLocation::target))
                .as("a coordinate selected for a module applies to that module wherever it sits below the domain")
                .contains("net.bytebuddy:byte-buddy-utility");
        assertThat(discovery.lookup("net.bytebuddy.agent", "moduletomaven", "net.bytebuddy.agent"))
                .as("a coordinate for all without placeholders still belongs to the domain's own module alone")
                .isEmpty();
    }

    @Test
    public void refuses_a_selector_that_is_no_name_or_prefix() {
        server.domain("bytebuddy.net", "maven[byte/buddy]=https://agent/");

        assertThatThrownBy(() -> server.discovery().lookup("net.bytebuddy", "maven", "byte-buddy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maven[byte/buddy]")
                .hasMessageContaining("selector");
    }
}
