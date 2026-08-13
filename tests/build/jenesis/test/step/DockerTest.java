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
import build.jenesis.step.Docker;

import static org.assertj.core.api.Assertions.assertThat;

public class DockerTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, input;

    @BeforeEach
    public void setUp() throws IOException {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        input = Files.createDirectory(root.resolve("input"));
    }

    @Test
    public void copies_a_non_modular_main_onto_the_class_path() throws IOException {
        writePlainJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"));
        writePlainJar(Files.createDirectory(input.resolve("resolved")).resolve("lib.jar"));
        SequencedProperties index = new SequencedProperties();
        index.setProperty("main/runtime/maven/lib", "resolved/lib.jar");
        index.store(input.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = new Docker("example:latest").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("resolved/lib.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path folder = next.resolve(Docker.DOCKER);
        assertThat(folder.resolve("classpath/app.jar")).isRegularFile();
        assertThat(folder.resolve("classpath/lib.jar")).isRegularFile();
        assertThat(folder.resolve("modulepath")).doesNotExist();
        assertThat(dockerfile(folder)).containsExactly(
                "FROM example:latest",
                "WORKDIR /app",
                "COPY classpath/ /app/classpath/",
                "ENTRYPOINT [\"java\", \"--class-path\", \"/app/classpath/*\", \"sample.Sample\"]");
    }

    @Test
    public void copies_a_modular_main_onto_the_module_path() throws IOException {
        writeModularJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar"));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.setProperty("mainModule", "sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = new Docker("example:latest").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path folder = next.resolve(Docker.DOCKER);
        assertThat(folder.resolve("modulepath/sample.jar")).isRegularFile();
        assertThat(folder.resolve("classpath")).doesNotExist();
        assertThat(dockerfile(folder)).containsExactly(
                "FROM example:latest",
                "WORKDIR /app",
                "COPY modulepath/ /app/modulepath/",
                "ENTRYPOINT [\"java\", \"--module-path\", \"/app/modulepath\", \"--module\", \"sample/sample.Sample\"]");
    }

    @Test
    public void relaxes_the_graph_when_a_class_path_jar_is_present() throws IOException {
        Path artifacts = Files.createDirectory(input.resolve(BuildStep.ARTIFACTS));
        writeModularJar(artifacts.resolve("sample.jar"));
        writePlainJar(artifacts.resolve("lib.jar"));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.setProperty("mainModule", "sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = new Docker("example:latest").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("artifacts/lib.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path folder = next.resolve(Docker.DOCKER);
        assertThat(folder.resolve("modulepath/sample.jar")).isRegularFile();
        assertThat(folder.resolve("classpath/lib.jar")).isRegularFile();
        assertThat(dockerfile(folder)).contains(
                "ENTRYPOINT [\"java\", \"--class-path\", \"/app/classpath/*\", \"--module-path\", \"/app/modulepath\", "
                        + "\"--add-modules\", \"ALL-MODULE-PATH,ALL-DEFAULT\", "
                        + "\"--module\", \"sample/sample.Sample\"]");
    }

    @Test
    public void skips_a_module_without_a_main() throws IOException {
        writePlainJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"));

        BuildStepResult result = new Docker("example:latest").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/app.jar"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Docker.DOCKER)).doesNotExist();
    }

    private static List<String> dockerfile(Path folder) throws IOException {
        return Files.readAllLines(folder.resolve("Dockerfile"));
    }

    private static void writePlainJar(Path path) throws IOException {
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry("sample/Sample.class"));
            jar.write(new byte[] {1, 2, 3});
            jar.closeEntry();
        }
    }

    private void writeModularJar(Path path) throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { public static void main(String[] args) { } }\n");
        Path classes = Files.createDirectory(root.resolve("classes"));
        if (ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString()) != 0) {
            throw new IllegalStateException("Failed to compile the sample module");
        }
        if (ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", path.toString(),
                "-C", classes.toString(), ".") != 0) {
            throw new IllegalStateException("Failed to archive the sample module");
        }
    }
}
