package build.jenesis.test.maven;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.DependencyScope;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
import build.jenesis.maven.MavenDependencyKey;
import build.jenesis.maven.MavenDependencyName;
import build.jenesis.maven.MavenDependencyScope;
import build.jenesis.maven.MavenDependencyValue;
import build.jenesis.maven.MavenLocalPom;
import build.jenesis.maven.MavenPomEmitter;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.maven.MavenRepository;
import build.jenesis.maven.MavenResolver;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenPomResolverTest {

    @TempDir
    private Path repository, project;
    private MavenRepository mavenRepository;
    private MavenPomResolver mavenPomResolver;

    @BeforeEach
    public void setUp() throws Exception {
        mavenRepository = new MavenDefaultRepository(repository.toUri(), repository, Map.of(), null);
        mavenPomResolver = new MavenPomResolver(MavenDefaultVersionNegotiator.maven());
    }

    @AfterEach
    public void clearProperties() {
        System.clearProperty("jenesis.resolver.maven");
    }

    @Test
    public void system_property_picks_each_negotiation_strategy() throws IOException {
        Map<String, MavenPomResolver> cases = Map.of(
                "maven", new MavenPomResolver(MavenDefaultVersionNegotiator.maven()),
                "latest", new MavenPomResolver(MavenDefaultVersionNegotiator.latest()),
                "release", new MavenPomResolver(MavenDefaultVersionNegotiator.release()),
                "closest", new MavenPomResolver(MavenDefaultVersionNegotiator.closest()),
                "fail", new MavenPomResolver(MavenDefaultVersionNegotiator.fail()),
                "managed", new MavenPomResolver(MavenDefaultVersionNegotiator.managed()));
        for (Map.Entry<String, MavenPomResolver> entry : cases.entrySet()) {
            assertThat(serialize(MavenPomResolver.ofEnvironment(new Environment(Map.of("resolver.maven", entry.getKey())))))
                    .as("strategy=%s", entry.getKey())
                    .isEqualTo(serialize(entry.getValue()));
        }
    }

    @Test
    public void system_property_is_read_case_insensitively() throws IOException {
        assertThat(serialize(MavenPomResolver.ofEnvironment(new Environment(Map.of("resolver.maven", "LaTeSt")))))
                .isEqualTo(serialize(new MavenPomResolver(MavenDefaultVersionNegotiator.latest())));
    }

    @Test
    public void system_property_rejects_an_unknown_strategy() {
        assertThatThrownBy(() -> MavenPomResolver.ofEnvironment(new Environment(Map.of("resolver.maven", "nonsense"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown jenesis.resolver.maven 'nonsense',"
                        + " expected one of: maven, latest, release, stable, closest, fail, managed");
    }

    @Test
    public void the_plain_resolver_negotiates_as_maven() throws IOException {
        assertThat(serialize(new MavenPomResolver(MavenDefaultVersionNegotiator.maven())))
                .as("maven() names what the no-argument constructor already does")
                .isEqualTo(serialize(MavenPomResolver.ofEnvironment(Environment.NONE)));
    }

    @Test
    public void a_selected_negotiator_changes_what_the_resolution_is_keyed_by() throws IOException {
        assertThat(serialize(new MavenPomResolver(MavenDefaultVersionNegotiator.latest())))
                .as("the resolver travels in the step's serialized form, which is its cache key,"
                        + " so a resolution decided differently cannot be served from the cache")
                .isNotEqualTo(serialize(MavenPomResolver.ofEnvironment(Environment.NONE)));
    }

    @Test
    public void fail_rejects_two_dependencies_that_require_different_versions() throws IOException {
        addDivergingClosure();

        assertThatThrownBy(() -> new MavenPomResolver(MavenDefaultVersionNegotiator.fail()).dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Diverging versions")
                .hasMessageContaining("shared:artifact");
    }

    @Test
    public void fail_accepts_a_closure_that_agrees_on_every_version() throws IOException {
        addAgreeingClosure();

        assertThatCode(() -> new MavenPomResolver(MavenDefaultVersionNegotiator.fail()).dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .doesNotThrowAnyException();
    }

    @Test
    public void managed_rejects_a_version_only_a_dependency_names() throws IOException {
        addAgreeingClosure();

        assertThatThrownBy(() -> new MavenPomResolver(MavenDefaultVersionNegotiator.managed()).dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No managed version for shared:artifact");
    }

    @Test
    public void managed_accepts_a_version_the_root_dependency_management_names() throws IOException {
        addAgreeingClosure("""
                <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>shared</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    """);

        assertThatCode(() -> new MavenPomResolver(MavenDefaultVersionNegotiator.managed()).dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .doesNotThrowAnyException();
    }

    @Test
    public void managed_accepts_what_the_project_declares_itself() throws IOException {
        addToRepository("group", "artifact", "1", rootPom("", "middle"));
        addToRepository("middle", "artifact", "1", leafPom());

        assertThatCode(() -> new MavenPomResolver(MavenDefaultVersionNegotiator.managed()).dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .as("a dependency the project declares itself is a version the project named")
                .doesNotThrowAnyException();
    }

    private void addAgreeingClosure() throws IOException {
        addAgreeingClosure("");
    }

    private void addAgreeingClosure(String management) throws IOException {
        addToRepository("group", "artifact", "1", rootPom(management, "middle"));
        addToRepository("middle", "artifact", "1", middlePom("1"));
        addToRepository("shared", "artifact", "1", leafPom());
    }

    private void addDivergingClosure() throws IOException {
        addToRepository("group", "artifact", "1", rootPom("", "middle", "other"));
        addToRepository("middle", "artifact", "1", middlePom("1"));
        addToRepository("other", "artifact", "1", middlePom("2"));
        addToRepository("shared", "artifact", "1", leafPom());
        addToRepository("shared", "artifact", "2", leafPom());
    }

    private static String rootPom(String management, String... groupIds) {
        StringBuilder dependencies = new StringBuilder();
        for (String groupId : groupIds) {
            dependencies.append(dependencyOn(groupId, "1"));
        }
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    %s<dependencies>
                        %s
                    </dependencies>
                </project>
                """.formatted(management, dependencies);
    }

    private static String middlePom(String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        %s
                    </dependencies>
                </project>
                """.formatted(dependencyOn("shared", version));
    }

    private static String leafPom() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """;
    }

    private static String dependencyOn(String groupId, String version) {
        return """
                <dependency>
                            <groupId>%s</groupId>
                            <artifactId>artifact</artifactId>
                            <version>%s</version>
                        </dependency>
                        """.formatted(groupId, version);
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @Test
    public void can_resolve_dependencies() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void test_jar_type_normalizes_to_tests_classifier() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <type>test-jar</type>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", "tests"),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void explicit_classifier_on_test_jar_type_is_preserved() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <type>test-jar</type>
                            <classifier>fixtures</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", "fixtures"),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void reads_a_parent_whose_plugin_configuration_holds_an_element_with_an_unbound_prefix() throws IOException {
        addToRepository("group", "parent", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <properties>
                        <other.version>1</other.version>
                    </properties>
                    <build>
                        <plugins>
                            <plugin>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <configuration>
                                    <compilerArguments>
                                        <Xlint:all />
                                    </compilerArguments>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>artifact</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>${other.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", leafPom());
        assertThat(mavenPomResolver.dependencies(Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .containsExactly(Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependencies_with_property() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <properties>
                        <my.version>1</my.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>${my.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependencies_with_property_name_containing_dash() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <properties>
                        <version.org-json>1</version.org-json>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>${version.org-json}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void managed_provided_scope_applies_to_scope_less_transitive_dependency() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>intermediate</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("intermediate", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <dependencies>
                        <dependency>
                            <groupId>inner</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <packaging>pom</packaging>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>inner</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <scope>provided</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("inner", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("intermediate", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void managed_dep_without_scope_does_not_override_transitive_test_scope() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>intermediate</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>inner</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("intermediate", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>inner</groupId>
                            <artifactId>artifact</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("inner", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("intermediate", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void undefined_property_in_unconsumed_managed_dependency_is_tolerated() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>unused</groupId>
                                <artifactId>managed</artifactId>
                                <version>${undefined.property}</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void undefined_managed_version_applied_to_a_consumed_dependency_still_fails() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>${undefined.property}</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The dependency other:artifact:${undefined.property} names the property undefined.property");
    }

    @Test
    public void undefined_property_in_a_consumed_dependency_still_fails() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>${undefined.property}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The dependency other:artifact:${undefined.property} names the property undefined.property");
    }

    @Test
    public void rejects_a_module_classifier_pin_on_the_maven_path() {
        SequencedMap<String, String> versions = new LinkedHashMap<>();
        versions.put("other/artifact", ":sources");
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.of(),
                new LinkedHashMap<>(),
                versions,
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Module classifiers are not supported")
                .hasMessageContaining("other/artifact")
                .hasMessageContaining(":sources");
    }

    @Test
    public void can_resolve_dependencies_with_nested_property() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <properties>
                        <my.version>${intermediate.version}</my.version>
                        <intermediate.version>1</intermediate.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>${my.version}</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void property_value_with_unclosed_brace_is_interpolated_literally() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <properties>
                        <cache.classifier>natives${marker</cache.classifier>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <classifier>${cache.classifier}</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", "natives${marker"),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void property_value_with_dollar_digit_is_interpolated_literally() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <properties>
                        <cache.classifier>build$2</cache.classifier>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <classifier>${cache.classifier}</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", "build$2"),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependency_without_pom() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependencies_with_duplicate() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependencies_from_parent() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                </project>
                """);
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void fails_naming_a_parent_that_cannot_be_fetched() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>missing</artifactId>
                        <version>1-SNAPSHOT</version>
                    </parent>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot fetch the parent parent:missing:1-SNAPSHOT")
                .hasMessageContaining("-Djenesis.maven.uri");
    }

    @Test
    public void resolves_a_snapshot_parent_by_the_timestamped_pom_its_metadata_names() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1-SNAPSHOT</version>
                    </parent>
                </project>
                """);
        Path folder = Files.createDirectories(repository.resolve("parent/artifact/1-SNAPSHOT"));
        Files.writeString(folder.resolve("maven-metadata.xml"), """
                <metadata>
                  <versioning>
                    <snapshotVersions>
                      <snapshotVersion>
                        <extension>pom</extension>
                        <value>1-20261001.194937-10</value>
                      </snapshotVersion>
                    </snapshotVersions>
                  </versioning>
                </metadata>
                """);
        Files.writeString(folder.resolve("artifact-1-20261001.194937-10.pom"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        assertThat(mavenPomResolver.dependencies(Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .containsExactly(Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependencies_from_parent_before_transitive() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("other", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_duplicate_dependencies_from_parent() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_transitive_dependencies() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_transitive_dependencies_with_exclusion() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>transitive</groupId>
                                    <artifactId>artifact</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1",
                        MavenDependencyScope.COMPILE,
                        null,
                        List.of(new MavenDependencyName("transitive", "artifact")),
                        null)));
    }

    @Test
    public void can_resolve_transitive_dependencies_with_exclusion_wildcard() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>*</groupId>
                                    <artifactId>*</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1",
                        MavenDependencyScope.COMPILE,
                        null,
                        List.of(MavenDependencyName.EXCLUDE_ALL),
                        null)));
    }

    @Test
    public void can_resolve_transitive_dependencies_with_optional() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <optional>true</optional>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void a_processor_a_dependency_declares_is_not_followed_as_an_artifact() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>processing</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <type>processor</type>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies)
                .as("a processor compiles the module that declares it and is no artifact of the module's consumers")
                .containsExactly(Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void follows_a_relocation_to_the_coordinate_it_names_and_says_so() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>old</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <scope>runtime</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("old", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>old</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <distributionManagement>
                        <relocation>
                            <groupId>new</groupId>
                        </relocation>
                    </distributionManagement>
                </project>
                """);
        addToRepository("new", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        List<String> printed = new ArrayList<>();
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver
                .printing(printed::add, Palette.NONE)
                .dependencies(Runnable::run, mavenRepository, "group", "artifact", "1", null);
        assertThat(dependencies)
                .as("the relocated artifact publishes no jar, so the coordinate it names takes its place and scope")
                .containsExactly(Map.entry(
                                new MavenDependencyKey("new", "artifact", "jar", null),
                                new MavenDependencyValue("1", MavenDependencyScope.RUNTIME, null, null, null)),
                        Map.entry(
                                new MavenDependencyKey("transitive", "artifact", "jar", null),
                                new MavenDependencyValue("1", MavenDependencyScope.RUNTIME, null, null, null)));
        assertThat(printed).containsExactly("[RELOCATED] old:artifact:1 is relocated to new:artifact:1,"
                + " which is resolved in its place");
    }

    @Test
    public void can_resolve_transitive_dependencies_with_scope() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>test</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.TEST, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.TEST, null, null, null)));
    }

    @Test
    public void scope_with_surrounding_whitespace_is_normalized() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <scope>
                                runtime
                            </scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.RUNTIME, null, null, null)));
    }

    @Test
    public void unknown_scope_is_reported_with_offending_value() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <scope>bogus</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .hasStackTraceContaining("Unknown Maven dependency scope")
                .hasStackTraceContaining("bogus");
    }

    @Test
    public void can_resolve_dependency_configuration() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void does_not_resolve_dependency_configuration_of_dependency() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>intermediate</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("intermediate", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>transitive</groupId>
                                <artifactId>artifact</artifactId>
                                <version>2</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("intermediate", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependency_configuration_explicit_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>2</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependency_bom_configuration() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>import</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("import", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependency_bom_configuration_picks_first_import() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>import</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                            <dependency>
                                <groupId>other-import</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("import", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("other-import", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>2</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependency_bom_configuration_but_prefer_dependency_configuration() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                            <dependency>
                                <groupId>import</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("import", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>2</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependency_bom_configuration_flattens_nested_import() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>leaf</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>aggregator</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("aggregator", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>nested</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("nested", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>leaf</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("leaf", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("leaf", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependency_bom_configuration_flattens_nested_import_with_own_properties() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>leaf</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>aggregator</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("aggregator", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>nested</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("nested", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <properties>
                        <leaf.version>1</leaf.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>leaf</groupId>
                                <artifactId>artifact</artifactId>
                                <version>${leaf.version}</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("leaf", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("leaf", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_dependency_bom_configuration_with_diamond_import() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>leaf</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>aggregator</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("aggregator", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>left</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                            <dependency>
                                <groupId>right</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("left", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>shared</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("right", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>shared</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("shared", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>leaf</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("leaf", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("leaf", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_lowest_depth_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>deep</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>shallow</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("deep", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>intermediate</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("intermediate", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("shallow", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("deep", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("shallow", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("intermediate", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_lowest_depth_version_with_scope_override() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>deep</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>shallow</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("deep", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>intermediate</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("intermediate", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("shallow", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("deep", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("shallow", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.TEST, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("intermediate", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_lowest_depth_version_with_scope_override_and_nested_transitives() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>deep</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>shallow</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("deep", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>intermediate</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("intermediate", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>nested</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("nested", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("shallow", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("deep", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("shallow", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.TEST, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("intermediate", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("nested", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_release_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>RELEASE</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>1</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_release_version_through_a_cached_repository() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>RELEASE</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <release>1</release>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository.cached(Files.createDirectory(repository.resolve("cache"))),
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_release_version_with_legacy_metadata_modelversion() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>RELEASE</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <versioning>
                    <latest>2</latest>
                    <release>1</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_latest_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>LATEST</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>1</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_closed_range_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1,2]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>1</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void reports_discovered_range_and_negotiated_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1,2]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>1</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        addJarToRepository("group", "artifact", "1");
        addJarToRepository("transitive", "artifact", "2");
        Resolver.Resolution resolution = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.<String, Repository>of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("group/artifact/1", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE);
        List<String> followed = resolution.edges().stream()
                .filter(Resolver.Edge::followed)
                .map(Resolver.Edge::coordinate)
                .toList();
        assertThat(followed).containsExactly("maven/group/artifact/1", "maven/transitive/artifact/[1,2]");
        assertThat(resolution.vertices().get("maven/transitive/artifact").resolvedVersion()).isEqualTo("2");
    }

    @Test
    public void refuses_a_transitive_dependency_naming_a_property_its_pom_does_not_define_whatever_the_jvm_holds() throws IOException {
        addToRepository("group", "artifact", "1", rootPom("", "middle"));
        addToRepository("middle", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>shared</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <classifier>${jenesis.undefined.platform}</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """);
        System.setProperty("jenesis.undefined.platform", "linux");
        try {
            assertThatThrownBy(() -> mavenPomResolver.dependencies(
                    Runnable::run, mavenRepository, "group", "artifact", "1", null))
                    .as("a POM is read from its model alone, so the build JVM's properties never decide a coordinate")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("The dependency shared:artifact:${jenesis.undefined.platform}:1 of middle:artifact:1")
                    .hasMessageContaining("names the property jenesis.undefined.platform")
                    .hasMessageContaining("exclude shared:artifact from middle:artifact");
        } finally {
            System.clearProperty("jenesis.undefined.platform");
        }
    }

    @Test
    public void activates_the_profiles_of_a_dependency_pom_that_the_operating_system_selects() throws IOException {
        addToRepository("group", "artifact", "1", rootPom("", "openjfx"));
        addToRepository("openjfx", "javafx", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>openjfx</groupId>
                    <artifactId>javafx</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <profiles>
                        <profile>
                            <activation><os><name>linux</name></os></activation>
                            <properties><javafx.platform>linux</javafx.platform></properties>
                        </profile>
                        <profile>
                            <activation><os><name>linux</name><arch>aarch64</arch></os></activation>
                            <properties><javafx.platform>linux-aarch64</javafx.platform></properties>
                        </profile>
                        <profile>
                            <activation><os><family>mac</family><arch>!aarch64</arch></os></activation>
                            <properties><javafx.platform>mac</javafx.platform></properties>
                        </profile>
                        <profile>
                            <activation><os><family>windows</family></os></activation>
                            <properties><javafx.platform>win</javafx.platform></properties>
                        </profile>
                        <profile>
                            <activation><os><family>unix</family></os><jdk>[1.8,9)</jdk></activation>
                            <properties><javafx.platform>legacy</javafx.platform></properties>
                        </profile>
                    </profiles>
                </project>
                """);
        addToRepository("openjfx", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>openjfx</groupId>
                        <artifactId>javafx</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>artifact</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>openjfx</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <classifier>${javafx.platform}</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Map<List<String>, String> platforms = Map.of(
                List.of("Linux", "amd64", "6.1"), "linux",
                List.of("Linux", "aarch64", "6.1"), "linux-aarch64",
                List.of("Mac OS X", "x86_64", "14.4"), "mac",
                List.of("Windows 11", "amd64", "10.0"), "win");
        for (Map.Entry<List<String>, String> platform : platforms.entrySet()) {
            List<String> os = platform.getKey();
            assertThat(mavenPomResolver.os(os.get(0), os.get(1), os.get(2)).dependencies(
                    Runnable::run, mavenRepository, "group", "artifact", "1", null).keySet())
                    .as("OpenJFX selects its platform's jar by profiles its POM activates on the OS, %s here", os)
                    .contains(new MavenDependencyKey("openjfx", "artifact", "jar", platform.getValue()));
        }
    }

    @Test
    public void matches_a_family_that_maven_does_not_name_against_the_operating_system_name() throws IOException {
        addToRepository("group", "artifact", "1", rootPom("", "brotli"));
        addToRepository("brotli", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>brotli</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <profiles>
                        <profile>
                            <activation><os><family>Linux</family><arch>amd64</arch></os></activation>
                            <dependencies>
                                <dependency>
                                    <groupId>brotli</groupId>
                                    <artifactId>native-linux-x86_64</artifactId>
                                    <version>1</version>
                                </dependency>
                            </dependencies>
                        </profile>
                    </profiles>
                </project>
                """);
        addToRepository("brotli", "native-linux-x86_64", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>brotli</groupId>
                    <artifactId>native-linux-x86_64</artifactId>
                    <version>1</version>
                </project>
                """);
        MavenDependencyKey linux = new MavenDependencyKey("brotli", "native-linux-x86_64", "jar", null);
        assertThat(mavenPomResolver.os("Linux", "amd64", "6.1").dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null).keySet())
                .as("Maven matches a family it has no name for as a part of os.name, ignoring case")
                .contains(linux);
        assertThat(mavenPomResolver.os("Mac OS X", "amd64", "14.4").dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null).keySet())
                .doesNotContain(linux);
    }

    @Test
    public void applies_the_exclusion_a_dependency_inherits_from_its_parents_management_to_its_own_dependency() throws IOException {
        addToRepository("group", "artifact", "1", rootPom("", "embedder"));
        addToRepository("managing", "artifact", "1", rootPom("""
                <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>guava</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <exclusions>
                                    <exclusion>
                                        <groupId>other</groupId>
                                        <artifactId>artifact</artifactId>
                                    </exclusion>
                                </exclusions>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                """, "embedder"));
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>guava</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <exclusions>
                                    <exclusion>
                                        <groupId>j2objc</groupId>
                                        <artifactId>artifact</artifactId>
                                    </exclusion>
                                </exclusions>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("embedder", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <groupId>embedder</groupId>
                    <artifactId>artifact</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>guava</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("guava", "artifact", "1", rootPom("", "j2objc", "other"));
        for (String groupId : List.of("j2objc", "other")) {
            addToRepository(groupId, "artifact", "1", leafPom());
        }
        for (String root : List.of("group", "managing")) {
            assertThat(mavenPomResolver.dependencies(Runnable::run, mavenRepository, root, "artifact", "1", null).keySet())
                    .as("the management a POM inherits shapes its own dependencies before %s's management adds to it", root)
                    .contains(new MavenDependencyKey("guava", "artifact", "jar", null))
                    .doesNotContain(new MavenDependencyKey("j2objc", "artifact", "jar", null));
        }
        assertThat(mavenPomResolver.dependencies(Runnable::run, mavenRepository, "managing", "artifact", "1", null).keySet())
                .as("the exclusion the project manages is applied as well")
                .doesNotContain(new MavenDependencyKey("other", "artifact", "jar", null));
    }

    @Test
    public void applies_a_managed_exclusion_to_the_managed_dependency_where_it_is_reached_transitively() throws IOException {
        addToRepository("group", "artifact", "1", rootPom("", "middle"));
        addToRepository("middle", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>shared</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>kept</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>own</groupId>
                                    <artifactId>artifact</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("kept", "artifact", "1", rootPom("", "own", "other"));
        for (String groupId : List.of("shared", "own", "other")) {
            addToRepository(groupId, "artifact", "1", leafPom());
        }
        for (String groupId : List.of("group", "middle", "shared", "kept", "own", "other")) {
            addJarToRepository(groupId, "artifact", "1");
        }
        Resolver.Resolution resolution = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.<String, Repository>of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("group/artifact/1", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(Map.of(
                        "middle/artifact", new LinkedHashSet<>(List.of("shared/artifact")),
                        "kept/artifact", new LinkedHashSet<>(List.of("other/artifact")))),
                DependencyScope.COMPILE);
        assertThat(resolution.vertices().keySet())
                .as("Maven applies the exclusions a <dependencyManagement> entry declares wherever its dependency"
                        + " appears, in addition to those the depending POM declares")
                .containsExactlyInAnyOrder("maven/group/artifact", "maven/middle/artifact", "maven/kept/artifact");
    }

    @Test
    public void draws_no_edge_to_a_test_or_provided_dependency_of_a_library_that_is_resolved_otherwise() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>tested</artifactId>
                            <version>0.9</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>provided</artifactId>
                            <version>0.9</version>
                            <scope>provided</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        for (String artifact : List.of("tested", "provided")) {
            addToRepository("other", artifact, "1", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                        <modelVersion>4.0.0</modelVersion>
                    </project>
                    """);
            addJarToRepository("other", artifact, "1");
        }
        addJarToRepository("group", "artifact", "1");
        Resolver.Resolution resolution = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.<String, Repository>of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of(
                        "other/tested/1", Collections.emptyNavigableSet(),
                        "other/provided/1", Collections.emptyNavigableSet(),
                        "group/artifact/1", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE);
        assertThat(resolution.edges())
                .as("a library's own test and provided dependencies are not followed, so the tree draws no edge to them")
                .noneMatch(edge -> "maven/group/artifact/1".equals(edge.parent()));
        assertThat(resolution.vertices().get("maven/other/tested").resolvedVersion()).isEqualTo("1");
        assertThat(resolution.vertices().get("maven/other/provided").resolvedVersion()).isEqualTo("1");
    }

    @Test
    public void can_resolve_open_range_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>(1,3)</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>1</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_range_over_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[2]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>2</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_divergent_ranges() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>first</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                        <dependency>
                            <groupId>second</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("first", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                        <dependency>
                            <groupId>second</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1]</version>
                        </dependency>
                </project>
                """);
        addToRepository("second", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                        <dependency>
                            <groupId>first</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1]</version>
                        </dependency>
                </project>
                """);
        addToRepository("first", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("second", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("first/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>2</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("second/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>2</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("first", "artifact", "jar", null),
                        new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("second", "artifact", "jar", null),
                        new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_unbounded_upper_range_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[2,)</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "3", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>3</latest>
                    <release>3</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("3", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_unbounded_lower_range_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>(,2]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>3</latest>
                    <release>3</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_exclusive_upper_range_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1,3)</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>3</latest>
                    <release>3</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_multi_range_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1,2),[3,)</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "4", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>4</latest>
                    <release>4</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                      <version>4</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("4", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_multi_range_version_with_gap() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1,2],[4,5]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "5", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>6</latest>
                    <release>6</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                      <version>4</version>
                      <version>5</version>
                      <version>6</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("5", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_intersecting_ranges() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>first</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>second</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("first", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1,3]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("second", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[2,4]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "3", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>4</latest>
                    <release>4</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                      <version>4</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("first", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("second", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("3", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void cannot_resolve_disjoint_ranges() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>first</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>second</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("first", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1,1]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("second", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[3,3]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("transitive", "artifact", "3", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>3</latest>
                    <release>3</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not resolve version");
    }

    @Test
    public void cannot_resolve_range_with_no_matching_version() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>(1,)</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>1</latest>
                    <release>1</release>
                    <versions>
                      <version>1</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Could not resolve version in range");
    }

    @Test
    public void can_resolve_hard_requirement_over_soft_requirement() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>dep</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("dep", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>2</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("dep", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void only_reports_the_converged_graph() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>mid</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>forcer</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("mid", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>conflict</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("forcer", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>conflict</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[2]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("conflict", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>childone</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("conflict", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>childtwo</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("childone", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("childtwo", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("conflict/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>2</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        addJarToRepository("group", "artifact", "1");
        addJarToRepository("mid", "artifact", "1");
        addJarToRepository("forcer", "artifact", "1");
        addJarToRepository("conflict", "artifact", "2");
        addJarToRepository("childtwo", "artifact", "1");
        Resolver.Resolution resolution = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.<String, Repository>of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("group/artifact/1", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE);
        List<String> followedCoordinates = resolution.edges().stream()
                .filter(Resolver.Edge::followed)
                .map(Resolver.Edge::coordinate)
                .toList();
        assertThat(followedCoordinates).contains("maven/childtwo/artifact/1");
        assertThat(followedCoordinates).doesNotContain("maven/childone/artifact/1");
    }

    @Test
    public void can_resolve_range_over_soft_requirement_in_transitive_dependencies() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>first</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>second</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("first", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1,2]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("second", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>3</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("transitive", "artifact", "3", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>3</latest>
                    <release>3</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("first", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("second", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_direct_soft_with_transitive_range() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>dep</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("dep", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[2,3]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "3", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>3</latest>
                    <release>3</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("3", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("dep", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_direct_range_with_transitive_soft() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[2,3]</version>
                        </dependency>
                        <dependency>
                            <groupId>dep</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("dep", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "3", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>3</latest>
                    <release>3</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                      <version>3</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("3", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("dep", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_range_over_qualifier_versions() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1.0-alpha,1.0]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1.0", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>1.0.1</latest>
                    <release>1.0.1</release>
                    <versions>
                      <version>1.0-alpha</version>
                      <version>1.0-beta</version>
                      <version>1.0-rc1</version>
                      <version>1.0-SNAPSHOT</version>
                      <version>1.0</version>
                      <version>1.0.1</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("1.0", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_range_with_multi_segment_versions() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1.0,2.0]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2.0", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>3.0</latest>
                    <release>3.0</release>
                    <versions>
                      <version>1.0</version>
                      <version>1.5</version>
                      <version>2.0</version>
                      <version>3.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("2.0", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void multiple_checksum_comments_on_one_dependency_fail() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>group</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <!--Checksum/SHA256/cafebabe-->
                                <!--Checksum/SHA256/deadbeef-->
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple Checksum/* comments")
                .hasMessageContaining("group:artifact:1");
    }

    @Test
    public void names_a_dependency_a_local_pom_declares_twice_and_keeps_the_second_as_maven_does() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>project</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        List<String> printed = new ArrayList<>();
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver
                .printing(printed::add, Palette.NONE)
                .local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).dependencies())
                .as("the second declaration replaces the first, as in Maven")
                .containsExactly(Map.entry(new MavenDependencyKey("group", "artifact", "jar", null),
                        new MavenDependencyValue("2", MavenDependencyScope.TEST, null, null, null)));
        assertThat(printed).containsExactly("[DUPLICATE] The pom.xml of project declares group:artifact:jar twice,"
                + " at version 1 and 2: the second declaration replaces the first, as in Maven, which warns as well"
                + " - remove one");
    }

    @Test
    public void names_a_dependency_a_published_pom_declares_twice_once_and_as_needing_no_action() throws IOException {
        addToRepository("group", "artifact", "1", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>2</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "2", """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>other</groupId>
                    <artifactId>artifact</artifactId>
                    <version>2</version>
                </project>
                """);
        List<String> printed = new ArrayList<>();
        MavenPomResolver resolver = mavenPomResolver.printing(printed::add, Palette.NONE);
        for (int index = 0; index < 2; index++) {
            resolver.dependencies(Runnable::run, mavenRepository, "group", "artifact", "1", null);
        }
        assertThat(printed)
                .as("a POM read again by every resolution of a build is named once, as one its artifact was published with")
                .containsExactly("[DUPLICATE] The pom.xml of artifact declares other:artifact:jar twice,"
                        + " at version 1 and 2: the second declaration replaces the first, as in Maven, which warns as well"
                        + " - it is the artifact's own published POM, so this needs no action");
    }

    @Test
    public void local_pom_inherits_the_metadata_of_its_parent_in_the_project_as_maven_does() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <name>Parent</name>
                    <description>The parent.</description>
                    <url>https://example.com/project</url>
                    <organization>
                        <name>Example Ltd</name>
                    </organization>
                    <licenses>
                        <license>
                            <name>Apache-2.0</name>
                            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                        </license>
                    </licenses>
                    <developers>
                        <developer>
                            <id>google</id>
                        </developer>
                    </developers>
                    <scm child.scm.connection.inherit.append.path="false">
                        <connection>scm:git:https://example.com/project.git</connection>
                        <developerConnection>scm:git:git@example.com:project.git</developerConnection>
                        <tag>v1</tag>
                        <url>https://example.com/project/</url>
                    </scm>
                    <modules>
                        <module>inheriting</module>
                        <module>declaring</module>
                    </modules>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("inheriting")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>inheriting-artifact</artifactId>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("declaring")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>declaring</artifactId>
                    <description>Its own.</description>
                    <url>https://example.com/${project.artifactId}</url>
                    <developers>
                        <developer>
                            <id>alice</id>
                            <name>Alice Example</name>
                        </developer>
                    </developers>
                    <scm>
                        <url>https://example.com/declaring</url>
                    </scm>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("inheriting")).metadata())
                .as("everything but the name is inherited, the url and the scm locations with the artifactId appended")
                .isEqualTo(new MavenPomEmitter.Metadata(null,
                        "The parent.",
                        "https://example.com/project/inheriting-artifact",
                        List.of(new MavenPomEmitter.Metadata.License("Apache-2.0",
                                "https://www.apache.org/licenses/LICENSE-2.0.txt")),
                        List.of(new MavenPomEmitter.Metadata.Developer("google", null, null)),
                        new MavenPomEmitter.Metadata.Scm("scm:git:https://example.com/project.git",
                                "scm:git:git@example.com:project.git/inheriting-artifact",
                                "https://example.com/project/inheriting-artifact/",
                                "v1"),
                        new MavenPomEmitter.Metadata.Organization("Example Ltd", null)));
        assertThat(poms.get(Path.of("declaring")).metadata())
                .as("what a module declares wins, a list replaces the parent's, and a declared scm keeps its own tag")
                .isEqualTo(new MavenPomEmitter.Metadata(null,
                        "Its own.",
                        "https://example.com/declaring",
                        List.of(new MavenPomEmitter.Metadata.License("Apache-2.0",
                                "https://www.apache.org/licenses/LICENSE-2.0.txt")),
                        List.of(new MavenPomEmitter.Metadata.Developer("alice", "Alice Example", null)),
                        new MavenPomEmitter.Metadata.Scm("scm:git:https://example.com/project.git",
                                "scm:git:git@example.com:project.git/declaring",
                                "https://example.com/declaring",
                                null),
                        new MavenPomEmitter.Metadata.Organization("Example Ltd", null)));
    }

    @Test
    public void local_pom_reads_a_developers_details_and_the_issue_and_ci_management_and_inherits_them() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <developers>
                        <developer>
                            <id>alice</id>
                            <name>Alice Example</name>
                            <url>https://example.com/alice</url>
                            <organization>Example Ltd</organization>
                            <organizationUrl>https://example.com</organizationUrl>
                            <roles>
                                <role>lead</role>
                                <role>developer</role>
                            </roles>
                            <timezone>Europe/Oslo</timezone>
                        </developer>
                        <developer>
                            <id>bob</id>
                            <url>https://example.com/bob</url>
                        </developer>
                    </developers>
                    <issueManagement>
                        <system>GitHub</system>
                        <url>https://example.com/issues</url>
                    </issueManagement>
                    <ciManagement>
                        <system>GitHub Actions</system>
                        <url>https://example.com/actions</url>
                    </ciManagement>
                    <modules>
                        <module>child</module>
                    </modules>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("child")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>child</artifactId>
                    <issueManagement>
                        <url>https://example.com/child/issues</url>
                    </issueManagement>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("child")).metadata())
                .as("the developers are inherited as a list, the issue and ci management field by field as Maven merges them")
                .isEqualTo(new MavenPomEmitter.Metadata(null,
                        null,
                        null,
                        List.of(),
                        List.of(new MavenPomEmitter.Metadata.Developer("alice",
                                        "Alice Example",
                                        null,
                                        "https://example.com/alice",
                                        "Example Ltd",
                                        "https://example.com",
                                        List.of("lead", "developer"),
                                        "Europe/Oslo"),
                                new MavenPomEmitter.Metadata.Developer("bob",
                                        null,
                                        null,
                                        "https://example.com/bob",
                                        null,
                                        null,
                                        List.of(),
                                        null)),
                        null,
                        null,
                        new MavenPomEmitter.Metadata.Management("GitHub", "https://example.com/child/issues"),
                        new MavenPomEmitter.Metadata.Management("GitHub Actions", "https://example.com/actions"),
                        null));
    }

    @Test
    public void local_pom_reads_a_licence_distribution_and_the_inception_year_and_inherits_them() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <inceptionYear>2010</inceptionYear>
                    <licenses>
                        <license>
                            <name>Apache 2.0</name>
                            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                            <distribution>repo</distribution>
                        </license>
                    </licenses>
                    <modules>
                        <module>child</module>
                        <module>other</module>
                    </modules>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("child")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>child</artifactId>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("other")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>other</artifactId>
                    <inceptionYear>2020</inceptionYear>
                    <licenses>
                        <license>
                            <name>MIT</name>
                        </license>
                    </licenses>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("child")).metadata())
                .as("the inception year and the licences with their distribution are inherited where a module declares none")
                .isEqualTo(new MavenPomEmitter.Metadata(null,
                        null,
                        null,
                        List.of(new MavenPomEmitter.Metadata.License("Apache 2.0",
                                "https://www.apache.org/licenses/LICENSE-2.0.txt",
                                "repo")),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        "2010"));
        assertThat(poms.get(Path.of("other")).metadata())
                .as("a module's own inception year and licences win over its parent's")
                .isEqualTo(new MavenPomEmitter.Metadata(null,
                        null,
                        null,
                        List.of(new MavenPomEmitter.Metadata.License("MIT", null)),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        "2020"));
    }

    @Test
    public void local_pom_inherits_the_metadata_of_a_parent_it_fetches() throws IOException {
        addToRepository("group", "grandparent", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>grandparent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <url>https://example.com</url>
                    <licenses>
                        <license>
                            <name>MIT</name>
                        </license>
                    </licenses>
                </project>
                """);
        addToRepository("group", "parent", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>grandparent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>parent</artifactId>
                    <packaging>pom</packaging>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                        <relativePath/>
                    </parent>
                    <artifactId>artifact</artifactId>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).metadata())
                .as("each generation appends its artifactId to the url it inherits")
                .isEqualTo(new MavenPomEmitter.Metadata(null,
                        null,
                        "https://example.com/parent/artifact",
                        List.of(new MavenPomEmitter.Metadata.License("MIT", null)),
                        List.of(),
                        null,
                        null));
    }

    @Test
    public void local_pom_dependency_management_checksum_is_honored() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>group</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <!--Checksum/SHA256/cafebabe-->
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.dependencies().get(new MavenDependencyKey("group", "artifact", "jar", null)).checksum())
                .isEqualTo("SHA256/cafebabe");
    }

    @Test
    public void local_pom_dependency_management_wins_over_a_parent_entry_naming_the_artifact_by_a_property() throws IOException {
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>subproject</module>
                    </modules>
                    <properties>
                        <managed.artifactId>artifact</managed.artifactId>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>group</groupId>
                                <artifactId>${managed.artifactId}</artifactId>
                                <version>1</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>project</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>artifact</artifactId>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>group</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <!--Checksum/SHA256/cafebabe-->
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>${managed.artifactId}</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        MavenLocalPom pom = poms.get(Path.of("subproject"));
        assertThat(pom.managedDependencies().get(new MavenDependencyKey("group", "artifact", "jar", null)).checksum())
                .as("the subproject's own entry is the one that manages the artifact the parent names by a property")
                .isEqualTo("SHA256/cafebabe");
        assertThat(pom.dependencies().get(new MavenDependencyKey("group", "artifact", "jar", null)).checksum())
                .isEqualTo("SHA256/cafebabe");
    }

    @Test
    public void local_pom_inherits_the_source_and_resource_directories_of_its_local_parent_unless_it_names_its_own() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>inheriting</module>
                        <module>overriding</module>
                    </modules>
                    <build>
                        <sourceDirectory>src/main/groovy</sourceDirectory>
                        <testSourceDirectory>src/test/groovy</testSourceDirectory>
                        <resources>
                            <resource>
                                <directory>assets</directory>
                            </resource>
                        </resources>
                        <testResources>
                            <testResource>
                                <directory>fixtures</directory>
                            </testResource>
                        </testResources>
                    </build>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("inheriting")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>project</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>inheriting</artifactId>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("overriding")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>project</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>overriding</artifactId>
                    <build>
                        <testSourceDirectory>src/test/java</testSourceDirectory>
                        <testResources>
                            <testResource>
                                <directory>data</directory>
                            </testResource>
                        </testResources>
                    </build>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        MavenLocalPom inheriting = poms.get(Path.of("inheriting"));
        assertThat(inheriting.sourceDirectory()).isEqualTo("src/main/groovy");
        assertThat(inheriting.testSourceDirectory()).isEqualTo("src/test/groovy");
        assertThat(inheriting.resourceDirectories()).containsExactly("assets");
        assertThat(inheriting.testResourceDirectories()).containsExactly("fixtures");
        MavenLocalPom overriding = poms.get(Path.of("overriding"));
        assertThat(overriding.sourceDirectory()).isEqualTo("src/main/groovy");
        assertThat(overriding.testSourceDirectory())
                .as("a module's own directory wins over its parent's")
                .isEqualTo("src/test/java");
        assertThat(overriding.resourceDirectories()).containsExactly("assets");
        assertThat(overriding.testResourceDirectories()).containsExactly("data");
    }

    @Test
    public void local_pom_of_model_4_1_0_infers_its_parent_subprojects_sources_and_sibling_versions() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.1.0" root="true">
                    <modelVersion>4.1.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>parent</artifactId>
                    <version>${revision}</version>
                    <packaging>pom</packaging>
                    <properties>
                        <revision>1.2</revision>
                    </properties>
                    <subprojects>
                        <subproject>api</subproject>
                        <subproject>app</subproject>
                    </subprojects>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("api")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.1.0">
                    <modelVersion>4.1.0</modelVersion>
                    <parent/>
                    <artifactId>api</artifactId>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("app")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.1.0">
                    <modelVersion>4.1.0</modelVersion>
                    <parent>
                        <relativePath>..</relativePath>
                    </parent>
                    <artifactId>app</artifactId>
                    <dependencies>
                        <dependency>
                            <groupId>project</groupId>
                            <artifactId>api</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>processor</artifactId>
                            <version>3</version>
                            <type>processor</type>
                        </dependency>
                    </dependencies>
                    <build>
                        <sources>
                            <source>
                                <directory>code</directory>
                            </source>
                            <source>
                                <scope>test</scope>
                            </source>
                            <source>
                                <lang>resources</lang>
                                <directory>assets</directory>
                            </source>
                            <source>
                                <directory>disabled</directory>
                                <enabled>false</enabled>
                            </source>
                        </sources>
                    </build>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.keySet()).containsExactly(Path.of(""), Path.of("api"), Path.of("app"));
        MavenLocalPom app = poms.get(Path.of("app"));
        assertThat(app.groupId()).as("the parent is the POM at its relativePath, and its groupId is inherited").isEqualTo("project");
        assertThat(app.version()).isEqualTo("1.2");
        assertThat(app.dependencies().get(new MavenDependencyKey("project", "api", "jar", null)).version())
                .as("a dependency on a subproject takes the subproject's version")
                .isEqualTo("1.2");
        assertThat(app.dependencies()).doesNotContainKey(new MavenDependencyKey("org.example", "processor", "processor", null));
        assertThat(app.plugins())
                .as("a processor dependency is placed on the processor path")
                .containsEntry("maven/org.example/processor/3", "plugin");
        assertThat(app.sourceDirectory()).isEqualTo("code");
        assertThat(app.testSourceDirectory()).isEqualTo("src/test/java");
        assertThat(app.resourceDirectories()).containsExactly("assets");
        assertThat(poms.get(Path.of("api")).version()).isEqualTo("1.2");
    }

    @Test
    public void a_processor_dependency_compiles_only_the_half_whose_scope_declares_it() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.plugin javac maven/org.example/checker/1-->
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>main-processor</artifactId>
                            <version>1</version>
                            <type>processor</type>
                        </dependency>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>test-processor</artifactId>
                            <version>1</version>
                            <type>processor</type>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        MavenLocalPom pom = mavenPomResolver.local(Runnable::run, mavenRepository, project).get(Path.of(""));
        assertThat(pom.plugins())
                .as("a processor of the main scopes compiles the main half alone, beside the plugins every half takes")
                .containsExactly(Map.entry("maven/org.example/checker/1", "javac"),
                        Map.entry("maven/org.example/main-processor/1", "plugin"));
        assertThat(pom.testPlugins())
                .as("a test-scoped processor compiles the test half alone")
                .containsExactly(Map.entry("maven/org.example/checker/1", "javac"),
                        Map.entry("maven/org.example/test-processor/1", "plugin"));
        assertThat(pom.dependencies()).isEmpty();
    }

    @Test
    public void reads_a_module_alias_from_a_pom_comment_and_inherits_one_from_a_local_parent() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <!--jenesis.alias groovy.all org.codehaus.groovy/groovy-all-->
                    <modules>
                        <module>child</module>
                    </modules>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("child")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>group</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>child</artifactId>
                    <!--jenesis.alias
                    jline jline/jline
                    -->
                </project>
                """);
        assertThat(mavenPomResolver.local(Runnable::run, mavenRepository, project).get(Path.of("child")).aliases())
                .as("a jar without a module name is named for the module path, as @jenesis.alias does in module-info.java")
                .containsExactly(Map.entry("jline", "jline/jline"), Map.entry("groovy.all", "org.codehaus.groovy/groovy-all"));
    }

    @Test
    public void aliases_a_java_prefixed_module_the_jdk_does_not_hold_and_refuses_one_it_does() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.alias java.money javax.money/money-api-->
                </project>
                """);
        assertThat(mavenPomResolver.local(Runnable::run, mavenRepository, project).get(Path.of("")).aliases())
                .containsExactly(Map.entry("java.money", "javax.money/money-api"));
        Files.writeString(project.resolve("pom.xml"), Files.readString(project.resolve("pom.xml"))
                .replace("java.money", "java.sql"));
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Illegal jenesis.alias name 'java.sql'");
    }

    @Test
    public void refuses_a_plugin_comment_that_writes_a_version_after_the_coordinate() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.plugin javac maven/com.google.errorprone/error_prone_core 2.36.0-->
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed jenesis.plugin declaration"
                        + " 'javac maven/com.google.errorprone/error_prone_core 2.36.0'")
                .hasMessageContaining("maven/<groupId>/<artifactId>/<version>");
    }

    @Test
    public void refuses_a_module_alias_comment_that_names_no_coordinate() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.alias jline jline-->
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed jenesis.alias target 'jline'")
                .hasMessageContaining("<groupId>/<artifactId>");
    }

    @Test
    public void local_pom_of_model_4_1_0_discovers_its_subprojects_without_a_list() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.1.0">
                    <modelVersion>4.1.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                </project>
                """);
        for (String name : List.of("second", "first")) {
            Files.writeString(Files.createDirectory(project.resolve(name)).resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.1.0">
                        <modelVersion>4.1.0</modelVersion>
                        <parent/>
                        <artifactId>%s</artifactId>
                    </project>
                    """.formatted(name));
        }
        Files.createDirectory(project.resolve("notes"));
        assertThat(mavenPomResolver.local(Runnable::run, mavenRepository, project).keySet())
                .containsExactly(Path.of(""), Path.of("first"), Path.of("second"));
    }

    @Test
    public void local_pom_of_model_4_1_0_refuses_what_jenesis_does_not_read() throws IOException {
        for (Map.Entry<String, String> refused : Map.of(
                "<build><sources><source><module>a</module></source></sources></build>", "<module>",
                "<build><sources><source><directory>a</directory></source><source><directory>b</directory></source></sources></build>",
                "one source directory per scope",
                "<dependencies><dependency><groupId>g</groupId><artifactId>a</artifactId><version>1</version><type>modular-jar</type></dependency></dependencies>",
                "modular-jar").entrySet()) {
            Files.writeString(project.resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.1.0">
                        <modelVersion>4.1.0</modelVersion>
                        <groupId>project</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                        %s
                    </project>
                    """.formatted(refused.getKey()));
            assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(refused.getValue());
        }
    }

    @Test
    public void local_pom_resolves_a_version_from_an_imported_bom() throws IOException {
        addToRepository("test", "bom", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>test</groupId>
                    <artifactId>bom</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>test</groupId>
                                <artifactId>lib</artifactId>
                                <version>2</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>test</groupId>
                                <artifactId>bom</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>test</groupId>
                            <artifactId>lib</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.dependencies().get(new MavenDependencyKey("test", "lib", "jar", null)).version())
                .isEqualTo("2");
    }

    @Test
    public void local_pom_interpolates_compiler_release_and_main_class() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <java.version>26</java.version>
                        <maven.compiler.release>${java.version}</maven.compiler.release>
                        <app.main>com.example.Main</app.main>
                        <mainClass>${app.main}</mainClass>
                    </properties>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.release()).isEqualTo("26");
        assertThat(pom.mainClass()).isEqualTo("com.example.Main");
    }

    @Test
    public void local_pom_enables_the_preview_features_of_its_release() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.release>25</maven.compiler.release>
                        <maven.compiler.enablePreview>true</maven.compiler.enablePreview>
                    </properties>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).release()).isEqualTo("25-preview");
    }

    @Test
    public void local_pom_without_a_release_enables_the_preview_features_of_the_jdk() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.enablePreview>true</maven.compiler.enablePreview>
                    </properties>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).release()).isEqualTo(Runtime.version().feature() + "-preview");
    }

    @Test
    public void local_pom_compiles_its_tests_for_the_test_release_or_else_its_release() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>tested</module>
                        <module>plain</module>
                    </modules>
                    <properties>
                        <tests.version>17</tests.version>
                        <maven.compiler.release>8</maven.compiler.release>
                    </properties>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("tested")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>project</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>tested</artifactId>
                    <properties>
                        <maven.compiler.testRelease>${tests.version}</maven.compiler.testRelease>
                    </properties>
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("plain")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>project</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>plain</artifactId>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("tested")).release()).isEqualTo("8");
        assertThat(poms.get(Path.of("tested")).testRelease()).isEqualTo("17");
        assertThat(poms.get(Path.of("plain")).testRelease())
                .as("without maven.compiler.testRelease the tests are compiled for the release of the main code")
                .isEqualTo("8");
    }

    @Test
    public void local_pom_enables_the_preview_features_of_its_test_release() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.release>21</maven.compiler.release>
                        <maven.compiler.testRelease>25</maven.compiler.testRelease>
                        <maven.compiler.enablePreview>true</maven.compiler.enablePreview>
                    </properties>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).release()).isEqualTo("21-preview");
        assertThat(poms.get(Path.of("")).testRelease()).isEqualTo("25-preview");
    }

    @Test
    public void local_pom_reads_the_compiler_target_or_else_its_source_as_the_release_it_does_not_declare() throws IOException {
        Map<String, String> cases = new LinkedHashMap<>();
        cases.put("<maven.compiler.source>1.8</maven.compiler.source><maven.compiler.target>1.8</maven.compiler.target>", "8");
        cases.put("<maven.compiler.source>11</maven.compiler.source>", "11");
        cases.put("<maven.compiler.release></maven.compiler.release><maven.compiler.target>1.8</maven.compiler.target>", "8");
        cases.put("<maven.compiler.release>17</maven.compiler.release><maven.compiler.target>1.8</maven.compiler.target>", "17");
        for (Map.Entry<String, String> entry : cases.entrySet()) {
            Files.writeString(project.resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>project</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                        <properties>%s</properties>
                    </project>
                    """.formatted(entry.getKey()));
            MavenLocalPom pom = mavenPomResolver.local(Runnable::run, mavenRepository, project).get(Path.of(""));
            assertThat(pom.release()).as(entry.getKey()).isEqualTo(entry.getValue());
            assertThat(pom.testRelease()).as(entry.getKey()).isEqualTo(entry.getValue());
        }
    }

    @Test
    public void a_profile_of_a_fetched_parent_that_the_jdk_activates_sets_the_release() throws IOException {
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <profiles>
                        <profile>
                            <id>java-9-up</id>
                            <activation>
                                <jdk>[9,)</jdk>
                            </activation>
                            <properties>
                                <maven.compiler.release>8</maven.compiler.release>
                            </properties>
                        </profile>
                    </profiles>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>child</artifactId>
                </project>
                """);
        assertThat(mavenPomResolver.jdk("25.0.4.1").local(Runnable::run, mavenRepository, project).get(Path.of("")).release())
                .as("Maven activates the parent's profile on a JDK of 9 or newer, so the jar is compiled for release 8")
                .isEqualTo("8");
        assertThat(mavenPomResolver.jdk("1.8.0_402").local(Runnable::run, mavenRepository, project).get(Path.of("")).release())
                .isNull();
    }

    @Test
    public void a_dependency_that_a_profile_active_by_default_declares_is_resolved() throws IOException {
        addToRepository("group", "artifact", "1", rootPom("", "client"));
        addToRepository("client", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <profiles>
                        <profile>
                            <id>default</id>
                            <activation>
                                <activeByDefault>true</activeByDefault>
                            </activation>
                            <dependencies>
                                %s
                            </dependencies>
                        </profile>
                    </profiles>
                </project>
                """.formatted(dependencyOn("core", "1")));
        addToRepository("core", "artifact", "1", leafPom());
        assertThat(mavenPomResolver.dependencies(Runnable::run, mavenRepository, "group", "artifact", "1", null).keySet())
                .containsExactly(
                        new MavenDependencyKey("client", "artifact", "jar", null),
                        new MavenDependencyKey("core", "artifact", "jar", null));
    }

    @Test
    public void a_profile_active_by_default_yields_to_one_the_jdk_activates_in_the_same_pom() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.release>${chosen}</maven.compiler.release>
                    </properties>
                    <profiles>
                        <profile>
                            <id>default</id>
                            <activation>
                                <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                                <chosen>11</chosen>
                            </properties>
                        </profile>
                        <profile>
                            <id>modern</id>
                            <activation>
                                <jdk>[17,)</jdk>
                            </activation>
                            <properties>
                                <chosen>17</chosen>
                            </properties>
                        </profile>
                    </profiles>
                </project>
                """);
        assertThat(mavenPomResolver.jdk("25.0.4.1").local(Runnable::run, mavenRepository, project).get(Path.of("")).release())
                .isEqualTo("17");
        assertThat(mavenPomResolver.jdk("11.0.2").local(Runnable::run, mavenRepository, project).get(Path.of("")).release())
                .isEqualTo("11");
    }

    @Test
    public void a_jdk_activation_matches_a_prefix_a_negation_and_a_range_as_maven_does() throws IOException {
        List<List<String>> cases = List.of(
                List.of("1.8", "1.8.0_402", "true"),
                List.of("1.8", "25.0.4.1", "false"),
                List.of("25", "25.0.4.1", "true"),
                List.of("!1.8", "25.0.4.1", "true"),
                List.of("!1.8", "1.8.0_402", "false"),
                List.of("[9,)", "25.0.4.1", "true"),
                List.of("[9,)", "1.8.0_402", "false"),
                List.of("[1.8,17)", "11.0.2", "true"),
                List.of("[1.8,17)", "17", "false"),
                List.of("[1.8,17]", "17", "true"),
                List.of("(,11)", "1.8.0_402", "true"),
                List.of("(,11)", "11.0.2", "false"),
                List.of("(17,)", "17", "false"),
                List.of("!(,11)", "25.0.4.1", "true"),
                List.of("[11,12),[16,)", "25.0.4.1", "true"),
                List.of("[11,12),[16,)", "11.0.2", "true"),
                List.of("[11,12),[16,)", "14.0.1", "false"),
                List.of("[17]", "17", "true"),
                List.of("[17]", "21", "false"),
                List.of("[9,", "25.0.4.1", "true"),
                List.of("[9,", "1.8.0_402", "false"),
                List.of("[11", "25.0.4.1", "true"),
                List.of("[11", "11.0.2", "true"),
                List.of("[11", "1.8.0_402", "false"),
                List.of("(11", "11", "false"),
                List.of("(11", "17", "true"),
                List.of("[11,12),[16,", "25.0.4.1", "true"));
        for (List<String> entry : cases) {
            Files.writeString(project.resolve("pom.xml"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>project</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                        <profiles>
                            <profile>
                                <activation>
                                    <jdk>%s</jdk>
                                </activation>
                                <properties>
                                    <maven.compiler.release>21</maven.compiler.release>
                                </properties>
                            </profile>
                        </profiles>
                    </project>
                    """.formatted(entry.get(0)));
            assertThat(mavenPomResolver.jdk(entry.get(1)).local(Runnable::run, mavenRepository, project).get(Path.of("")).release() != null)
                    .as("<jdk>%s</jdk> on Java %s", entry.get(0), entry.get(1))
                    .isEqualTo(Boolean.parseBoolean(entry.get(2)));
        }
    }

    @Test
    public void a_profile_activated_by_a_property_or_a_file_stays_inactive_and_one_activated_by_the_os_follows_it()
            throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <maven.compiler.release>${chosen}</maven.compiler.release>
                    </properties>
                    <profiles>
                        <profile>
                            <id>default</id>
                            <activation>
                                <activeByDefault>true</activeByDefault>
                            </activation>
                            <properties>
                                <chosen>11</chosen>
                            </properties>
                        </profile>
                        <profile>
                            <id>property</id>
                            <activation>
                                <jdk>[1.8,)</jdk>
                                <property>
                                    <name>!skip</name>
                                </property>
                            </activation>
                            <properties>
                                <chosen>17</chosen>
                            </properties>
                        </profile>
                        <profile>
                            <id>os</id>
                            <activation>
                                <os>
                                    <family>unix</family>
                                </os>
                            </activation>
                            <properties>
                                <chosen>21</chosen>
                            </properties>
                        </profile>
                        <profile>
                            <id>file</id>
                            <activation>
                                <file>
                                    <exists>pom.xml</exists>
                                </file>
                            </activation>
                            <properties>
                                <chosen>25</chosen>
                            </properties>
                        </profile>
                    </profiles>
                </project>
                """);
        assertThat(mavenPomResolver.jdk("25.0.4.1").os("Windows 11", "amd64", "10.0")
                .local(Runnable::run, mavenRepository, project).get(Path.of("")).release())
                .isEqualTo("11");
        assertThat(mavenPomResolver.jdk("25.0.4.1").os("Linux", "amd64", "6.1")
                .local(Runnable::run, mavenRepository, project).get(Path.of("")).release())
                .isEqualTo("21");
    }

    @Test
    public void a_profile_of_the_module_adds_its_dependencies_managed_versions_and_resource_directories() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>declared</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <profiles>
                        <profile>
                            <id>default</id>
                            <activation>
                                <activeByDefault>true</activeByDefault>
                            </activation>
                            <dependencyManagement>
                                <dependencies>
                                    <dependency>
                                        <groupId>managed</groupId>
                                        <artifactId>artifact</artifactId>
                                        <version>2</version>
                                    </dependency>
                                </dependencies>
                            </dependencyManagement>
                            <dependencies>
                                <dependency>
                                    <groupId>managed</groupId>
                                    <artifactId>artifact</artifactId>
                                </dependency>
                            </dependencies>
                            <build>
                                <resources>
                                    <resource>
                                        <directory>src/extra</directory>
                                    </resource>
                                </resources>
                            </build>
                        </profile>
                    </profiles>
                </project>
                """);
        MavenLocalPom pom = mavenPomResolver.local(Runnable::run, mavenRepository, project).get(Path.of(""));
        assertThat(pom.dependencies()).containsExactly(
                Map.entry(new MavenDependencyKey("declared", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(new MavenDependencyKey("managed", "artifact", "jar", null),
                        new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(pom.resourceDirectories())
                .as("a profile's resources replace the default folder, as in Maven")
                .containsExactly("src/extra");
    }

    @Test
    public void an_unclosed_jdk_range_of_a_fetched_parent_is_open_ended_as_in_objenesis() throws IOException {
        addToRepository("org/objenesis", "objenesis-parent", "3.4", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>org.objenesis</groupId>
                    <artifactId>objenesis-parent</artifactId>
                    <version>3.4</version>
                    <packaging>pom</packaging>
                    <profiles>
                        <profile>
                            <id>java9</id>
                            <activation>
                                <jdk>[9,</jdk>
                            </activation>
                            <properties>
                                <maven.compiler.release>8</maven.compiler.release>
                            </properties>
                        </profile>
                    </profiles>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.objenesis</groupId>
                        <artifactId>objenesis-parent</artifactId>
                        <version>3.4</version>
                    </parent>
                    <artifactId>objenesis</artifactId>
                </project>
                """);
        assertThat(mavenPomResolver.jdk("25.0.4.1").local(Runnable::run, mavenRepository, project).get(Path.of("")).release())
                .as("Maven reads <jdk>[9,</jdk> as [9,) and activates the profile on Java 25")
                .isEqualTo("8");
        assertThat(mavenPomResolver.jdk("1.8.0_402").local(Runnable::run, mavenRepository, project).get(Path.of("")).release())
                .isNull();
    }

    @Test
    public void a_jdk_activation_of_a_fetched_pom_that_is_no_version_range_leaves_its_profile_inactive() throws IOException {
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <profiles>
                        <profile>
                            <activation>
                                <jdk>[nine,)</jdk>
                            </activation>
                            <properties>
                                <maven.compiler.release>8</maven.compiler.release>
                            </properties>
                        </profile>
                    </profiles>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>child</artifactId>
                </project>
                """);
        assertThat(mavenPomResolver.jdk("25.0.4.1").local(Runnable::run, mavenRepository, project).get(Path.of("")).release())
                .as("a third-party POM's activation that cannot be read does not stop the build")
                .isNull();
    }

    @Test
    public void a_jdk_activation_that_is_no_version_range_is_refused() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <profiles>
                        <profile>
                            <activation>
                                <jdk>[nine,)</jdk>
                            </activation>
                        </profile>
                    </profiles>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("<jdk>[nine,)</jdk>")
                .hasMessageContaining("such as [9,), [1.8,17) or [11,12),[16,)");
    }

    @Test
    public void local_pom_direct_dependency_checksum_is_ignored() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <!--Checksum/SHA256/cafebabe-->
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.dependencies().get(new MavenDependencyKey("group", "artifact", "jar", null)).checksum())
                .isNull();
    }

    @Test
    public void local_pom_inherits_the_plugin_comment_of_its_local_parent_but_not_its_pins() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <modules>
                        <module>child</module>
                    </modules>
                    <!--jenesis.plugin
                    javac maven/com.google.errorprone/error_prone_core
                    maven/org.example/processor/1.0
                    -->
                    <!--jenesis.pin
                    javac/maven/com.google.errorprone/error_prone_core 2.50.0
                    javac/maven/com.google.guava/guava 33.0.0-jre
                    -->
                </project>
                """);
        Files.writeString(Files.createDirectories(project.resolve("child")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>project</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>child</artifactId>
                    <!--jenesis.plugin
                    maven/org.example/other/2.0
                    -->
                    <!--jenesis.pin
                    javac/maven/com.google.guava/guava 33.5.0-jre
                    -->
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        MavenLocalPom child = poms.get(Path.of("child"));
        assertThat(child.plugins())
                .as("a module inherits the plugins its parent in the project declares, beside its own")
                .containsEntry("maven/com.google.errorprone/error_prone_core", "javac")
                .containsEntry("maven/org.example/processor/1.0", "plugin")
                .containsEntry("maven/org.example/other/2.0", "plugin");
        assertThat(child.qualifiedDependencies())
                .as("pin writes a module's pins into its own POM, so a parent's would only be shadowed there")
                .containsExactly(Map.entry("javac/maven/com.google.guava/guava", "33.5.0-jre"));
        assertThat(poms.get(Path.of("")).qualifiedDependencies())
                .containsEntry("javac/maven/com.google.guava/guava", "33.0.0-jre");
    }

    @Test
    public void local_pom_reads_plugin_comment_block() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.plugin
                    some.processor
                    maven/org.example/processor/1.0
                    kotlinc org.jetbrains.kotlin/kotlin-serialization
                    -->
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).plugins())
                .as("a leading compiler names the group the plugin is resolved in, and plugin is the default")
                .containsExactly(
                        Map.entry("module/some.processor", "plugin"),
                        Map.entry("maven/org.example/processor/1.0", "plugin"),
                        Map.entry("org.jetbrains.kotlin/kotlin-serialization", "kotlinc"));
    }

    @Test
    public void local_pom_takes_the_version_of_a_processor_named_without_one_from_its_dependency_management() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <properties>
                        <processor.version>1.5</processor.version>
                    </properties>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>processor</artifactId>
                                <version>${processor.version}</version>
                            </dependency>
                            <dependency>
                                <groupId>org.example</groupId>
                                <artifactId>typed</artifactId>
                                <version>2.5</version>
                            </dependency>
                            <dependency>
                                <groupId>org.jetbrains.kotlin</groupId>
                                <artifactId>kotlin-serialization</artifactId>
                                <version>3.0</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>typed</artifactId>
                            <type>processor</type>
                        </dependency>
                    </dependencies>
                    <!--jenesis.plugin
                    maven/org.example/processor
                    maven/org.example/pinned/1.0
                    maven/org.example/unmanaged
                    kotlinc maven/org.jetbrains.kotlin/kotlin-serialization
                    -->
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).plugins())
                .as("Maven resolves a versionless annotation processor path at the version its dependency management"
                        + " names, but no plugin of another tool")
                .containsExactly(
                        Map.entry("maven/org.example/processor/1.5", "plugin"),
                        Map.entry("maven/org.example/pinned/1.0", "plugin"),
                        Map.entry("maven/org.example/unmanaged", "plugin"),
                        Map.entry("maven/org.jetbrains.kotlin/kotlin-serialization", "kotlinc"),
                        Map.entry("maven/org.example/typed/2.5", "plugin"));
    }

    @Test
    public void local_pom_reads_signature_comment_block() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.signature
                    OpenPGP/B4D5 org.example/lib some.module
                    OpenPGP/FEED tool/maven/org.example/other org.example/*
                    -->
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).signatures())
                .as("the tokens expand exactly as the module-info tag expands them")
                .containsEntry("OpenPGP/B4D5", "main/maven/org.example/lib main/module/some.module")
                .containsEntry("OpenPGP/FEED", "main/maven/org.example/* tool/maven/org.example/other");
    }

    @Test
    public void signature_block_joins_repeated_declarations_of_one_key() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.signature
                    OpenPGP/B4D5 org.example/lib
                    OpenPGP/B4D5 org.example/lib org.example/other
                    -->
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).signatures())
                .as("declaring a key twice adds nothing the first line did not already say")
                .containsEntry("OpenPGP/B4D5", "main/maven/org.example/lib main/maven/org.example/other");
    }

    @Test
    public void signature_block_rejects_a_declaration_that_names_no_coordinate() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.signature
                    OpenPGP/B4D5
                    -->
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected <algorithm>/<fingerprint> <token>...");
    }

    @Test
    public void signature_block_rejects_prose_written_among_the_declarations() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.signature
                    OpenPGP/B4D5 org.example/lib
                    Vetted against the vendor's published KEYS.
                    -->
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .as("a prose line inside the comment is a declaration, and it is named as the cause")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed jenesis.signature fingerprint 'Vetted'")
                .hasMessageContaining("move it outside the comment");
    }

    @Test
    public void local_pom_reads_attach_comment_block() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    org.mockito/mockito-core
                    io.opentelemetry.javaagent/opentelemetry-javaagent otel.option=value &#45;&#45;flag
                    some.module plain arguments
                    -->
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).attachments()).containsExactly(
                Map.entry("main/maven/org.mockito/mockito-core", ""),
                Map.entry("main/maven/io.opentelemetry.javaagent/opentelemetry-javaagent", "otel.option=value --flag"),
                Map.entry("main/module/some.module", "plain arguments"));
    }

    @Test
    public void local_pom_reads_native_comment_blocks() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.native project/artifact-->
                    <!--jenesis.native
                    org.example/jni
                    some.module
                    -->
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).natives()).containsExactly(
                "main/maven/project/artifact",
                "main/maven/org.example/jni",
                "main/module/some.module");
    }

    @Test
    public void native_block_that_names_nothing_is_refused() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.native-->
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no module");
    }

    @Test
    public void native_block_rejects_platform_modules() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.native java.base-->
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform modules cannot be granted native access");
    }

    @Test
    public void attach_block_conflicting_duplicates_throw() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    org.example/agent first
                    org.example/agent second
                    -->
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate jenesis.attach for main/maven/org.example/agent");
    }

    @Test
    public void attach_block_identical_duplicates_collapse() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    org.example/agent option
                    org.example/agent option
                    -->
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).attachments()).containsExactly(
                Map.entry("main/maven/org.example/agent", "option"));
    }

    @Test
    public void attach_block_rejects_platform_modules() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    java.instrument
                    -->
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("platform modules cannot be attached");
    }

    @Test
    public void attach_block_rejects_malformed_tokens() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    org.example/
                    -->
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed jenesis.attach token");
    }

    @Test
    public void attach_block_in_parent_pom_is_ignored() throws IOException {
        addToRepository("parent", "parent", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <!--jenesis.attach
                    org.example/agent
                    -->
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>artifact</artifactId>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms.get(Path.of("")).attachments()).isEmpty();
    }

    @Test
    public void can_resolve_local_pom() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <build>
                        <sourceDirectory>sources</sourceDirectory>
                        <resources>
                            <resource>
                                <directory>resource-1</directory>
                            </resource>
                            <resource>
                                <directory>resource-2</directory>
                            </resource>
                        </resources>
                        <testSourceDirectory>tests</testSourceDirectory>
                        <testResources>
                            <testResource>
                                <directory>testResource-1</directory>
                            </testResource>
                            <testResource>
                                <directory>testResource-2</directory>
                            </testResource>
                        </testResources>
                    </build>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms).containsOnlyKeys(Path.of(""));
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.groupId()).isEqualTo("project");
        assertThat(pom.artifactId()).isEqualTo("artifact");
        assertThat(pom.version()).isEqualTo("1");
        assertThat(pom.sourceDirectory()).isEqualTo("sources");
        assertThat(pom.resourceDirectories()).containsExactly("resource-1", "resource-2");
        assertThat(pom.testSourceDirectory()).isEqualTo("tests");
        assertThat(pom.testResourceDirectories()).containsExactly("testResource-1", "testResource-2");
        assertThat(pom.dependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("group", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(pom.managedDependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", null, null, null, null)));
    }

    @Test
    public void can_resolve_local_pom_parent() throws IOException {
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>artifact</artifactId>
                    <parent>
                        <groupId>project</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                    </parent>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>parent</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>parent</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, subproject);
        assertThat(poms).containsOnlyKeys(Path.of(""));
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.groupId()).isEqualTo("project");
        assertThat(pom.artifactId()).isEqualTo("artifact");
        assertThat(pom.version()).isEqualTo("1");
        assertThat(pom.dependencies()).containsExactly(
                Map.entry(
                        new MavenDependencyKey("group", "parent", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("group", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(pom.managedDependencies()).containsExactly(
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", null, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("other", "parent", "jar", null),
                        new MavenDependencyValue("1", null, null, null, null)));
    }

    @Test
    public void can_resolve_local_pom_parent_explicit_location() throws IOException {
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <artifactId>artifact</artifactId>
                    <parent>
                        <groupId>project</groupId>
                        <artifactId>parent</artifactId>
                        <version>1</version>
                        <relativePath>../parent/pom.xml</relativePath>
                    </parent>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(Files.createDirectory(project.resolve("parent")).resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>parent</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>parent</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>parent</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, subproject);
        assertThat(poms).containsOnlyKeys(Path.of(""));
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.groupId()).isEqualTo("project");
        assertThat(pom.artifactId()).isEqualTo("artifact");
        assertThat(pom.version()).isEqualTo("1");
        assertThat(pom.dependencies()).containsExactly(
                Map.entry(
                        new MavenDependencyKey("group", "parent", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("group", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(pom.managedDependencies()).containsExactly(
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", null, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("other", "parent", "jar", null),
                        new MavenDependencyValue("1", null, null, null, null)));
    }

    @Test
    public void can_resolve_local_pom_repository_parent() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                </project>
                """);
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms).containsOnlyKeys(Path.of(""));
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.dependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("group", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(pom.managedDependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", null, null, null, null)));
    }

    @Test
    public void can_resolve_local_pom_repository_parent_on_mismatch() throws IOException {
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>mismatch</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, subproject);
        assertThat(poms).containsOnlyKeys(Path.of(""));
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.dependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("group", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(pom.managedDependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", null, null, null, null)));
    }

    @Test
    public void can_resolve_local_pom_parent_on_match() throws IOException {
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, subproject);
        assertThat(poms).containsOnlyKeys(Path.of(""));
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.dependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("group", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(pom.managedDependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", null, null, null, null)));
    }

    @Test
    public void can_resolve_repository_parent_if_specified() throws IOException {
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                        <relativePath/>
                    </parent>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, subproject);
        assertThat(poms).containsOnlyKeys(Path.of(""));
        MavenLocalPom pom = poms.get(Path.of(""));
        assertThat(pom.dependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("group", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(pom.managedDependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", null, null, null, null)));
    }

    @Test
    public void can_resolve_sub_modules() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <modules>
                      <module>subproject</module>
                    </modules>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, project);
        assertThat(poms).containsOnlyKeys(Path.of(""), Path.of("subproject"));
        assertThat(poms.get(Path.of("")).dependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("group", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(poms.get(Path.of("")).managedDependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", null, null, null, null)));
        assertThat(poms.get(Path.of("subproject")).dependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("group", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(poms.get(Path.of("subproject")).managedDependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", null, null, null, null)));
    }

    @Test
    public void can_resolve_sub_modules_reverse_location() throws IOException {
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <modules>
                      <module>..</module>
                    </modules>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                        <relativePath>subproject/pom.xml</relativePath>
                    </parent>
                </project>
                """);
        SequencedMap<Path, MavenLocalPom> poms = mavenPomResolver.local(Runnable::run, mavenRepository, subproject);
        assertThat(poms).containsOnlyKeys(Path.of(""), Path.of(".."));
        assertThat(poms.get(Path.of("")).dependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("group", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(poms.get(Path.of("")).managedDependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", null, null, null, null)));
        assertThat(poms.get(Path.of("..")).dependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("group", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
        assertThat(poms.get(Path.of("..")).managedDependencies()).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", null, null, null, null)));
    }

    @Test
    public void can_detect_circular_modules() throws IOException {
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <modules>
                      <module>subproject</module>
                    </modules>
                </project>
                """);
        Path subproject = Files.createDirectory(project.resolve("subproject"));
        Files.writeString(subproject.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>project</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <modules>
                      <module>..</module>
                    </modules>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.local(Runnable::run, mavenRepository, project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Circular POM module reference to ");
    }

    @Test
    public void captures_declared_and_parent_inherited_licenses() throws IOException {
        addToRepository("parentgroup", "parentlib", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parentgroup</groupId>
                    <artifactId>parentlib</artifactId>
                    <version>1</version>
                    <licenses>
                        <license>
                            <name>Apache-2.0</name>
                            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                        </license>
                    </licenses>
                </project>
                """);
        addToRepository("leafgroup", "leaflib", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parentgroup</groupId>
                        <artifactId>parentlib</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>leaflib</artifactId>
                </project>
                """);
        addToRepository("dirgroup", "dirlib", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>dirgroup</groupId>
                    <artifactId>dirlib</artifactId>
                    <version>1</version>
                    <licenses>
                        <license>
                            <name>MIT</name>
                            <url>https://opensource.org/license/mit</url>
                        </license>
                    </licenses>
                </project>
                """);
        addToRepository("rootgroup", "rootlib", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>rootgroup</groupId>
                    <artifactId>rootlib</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>leafgroup</groupId>
                            <artifactId>leaflib</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>dirgroup</groupId>
                            <artifactId>dirlib</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);

        addJarToRepository("rootgroup", "rootlib", "1");
        addJarToRepository("leafgroup", "leaflib", "1");
        addJarToRepository("dirgroup", "dirlib", "1");
        Resolver.Resolution resolution = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.<String, Repository>of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("rootgroup/rootlib/1", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE);

        assertThat(resolution.vertices().get("maven/leafgroup/leaflib").licenses())
                .as("a dependency inherits its parent POM's license, captured verbatim before classification")
                .containsExactly(new License(null, null, "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0.txt"));
        assertThat(resolution.vertices().get("maven/dirgroup/dirlib").licenses())
                .as("a dependency's own declared license is captured verbatim before classification")
                .containsExactly(new License(null, null, "MIT", "https://opensource.org/license/mit"));
    }

    @Test
    public void captures_the_license_of_a_dependency_whose_own_dependencies_are_all_excluded() throws IOException {
        addToRepository("flatgroup", "flatlib", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>flatgroup</groupId>
                    <artifactId>flatlib</artifactId>
                    <version>1</version>
                    <licenses>
                        <license>
                            <name>Apache-2.0</name>
                            <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
                        </license>
                    </licenses>
                    <dependencies>
                        <dependency>
                            <groupId>hiddengroup</groupId>
                            <artifactId>hiddenlib</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("rootgroup", "rootlib", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>rootgroup</groupId>
                    <artifactId>rootlib</artifactId>
                    <version>1</version>
                    <dependencies>
                        <dependency>
                            <groupId>flatgroup</groupId>
                            <artifactId>flatlib</artifactId>
                            <version>1</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>*</groupId>
                                    <artifactId>*</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addJarToRepository("rootgroup", "rootlib", "1");
        addJarToRepository("flatgroup", "flatlib", "1");

        Resolver.Resolution resolution = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.<String, Repository>of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("rootgroup/rootlib/1", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE);

        assertThat(resolution.vertices().get("maven/flatgroup/flatlib").licenses())
                .as("a POM that lists its closure flat excludes every dependency's own dependencies, as Jenesis writes"
                        + " one, and the license of each is still read from its POM")
                .containsExactly(new License(null, null, "Apache-2.0", "https://www.apache.org/licenses/LICENSE-2.0.txt"));
        assertThat(resolution.vertices()).doesNotContainKey("maven/hiddengroup/hiddenlib");
    }

    @Test
    public void bom_flattens_dependency_management() throws IOException {
        addToRepository("group", "bom", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>2</version>
                            </dependency>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>windows</artifactId>
                                <version>2</version>
                                <classifier>win</classifier>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Resolver.Bom bom = mavenPomResolver.bom(Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                "group/bom",
                "1",
                null,
                false);
        assertThat(bom.verifiable()).isFalse();
        assertThat(bom.version()).isEqualTo("1");
        assertThat(bom.entries()).containsExactly(
                Map.entry("maven/other/artifact", "2"),
                Map.entry("maven/other/windows/jar/win", "2"));
    }

    @Test
    public void bom_drops_managed_scope_and_exclusions() throws IOException {
        addToRepository("group", "bom", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>2</version>
                                <scope>test</scope>
                                <exclusions>
                                    <exclusion>
                                        <groupId>excluded</groupId>
                                        <artifactId>artifact</artifactId>
                                    </exclusion>
                                </exclusions>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Resolver.Bom bom = mavenPomResolver.bom(Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                "group/bom",
                "1",
                null,
                false);
        assertThat(bom.entries()).containsExactly(Map.entry("maven/other/artifact", "2"));
    }

    @Test
    public void bom_skips_versionless_managed_entry() throws IOException {
        addToRepository("group", "bom", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Resolver.Bom bom = mavenPomResolver.bom(Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                "group/bom",
                "1",
                null,
                false);
        assertThat(bom.entries()).isEmpty();
    }

    @Test
    public void bom_negotiates_release_when_floating() throws IOException {
        addToRepository("group", "bom", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>2</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        Files.writeString(repository.resolve("group/bom").resolve("maven-metadata.xml"),
                "<metadata><versioning><release>1</release></versioning></metadata>");
        Resolver.Bom bom = mavenPomResolver.bom(Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                "group/bom",
                "",
                null,
                false);
        assertThat(bom.version()).isEqualTo("1");
        assertThat(bom.entries()).containsExactly(Map.entry("maven/other/artifact", "2"));
    }

    @Test
    public void bom_rejects_checksum() {
        assertThatThrownBy(() -> mavenPomResolver.bom(Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                "group/bom",
                "1",
                "SHA-256/abcd",
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot carry a checksum");
    }

    @Test
    public void bom_rejects_qualified_coordinate() {
        assertThatThrownBy(() -> mavenPomResolver.bom(Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                "group/bom/pom",
                "1",
                null,
                false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expected <groupId>/<artifactId>");
    }

    private void addToRepository(String groupId, String artifactId, String version, String pom) throws IOException {
        Files.writeString(Files
                .createDirectories(repository.resolve(groupId + "/" + artifactId + "/" + version))
                .resolve(artifactId + "-" + version + ".pom"), pom);
    }

    private void addJarToRepository(String groupId, String artifactId, String version) throws IOException {
        addJarToRepository(groupId, artifactId, version, null);
    }

    private void addJarToRepository(String groupId, String artifactId, String version, String classifier) throws IOException {
        Files.write(Files
                        .createDirectories(repository.resolve(groupId + "/" + artifactId + "/" + version))
                        .resolve(artifactId + "-" + version + (classifier == null ? "" : "-" + classifier) + ".jar"),
                (groupId + ":" + artifactId + ":" + version).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void external_managed_dep_checksum_is_ignored() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <!--Checksum/SHA256/cafebabe-->
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> deps = mavenPomResolver.dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null);
        assertThat(deps).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null, null)));
    }

    @Test
    public void first_party_managed_dep_checksum_propagates_to_resolved_value() throws IOException {
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        String rootPom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>other</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <!--Checksum/SHA256/cafebabe-->
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;
        SequencedMap<MavenDependencyKey, MavenDependencyValue> deps = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                List.of(new MavenResolver.RootPom(new ByteArrayInputStream(rootPom.getBytes(StandardCharsets.UTF_8)))),
                Map.of(),
                MavenDependencyScope.COMPILE,
                "main").dependencies();
        assertThat(deps.get(new MavenDependencyKey("other", "artifact", "jar", null)).checksum())
                .isEqualTo("SHA256/cafebabe");
    }

    @Test
    public void inline_version_still_enforces_a_managed_checksum() throws IOException {
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addJarToRepository("other", "artifact", "1");
        SequencedMap<String, SequencedSet<String>> coordinates = new LinkedHashMap<>();
        coordinates.put("other/artifact/1", new LinkedHashSet<>());
        SequencedMap<String, String> versions = new LinkedHashMap<>();
        versions.put("other/artifact", "1 SHA-256/deadbeef");
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run,
                "group",
                Map.of("group", mavenRepository),
                coordinates,
                versions,
                DependencyScope.COMPILE))
                .hasStackTraceContaining("Mismatched digest");
    }

    @Test
    public void parent_pom_checksum_is_ignored() throws IOException {
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                </project>
                """);
        Files.writeString(project.resolve("pom.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                        <relativePath/>
                        <!--Checksum/SHA-256/deadbeef-->
                    </parent>
                    <artifactId>child</artifactId>
                </project>
                """);
        assertThatCode(() -> mavenPomResolver.local(
                Runnable::run, mavenRepository, project)).doesNotThrowAnyException();
    }

    @Test
    public void first_declared_imported_bom_wins() throws IOException {
        addToRepository("bomone", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>bomone</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>shared</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("bomtwo", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>bomtwo</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>shared</groupId>
                                <artifactId>artifact</artifactId>
                                <version>2</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>bomone</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                            <dependency>
                                <groupId>bomtwo</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>shared</groupId>
                            <artifactId>artifact</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("shared", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("shared", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> deps = mavenPomResolver.dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null);
        assertThat(deps.get(new MavenDependencyKey("shared", "artifact", "jar", null)).version())
                .as("the first-declared imported BOM manages the version")
                .isEqualTo("1");
    }

    @Test
    public void own_imported_bom_wins_over_inherited_imported_bom() throws IOException {
        addToRepository("unscoped", "bom", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>unscoped</groupId>
                    <artifactId>bom</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>testing</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("scoped", "bom", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>scoped</groupId>
                    <artifactId>bom</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>testing</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("parent", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>parent</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>unscoped</groupId>
                                <artifactId>bom</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>parent</groupId>
                        <artifactId>artifact</artifactId>
                        <version>1</version>
                    </parent>
                    <artifactId>artifact</artifactId>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>scoped</groupId>
                                <artifactId>bom</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>testing</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("consumer", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>group</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("testing", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run, mavenRepository, "consumer", "artifact", "1", null);
        assertThat(dependencies)
                .as("the POM's own import manages the scope before its parent's import, so the test dependency stays out")
                .containsOnlyKeys(new MavenDependencyKey("group", "artifact", "jar", null));
    }

    @Test
    public void external_bom_import_checksum_is_ignored() throws IOException {
        addToRepository("bom", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>bom</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                </project>
                """);
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>bom</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                                <!--Checksum/SHA-256/cafebabe-->
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        assertThatCode(() -> mavenPomResolver.dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null)).doesNotThrowAnyException();
    }

    @Test
    public void first_party_bom_import_checksum_mismatch_fails_resolution() throws IOException {
        addToRepository("bom", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>bom</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <packaging>pom</packaging>
                </project>
                """);
        String rootPom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>group</groupId>
                    <artifactId>artifact</artifactId>
                    <version>1</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>bom</groupId>
                                <artifactId>artifact</artifactId>
                                <version>1</version>
                                <type>pom</type>
                                <scope>import</scope>
                                <!--Checksum/SHA-256/cafebabe-->
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """;
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                List.of(new MavenResolver.RootPom(new ByteArrayInputStream(rootPom.getBytes(StandardCharsets.UTF_8)))),
                Map.of(),
                MavenDependencyScope.COMPILE,
                "main"))
                .hasStackTraceContaining("Mismatched POM checksum")
                .hasStackTraceContaining("bom:artifact:1");
    }

    @Test
    public void spi_external_versions_pin_transitive() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>pinned</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("pinned", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("pinned", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addJarToRepository("group", "artifact", "1");
        addJarToRepository("other", "artifact", "1");
        addJarToRepository("pinned", "artifact", "2");
        SequencedMap<String, String> versions = new LinkedHashMap<>();
        versions.put("pinned/artifact/jar", "2");
        SequencedMap<String, Resolver.Resolved> resolved = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("group/artifact/1", Collections.emptyNavigableSet())),
                versions,
                DependencyScope.COMPILE).artifacts();
        assertThat(resolved).containsOnlyKeys(
                "maven/group/artifact/1",
                "maven/other/artifact/1",
                "maven/pinned/artifact/2");
    }

    @Test
    public void spi_external_versions_pin_with_short_key_form() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>pinned</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("pinned", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("pinned", "artifact", "5", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addJarToRepository("group", "artifact", "1");
        addJarToRepository("pinned", "artifact", "5");
        SequencedMap<String, String> versions = new LinkedHashMap<>();
        versions.put("pinned/artifact", "5");
        SequencedMap<String, Resolver.Resolved> resolved = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("group/artifact/1", Collections.emptyNavigableSet())),
                versions,
                DependencyScope.COMPILE).artifacts();
        assertThat(resolved).containsOnlyKeys(
                "maven/group/artifact/1",
                "maven/pinned/artifact/5");
    }

    @Test
    public void spi_external_versions_pin_with_classifier() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>pinned</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <classifier>sources</classifier>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("pinned", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("pinned", "artifact", "3", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addJarToRepository("group", "artifact", "1");
        addJarToRepository("pinned", "artifact", "3", "sources");
        SequencedMap<String, String> versions = new LinkedHashMap<>();
        versions.put("pinned/artifact/jar/sources", "3");
        SequencedMap<String, Resolver.Resolved> resolved = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("group/artifact/1", Collections.emptyNavigableSet())),
                versions,
                DependencyScope.COMPILE).artifacts();
        assertThat(resolved).containsOnlyKeys(
                "maven/group/artifact/1",
                "maven/pinned/artifact/jar/sources/3");
    }

    @Test
    public void spi_empty_versions_does_not_change_resolution() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addJarToRepository("group", "artifact", "1");
        addJarToRepository("other", "artifact", "1");
        SequencedMap<String, Resolver.Resolved> resolved = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("group/artifact/1", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(resolved).containsOnlyKeys(
                "maven/group/artifact/1",
                "maven/other/artifact/1");
    }

    @Test
    public void spi_external_versions_pin_without_direct_dependency() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>middle</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("middle", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>deep</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("deep", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addToRepository("deep", "artifact", "7", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        addJarToRepository("group", "artifact", "1");
        addJarToRepository("middle", "artifact", "1");
        addJarToRepository("deep", "artifact", "7");
        SequencedMap<String, String> versions = new LinkedHashMap<>();
        versions.put("deep/artifact/jar", "7");
        SequencedMap<String, Resolver.Resolved> resolved = mavenPomResolver.dependencies(
                Runnable::run,
                "maven",
                Map.of("maven", mavenRepository),
                new LinkedHashMap<>(Map.of("group/artifact/1", Collections.emptyNavigableSet())),
                versions,
                DependencyScope.COMPILE).artifacts();
        assertThat(resolved).containsOnlyKeys(
                "maven/group/artifact/1",
                "maven/middle/artifact/1",
                "maven/deep/artifact/7");
    }

    @Test
    public void coordinate_key_rejects_traversal_component() {
        assertThatThrownBy(() -> new MavenDependencyKey("..", "artifact", "jar", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("..");
        assertThatThrownBy(() -> new MavenDependencyKey("group", "a/b", "jar", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a/b");
        assertThatThrownBy(() -> new MavenDependencyKey("group", "artifact", "ja\\r", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void parsed_coordinate_rejects_traversal_component() {
        assertThatThrownBy(() -> MavenDependencyKey.parseKey("group/.."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("..");
        assertThatThrownBy(() -> MavenDependencyKey.tryParse("group/artifact/.."))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("..");
    }

    @Test
    public void transitive_traversal_coordinate_is_rejected() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>..</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null))
                .hasStackTraceContaining("Illegal Maven coordinate groupId");
    }

    @Test
    public void external_conflicting_checksums_are_ignored() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>left</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                        <dependency>
                            <groupId>right</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("left", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>shared</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <!--Checksum/SHA256/aaaa-->
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("right", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>shared</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <!--Checksum/SHA256/bbbb-->
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("shared", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        assertThatCode(() -> mavenPomResolver.dependencies(
                Runnable::run, mavenRepository, "group", "artifact", "1", null)).doesNotThrowAnyException();
    }

    @Test
    public void root_dependency_management_overrides_purely_transitive_dependency() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>transitive</groupId>
                                <artifactId>artifact</artifactId>
                                <version>2</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void snapshot_version_is_passed_through() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1-SNAPSHOT</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1-SNAPSHOT", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1-SNAPSHOT", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void can_resolve_transitive_dependencies_with_exclusion_group_wildcard() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>transitive</groupId>
                                    <artifactId>*</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1",
                        MavenDependencyScope.COMPILE,
                        null,
                        List.of(new MavenDependencyName("transitive", "*")),
                        null)));
    }

    @Test
    public void can_resolve_transitive_dependencies_with_exclusion_artifact_wildcard() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>*</groupId>
                                    <artifactId>artifact</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1",
                        MavenDependencyScope.COMPILE,
                        null,
                        List.of(new MavenDependencyName("*", "artifact")),
                        null)));
    }

    @Test
    public void provided_scope_propagates_to_transitive_dependency() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <scope>provided</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.PROVIDED, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.PROVIDED, null, null, null)));
    }

    @Test
    public void runtime_scope_propagates_to_transitive_dependency() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <scope>runtime</scope>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("other", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.RUNTIME, null, null, null)),
                Map.entry(
                        new MavenDependencyKey("transitive", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.RUNTIME, null, null, null)));
    }

    @Test
    public void root_optional_dependency_is_retained() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>other</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <optional>true</optional>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("other", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("other", "artifact", "jar", null),
                new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, true)));
    }

    @Test
    public void exclusion_accumulates_across_transitive_levels() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>mid</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                            <exclusions>
                                <exclusion>
                                    <groupId>leaf</groupId>
                                    <artifactId>artifact</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("mid", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>inner</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("inner", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>leaf</groupId>
                            <artifactId>artifact</artifactId>
                            <version>1</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(
                Map.entry(
                        new MavenDependencyKey("mid", "artifact", "jar", null),
                        new MavenDependencyValue("1",
                                MavenDependencyScope.COMPILE,
                                null,
                                List.of(new MavenDependencyName("leaf", "artifact")),
                                null)),
                Map.entry(
                        new MavenDependencyKey("inner", "artifact", "jar", null),
                        new MavenDependencyValue("1", MavenDependencyScope.COMPILE, null, null, null)));
    }

    @Test
    public void malformed_range_syntax_is_rejected() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[1,2]]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null))
                .hasStackTraceContaining("Invalid version range");
    }

    @Test
    public void missing_metadata_for_release_version_is_rejected() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>RELEASE</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null))
                .hasStackTraceContaining("No metadata for transitive:artifact");
    }

    @Test
    public void unknown_metadata_model_version_is_rejected() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>RELEASE</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="9.9.9">
                  <versioning>
                    <release>1</release>
                    <versions>
                      <version>1</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        assertThatThrownBy(() -> mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null))
                .hasStackTraceContaining("Unknown model version");
    }

    @Test
    public void can_resolve_range_with_interior_whitespace() throws IOException {
        addToRepository("group", "artifact", "1", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                    <dependencies>
                        <dependency>
                            <groupId>transitive</groupId>
                            <artifactId>artifact</artifactId>
                            <version>[ 1 , 2 ]</version>
                        </dependency>
                    </dependencies>
                </project>
                """);
        addToRepository("transitive", "artifact", "2", """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                    <modelVersion>4.0.0</modelVersion>
                </project>
                """);
        Files.writeString(Files
                .createDirectories(repository.resolve("transitive/artifact/"))
                .resolve("maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata modelVersion="1.1.0">
                  <versioning>
                    <latest>2</latest>
                    <release>1</release>
                    <versions>
                      <version>1</version>
                      <version>2</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = mavenPomResolver.dependencies(
                Runnable::run,
                mavenRepository,
                "group",
                "artifact",
                "1",
                null);
        assertThat(dependencies).containsExactly(Map.entry(
                new MavenDependencyKey("transitive", "artifact", "jar", null),
                new MavenDependencyValue("2", MavenDependencyScope.COMPILE, null, null, null)));
    }
}
