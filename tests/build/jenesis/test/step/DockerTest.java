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
import build.jenesis.step.JPackage;
import build.jenesis.step.ProcessBuildStep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        assertThat(dockerfile(folder))
                .as("the copies create /app owned by root before WORKDIR names it, so an unprivileged user cannot replace what starts")
                .containsSubsequence(
                        "FROM example:latest",
                        "COPY jars/ /app/jars/",
                        "COPY application.args /app/",
                        "WORKDIR /app",
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
        assertThat(dockerfile(folder))
                .as("a module without metadata writes its labels empty, so that the base image's are not inherited")
                .contains("      \"org.opencontainers.image.version\"=\"\" \\",
                        "      \"org.opencontainers.image.created\"=\"\" \\");
        assertThat(dockerfile(folder)).containsSubsequence(
                "FROM example:latest",
                "COPY jars/ /app/jars/",
                "COPY application.args /app/",
                "WORKDIR /app",
                "ENTRYPOINT [\"java\", \"@/app/application.args\"]");
        assertThat(arguments(folder))
                .as("an image built from this one adds a jar by copying it into a folder under /app/extensions")
                .containsExactly(
                        "--class-path", "/app/extensions/classpath/*",
                        "--module-path", "/app/jars/sample.jar:/app/extensions/modulepath",
                        "--module", "sample/sample.Sample");
    }

    @Test
    public void carries_the_java_options_of_the_module_into_the_argument_file_of_the_image() throws IOException {
        writeModularJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("sample.jar"));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.setProperty("mainModule", "sample");
        launcher.store(input.resolve("launcher.properties"));
        SequencedProperties java = new SequencedProperties();
        java.setProperty("--add-reads", "org.hibernate.validator=org.apache.tomcat.embed.el");
        java.setProperty("-Dsample=true", "");
        java.store(Files.createDirectory(input.resolve(ProcessBuildStep.PROCESS)).resolve("java.properties"));

        BuildStepResult result = new Docker("example:latest").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(input, Map.of())))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        assertThat(arguments(next.resolve(Docker.DOCKER)))
                .as("the options every JVM of the module runs with are part of the image's launch, as of a bundle's")
                .containsExactly(
                        "--add-reads", "org.hibernate.validator=org.apache.tomcat.embed.el",
                        "-Dsample=true",
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
                        "COPY jars/ /app/jars/",
                        "COPY application.args /app/",
                        "WORKDIR /app",
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

    @Test
    public void copies_a_jpackage_app_image_in_place_of_the_jars() throws IOException {
        Path image = Files.createDirectories(input.resolve(JPackage.PACKAGES).resolve("sample"));
        Files.writeString(Files.createDirectory(image.resolve("bin")).resolve("sample"), "launcher");
        Path lib = Files.createDirectory(image.resolve("lib"));
        Files.writeString(Files.createDirectory(lib.resolve("app")).resolve("sample.cfg"), "[Application]");
        Files.writeString(Files.createDirectories(lib.resolve("runtime").resolve("bin")).resolve("java"), "runtime");
        writePlainJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"));
        SequencedProperties launcher = new SequencedProperties();
        launcher.setProperty("mainClass", "sample.Sample");
        launcher.store(input.resolve("launcher.properties"));

        BuildStepResult result = new Docker("debian:stable-slim").jpackage("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("packages/sample/bin/sample"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path folder = next.resolve(Docker.DOCKER);
        assertThat(folder.resolve("sample/bin/sample")).hasContent("launcher");
        assertThat(folder.resolve("sample/lib/app/sample.cfg")).isRegularFile();
        assertThat(folder.resolve("sample/lib/runtime/bin/java")).isRegularFile();
        assertThat(folder.resolve("jars"))
                .as("the app-image carries the application and its runtime, so no jar is copied beside it")
                .doesNotExist();
        assertThat(folder.resolve("application.args")).doesNotExist();
        assertThat(dockerfile(folder)).containsSubsequence(
                "FROM debian:stable-slim",
                "COPY [\"sample/\", \"/app/\"]",
                "WORKDIR /app",
                "ENTRYPOINT [\"/app/bin/sample\"]");
    }

    @Test
    public void installs_a_jpackage_debian_package_and_starts_its_launcher() throws IOException {
        Files.writeString(Files.createDirectories(input.resolve(JPackage.PACKAGES)).resolve("sample_1.0_amd64.deb"), "package");
        SequencedProperties jpackage = new SequencedProperties();
        jpackage.setProperty("--name", "sample");
        jpackage.store(Files.createDirectory(input.resolve("process")).resolve("jpackage.properties"));

        BuildStepResult result = new Docker("debian:stable-slim").jpackage("deb").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("packages/sample_1.0_amd64.deb"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        Path folder = next.resolve(Docker.DOCKER);
        assertThat(folder.resolve("sample_1.0_amd64.deb")).hasContent("package");
        assertThat(dockerfile(folder)).containsSubsequence(
                "FROM debian:stable-slim",
                "WORKDIR /app",
                "COPY [\"sample_1.0_amd64.deb\", \"/tmp/jpackage/\"]",
                "RUN apt-get update \\",
                "    && apt-get install -y --no-install-recommends /tmp/jpackage/'sample_1.0_amd64.deb' \\",
                "    && dpkg --listfiles \"$package\" | while IFS= read -r file; do case \"$file\" in */lib/app/'sample'.cfg) \\",
                "       ln -s \"${file%/lib/app/*}/bin/\"'sample' /app/launcher;; esac; done \\",
                "ENTRYPOINT [\"/app/launcher\"]");
    }

    @Test
    public void installs_a_jpackage_rpm_package_with_the_launcher_its_format_names() throws IOException {
        Files.writeString(Files.createDirectories(input.resolve(JPackage.PACKAGES)).resolve("sample-1.0-1.x86_64.rpm"), "package");
        Path process = Files.createDirectory(input.resolve("process"));
        SequencedProperties shared = new SequencedProperties();
        shared.setProperty("--name", "sample");
        shared.store(process.resolve("jpackage.properties"));
        SequencedProperties typed = new SequencedProperties();
        typed.setProperty("--name", "Sample's");
        typed.store(process.resolve("jpackage-rpm.properties"));

        new Docker("fedora:latest").jpackage("rpm").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("packages/sample-1.0-1.x86_64.rpm"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(dockerfile(next.resolve(Docker.DOCKER))).containsSubsequence(
                "COPY [\"sample-1.0-1.x86_64.rpm\", \"/tmp/jpackage/\"]",
                "RUN package=\"$(rpm --query --package --queryformat '%{NAME}' /tmp/jpackage/'sample-1.0-1.x86_64.rpm')\" \\",
                "    && rpm --query --list \"$package\" | while IFS= read -r file; do case \"$file\" in */lib/app/'Sample'\\''s'.cfg) \\",
                "ENTRYPOINT [\"/app/launcher\"]");
    }

    @Test
    public void refuses_a_jpackage_format_that_a_linux_image_cannot_run() {
        assertThatThrownBy(() -> new Docker("debian:stable-slim").jpackage("msi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[app-image, deb, rpm]");
    }

    @Test
    public void refuses_a_jpackage_output_that_is_no_linux_app_image() throws IOException {
        Files.createDirectories(input.resolve(JPackage.PACKAGES).resolve("sample.app").resolve("Contents"));

        assertThatThrownBy(() -> new Docker("debian:stable-slim").jpackage("app-image").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("input", new BuildStepArgument(
                        input,
                        Map.of(Path.of("packages/sample.app/Contents"), Checksum.of(ChecksumStatus.ADDED)))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[sample.app]")
                .hasMessageContaining("-Djenesis.project.docker=true");
    }

    @Test
    public void skips_a_jpackage_image_when_nothing_was_packaged() throws IOException {
        writePlainJar(Files.createDirectory(input.resolve(BuildStep.ARTIFACTS)).resolve("app.jar"));

        BuildStepResult result = new Docker("debian:stable-slim").jpackage("app-image").apply(
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
        if (ToolProvider.findFirst("javac").orElseThrow().run(System.out,
                System.err,
                "-d",
                classes.toString(),
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
