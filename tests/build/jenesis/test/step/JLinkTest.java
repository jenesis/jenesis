package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.JLink;
import build.jenesis.step.Layers;

import static org.assertj.core.api.Assertions.assertThat;

public class JLinkTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, bundle;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        bundle = Files.createDirectory(root.resolve("bundle"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void can_execute_jlink(boolean process) throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { }\n");
        Path classes = Files.createDirectory(root.resolve("classes"));
        int compiled = ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString());
        assertThat(compiled).isZero();
        Path artifacts = Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS));
        int archived = ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", artifacts.resolve("sample.jar").toString(),
                "-C", classes.toString(), ".");
        assertThat(archived).isZero();
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--add-modules", "sample");
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jlink.properties"));
        BuildStepResult result = new JLink(process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("process/jlink.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(JLink.RUNTIME + "release")).isRegularFile();
        assertThat(Files.exists(supplement.resolve("jlink.args")))
                .as("only a forked jlink reads its arguments from a file, as its tool refuses one")
                .isEqualTo(process);
    }

    @Test
    public void links_the_platform_modules_a_layer_requires_but_not_the_layer() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Path classes = Files.createDirectory(root.resolve("classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(), sources.resolve("module-info.java").toString())).isZero();
        Path artifacts = Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS));
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", artifacts.resolve("sample.jar").toString(), "-C", classes.toString(), ".")).isZero();
        Path layered = Files.createDirectory(root.resolve("layered"));
        Files.writeString(layered.resolve("module-info.java"), "module layered { requires java.logging; }\n");
        Path layeredClasses = Files.createDirectory(root.resolve("layered-classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", layeredClasses.toString(), layered.resolve("module-info.java").toString())).isZero();
        Path resolved = Files.createDirectory(bundle.resolve("resolved"));
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", resolved.resolve("layered.jar").toString(), "-C", layeredClasses.toString(), ".")).isZero();
        SequencedProperties index = new SequencedProperties();
        index.setProperty("layer:render/runtime/module/layered", "resolved/layered.jar");
        index.store(bundle.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties membership = new SequencedProperties();
        membership.setProperty("modulepath.render", "layered.jar");
        membership.store(bundle.resolve(Layers.MEMBERSHIP));
        SequencedProperties configuration = new SequencedProperties();
        configuration.setProperty("--add-modules", "sample");
        configuration.store(Files.createDirectory(bundle.resolve("process")).resolve("jlink.properties"));
        BuildStepResult result = new JLink(ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("process/jlink.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(SequencedProperties.ofFiles(next.resolve(JLink.RUNTIME + "release")).value("MODULES"))
                .as("the layer is loaded from its jars at run time, but what it requires of the platform is linked")
                .contains("java.logging")
                .doesNotContain("layered");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void skips_when_no_add_modules_is_configured(boolean process) throws IOException {
        Files.writeString(Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar"), "jar");
        BuildStepResult result = new JLink(process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(JLink.RUNTIME)).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void skips_when_no_modules_are_present(boolean process) throws IOException {
        BuildStepResult result = new JLink(process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("artifacts", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("metadata.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(JLink.RUNTIME)).doesNotExist();
    }
}
