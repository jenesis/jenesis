package build.jenesis.test.module;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.module.DiscoveryModuleRepository;
import build.jenesis.module.JenesisRepository;
import build.jenesis.test.DiscoveryServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DiscoveryModuleRepositoryTest {

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
    public void resolves_a_module_from_the_template_its_domain_publishes() throws IOException {
        server.domain("bytebuddy.net", "module=" + server.files() + "{module}-{version}.{type}")
                .file("net.bytebuddy.agent-1.0.jar", "agent");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy.agent/1.0"))).isEqualTo("agent");
    }

    @Test
    public void leaves_a_version_below_the_floor_of_the_record_to_the_module_repository() throws IOException {
        server.domain("bytebuddy.net",
                        "module=" + server.files() + "{module}-{version}.{type}",
                        "module.since=1.2.3")
                .file("net.bytebuddy-1.2.2.jar", "old")
                .file("net.bytebuddy-1.2.3.jar", "floor");

        DiscoveryModuleRepository repository = repository();

        assertThat(repository.fetch(Runnable::run, "net.bytebuddy/1.2.2")).isEmpty();
        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/1.2.3"))).isEqualTo("floor");
    }

    @Test
    public void asks_the_module_service_a_record_names_as_its_root() throws IOException {
        List<String> requested = new CopyOnWriteArrayList<>();
        server.domain("bytebuddy.net", "module=" + server.root() + "/service")
                .context("/service/", "fromService", requested);

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy"))).isEqualTo("fromService");
        assertThat(requested).allMatch(path -> path.startsWith("/service/module/"));
    }

    @Test
    public void maps_every_module_below_a_domain_to_the_maven_coordinate_its_record_names() throws IOException {
        server.domain("jenesis.build", "moduletomaven=build.jenesis:{module}")
                .file("maven/build/jenesis/build.jenesis.tools/1.0/build.jenesis.tools-1.0.jar", "tools")
                .file("maven/build/jenesis/build.jenesis.tools/1.0/build.jenesis.tools-1.0-sources.jar", "sources");

        DiscoveryModuleRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "build.jenesis.tools/1.0"))).isEqualTo("tools");
        assertThat(content(repository.fetch(Runnable::run, "build.jenesis.tools-sources/1.0"))).isEqualTo("sources");
    }

    @Test
    public void resolves_a_mapped_module_without_a_version_through_the_maven_metadata() throws IOException {
        server.domain("jenesis.build", "moduletomaven=build.jenesis:{module}")
                .file("maven/build/jenesis/build.jenesis/maven-metadata.xml",
                        "<metadata><versioning><release>2.0</release></versioning></metadata>")
                .file("maven/build/jenesis/build.jenesis/2.0/build.jenesis-2.0.jar", "release");

        assertThat(content(repository().fetch(Runnable::run, "build.jenesis"))).isEqualTo("release");
    }

    @Test
    public void applies_a_coordinate_without_the_module_placeholder_only_to_the_module_it_is_published_for()
            throws IOException {
        server.domain("bytebuddy.net", "moduletomaven=net.bytebuddy:byte-buddy")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "byte-buddy");

        DiscoveryModuleRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/1.0"))).isEqualTo("byte-buddy");
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy.agent/1.0"))
                .as("a coordinate naming one artifact never stands in for a module below it")
                .isEmpty();
        assertThat(server.queried())
                .as("nor does a subdomain answer where its domain publishes a file")
                .containsOnly("bytebuddy.net");
    }

    @Test
    public void maps_a_module_by_the_file_of_its_own_domain_where_no_shorter_domain_publishes_one()
            throws IOException {
        server.domain("agent.bytebuddy.net", "moduletomaven=net.bytebuddy:byte-buddy-agent")
                .file("maven/net/bytebuddy/byte-buddy-agent/1.0/byte-buddy-agent-1.0.jar", "agent");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy.agent/1.0"))).isEqualTo("agent");
    }

    @Test
    public void lets_the_coordinate_its_domain_maps_every_module_to_outrank_a_subdomain() throws IOException {
        server.domain("agent.bytebuddy.net", "moduletomaven=net.bytebuddy:other-agent")
                .domain("bytebuddy.net", "moduletomaven=net.bytebuddy:byte-buddy{-suffix}")
                .file("maven/net/bytebuddy/byte-buddy-agent/1.0/byte-buddy-agent-1.0.jar", "agent");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy.agent/1.0"))).isEqualTo("agent");
        assertThat(server.queried()).containsExactly("bytebuddy.net");
    }

    @Test
    public void maps_a_module_to_the_extension_and_classifier_its_coordinate_names() throws IOException {
        server.domain("example.com", "moduletomaven=com.example:tool:jar:shaded")
                .file("maven/com/example/tool/1.0/tool-1.0-shaded.jar", "shaded");

        assertThat(content(repository().fetch(Runnable::run, "com.example/1.0"))).isEqualTo("shaded");
    }

    @Test
    public void asks_for_the_files_of_a_module_first_and_for_its_maven_artifact_first_where_asked_as_an_artifact()
            throws IOException {
        server.domain("jenesis.build",
                        "module=" + server.files() + "release/{module}-{version}{-classifier}.{type}",
                        "moduletomaven=build.jenesis:{module}")
                .file("release/build.jenesis-1.0.jar", "fromModule")
                .file("release/build.jenesis-1.0.pom", "fromModule")
                .file("maven/build/jenesis/build.jenesis/1.0/build.jenesis-1.0.jar", "fromMaven")
                .file("maven/build/jenesis/build.jenesis/1.0/build.jenesis-1.0.pom", "fromMaven");

        assertThat(content(repository().fetch(Runnable::run, "build.jenesis/1.0")))
                .as("a module path resolves the module's own files before the Maven artifact it is published as")
                .isEqualTo("fromModule");
        assertThat(content(repository().scope(JenesisRepository.Scope.ARTIFACT)
                .fetch(Runnable::run, "build.jenesis/1.0:pom")))
                .as("a resolution through Maven asks the Maven artifact before the module's own files")
                .isEqualTo("fromMaven");
    }

    @Test
    public void leaves_a_request_the_files_of_a_module_cannot_answer_to_its_maven_artifact() throws IOException {
        server.domain("jenesis.build",
                        "module=" + server.files() + "release/{module}-{version}.{type}",
                        "moduletomaven=build.jenesis:{module}")
                .file("maven/build/jenesis/build.jenesis/maven-metadata.xml",
                        "<metadata><versioning><release>2.0</release></versioning></metadata>")
                .file("maven/build/jenesis/build.jenesis/2.0/build.jenesis-2.0.jar", "release");

        assertThat(content(repository().fetch(Runnable::run, "build.jenesis")))
                .as("a template names no version, so the newest release comes from the Maven metadata")
                .isEqualTo("release");
    }

    @Test
    public void refuses_a_coordinate_as_the_location_of_a_module_and_a_location_as_its_maven_artifact() {
        server.domain("bytebuddy.net", "module=net.bytebuddy:byte-buddy")
                .domain("jenesis.build", "moduletomaven=https://example.com/{module}.jar");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bytebuddy.net/.well-known/java-repository.properties")
                .hasMessageContaining("belongs in moduletomaven");
        assertThatThrownBy(() -> repository().fetch(Runnable::run, "build.jenesis/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.build/.well-known/java-repository.properties")
                .hasMessageContaining("belongs in module");
    }

    @Test
    public void resolves_the_newest_version_its_latest_link_redirects_to() throws IOException {
        server.domain("bytebuddy.net",
                        "module=" + server.files() + "{module}-{version}{-classifier}.{type}",
                        "module.latest=" + server.latest() + "{module}.jar")
                .answer("net.bytebuddy.agent.jar", 302, "Location", server.files() + "net.bytebuddy.agent-1.2.3.jar")
                .file("net.bytebuddy.agent-1.2.3.jar", "newest")
                .file("net.bytebuddy.agent-1.2.3-sources.jar", "sources");

        DiscoveryModuleRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy.agent"))).isEqualTo("newest");
        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy.agent-sources"))).isEqualTo("sources");
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy"))
                .as("a module whose latest link leads nowhere is left to the module repository")
                .isEmpty();
    }

    @Test
    public void leaves_a_newest_version_its_key_does_not_serve_to_the_module_repository() throws IOException {
        server.domain("bytebuddy.net",
                        "module=" + server.files() + "{module}-{version}.{type}",
                        "module.latest=" + server.latest() + "{module}.jar",
                        "module.suffixes=none")
                .answer("net.bytebuddy.jar", 302, "Location", server.files() + "net.bytebuddy-1.3.0-SNAPSHOT.jar")
                .file("net.bytebuddy-1.3.0-SNAPSHOT.jar", "snapshot");

        assertThat(repository().fetch(Runnable::run, "net.bytebuddy")).isEmpty();
    }

    @Test
    public void refuses_a_coordinate_that_is_not_written_with_colons() {
        server.domain("bytebuddy.net", "moduletomaven=net.bytebuddy/byte-buddy");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("net.bytebuddy/byte-buddy")
                .hasMessageContaining("<groupId>:<artifactId>[:<extension>[:<classifier>]]");
    }

    @Test
    public void refuses_a_coordinate_that_names_no_artifact() {
        server.domain("bytebuddy.net", "moduletomaven=net.bytebuddy");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/1.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bytebuddy.net/.well-known/java-repository.properties")
                .hasMessageContaining("<groupId>:<artifactId>");
    }

    @Test
    public void resolves_a_mapped_module_from_the_maven_location_its_group_publishes() throws IOException {
        server.domain("jenesis.build",
                        "moduletomaven=build.jenesis:{module}",
                        "maven=" + server.files() + "release/{artifactId}-{version}{-classifier}.{type}")
                .file("release/build.jenesis-1.0.jar", "fromRelease")
                .context("/service/", "fromService");
        Map<String, String> settings = Map.of("repository.insecure", "true",
                "module.uri", server.root() + "/service/",
                "module.local", local.toString(),
                "maven.uri", server.files() + "central/");

        assertThat(content(JenesisRepository.ofEnvironment(new Environment(settings),
                        JenesisRepository.Scope.MODULE,
                        server.discovery()).fetch(Runnable::run, "build.jenesis/1.0")))
                .as("the module maps to a coordinate, and the coordinate's group names where it is published")
                .isEqualTo("fromRelease");
        assertThat(server.queried())
                .as("the module and the Maven artifact it maps to share one reading of the domain's file")
                .containsExactly("jenesis.build");
    }

    @Test
    public void serves_the_pom_of_a_mapped_module_where_a_module_is_asked_for_as_an_artifact() throws IOException {
        server.domain("jenesis.build", "moduletomaven=build.jenesis:{module}")
                .file("maven/build/jenesis/build.jenesis/1.0/build.jenesis-1.0.pom", "<project/>");

        assertThat(content(repository().scope(JenesisRepository.Scope.ARTIFACT)
                .fetch(Runnable::run, "build.jenesis/1.0:pom"))).isEqualTo("<project/>");
    }

    @Test
    public void asks_the_artifact_endpoint_of_a_module_service_where_a_module_is_asked_for_as_an_artifact()
            throws IOException {
        List<String> requested = new CopyOnWriteArrayList<>();
        server.domain("bytebuddy.net", "module=" + server.root() + "/service")
                .context("/service/", "<project/>", requested);

        assertThat(content(repository().scope(JenesisRepository.Scope.ARTIFACT)
                .fetch(Runnable::run, "net.bytebuddy/1.0:pom"))).isEqualTo("<project/>");
        assertThat(requested).isNotEmpty().allMatch(path -> path.startsWith("/service/artifact/"));
    }

    @Test
    public void resolves_the_pom_of_a_mapped_module_from_the_maven_location_its_group_publishes() throws IOException {
        server.domain("jenesis.build",
                        "moduletomaven=build.jenesis:{module}",
                        "maven=" + server.files() + "release/{artifactId}-{version}{-classifier}.{type}")
                .file("release/build.jenesis-1.0.pom", "fromRelease")
                .context("/service/", "fromService");
        Map<String, String> settings = Map.of("repository.insecure", "true",
                "module.uri", server.root() + "/service/",
                "module.local", local.toString(),
                "maven.uri", server.files() + "central/");

        assertThat(content(JenesisRepository.ofEnvironment(new Environment(settings),
                        JenesisRepository.Scope.ARTIFACT,
                        server.discovery()).fetch(Runnable::run, "build.jenesis/1.0:pom")))
                .as("the module service is not asked for the POM of a module its domain maps to Maven")
                .isEqualTo("fromRelease");
    }

    @Test
    public void maps_every_module_of_a_domain_with_one_record_through_the_suffix_of_its_name() throws IOException {
        server.domain("bytebuddy.net", "moduletomaven=net.bytebuddy:byte-buddy{-suffix}")
                .file("maven/net/bytebuddy/byte-buddy/1.0/byte-buddy-1.0.jar", "byte-buddy")
                .file("maven/net/bytebuddy/byte-buddy-agent/1.0/byte-buddy-agent-1.0.jar", "agent");

        DiscoveryModuleRepository repository = repository();

        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/1.0"))).isEqualTo("byte-buddy");
        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy.agent/1.0"))).isEqualTo("agent");
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy.utility/1.0"))
                .as("a module whose suffix names no artifact maps to nothing rather than to another artifact")
                .isEmpty();
    }

    @Test
    public void fills_in_the_suffix_of_a_module_in_a_template_naming_its_files() throws IOException {
        server.domain("bytebuddy.net", "module=" + server.files()
                        + "byte-buddy-{version}/byte-buddy{-suffix}-{version}{-classifier}.{type}")
                .file("byte-buddy-1.0/byte-buddy-agent-1.0.jar", "agent");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy.agent/1.0"))).isEqualTo("agent");
    }

    @Test
    public void returns_empty_where_no_record_exists() throws IOException {
        assertThat(repository().fetch(Runnable::run, "net.bytebuddy/1.0")).isEmpty();
    }

    @Test
    public void returns_empty_where_the_location_does_not_exist() throws IOException {
        server.domain("bytebuddy.net", "module=" + server.files() + "{module}-{version}.{type}");

        assertThat(repository().fetch(Runnable::run, "net.bytebuddy/1.0")).isEmpty();
    }

    @Test
    public void serves_a_classifier_or_another_type_only_where_the_template_names_it() throws IOException {
        server.domain("bytebuddy.net", "module=" + server.files() + "{module}-{version}.jar")
                .file("net.bytebuddy-1.0.jar", "jar");

        DiscoveryModuleRepository repository = repository();

        assertThat(repository.fetch(Runnable::run, "net.bytebuddy-sources/1.0")).isEmpty();
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy/1.0:pom")).isEmpty();
        assertThat(repository.fetch(Runnable::run, "net.bytebuddy"))
                .as("a domain names no version, so a template with {version} needs one to be asked for")
                .isEmpty();
    }

    @Test
    public void fills_in_the_classifier_with_its_leading_dash() throws IOException {
        server.domain("bytebuddy.net", "module=" + server.files() + "{module}-{version}{-classifier}.{type}")
                .file("net.bytebuddy-1.0-sources.jar", "sources");

        assertThat(content(repository().fetch(Runnable::run, "net.bytebuddy-sources/1.0"))).isEqualTo("sources");
    }

    @Test
    public void refuses_a_location_that_is_not_fetched_over_http() {
        server.domain("bytebuddy.net", "module=file:///etc/{module}")
                .domain("example.com", "module=jar:https://example.com/{module}.jar!/")
                .domain("jenesis.build", "module=ftp://example.com/");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "net.bytebuddy/1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file:///etc/net.bytebuddy")
                .hasMessageContaining("only read over https");
        assertThatThrownBy(() -> repository().fetch(Runnable::run, "com.example/1.0"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jar:https://example.com/com.example.jar!/");
        assertThatThrownBy(() -> repository().fetch(Runnable::run, "build.jenesis/1.0"))
                .as("a root is held to the same schemes as a template")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ftp://example.com/");
    }

    @Test
    public void resolves_through_discovery_alone_where_the_module_remotes_are_empty() throws IOException {
        server.domain("bytebuddy.net", "module=" + server.files() + "{module}-{version}.{type}")
                .file("net.bytebuddy-1.0.jar", "fromDomain");
        Environment environment = new Environment(Map.of("repository.insecure", "true",
                "module.uri", "",
                "maven.uri", "",
                "module.local", local.toString()));

        JenesisRepository repository = JenesisRepository.ofEnvironment(environment,
                JenesisRepository.Scope.MODULE,
                server.discovery());

        assertThat(content(repository.fetch(Runnable::run, "net.bytebuddy/1.0"))).isEqualTo("fromDomain");
        assertThat(repository.fetch(Runnable::run, "com.example/1.0"))
                .as("an empty jenesis.module.uri leaves no remote to fall back to")
                .isEmpty();
    }

    @Test
    public void asks_the_domain_before_the_module_repository_where_discovery_is_given() throws IOException {
        server.domain("bytebuddy.net", "module=" + server.files() + "{module}-{version}.{type}")
                .file("net.bytebuddy-1.0.jar", "fromDomain")
                .context("/service/", "fromService");
        Environment environment = new Environment(Map.of("repository.insecure", "true",
                "module.uri", server.root() + "/service/",
                "module.local", local.toString()));

        assertThat(content(JenesisRepository.ofEnvironment(environment, JenesisRepository.Scope.MODULE)
                .fetch(Runnable::run, "net.bytebuddy/1.0")))
                .as("a domain is asked only where discovery is switched on")
                .isEqualTo("fromService");
        assertThat(content(JenesisRepository.ofEnvironment(environment,
                JenesisRepository.Scope.MODULE,
                server.discovery()).fetch(Runnable::run, "net.bytebuddy/1.0"))).isEqualTo("fromDomain");
    }

    private DiscoveryModuleRepository repository() {
        return new DiscoveryModuleRepository()
                .discovery(server.discovery())
                .maven(new MavenDefaultRepository(URI.create(server.files() + "maven/"), null, Map.of(), null)
                        .connection(server.connection()))
                .connection(server.connection());
    }

    private static String content(Optional<RepositoryItem> item) throws IOException {
        assertThat(item).isPresent();
        try (InputStream inputStream = item.orElseThrow().toInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
