package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
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

public class DependenciesMavenBomTest {

    @TempDir
    private Path root, artifacts;
    private Path previous, next, supplement, dependencies;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        dependencies = Files.createDirectory(root.resolve("dependencies"));
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

    private BuildStepResult apply(Dependencies resolve) throws IOException {
        return resolve.apply(
                        Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("dependencies", new BuildStepArgument(
                                dependencies,
                                Map.of(Path.of(BuildStep.REQUIRES), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.BOMS), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
    }

    @Test
    public void maven_bom_entries_manage_maven_resolution() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        BuildStepResult result = apply(new Dependencies(
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
                Map.of("maven", new MavenPomResolver())));
        assertThat(result.next()).isTrue();
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames()).contains("main/compile/maven/org.acme/lib/2.0");
        SequencedProperties resolvedBoms = SequencedProperties.ofFiles(next.resolve(BuildStep.BOMS));
        assertThat(resolvedBoms.getProperty("version/main/maven/org.acme/platform-bom")).isEqualTo("1.0");
        assertThat(resolvedBoms.getProperty("pin/main/maven/org.acme/lib")).isEqualTo("2.0");
        assertThat(resolvedBoms.stringPropertyNames()).noneMatch(key -> key.startsWith("bom/"));
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
        BuildStepResult result = apply(new Dependencies(
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
                Map.of("maven", new MavenPomResolver())));
        assertThat(result.next()).isTrue();
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames()).contains("main/compile/maven/org.acme/lib/3.0");
    }

    @Test
    public void strict_pinning_accepts_hashless_maven_bom_reference() throws IOException {
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        BuildStepResult result = apply(new Dependencies(
                Map.of("maven", maven(Map.of("org.acme/platform-bom/pom/1.0", """
                        <project xmlns="http://maven.apache.org/POM/4.0.0">
                            <modelVersion>4.0.0</modelVersion>
                        </project>
                        """))),
                Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT));
        assertThat(result.next()).isTrue();
    }

    @Test
    public void checksum_comments_in_maven_bom_are_ignored() throws IOException {
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        BuildStepResult result = apply(new Dependencies(
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
                Map.of("maven", new MavenPomResolver())));
        assertThat(result.next()).isTrue();
        SequencedProperties resolvedBoms = SequencedProperties.ofFiles(next.resolve(BuildStep.BOMS));
        assertThat(resolvedBoms.getProperty("pin/main/maven/org.acme/lib")).isEqualTo("2.0");
    }

    @Test
    public void nested_import_scoped_bom_flattens_first_wins() throws IOException {
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "1.0");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        BuildStepResult result = apply(new Dependencies(
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
                Map.of("maven", new MavenPomResolver())));
        assertThat(result.next()).isTrue();
        SequencedProperties resolvedBoms = SequencedProperties.ofFiles(next.resolve(BuildStep.BOMS));
        assertThat(resolvedBoms.getProperty("pin/main/maven/org.acme/lib")).isEqualTo("2.0");
        assertThat(resolvedBoms.getProperty("pin/main/maven/org.acme/extra")).isEqualTo("1.5");
    }

    @Test
    public void versionless_maven_bom_negotiates_release_and_stays_floating() throws IOException {
        SequencedProperties boms = new SequencedProperties();
        boms.setProperty("bom/main/maven/org.acme/platform-bom", "");
        boms.store(dependencies.resolve(BuildStep.BOMS));
        BuildStepResult result = apply(new Dependencies(
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
                Map.of("maven", new MavenPomResolver())));
        assertThat(result.next()).isTrue();
        SequencedProperties resolvedBoms = SequencedProperties.ofFiles(next.resolve(BuildStep.BOMS));
        assertThat(resolvedBoms.getProperty("pin/main/maven/org.acme/lib")).isEqualTo("2.0");
        assertThat(resolvedBoms.stringPropertyNames()).noneMatch(key -> key.startsWith("version/"));
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
        BuildStepResult result = apply(new Dependencies(
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
                Map.of("maven", new MavenPomResolver())).pinning(Pinning.IGNORE));
        assertThat(result.next()).isTrue();
        SequencedProperties index = SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES));
        assertThat(index.stringPropertyNames()).contains("main/compile/maven/org.acme/lib/2.5");
        SequencedProperties resolvedBoms = SequencedProperties.ofFiles(next.resolve(BuildStep.BOMS));
        assertThat(resolvedBoms.getProperty("version/main/maven/org.acme/platform-bom")).isEqualTo("2.0");
        assertThat(resolvedBoms.getProperty("pin/main/maven/org.acme/lib")).isEqualTo("2.5");
    }
}
