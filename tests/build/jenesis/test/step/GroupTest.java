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
import build.jenesis.step.Group;

import static org.assertj.core.api.Assertions.assertThat;

public class GroupTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, left, right;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        left = Files.createDirectory(root.resolve("left"));
        right = Files.createDirectory(root.resolve("right"));
    }

    @Test
    public void can_link_related_groups() throws IOException {
        SequencedProperties leftCoordinates = new SequencedProperties(), rightCoordinates = new SequencedProperties();
        leftCoordinates.setProperty("foo", "");
        rightCoordinates.setProperty("bar", "");
        leftCoordinates.store(left.resolve(BuildStep.IDENTITY));
        rightCoordinates.store(right.resolve(BuildStep.IDENTITY));
        SequencedProperties leftDependencies = new SequencedProperties(), rightDependencies = new SequencedProperties();
        leftDependencies.setProperty("qux", "");
        rightDependencies.setProperty("foo", "");
        rightDependencies.setProperty("baz", "");
        leftDependencies.store(left.resolve(BuildStep.REQUIRES));
        rightDependencies.store(right.resolve(BuildStep.REQUIRES));
        BuildStepResult result = new Group(Optional::of).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of(
                        "left", new BuildStepArgument(
                                left,
                                Map.of(
                                        Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.REQUIRES), Checksum.of(ChecksumStatus.ADDED))),
                        "right", new BuildStepArgument(
                                right,
                                Map.of(
                                        Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.REQUIRES), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        List<String> leftGroup = SequencedProperties.ofFiles(next.resolve(Group.GROUPS)).entries("left");
        List<String> rightGroup = SequencedProperties.ofFiles(next.resolve(Group.GROUPS)).entries("right");
        assertThat(leftGroup).isNull();
        assertThat(rightGroup).containsExactly("left");
        assertThat(result.next()).isTrue();
    }

    @Test
    public void does_not_link_unrelated_groups() throws IOException {
        SequencedProperties leftCoordinates = new SequencedProperties(), rightCoordinates = new SequencedProperties();
        leftCoordinates.setProperty("foo", "");
        rightCoordinates.setProperty("bar", "");
        leftCoordinates.store(left.resolve(BuildStep.IDENTITY));
        rightCoordinates.store(right.resolve(BuildStep.IDENTITY));
        SequencedProperties leftDependencies = new SequencedProperties(), rightDependencies = new SequencedProperties();
        leftDependencies.setProperty("qux", "");
        rightDependencies.setProperty("baz", "");
        leftDependencies.store(left.resolve(BuildStep.REQUIRES));
        rightDependencies.store(right.resolve(BuildStep.REQUIRES));
        BuildStepResult result = new Group(Optional::of).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of(
                        "left", new BuildStepArgument(
                                left,
                                Map.of(
                                        Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.REQUIRES), Checksum.of(ChecksumStatus.ADDED))),
                        "right", new BuildStepArgument(
                                right,
                                Map.of(
                                        Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.REQUIRES), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        List<String> leftGroup = SequencedProperties.ofFiles(next.resolve(Group.GROUPS)).entries("left");
        List<String> rightGroup = SequencedProperties.ofFiles(next.resolve(Group.GROUPS)).entries("right");
        assertThat(leftGroup).isNull();
        assertThat(rightGroup).isNull();
        assertThat(result.next()).isTrue();
    }

    @Test
    public void does_not_link_self_referencing_group() throws IOException {
        SequencedProperties leftCoordinates = new SequencedProperties();
        leftCoordinates.setProperty("foo", "");
        leftCoordinates.store(left.resolve(BuildStep.IDENTITY));
        SequencedProperties leftDependencies = new SequencedProperties();
        leftDependencies.setProperty("foo", "");
        leftDependencies.store(left.resolve(BuildStep.REQUIRES));
        BuildStepResult result = new Group(Optional::of).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of(
                        "left", new BuildStepArgument(
                                left,
                                Map.of(
                                        Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.REQUIRES), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        List<String> leftGroup = SequencedProperties.ofFiles(next.resolve(Group.GROUPS)).entries("left");
        assertThat(leftGroup).isNull();
        assertThat(result.next()).isTrue();
    }

    @Test
    public void links_a_module_that_requires_a_classifier_of_a_sibling_to_that_sibling() throws IOException {
        SequencedProperties identity = new SequencedProperties(), requires = new SequencedProperties();
        identity.setProperty("maven/org.example/native/1.0", "");
        identity.store(left.resolve(BuildStep.IDENTITY));
        requires.setProperty("main/compile/maven/org.example/native/jar/linux/1.0", "");
        requires.store(right.resolve(BuildStep.REQUIRES));
        new Group(Optional::of).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of(
                        "left", new BuildStepArgument(left, Map.of(Path.of(BuildStep.IDENTITY), Checksum.of(ChecksumStatus.ADDED))),
                        "right", new BuildStepArgument(right, Map.of(Path.of(BuildStep.REQUIRES), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        assertThat(SequencedProperties.ofFiles(next.resolve(Group.GROUPS)).entries("right"))
                .as("a classifier of a module of this build is built by that module")
                .containsExactly("left");
    }
}
