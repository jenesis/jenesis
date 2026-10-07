package build.jenesis.test.module;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.module.JenesisDnsRepository;
import build.jenesis.module.JenesisRepository;
import build.jenesis.test.DnsServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JenesisDnsRepositoryTest {

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
    public void resolves_a_module_from_the_template_its_domain_publishes() throws IOException {
        dns.record("_java.bytebuddy.net", "\"javamodule=" + dns.files() + "{module}-{version}.{type}\"")
                .file("net.bytebuddy.agent-1.0.jar", "agent");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy.agent/1.0"))).isEqualTo("agent");
    }

    @Test
    public void leaves_a_version_below_the_floor_of_the_record_to_the_module_repository() throws IOException {
        dns.record("_java.bytebuddy.net", "\"javamodule=" + dns.files() + "{module}-{version}.{type} since=1.2.3\"")
                .file("net.bytebuddy-1.2.2.jar", "old")
                .file("net.bytebuddy-1.2.3.jar", "floor");

        JenesisDnsRepository repository = repository();

        assertThat(repository.fetch(Runnable::run, "net.bytebuddy/1.2.2")).isEmpty();
        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/1.2.3"))).isEqualTo("floor");
    }

    @Test
    public void asks_the_module_service_a_record_names_as_its_root() throws IOException {
        List<String> requested = new CopyOnWriteArrayList<>();
        dns.record("_java.bytebuddy.net", "\"javamodule=" + dns.root() + "/service\"")
                .context("/service/", "fromService", requested);

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy"))).isEqualTo("fromService");
        assertThat(requested).allMatch(path -> path.startsWith("/service/module/"));
    }

    @Test
    public void maps_every_module_below_a_domain_to_the_maven_coordinate_its_record_names() throws IOException {
        dns.record("_java.jenesis.build", "\"coordinate=build.jenesis:{module}\"")
                .file("maven/build/jenesis/build.jenesis.tools/1.0/build.jenesis.tools-1.0.jar", "tools")
                .file("maven/build/jenesis/build.jenesis.tools/1.0/build.jenesis.tools-1.0-sources.jar", "sources");

        JenesisDnsRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "build.jenesis.tools/1.0"))).isEqualTo("tools");
        assertThat(content(repository.fetch(Runnable::run, "build.jenesis.tools-sources/1.0"))).isEqualTo("sources");
    }

    @Test
    public void resolves_a_mapped_module_without_a_version_through_the_maven_metadata() throws IOException {
        dns.record("_java.jenesis.build", "\"coordinate=build.jenesis:{module}\"")
                .file("maven/build/jenesis/build.jenesis/maven-metadata.xml",
                        "<metadata><versioning><release>2.0</release></versioning></metadata>")
                .file("maven/build/jenesis/build.jenesis/2.0/build.jenesis-2.0.jar", "release");

        assertThat(content(repository().fetch(Runnable::run, "build.jenesis"))).isEqualTo("release");
    }

    @Test
    public void applies_a_coordinate_without_the_module_placeholder_only_to_the_module_it_is_published_for()
            throws IOException {
        dns.record("_java.bytebuddy.net", "\"coordinate=net.bytebuddy:byte-buddy\"")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "byte-buddy");

        JenesisDnsRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/1.0"))).isEqualTo("byte-buddy");
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy.agent/1.0"))
                .as("a coordinate naming one artifact never stands in for a module below it")
                .isEmpty();
    }

    @Test
    public void maps_a_module_by_the_record_of_its_own_name_before_that_of_its_domain() throws IOException {
        dns.record("_java.agent.bytebuddy.net", "\"coordinate=net.bytebuddy:byte-buddy-agent\"")
                .record("_java.bytebuddy.net", "\"coordinate=net.bytebuddy:byte-buddy\"")
                .file("maven/net/bytebuddy/byte-buddy-agent/1.0/byte-buddy-agent-1.0.jar", "agent");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy.agent/1.0"))).isEqualTo("agent");
    }

    @Test
    public void maps_a_module_to_the_extension_and_classifier_its_coordinate_names() throws IOException {
        dns.record("_java.example.com", "\"coordinate=com.example:tool:jar:shaded\"")
                .file("maven/com/example/tool/1.0/tool-1.0-shaded.jar", "shaded");

        assertThat(content(repository().fetch(Runnable::run, "com.example/1.0"))).isEqualTo("shaded");
    }

    @Test
    public void refuses_a_coordinate_that_is_not_written_with_colons() {
        dns.record("_java.bytebuddy.net", "\"coordinate=net.bytebuddy/byte-buddy\"");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("net.bytebuddy/byte-buddy")
                .hasMessageContaining("<groupId>:<artifactId>[:<extension>[:<classifier>]]");
    }

    @Test
    public void refuses_a_coordinate_that_names_no_artifact() {
        dns.record("_java.bytebuddy.net", "\"coordinate=net.bytebuddy\"");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("_java.bytebuddy.net")
                .hasMessageContaining("<groupId>:<artifactId>");
    }

    @Test
    public void resolves_a_mapped_module_from_the_maven_location_its_group_publishes() throws IOException {
        dns.record("_java.jenesis.build",
                        "\"coordinate=build.jenesis:{module}\"",
                        "\"maven=" + dns.files() + "release/{artifactId}-{version}{-classifier}.{type}\"")
                .file("release/build.jenesis-1.0.jar", "fromRelease")
                .context("/service/", "fromService");
        Map<String, String> settings = Map.of("repository.insecure", "true",
                "dns.enabled", "true",
                "dns.uri", dns.resolver().toString(),
                "module.uri", dns.root() + "/service/",
                "module.local", local.toString(),
                "maven.uri", dns.files() + "central/");

        assertThat(content(JenesisRepository.ofEnvironment(new Environment(settings), JenesisRepository.Scope.MODULE)
                .fetch(Runnable::run, "build.jenesis/1.0")))
                .as("the module maps to a coordinate, and the coordinate's group names where it is published")
                .isEqualTo("fromRelease");
    }

    @Test
    public void serves_the_pom_of_a_mapped_module_where_a_module_is_asked_for_as_an_artifact() throws IOException {
        dns.record("_java.jenesis.build", "\"coordinate=build.jenesis:{module}\"")
                .file("maven/build/jenesis/build.jenesis/1.0/build.jenesis-1.0.pom", "<project/>");

        assertThat(content(repository().scope(JenesisRepository.Scope.ARTIFACT)
                .fetch(Runnable::run, "build.jenesis/1.0:pom"))).isEqualTo("<project/>");
    }

    @Test
    public void asks_the_artifact_endpoint_of_a_module_service_where_a_module_is_asked_for_as_an_artifact()
            throws IOException {
        List<String> requested = new CopyOnWriteArrayList<>();
        dns.record("_java.bytebuddy.net", "\"javamodule=" + dns.root() + "/service\"")
                .context("/service/", "<project/>", requested);

        assertThat(content(repository().scope(JenesisRepository.Scope.ARTIFACT)
                .fetch(Runnable::run, "net.bytebuddy/1.0:pom"))).isEqualTo("<project/>");
        assertThat(requested).isNotEmpty().allMatch(path -> path.startsWith("/service/artifact/"));
    }

    @Test
    public void resolves_the_pom_of_a_mapped_module_from_the_maven_location_its_group_publishes() throws IOException {
        dns.record("_java.jenesis.build",
                        "\"coordinate=build.jenesis:{module}\"",
                        "\"maven=" + dns.files() + "release/{artifactId}-{version}{-classifier}.{type}\"")
                .file("release/build.jenesis-1.0.pom", "fromRelease")
                .context("/service/", "fromService");
        Map<String, String> settings = Map.of("repository.insecure", "true",
                "dns.enabled", "true",
                "dns.uri", dns.resolver().toString(),
                "module.uri", dns.root() + "/service/",
                "module.local", local.toString(),
                "maven.uri", dns.files() + "central/");

        assertThat(content(JenesisRepository.ofEnvironment(new Environment(settings), JenesisRepository.Scope.ARTIFACT)
                .fetch(Runnable::run, "build.jenesis/1.0:pom")))
                .as("the module service is not asked for the POM of a module its domain maps to Maven")
                .isEqualTo("fromRelease");
    }

    @Test
    public void maps_every_module_of_a_domain_with_one_record_through_the_suffix_of_its_name() throws IOException {
        dns.record("_java.bytebuddy.net", "\"coordinate=net.bytebuddy:byte-buddy{-suffix}\"")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "byte-buddy")
                .file("maven/net/bytebuddy/byte-buddy-agent/1.0/byte-buddy-agent-1.0.jar", "agent");

        JenesisDnsRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/1.0"))).isEqualTo("byte-buddy");
        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy.agent/1.0"))).isEqualTo("agent");
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy.utility/1.0"))
                .as("a module whose suffix names no artifact maps to nothing rather than to another artifact")
                .isEmpty();
    }

    @Test
    public void fills_in_the_suffix_of_a_module_in_a_template_naming_its_files() throws IOException {
        dns.record("_java.bytebuddy.net", "\"javamodule=" + dns.files()
                        + "byte-buddy-{version}/byte-buddy{-suffix}-{version}{-classifier}.{type}\"")
                .file("byte-buddy-1.0/byte-buddy-agent-1.0.jar", "agent");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy.agent/1.0"))).isEqualTo("agent");
    }

    @Test
    public void returns_empty_where_no_record_exists() throws IOException {
        assertThat(repository().fetch(Runnable::run, "net.bytebuddy/1.0")).isEmpty();
    }

    @Test
    public void returns_empty_where_the_location_does_not_exist() throws IOException {
        dns.record("_java.bytebuddy.net", "\"javamodule=" + dns.files() + "{module}-{version}.{type}\"");

        assertThat(repository().fetch(Runnable::run, "net.bytebuddy/1.0")).isEmpty();
    }

    @Test
    public void serves_a_classifier_or_another_type_only_where_the_template_names_it() throws IOException {
        dns.record("_java.bytebuddy.net", "\"javamodule=" + dns.files() + "{module}-{version}.jar\"")
                .file("net.bytebuddy-1.0.jar", "jar");

        JenesisDnsRepository repository = repository();

        assertThat(repository.fetch(Runnable::run, "net.bytebuddy-sources/1.0")).isEmpty();
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy/1.0:pom")).isEmpty();
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy"))
                .as("DNS names no version, so a template with {version} needs one to be asked for")
                .isEmpty();
    }

    @Test
    public void fills_in_the_classifier_with_its_leading_dash() throws IOException {
        dns.record("_java.bytebuddy.net", "\"javamodule=" + dns.files() + "{module}-{version}{-classifier}.{type}\"")
                .file("net.bytebuddy-1.0-sources.jar", "sources");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy-sources/1.0"))).isEqualTo("sources");
    }

    @Test
    public void refuses_a_location_that_is_not_fetched_over_http() {
        dns.record("_java.bytebuddy.net", "\"javamodule=file:///etc/{module}\"");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file:///etc/net.bytebuddy");
    }

    @Test
    public void asks_dns_before_the_module_repository_where_enabled() throws IOException {
        dns.record("_java.bytebuddy.net", "\"javamodule=" + dns.files() + "{module}-{version}.{type}\"")
                .file("net.bytebuddy-1.0.jar", "fromDns")
                .context("/service/", "fromService");
        Map<String, String> settings = Map.of("repository.insecure", "true",
                "module.uri", dns.root() + "/service/",
                "module.local", local.toString(),
                "dns.uri", dns.resolver().toString());

        assertThat(content(JenesisRepository.ofEnvironment(new Environment(settings), JenesisRepository.Scope.MODULE)
                .fetch(Runnable::run, "net.bytebuddy/1.0")))
                .as("DNS is asked only where it is switched on")
                .isEqualTo("fromService");
        Map<String, String> enabled = new HashMap<>(settings);
        enabled.put("dns.enabled", "true");
        assertThat(content(JenesisRepository.ofEnvironment(new Environment(enabled), JenesisRepository.Scope.MODULE)
                .fetch(Runnable::run, "net.bytebuddy/1.0"))).isEqualTo("fromDns");
    }

    private JenesisDnsRepository repository() {
        return new JenesisDnsRepository()
                .dns(dns.lookup())
                .maven(new MavenDefaultRepository(URI.create(dns.files() + "maven/"), null, Map.of(), null)
                        .connection(dns.connection()))
                .connection(dns.connection());
    }

    private static String content(Optional<RepositoryItem> item) throws IOException {
        assertThat(item).isPresent();
        try (InputStream inputStream = item.orElseThrow().toInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
