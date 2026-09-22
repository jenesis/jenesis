package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.maven.MavenRepository;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static build.jenesis.SequencedProperties.SYSTEM;

public class DependenciesMavenBomTest {

    @TempDir
    private Path root, artifacts;
    private Path next, dependencies, build;

    @BeforeEach
    public void setUp() throws Exception {
        dependencies = Files.createDirectory(root.resolve("dependencies"));
        build = Files.createDirectory(root.resolve("build"));
    }

    private Repository maven(Map<String, String> poms) {
        return maven(poms, null);
    }

    private MavenRepository maven(Map<String, String> poms, String metadata) {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) {
                String coordinate = groupId + "/" + artifactId + "/" + type + "/" + version;
                String content = "pom".equals(type) ? poms.get(coordinate) : coordinate;
                return content == null ? Optional.empty() : Optional.of(item(coordinate, content));
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) {
                return metadata == null
                        ? Optional.empty()
                        : Optional.of(item(groupId + "/" + artifactId + "/maven-metadata.xml", metadata));
            }

            private RepositoryItem item(String coordinate, String content) {
                Path file;
                try {
                    file = Files.write(
                            artifacts.resolve(coordinate.replace('/', '-')),
                            content.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return RepositoryItem.ofFile(file);
            }
        };
    }

    private Path apply(Dependencies module) throws IOException {
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
        executor.addSource("dependencies", dependencies);
        executor.addModule("resolved", module, "dependencies");
        next = executor.execute().get("resolved");
        return next;
    }

    @Test
    public void maven_bom_entries_manage_maven_resolution() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/platform-bom/pom/1.0", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                            <dependencyManagement>
                                <dependencies>
                                    <dependency>
                                        <groupId>org.acme</groupId>
                                        <artifactId>lib</artifactId>
                                        <version>2.0</version>
                                    </dependency>
                                </dependencies>
                            </dependencyManagement>
                        </project>
                        """))),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))));
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames()).contains("main/compile/maven/org.acme/lib/2.0");
        SequencedProperties resolvedBoms = SequencedProperties.ofFiles(next.resolve(BuildStep.BOMS));
        assertThat(resolvedBoms.getProperty("version/main/maven/org.acme/platform-bom")).isEqualTo("1.0");
        assertThat(resolvedBoms.stringPropertyNames()).noneMatch(key -> key.startsWith("bom/"));
        assertThat(resolvedBoms.stringPropertyNames()).noneMatch(key -> key.startsWith("entry/"));
    }

    @Test
    public void a_scoped_step_ignores_a_maven_bom_of_another_group() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("tool/runtime/maven/org.acme/tool/1.0", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/tool/pom/1.0", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                        </project>
                        """))),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))).group("tool"));
        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES)).stringPropertyNames())
                .as("a tool never fetches the bill of materials that manages the module's own closure")
                .containsExactly("tool/runtime/maven/org.acme/tool/1.0");
    }

    @Test
    public void local_pin_wins_over_maven_bom_entry() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("main/maven/org.acme/lib", "3.0");
        versions.store(dependencies.resolve(BuildStep.VERSIONS));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/platform-bom/pom/1.0", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                            <dependencyManagement>
                                <dependencies>
                                    <dependency>
                                        <groupId>org.acme</groupId>
                                        <artifactId>lib</artifactId>
                                        <version>2.0</version>
                                    </dependency>
                                </dependencies>
                            </dependencyManagement>
                        </project>
                        """))),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))));
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames()).contains("main/compile/maven/org.acme/lib/3.0");
    }

    @Test
    public void strict_pinning_names_the_bill_of_materials_that_moved_a_version() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("main/maven/org.acme/lib", "1.0 SHA-256/8943fd8f317f46b8ab10ccd163777312f486b4ed24953460a1c2d16993d3daa5");
        versions.store(dependencies.resolve(BuildStep.VERSIONS));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("entry/main/maven/org.slf4j/slf4j-api", "2.0.18");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        assertThatThrownBy(() -> apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/lib/pom/1.0", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                            <dependencies>
                                <dependency>
                                    <groupId>org.slf4j</groupId>
                                    <artifactId>slf4j-api</artifactId>
                                    <version>2.0.16</version>
                                </dependency>
                            </dependencies>
                        </project>
                        """))),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))).pinning(Pinning.STRICT)))
                .as("a version-only entry re-versions what other modules reach transitively, and the"
                        + " module that fails never named the coordinate at all")
                .hasStackTraceContaining("No checksum pinned for maven/org.slf4j/slf4j-api/2.0.18")
                .hasStackTraceContaining("A bill of materials manages it at this version"
                        + " and records no checksum");
    }

    @Test
    public void a_classified_coordinate_in_a_bill_of_materials_names_its_repository() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("main/maven/org.acme/lib", "1.0");
        versions.store(dependencies.resolve(BuildStep.VERSIONS));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("entry/main/maven/io.netty/netty-transport-native-epoll/jar/linux-x86_64", "4.2.18");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/lib/pom/1.0", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                            <dependencies>
                                <dependency>
                                    <groupId>io.netty</groupId>
                                    <artifactId>netty-transport-native-epoll</artifactId>
                                    <version>4.2.10</version>
                                    <classifier>linux-x86_64</classifier>
                                </dependency>
                            </dependencies>
                        </project>
                        """))),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))));
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames())
                .as("written in full, a classified coordinate manages a version like any other")
                .contains("main/compile/maven/io.netty/netty-transport-native-epoll/jar/linux-x86_64/4.2.18");
    }

    @Test
    public void a_bill_of_materials_entry_contradicting_an_alias_is_rejected() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties aliases = new SequencedProperties();
        aliases.setProperty("main/module/com.acme.lib", "org.acme/lib");
        aliases.store(dependencies.resolve(BuildStep.ALIASES));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("entry/main/module/com.acme.lib", "2.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        assertThatThrownBy(() -> apply(new Dependencies(
                Map.of("maven", maven(Map.of()), "module", maven(Map.of())),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM)))))
                .as("an entry under a module name sends every module importing the file to the module"
                        + " repository, which is the lookup the alias exists to replace")
                .hasStackTraceContaining("names a module this project aliases to org.acme/lib")
                .hasStackTraceContaining("Pin the coordinate the alias names instead");
    }

    @Test
    public void a_bill_of_materials_entry_naming_no_repository_is_rejected() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("entry/main/io.netty/netty-transport-native-epoll/jar/linux-x86_64", "4.2.18");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        assertThatThrownBy(() -> apply(new Dependencies(
                Map.of("maven", maven(Map.of())),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM)))))
                .as("dropping the repository turns the groupId into one, and the entry then manages"
                        + " a coordinate nothing resolves rather than the one that was meant")
                .hasStackTraceContaining("Unknown repository 'io.netty'")
                .hasStackTraceContaining("maven/<groupId>/<artifactId>/<type>/<classifier>");
    }

    @Test
    public void strict_pinning_accepts_hashless_maven_bom_reference() throws IOException {
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/platform-bom/pom/1.0", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                        </project>
                        """))),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))).pinning(Pinning.STRICT));
    }

    @Test
    public void checksum_comments_in_maven_bom_are_ignored() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/platform-bom/pom/1.0", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                            <dependencyManagement>
                                <dependencies>
                                    <dependency>
                                        <groupId>org.acme</groupId>
                                        <artifactId>lib</artifactId>
                                        <version>2.0</version><!--Checksum/SHA-256/abcd-->
                                    </dependency>
                                </dependencies>
                            </dependencyManagement>
                        </project>
                        """))),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))));
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.getProperty("main/compile/maven/org.acme/lib/2.0")).doesNotContain("SHA");
    }

    @Test
    public void nested_import_scoped_bom_flattens_first_wins() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.setProperty("main/compile/maven/org.acme/extra", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of(
                        "org.acme/platform-bom/pom/1.0", """
                                <project xmlns="http://maven.apache.org/POM/4.0.0">
                                    <modelVersion>4.0.0</modelVersion>
                                    <dependencyManagement>
                                        <dependencies>
                                            <dependency>
                                                <groupId>org.acme</groupId>
                                                <artifactId>lib</artifactId>
                                                <version>2.0</version>
                                            </dependency>
                                            <dependency>
                                                <groupId>org.acme</groupId>
                                                <artifactId>child-bom</artifactId>
                                                <version>1.0</version>
                                                <type>pom</type>
                                                <scope>import</scope>
                                            </dependency>
                                        </dependencies>
                                    </dependencyManagement>
                                </project>
                                """,
                        "org.acme/child-bom/pom/1.0", """
                                <project xmlns="http://maven.apache.org/POM/4.0.0">
                                    <modelVersion>4.0.0</modelVersion>
                                    <dependencyManagement>
                                        <dependencies>
                                            <dependency>
                                                <groupId>org.acme</groupId>
                                                <artifactId>lib</artifactId>
                                                <version>9.9</version>
                                            </dependency>
                                            <dependency>
                                                <groupId>org.acme</groupId>
                                                <artifactId>extra</artifactId>
                                                <version>1.5</version>
                                            </dependency>
                                        </dependencies>
                                    </dependencyManagement>
                                </project>
                                """))),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))));
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames()).contains(
                "main/compile/maven/org.acme/lib/2.0",
                "main/compile/maven/org.acme/extra/1.5");
    }

    @Test
    public void later_maven_bom_overrides_earlier_entry() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("entry/main/maven/org.acme/lib", "1.0 SHA-256/aaaa");
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/platform-bom/pom/1.0", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                            <dependencyManagement>
                                <dependencies>
                                    <dependency>
                                        <groupId>org.acme</groupId>
                                        <artifactId>lib</artifactId>
                                        <version>2.0</version>
                                    </dependency>
                                </dependencies>
                            </dependencyManagement>
                        </project>
                        """))),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))));
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames()).contains("main/compile/maven/org.acme/lib/2.0");
        SequencedProperties resolvedBoms = SequencedProperties.ofFiles(next.resolve(BuildStep.BOMS));
        assertThat(resolvedBoms.stringPropertyNames()).noneMatch(key -> key.startsWith("entry/"));
    }

    @Test
    public void versionless_maven_bom_negotiates_release_and_stays_floating() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/platform-bom/pom/1.0", """
                                <project xmlns="http://maven.apache.org/POM/4.0.0">
                                    <modelVersion>4.0.0</modelVersion>
                                    <dependencyManagement>
                                        <dependencies>
                                            <dependency>
                                                <groupId>org.acme</groupId>
                                                <artifactId>lib</artifactId>
                                                <version>2.0</version>
                                            </dependency>
                                        </dependencies>
                                    </dependencyManagement>
                                </project>
                                """),
                        "<metadata><versioning><release>1.0</release></versioning></metadata>")),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))));
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames()).contains("main/compile/maven/org.acme/lib/2.0");
        Path resolvedBoms = next.resolve(BuildStep.BOMS);
        if (Files.exists(resolvedBoms)) {
            assertThat(SequencedProperties.ofFiles(resolvedBoms).stringPropertyNames())
                    .noneMatch(key -> key.startsWith("version/"));
        }
    }

    @Test
    public void ignore_pinning_floats_maven_bom_to_latest() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("main/maven/org.acme/lib", "1.9");
        versions.store(dependencies.resolve(BuildStep.VERSIONS));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        apply(new Dependencies(
                Map.of("maven", maven(Map.of(
                                "org.acme/platform-bom/pom/1.0", """
                                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                                            <modelVersion>4.0.0</modelVersion>
                                            <dependencyManagement>
                                                <dependencies>
                                                    <dependency>
                                                        <groupId>org.acme</groupId>
                                                        <artifactId>lib</artifactId>
                                                        <version>2.0</version>
                                                    </dependency>
                                                </dependencies>
                                            </dependencyManagement>
                                        </project>
                                        """,
                                "org.acme/platform-bom/pom/2.0", """
                                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                                            <modelVersion>4.0.0</modelVersion>
                                            <dependencyManagement>
                                                <dependencies>
                                                    <dependency>
                                                        <groupId>org.acme</groupId>
                                                        <artifactId>lib</artifactId>
                                                        <version>2.5</version>
                                                    </dependency>
                                                </dependencies>
                                            </dependencyManagement>
                                        </project>
                                        """),
                        "<metadata><versioning><release>2.0</release></versioning></metadata>")),
                Map.of("maven", MavenPomResolver.ofKeys(SYSTEM))).pinning(Pinning.IGNORE));
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames()).contains("main/compile/maven/org.acme/lib/2.5");
        SequencedProperties resolvedBoms = SequencedProperties.ofFiles(next.resolve(BuildStep.BOMS));
        assertThat(resolvedBoms.getProperty("version/main/maven/org.acme/platform-bom")).isEqualTo("2.0");
    }
}
