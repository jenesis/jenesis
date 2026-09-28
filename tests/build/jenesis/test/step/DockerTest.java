package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Environment;
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
        assertThat(folder.resolve("jars/app.jar")).isRegularFile();
        assertThat(folder.resolve("jars/lib.jar")).isRegularFile();
        assertThat(dockerfile(folder)).containsSubsequence(
                "FROM example:latest",
                "WORKDIR /app",
                "COPY jars/ /app/jars/",
                "COPY application.args /app/",
                "ENTRYPOINT [\"java\", \"@/app/application.args\"]");
        assertThat(arguments(folder))
                .as("the entry point names an argument file, so no path can outgrow the command line")
                .containsExactly(
                        "--class-path", "/app/jars/app.jar:/app/jars/lib.jar:/app/extensions/classpath/*",
                        "--module-path", "/app/extensions/modulepath",
                        "sample.Sample");
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
        assertThat(folder.resolve("jars/sample.jar")).isRegularFile();
        assertThat(dockerfile(folder)).containsSubsequence(
                "FROM example:latest",
                "WORKDIR /app",
                "COPY jars/ /app/jars/",
                "COPY application.args /app/",
                "ENTRYPOINT [\"java\", \"@/app/application.args\"]");
        assertThat(arguments(folder))
                .as("an image built from this one adds a jar by copying it into a folder under /app/extensions")
                .containsExactly(
                        "--class-path", "/app/extensions/classpath/*",
                        "--module-path", "/app/jars/sample.jar:/app/extensions/modulepath",
                        "--module", "sample/sample.Sample");
    }

    @Test
    public void labels_the_image_with_the_metadata_of_the_module_and_the_configured_labels() throws IOException {
        writeModularJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar"));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.setProperty("mainModule", "sample");
        launcher.store(input.resolve("launcher.properties"));
        SequencedProperties metadata = new SequencedProperties();
        metadata.setProperty("project", "com.example");
        metadata.setProperty("artifact", "sample");
        metadata.setProperty("name", "Sample");
        metadata.setProperty("version", "1.0");
        metadata.setProperty("description", "A sample\n   application");
        metadata.setProperty("url", "https://example.com");
        metadata.setProperty("scm.url", "https://example.com/sample.git");
        metadata.setProperty("scm.revision", "0123abc");
        metadata.setProperty("organization.name", "Example Corp.");
        metadata.setProperty("developer.0.name", "Ada Lovelace");
        metadata.setProperty("developer.0.email", "ada@example.com");
        metadata.setProperty("developer.1.name", "Charles Babbage");
        metadata.setProperty("license.0.name", "Apache License, Version 2.0");
        metadata.store(input.resolve(BuildStep.METADATA));
        SequencedMap<String, String> labels = new LinkedHashMap<>();
        labels.put("com.example.team", "core \"$HOME\" \\");
        labels.put("org.opencontainers.image.url", "");
        labels.put("org.opencontainers.image.version", "1.0-custom");

        BuildStepResult result = new Docker("example:latest").labels(labels).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of(BuildStep.METADATA), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        assertThat(dockerfile(next.resolve(Docker.DOCKER)))
                .as("every standard label is written, so that none is inherited from the base image,"
                        + " and a configured label replaces the one of the same name")
                .containsExactly(
                        "FROM example:latest",
                        "LABEL \"org.opencontainers.image.base.name\"=\"example:latest\" \\",
                        "      \"org.opencontainers.image.base.digest\"=\"\" \\",
                        "      \"org.opencontainers.image.title\"=\"Sample\" \\",
                        "      \"org.opencontainers.image.description\"=\"A sample application\" \\",
                        "      \"org.opencontainers.image.version\"=\"1.0-custom\" \\",
                        "      \"org.opencontainers.image.created\"=\"\" \\",
                        "      \"org.opencontainers.image.authors\"=\"Ada Lovelace <ada@example.com>, Charles Babbage\" \\",
                        "      \"org.opencontainers.image.url\"=\"\" \\",
                        "      \"org.opencontainers.image.documentation\"=\"\" \\",
                        "      \"org.opencontainers.image.source\"=\"https://example.com/sample.git\" \\",
                        "      \"org.opencontainers.image.revision\"=\"0123abc\" \\",
                        "      \"org.opencontainers.image.vendor\"=\"Example Corp.\" \\",
                        "      \"org.opencontainers.image.licenses\"=\"Apache-2.0\" \\",
                        "      \"org.opencontainers.image.ref.name\"=\"\" \\",
                        "      \"com.example.team\"=\"core \\\"\\$HOME\\\" \\\\\"",
                        "WORKDIR /app",
                        "COPY jars/ /app/jars/",
                        "COPY application.args /app/",
                        "ENTRYPOINT [\"java\", \"@/app/application.args\"]");
    }

    @Test
    public void labels_the_creation_time_only_when_the_archive_timestamp_is_set_explicitly() throws IOException {
        writeModularJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar"));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.setProperty("mainModule", "sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = Docker.ofEnvironment(
                new Environment(Map.of("archive.timestamp", "2026-01-01T00:00:00+01:00")),
                "example:latest").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        assertThat(dockerfile(next.resolve(Docker.DOCKER)))
                .as("the creation time is written as an instant in UTC, as the OCI annotation expects")
                .contains("      \"org.opencontainers.image.created\"=\"2025-12-31T23:00:00Z\" \\");
    }

    @Test
    public void suppresses_the_creation_time_by_an_empty_label() throws IOException {
        writeModularJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar"));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.setProperty("mainModule", "sample");
        launcher.store(input.resolve("launcher.properties"));
        SequencedMap<String, String> labels = new LinkedHashMap<>();
        labels.put("org.opencontainers.image.created", "");

        BuildStepResult result = Docker.ofEnvironment(
                new Environment(Map.of("archive.timestamp", "2026-01-01T00:00:00Z")),
                "example:latest").labels(labels).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("artifacts/sample.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("launcher.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        assertThat(dockerfile(next.resolve(Docker.DOCKER)))
                .as("an empty label stays written, so the base image's creation time is not inherited either")
                .contains("      \"org.opencontainers.image.created\"=\"\" \\");
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
        assertThat(folder.resolve("jars/sample.jar")).isRegularFile();
        assertThat(folder.resolve("jars/lib.jar")).isRegularFile();
        assertThat(arguments(folder)).containsExactly(
                "--class-path", "/app/jars/lib.jar:/app/extensions/classpath/*",
                "--module-path", "/app/jars/sample.jar:/app/extensions/modulepath",
                "--add-modules", "ALL-MODULE-PATH,ALL-DEFAULT",
                "--module", "sample/sample.Sample");
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

    private static List<String> arguments(Path folder) throws IOException {
        return Files.readAllLines(folder.resolve("application.args")).stream()
                .map(line -> line.substring(1, line.length() - 1))
                .toList();
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
