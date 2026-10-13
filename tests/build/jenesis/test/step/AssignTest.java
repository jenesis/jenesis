package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Assign;
import build.jenesis.step.Classifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AssignTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, argument;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        argument = Files.createDirectory(root.resolve("argument"));
    }

    @Test
    public void can_assign_all_dependencies() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("foo", "");
        Path passthroughArtifact = argument.resolve("passthrough.jar");
        Files.writeString(passthroughArtifact, "other");
        properties.setProperty("bar", argument.relativize(passthroughArtifact).toString());
        properties.store(argument.resolve(BuildStep.IDENTITY));
        Files.writeString(Files.createDirectory(argument.resolve(BuildStep.ARTIFACTS)).resolve("artifact"), "baz");
        BuildStepResult result = new Assign().apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                                argument,
                                Map.of(
                                        Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.ARTIFACTS + "foo"), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        assertThat(result.next()).isTrue();
        SequencedProperties coordinates = SequencedProperties.ofFiles(next.resolve(BuildStep.IDENTITY));
        assertThat(coordinates).containsExactly(
                Map.entry("foo", next.relativize(argument.resolve(BuildStep.ARTIFACTS).resolve("artifact")).toString().replace(File.separatorChar, '/')),
                Map.entry("bar", next.relativize(passthroughArtifact).toString().replace(File.separatorChar, '/')));
        assertThat(coordinates.getProperty("foo")).doesNotStartWith("/");
        assertThat(coordinates.getProperty("bar")).doesNotStartWith("/");
    }

    @Test
    public void names_the_folder_and_every_file_it_found_where_it_expected_one_artifact() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("foo", "");
        properties.store(argument.resolve(BuildStep.IDENTITY));
        Path artifacts = Files.createDirectory(argument.resolve(BuildStep.ARTIFACTS));
        Files.writeString(artifacts.resolve("artifact.jar"), "jar");
        Files.createDirectory(artifacts.resolve(".index-tmp"));
        assertThatThrownBy(() -> new Assign().apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                                argument,
                                Map.of(Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED)))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 files in " + artifacts + ": .index-tmp, artifact.jar")
                .hasMessageContaining("wrote into a folder it was handed");
    }

    @Test
    public void publishes_jmod_alongside_assigned_jar() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("module/foo", "");
        properties.store(argument.resolve(BuildStep.IDENTITY));
        Path jar = Files.writeString(
                Files.createDirectory(argument.resolve(BuildStep.ARTIFACTS)).resolve("classes.jar"), "classes");
        Path jmod = Files.writeString(
                Files.createDirectory(argument.resolve("jmods")).resolve("foo.jmod"), "jmod");
        BuildStepResult result = new Assign().apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                                argument,
                                Map.of(
                                        Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.ARTIFACTS + "classes.jar"), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of("jmods/foo.jmod"), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        assertThat(result.next()).isTrue();
        SequencedProperties coordinates = SequencedProperties.ofFiles(next.resolve(BuildStep.IDENTITY));
        assertThat(coordinates.getProperty("module/foo"))
                .isEqualTo(next.relativize(jar).toString().replace(File.separatorChar, '/'));
        assertThat(coordinates.getProperty("module/foo:jmod"))
                .isEqualTo(next.relativize(jmod).toString().replace(File.separatorChar, '/'));
    }

    @Test
    public void can_assign_dependencies() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("foo", "");
        Path passthroughArtifact = argument.resolve("passthrough.jar");
        Files.writeString(passthroughArtifact, "other");
        properties.setProperty("bar", argument.relativize(passthroughArtifact).toString());
        properties.store(argument.resolve(BuildStep.IDENTITY));
        Files.writeString(Files.createDirectory(argument.resolve(BuildStep.ARTIFACTS)).resolve("artifact"), "baz");
        BuildStepResult result = new Assign().assigner((coordinates, files) -> {
            assertThat(coordinates).containsExactly("foo");
            assertThat(files).containsExactly(argument.resolve(BuildStep.ARTIFACTS).resolve("artifact"));
            return Map.of(
                    "foo", argument.resolve(BuildStep.ARTIFACTS).resolve("artifact"),
                    "qux", argument.resolve(BuildStep.ARTIFACTS).resolve("artifact"));
        }).apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                                argument,
                                Map.of(
                                        Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.ARTIFACTS + "foo"), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        assertThat(result.next()).isTrue();
        SequencedProperties coordinates = SequencedProperties.ofFiles(next.resolve(BuildStep.IDENTITY));
        Path artifact = argument.resolve(BuildStep.ARTIFACTS).resolve("artifact");
        assertThat(coordinates).containsExactly(
                Map.entry("foo", next.relativize(artifact).toString().replace(File.separatorChar, '/')),
                Map.entry("bar", next.relativize(passthroughArtifact).toString().replace(File.separatorChar, '/')),
                Map.entry("qux", next.relativize(artifact).toString().replace(File.separatorChar, '/')));
        coordinates.stringPropertyNames().forEach(name ->
                assertThat(coordinates.getProperty(name)).doesNotStartWith("/"));
    }

    @Test
    public void assigns_each_classifier_beside_every_coordinate_of_the_jar() throws IOException {
        SequencedProperties properties = new SequencedProperties();
        properties.setProperty("maven/org.example/native/1.0", "");
        properties.setProperty("module/org.example.natives", "");
        properties.store(argument.resolve(BuildStep.IDENTITY));
        Files.writeString(Files.createDirectory(argument.resolve(BuildStep.ARTIFACTS)).resolve("native.jar"), "jar");
        Path classifiers = Files.createDirectory(argument.resolve(Classifiers.CLASSIFIERS));
        Path variant = Files.writeString(classifiers.resolve("any-name.jar"), "variant");
        SequencedProperties classified = new SequencedProperties();
        classified.setProperty("linux", Classifiers.CLASSIFIERS + "any-name.jar");
        classified.store(argument.resolve(Classifiers.CLASSIFIED));
        SequencedProperties dormant = new SequencedProperties();
        dormant.setProperty("windows", "platform-w");
        dormant.store(argument.resolve(Classifiers.DORMANT));
        new Assign().apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                                argument,
                                Map.of(Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        String located = next.relativize(variant).toString().replace(File.separatorChar, '/');
        assertThat(SequencedProperties.ofFiles(next.resolve(BuildStep.IDENTITY)))
                .as("a variant is published under the classifier of every coordinate the jar is")
                .containsEntry("maven/org.example/native/jar/linux/1.0", located)
                .containsEntry("module/org.example.natives-linux", located);
        assertThat(SequencedProperties.ofFiles(next.resolve(Assign.VARIANTS)))
                .containsEntry("maven/org.example/native/jar/linux/1.0", "maven/org.example/native/1.0")
                .containsEntry("module/org.example.natives-linux", "module/org.example.natives");
        assertThat(SequencedProperties.ofFiles(next.resolve(Assign.INACTIVE)))
                .as("a classifier only an inactive profile declares is remembered with the profile that does")
                .containsEntry("maven/org.example/native/jar/windows/1.0", "platform-w")
                .containsEntry("module/org.example.natives-windows", "platform-w");
    }

    @Test
    public void derives_a_classifier_coordinate_and_back() {
        assertThat(Assign.classified("maven/org.example/native/1.0", "linux"))
                .isEqualTo("maven/org.example/native/jar/linux/1.0");
        assertThat(Assign.classified("module/org.example.natives", "linux")).isEqualTo("module/org.example.natives-linux");
        assertThat(Assign.classified("maven/org.example/native/jar/tests/1.0", "linux"))
                .as("a coordinate that is a classifier already has none of its own")
                .isNull();
        assertThat(Assign.unclassified("maven/org.example/native/jar/linux/1.0")).isEqualTo("maven/org.example/native/1.0");
        assertThat(Assign.unclassified("module/org.example.natives-linux")).isEqualTo("module/org.example.natives");
        assertThat(Assign.unclassified("maven/org.example/native/1.0")).isNull();
    }
}
