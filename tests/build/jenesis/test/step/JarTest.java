package build.jenesis.test.step;

import module java.base;
import java.util.jar.Attributes;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Environment;
import build.jenesis.step.Jar;
import build.jenesis.step.Javac;
import build.jenesis.step.Javadoc;
import build.jenesis.step.ProcessHandler;
import sample.Sample;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JarTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, classes;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        classes = Files.createDirectory(root.resolve("classes"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void can_execute_classes_jar(boolean process) throws IOException {
        Path folder = Files.createDirectory(classes.resolve(Javac.CLASSES));
        try (InputStream inputStream = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(inputStream), Files
                    .createDirectory(folder.resolve("sample"))
                    .resolve("Sample.class"));
        }
        BuildStepResult result = Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        classes,
                        Map.of(Path.of("sample/Sample.class"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(BuildStep.ARTIFACTS + "classes.jar")).isNotEmptyFile();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "-", value = {
            "CLASSES | modular=true;module=demo.app | version=1.0         | demo.app-1.0.jar",
            "SOURCES | modular=true;module=demo.app | version=1.0         | demo.app-1.0-sources.jar",
            "JAVADOC | modular=true;module=demo.app | -                   | demo.app-javadoc.jar",
            "CLASSES | modular=true;module=demo.app | project=demo        | demo.app-0-SNAPSHOT.jar",
            "CLASSES | modular=false                | project=g;artifact=a;version=1 | g%2Fa%2F1.jar",
            "SOURCES | modular=false;test=a         | project=g;artifact=a;version=1 | g%2Fa%2F1-tests-sources.jar",
            "CLASSES | -                            | -                   | classes.jar"})
    public void names_a_jar_as_a_dependency_on_it_is_named(Jar.Sort sort, String module, String metadata, String name)
            throws IOException {
        if (module != null) {
            Files.writeString(classes.resolve(BuildStep.MODULE), module.replace(';', '\n'));
        }
        if (metadata != null) {
            Files.writeString(classes.resolve(BuildStep.METADATA), metadata.replace(';', '\n'));
        }
        assertThat(sort.file(new LinkedHashMap<>(Map.of("manifests", new BuildStepArgument(classes, Map.of())))))
                .isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void can_execute_sources_jar(boolean process) throws IOException {
        Path folder = Files.createDirectory(classes.resolve(Javac.SOURCES));
        Files.writeString(Files
                .createDirectory(folder.resolve("sample"))
                .resolve("Sample.java"), """
                package sample;
                public class Sample {
                    public static void main(String[] args) {
                        System.out.print("Hello world!");
                    }
                }
                """);
        BuildStepResult result = Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.SOURCES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        classes,
                        Map.of(Path.of("sample/Sample.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(BuildStep.SOURCES + "sources.jar")).isNotEmptyFile();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void can_execute_javadoc_jar(boolean process) throws IOException {
        Path folder = Files.createDirectory(classes.resolve(Javadoc.JAVADOC));
        Files.writeString(Files
                .createDirectory(folder.resolve("sample"))
                .resolve("Sample.html"), """
                <html>
                  <p>This is a javadoc.</p>
                </html>
                """);
        BuildStepResult result = Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.JAVADOC).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        classes,
                        Map.of(Path.of("sample/Sample.html"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(BuildStep.DOCUMENTATION + "javadoc.jar")).isNotEmptyFile();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void classes_jar_includes_multi_release_manifest_when_supplied(boolean process) throws IOException {
        Path folder = Files.createDirectory(classes.resolve(Javac.CLASSES));
        try (InputStream inputStream = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(inputStream), Files
                    .createDirectory(folder.resolve("sample"))
                    .resolve("Sample.class"));
        }
        Files.writeString(classes.resolve("manifest.mf"), "Manifest-Version: 1.0\r\nMulti-Release: true\r\n");
        BuildStepResult result = Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        classes,
                        Map.of(Path.of("sample/Sample.class"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("manifest.mf"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        Path jar = next.resolve(BuildStep.ARTIFACTS + "classes.jar");
        assertThat(jar).isNotEmptyFile();
        assertThat(supplement.resolve("manifest.mf"))
                .as("merged manifest is staged into supplement, not the step output")
                .isRegularFile();
        assertThat(next.resolve("manifest.mf")).doesNotExist();
        try (JarInputStream jarStream = new JarInputStream(Files.newInputStream(jar))) {
            assertThat(jarStream.getManifest().getMainAttributes().getValue(Attributes.Name.MULTI_RELEASE))
                    .isEqualTo("true");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void classes_jar_merges_manifests_from_multiple_predecessors(boolean process) throws IOException {
        Path firstFolder = Files.createDirectory(classes.resolve(Javac.CLASSES));
        try (InputStream inputStream = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(inputStream), Files
                    .createDirectory(firstFolder.resolve("sample"))
                    .resolve("Sample.class"));
        }
        Files.writeString(classes.resolve("manifest.mf"), "Manifest-Version: 1.0\r\nMulti-Release: true\r\n");
        Path second = Files.createDirectory(root.resolve("second"));
        Files.writeString(second.resolve("manifest.mf"),
                "Manifest-Version: 1.0\r\nMain-Class: sample.Sample\r\nImplementation-Title: example\r\n");
        BuildStepResult result = Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of(
                        "classes", new BuildStepArgument(
                                classes,
                                Map.of(Path.of("sample/Sample.class"), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of("manifest.mf"), Checksum.of(ChecksumStatus.ADDED))),
                        "second", new BuildStepArgument(
                                second,
                                Map.of(Path.of("manifest.mf"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        try (JarInputStream jarStream = new JarInputStream(Files.newInputStream(
                next.resolve(BuildStep.ARTIFACTS + "classes.jar")))) {
            Attributes attributes = jarStream.getManifest().getMainAttributes();
            assertThat(attributes.getValue(Attributes.Name.MULTI_RELEASE)).isEqualTo("true");
            assertThat(attributes.getValue(Attributes.Name.MAIN_CLASS)).isEqualTo("sample.Sample");
            assertThat(attributes.getValue("Implementation-Title")).isEqualTo("example");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void identical_manifest_values_across_predecessors_do_not_conflict(boolean process) throws IOException {
        Path firstFolder = Files.createDirectory(classes.resolve(Javac.CLASSES));
        try (InputStream inputStream = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(inputStream), Files
                    .createDirectory(firstFolder.resolve("sample"))
                    .resolve("Sample.class"));
        }
        Files.writeString(classes.resolve("manifest.mf"), "Manifest-Version: 1.0\r\nMulti-Release: true\r\n");
        Path second = Files.createDirectory(root.resolve("second"));
        Files.writeString(second.resolve("manifest.mf"), "Manifest-Version: 1.0\r\nMulti-Release: true\r\n");
        BuildStepResult result = Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of(
                        "classes", new BuildStepArgument(
                                classes,
                                Map.of(Path.of("sample/Sample.class"), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of("manifest.mf"), Checksum.of(ChecksumStatus.ADDED))),
                        "second", new BuildStepArgument(
                                second,
                                Map.of(Path.of("manifest.mf"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
    }

    @Test
    public void conflicting_manifest_attributes_across_predecessors_throw() throws IOException {
        Files.writeString(classes.resolve("manifest.mf"), "Manifest-Version: 1.0\r\nMain-Class: a.A\r\n");
        Path second = Files.createDirectory(root.resolve("second"));
        Files.writeString(second.resolve("manifest.mf"), "Manifest-Version: 1.0\r\nMain-Class: b.B\r\n");
        LinkedHashMap<String, BuildStepArgument> args = new LinkedHashMap<>();
        args.put("classes", new BuildStepArgument(classes, Map.of(Path.of("manifest.mf"), Checksum.of(ChecksumStatus.ADDED))));
        args.put("second", new BuildStepArgument(second, Map.of(Path.of("manifest.mf"), Checksum.of(ChecksumStatus.ADDED))));
        assertThatThrownBy(() -> Jar.ofEnvironment(Environment.NONE, ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                args).toCompletableFuture().join())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Conflicting manifest attribute 'Main-Class' in "
                        + second.resolve("manifest.mf")
                        + ": 'a.A' vs 'b.B'");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void produces_deterministic_output(boolean process) throws IOException {
        Path folder = Files.createDirectory(classes.resolve(Javac.CLASSES));
        try (InputStream inputStream = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(inputStream), Files
                    .createDirectory(folder.resolve("sample"))
                    .resolve("Sample.class"));
        }
        Path firstNext = Files.createDirectory(root.resolve("first"));
        Path secondNext = Files.createDirectory(root.resolve("second"));
        BuildStepArgument argument = new BuildStepArgument(
                classes,
                Map.of(Path.of("sample/Sample.class"), Checksum.of(ChecksumStatus.ADDED)));
        Jar jar = Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES);
        jar.apply(Runnable::run,
                new BuildStepContext(previous, firstNext, supplement),
                new LinkedHashMap<>(Map.of("sources", argument))).toCompletableFuture().join();
        jar.apply(Runnable::run,
                new BuildStepContext(previous, secondNext, supplement),
                new LinkedHashMap<>(Map.of("sources", argument))).toCompletableFuture().join();
        assertThat(Files.readAllBytes(firstNext.resolve(BuildStep.ARTIFACTS + "classes.jar")))
                .isEqualTo(Files.readAllBytes(secondNext.resolve(BuildStep.ARTIFACTS + "classes.jar")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void stamps_every_entry_with_the_archive_timestamp(boolean process) throws IOException {
        Path folder = Files.createDirectory(classes.resolve(Javac.CLASSES));
        try (InputStream inputStream = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(inputStream), Files
                    .createDirectory(folder.resolve("sample"))
                    .resolve("Sample.class"));
        }
        Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        classes,
                        Map.of(Path.of("classes/sample/Sample.class"), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture().join();
        try (ZipFile jar = new ZipFile(next.resolve(BuildStep.ARTIFACTS + "classes.jar").toFile())) {
            assertThat(jar.stream().map(ZipEntry::getTimeLocal))
                    .as("the jar tool records the same time the build's own archive writers use")
                    .containsOnly(BuildStep.timestamp(Environment.NONE).toLocalDateTime());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void passes_no_date_when_the_archive_timestamp_is_empty(boolean process) throws IOException {
        Files.createDirectory(classes.resolve(Javac.CLASSES));
        Jar jar;
        jar = Jar.ofEnvironment(new Environment(Map.of("archive.timestamp", "")), process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES);
        jar.apply(Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(classes, Map.of()))))
                .toCompletableFuture().join();
        assertThat(supplement.resolve("command")).content()
                .as("without a date the jar tool records when each file was last modified")
                .doesNotContain("--date");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void records_the_tool_rather_than_the_jdk_that_ran_it(boolean process) throws IOException {
        Files.createDirectory(classes.resolve(Javac.CLASSES));
        Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(classes, Map.of()))))
                .toCompletableFuture().join();

        assertThat(mainAttributes().getValue("Created-By"))
                .as("a jar that records the running JDK cannot be reproduced on another one")
                .isEqualTo("Jenesis");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void a_declared_created_by_is_left_alone(boolean process) throws IOException {
        Files.createDirectory(classes.resolve(Javac.CLASSES));
        Files.writeString(classes.resolve("manifest.mf"), """
                Manifest-Version: 1.0
                Created-By: Something Else
                """);
        Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(classes, Map.of()))))
                .toCompletableFuture().join();

        assertThat(mainAttributes().getValue("Created-By")).isEqualTo("Something Else");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void a_manifest_among_the_resources_is_the_basis_of_the_jar_manifest(boolean process) throws IOException {
        Files.createDirectory(classes.resolve(Javac.CLASSES));
        Files.writeString(Files.createDirectories(classes.resolve("resources/META-INF")).resolve("MANIFEST.MF"), """
                Manifest-Version: 1.0
                Automatic-Module-Name: sample.library
                Created-By: Maven JAR Plugin
                """);
        Files.writeString(classes.resolve("manifest.mf"), """
                Manifest-Version: 1.0
                Sbom-Format: CycloneDX
                """);
        Jar.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(classes, Map.of()))))
                .toCompletableFuture().join();

        Attributes attributes = mainAttributes();
        assertThat(attributes.getValue("Automatic-Module-Name")).isEqualTo("sample.library");
        assertThat(attributes.getValue("Sbom-Format"))
                .as("what the build writes is merged over the project's manifest")
                .isEqualTo("CycloneDX");
        assertThat(attributes.getValue("Created-By")).isEqualTo("Maven JAR Plugin");
        try (JarFile jar = new JarFile(next.resolve(BuildStep.ARTIFACTS + "classes.jar").toFile())) {
            assertThat(jar.stream().filter(entry -> entry.getName().equalsIgnoreCase(JarFile.MANIFEST_NAME)))
                    .as("the project's manifest is not added a second time beside the merged one")
                    .hasSize(1);
        }
    }

    @Test
    public void a_manifest_among_the_resources_that_contradicts_the_build_is_refused() throws IOException {
        Files.createDirectory(classes.resolve(Javac.CLASSES));
        Path manifest = Files.createDirectories(classes.resolve("resources/META-INF")).resolve("MANIFEST.MF");
        Files.writeString(manifest, "Manifest-Version: 1.0\nMulti-Release: false\n");
        Files.writeString(classes.resolve("manifest.mf"), "Manifest-Version: 1.0\nMulti-Release: true\n");
        assertThatThrownBy(() -> Jar.ofEnvironment(Environment.NONE, ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(classes, Map.of()))))
                .toCompletableFuture().join())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Conflicting manifest attribute 'Multi-Release' in "
                        + classes.resolve("manifest.mf")
                        + ": 'false' vs 'true'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"--manifest", "-m"})
    public void refuses_a_manifest_option_in_the_process_file(String option) throws IOException {
        Files.createDirectory(classes.resolve(Javac.CLASSES));
        Files.writeString(Files.createDirectory(classes.resolve("process")).resolve("jar.properties"),
                option + "=extra.mf\n");
        assertThatThrownBy(() -> Jar.ofEnvironment(Environment.NONE, ProcessHandler.Factory.TOOL, Jar.Sort.CLASSES).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(classes, Map.of()))))
                .toCompletableFuture().join())
                .as("a manifest named on the command line would silently replace the one the step merges")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("process-jar.properties sets " + option)
                .hasMessageContaining("META-INF/MANIFEST.MF among the module's resources");
    }

    private Attributes mainAttributes() throws IOException {
        try (JarFile jar = new JarFile(next.resolve(BuildStep.ARTIFACTS + "classes.jar").toFile())) {
            return jar.getManifest().getMainAttributes();
        }
    }
}
