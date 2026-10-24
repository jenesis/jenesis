package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Pinning;
import build.jenesis.RepositoryItem;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.maven.MavenRepository;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DependenciesPomPinTest {

    private static final Map<String, String> POMS = Map.of(
            "org.acme/lib/pom/1.0", """
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>org.acme</groupId>
                            <artifactId>parent</artifactId>
                            <version>64</version>
                        </parent>
                        <artifactId>lib</artifactId>
                        <version>1.0</version>
                        <dependencies>
                            <dependency>
                                <groupId>org.acme</groupId>
                                <artifactId>other</artifactId>
                                <version>1.0</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
            "org.acme/other/pom/1.0", """
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <parent>
                            <groupId>org.acme</groupId>
                            <artifactId>parent</artifactId>
                            <version>69</version>
                        </parent>
                        <artifactId>other</artifactId>
                        <version>1.0</version>
                    </project>
                    """,
            "org.acme/parent/pom/64", """
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.acme</groupId>
                        <artifactId>parent</artifactId>
                        <version>64</version>
                        <packaging>pom</packaging>
                    </project>
                    """,
            "org.acme/parent/pom/69", """
                    <project xmlns="http://maven.apache.org/POM/4.0.0">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>org.acme</groupId>
                        <artifactId>parent</artifactId>
                        <version>69</version>
                        <packaging>pom</packaging>
                    </project>
                    """);

    @TempDir
    private Path root, artifacts;
    private Path dependencies, build;

    @BeforeEach
    public void setUp() throws Exception {
        dependencies = Files.createDirectory(root.resolve("dependencies"));
        build = Files.createDirectory(root.resolve("build"));
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.acme/lib", "");
        requires.store(dependencies.resolve(BuildStep.REQUIRES));
    }

    private MavenRepository maven(Map<String, String> poms) {
        return (_, groupId, artifactId, version, type, _, _) -> {
            String coordinate = groupId + "/" + artifactId + "/" + type + "/" + version;
            String content = "pom".equals(type) ? poms.get(coordinate) : coordinate;
            if (content == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(RepositoryItem.ofFile(Files.writeString(artifacts.resolve(coordinate.replace('/', '-')), content)));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static String sha256(String content) {
        try {
            return "SHA-256/" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    private void pin(Map<String, String> pins) throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("main/maven/org.acme/lib", "1.0 " + sha256("org.acme/lib/jar/1.0"));
        versions.setProperty("main/maven/org.acme/other", "1.0 " + sha256("org.acme/other/jar/1.0"));
        pins.forEach(versions::setProperty);
        versions.store(dependencies.resolve(BuildStep.VERSIONS));
    }

    private Path apply(Map<String, String> poms, Pinning pinning) throws IOException {
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("dependencies", dependencies);
        executor.addModule("resolved", new Dependencies(
                Map.of("maven", maven(poms)),
                Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE))).pinning(pinning), "dependencies");
        return executor.execute().get("resolved");
    }

    @Test
    public void records_every_pom_a_resolution_reads_beside_the_checksum_pinned_for_it() throws IOException {
        pin(Map.of(
                "main/maven/org.acme/lib/pom/1.0", "1.0 " + sha256(POMS.get("org.acme/lib/pom/1.0")),
                "main/maven/org.acme/parent/pom/64", "64 " + sha256(POMS.get("org.acme/parent/pom/64"))));
        Path next = apply(POMS, null);
        SequencedProperties poms = SequencedProperties.ofFiles(next.resolve(Dependencies.POMS));
        assertThat(poms.stringPropertyNames())
                .as("the POMs of every dependency and of every parent, one per version read")
                .containsExactlyInAnyOrder("main/maven/org.acme/lib/pom/1.0",
                        "main/maven/org.acme/other/pom/1.0",
                        "main/maven/org.acme/parent/pom/64",
                        "main/maven/org.acme/parent/pom/69");
        assertThat(poms.getProperty("main/maven/org.acme/parent/pom/64"))
                .endsWith(" " + sha256(POMS.get("org.acme/parent/pom/64")));
        assertThat(next.resolve(poms.getProperty("main/maven/org.acme/parent/pom/69"))).hasContent(POMS.get("org.acme/parent/pom/69"));
        assertThat(SequencedProperties.ofFiles(next.resolve(Dependencies.UNPINNED)).stringPropertyNames())
                .containsExactlyInAnyOrder("maven/org.acme/other/pom/1.0", "maven/org.acme/parent/pom/69");
        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.DEPENDENCIES)).stringPropertyNames())
                .as("a POM is read, not placed on a path")
                .containsExactlyInAnyOrder("main/compile/maven/org.acme/lib/1.0", "main/compile/maven/org.acme/other/1.0");
    }

    @Test
    public void strict_pinning_fails_naming_a_pom_without_a_pin() {
        assertThatThrownBy(() -> {
            pin(Map.of(
                    "main/maven/org.acme/lib/pom/1.0", "1.0 " + sha256(POMS.get("org.acme/lib/pom/1.0")),
                    "main/maven/org.acme/other/pom/1.0", "1.0 " + sha256(POMS.get("org.acme/other/pom/1.0")),
                    "main/maven/org.acme/parent/pom/64", "64 " + sha256(POMS.get("org.acme/parent/pom/64"))));
            apply(POMS, Pinning.STRICT);
        }).hasStackTraceContaining("No checksum pinned for maven/org.acme/parent/pom/69");
    }

    @Test
    public void strict_pinning_accepts_a_resolution_whose_every_pom_is_pinned() throws IOException {
        SequencedMap<String, String> pins = new LinkedHashMap<>();
        POMS.forEach((coordinate, content) -> pins.put("main/maven/" + coordinate,
                coordinate.substring(coordinate.lastIndexOf('/') + 1) + " " + sha256(content)));
        pin(pins);
        assertThat(apply(POMS, Pinning.STRICT).resolve(Dependencies.UNPINNED)).doesNotExist();
    }

    @Test
    public void a_pom_whose_bytes_differ_from_its_pin_fails_the_resolution() {
        assertThatThrownBy(() -> {
            pin(Map.of("main/maven/org.acme/parent/pom/69", "69 " + sha256("<project/>")));
            apply(POMS, null);
        }).hasStackTraceContaining("Mismatched digest for maven/org.acme/parent/pom/69");
    }

    @Test
    public void pinning_versions_alone_reads_a_pom_whose_bytes_differ_from_its_pin() throws IOException {
        pin(Map.of("main/maven/org.acme/parent/pom/69", "69 " + sha256("<project/>")));
        assertThat(apply(POMS, Pinning.VERSIONS).resolve(Dependencies.POMS)).exists();
    }
}
