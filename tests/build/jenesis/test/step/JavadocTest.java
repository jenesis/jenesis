package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.params;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Javac;
import build.jenesis.step.Javadoc;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JavadocTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, sources;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        sources = Files.createDirectory(root.resolve("sources"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void can_execute_sources_jar(boolean process) throws IOException {
        Path folder = Files.createDirectory(sources.resolve(Javac.SOURCES));
        Files.writeString(Files
                .createDirectory(folder.resolve("sample"))
                .resolve("Sample.java"), """
                package sample;
                /**
                 * This is a javadoc.
                */
                public class Sample { }
                """);
        BuildStepResult result = Javadoc.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("sample/Sample.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Javadoc.JAVADOC)).isNotEmptyDirectory();
        assertThat(next.resolve(Javadoc.JAVADOC + "sample/Sample.html")).content().contains("This is a javadoc.");
    }

    @Test
    public void documents_sources_that_use_the_preview_features_they_were_compiled_with() throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample")).resolve("Sample.java"), """
                package sample;
                /** Documented. */
                public class Sample {
                    /** Describes a value. */
                    public static String describe(long value) {
                        return switch (value) {
                            case int small -> "int";
                            case long large -> "long";
                        };
                    }
                }
                """);
        Javac.writeRelease(sources, Runtime.version().feature() + "-preview", Runtime.version().feature());
        BuildStepResult result = Javadoc.ofEnvironment(Environment.NONE, ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("sample/Sample.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Javadoc.JAVADOC + "sample/Sample.html")).content().contains("Describes a value.");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void a_configured_doclint_flag_replaces_the_build_default(boolean process) throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample")).resolve("Sample.java"),
                "package sample; /** Links {@link Missing}. */ public class Sample { }\n");
        Files.writeString(Files.createDirectories(sources.resolve("process")).resolve("javadoc.properties"),
                "-Xdoclint\\:all=\n");
        assertThatThrownBy(() -> Javadoc.ofEnvironment(Environment.NONE,
                        process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("sample/Sample.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join())
                .as("doclint reports the broken link as an error once the configuration switches it on")
                .hasMessageContaining("Unexpected exit code");
        assertThat(Files.readString(supplement.resolve("command"))).doesNotContain("-Xdoclint:none");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void an_excluded_package_and_its_subpackages_are_not_documented(boolean process) throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample")).resolve("Sample.java"),
                "package sample; /** Documented. */ public class Sample { }\n");
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample/internal")).resolve("Hidden.java"),
                "package sample.internal; /** Hidden. */ public class Hidden { }\n");
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample/internal/deep")).resolve("Deeper.java"),
                "package sample.internal.deep; /** Hidden. */ public class Deeper { }\n");
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample/internals")).resolve("Kept.java"),
                "package sample.internals; /** Kept. */ public class Kept { }\n");
        Files.writeString(Files.createDirectories(sources.resolve("process")).resolve("javadoc.properties"),
                "-exclude=sample.internal:sample.other\n");
        BuildStepResult result = Javadoc.ofEnvironment(Environment.NONE,
                        process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("sample/Sample.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Javadoc.JAVADOC + "sample/Sample.html")).exists();
        assertThat(next.resolve(Javadoc.JAVADOC + "sample/internals/Kept.html"))
                .as("a package whose name only starts like an excluded one is documented")
                .exists();
        assertThat(next.resolve(Javadoc.JAVADOC + "sample/internal"))
                .as("-exclude leaves out the package and its subpackages, as it does with -subpackages")
                .doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void fails_where_javadoc_reports_an_error(boolean process) throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample")).resolve("Sample.java"),
                "package sample; /** Tagged @unknown. */ public class Sample { /** @custom value */ public void run() { } }\n");
        Files.writeString(Files.createDirectories(sources.resolve("process")).resolve("javadoc.properties"),
                "-Werror=\n");
        assertThatThrownBy(() -> Javadoc.ofEnvironment(Environment.NONE,
                        process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("sample/Sample.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join())
                .as("-Werror turns the warning about an unknown tag into a failure of the build")
                .hasMessageContaining("Unexpected exit code");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void documents_nothing_where_no_type_is_public(boolean process) throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample")).resolve("SampleTest.java"),
                "package sample; class SampleTest { }\n");
        BuildStepResult result = Javadoc.ofEnvironment(Environment.NONE,
                process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("sample/SampleTest.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Javadoc.JAVADOC))
                .as("javadoc documents public and protected types alone, so a module without one has no documentation")
                .isEmptyDirectory();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void documents_a_type_that_is_not_public_where_the_configuration_asks_for_it(boolean process)
            throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample")).resolve("SampleTest.java"),
                "package sample; /** Documented. */ class SampleTest { }\n");
        Files.writeString(Files.createDirectories(sources.resolve("process")).resolve("javadoc.properties"),
                "-package=\n");
        BuildStepResult result = Javadoc.ofEnvironment(Environment.NONE,
                process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("sample/SampleTest.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Javadoc.JAVADOC + "sample/SampleTest.html")).content().contains("Documented.");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void records_when_a_page_was_generated_only_when_the_archive_timestamp_is_empty(boolean empty)
            throws IOException {
        Files.writeString(Files.createDirectories(sources.resolve(Javac.SOURCES + "sample")).resolve("Sample.java"),
                "package sample; /** Documented. */ public class Sample { }\n");
        Javadoc javadoc = Javadoc.ofEnvironment(empty
                        ? new Environment(Map.of("archive.timestamp", ""))
                        : Environment.NONE,
                ProcessHandler.Factory.TOOL);
        javadoc.apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("sample/Sample.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(Files.readString(next.resolve(Javadoc.JAVADOC + "sample/Sample.html")).contains("dc.created"))
                .as("javadoc dates every page unless told -notimestamp")
                .isEqualTo(empty);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void modular_documentation_splits_the_module_path_from_the_class_path(boolean process)
            throws IOException {
        Path library = Files.createDirectories(root.resolve("library"));
        Path plain = plainJar(library.resolve("plain.jar")), named = modularJar(library.resolve("named.jar"), "lib.named");
        SequencedProperties index = new SequencedProperties();
        index.setProperty("main/compile/maven/plain", "../library/plain.jar");
        index.setProperty("main/compile/maven/named", "../library/named.jar");
        index.store(sources.resolve(BuildStep.DEPENDENCIES));
        Path folder = Files.createDirectory(sources.resolve(Javac.SOURCES));
        Files.writeString(folder.resolve("module-info.java"), "module sample {\n    requires lib.named;\n}\n");
        Files.writeString(Files.createDirectory(folder.resolve("sample")).resolve("Sample.java"), """
                package sample;
                /**
                 * This is a javadoc.
                */
                public class Sample { }
                """);

        BuildStepResult result = Javadoc.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("module-info.java"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("sample/Sample.java"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Javadoc.JAVADOC + "sample/sample/Sample.html")).isNotEmptyFile();
        assertThat(Files.readString(supplement.resolve("javadoc.args")))
                .as("a jar without a module name is unscannable on the module path, so it goes where it belongs")
                .contains("--module-path\n\"" + escaped(named))
                .contains("--class-path\n\"" + escaped(plain))
                .doesNotContain("--add-reads");
    }

    private static String escaped(Path path) {
        return path.toString().replace("\\", "\\\\");
    }

    private Path plainJar(Path file) throws IOException {
        Path classes = compile(file.getParent().resolve("plain-classes"), "plain/Lib.java", """
                package plain;
                public class Lib {
                }
                """);
        jarOf(file, classes);
        return file;
    }

    private Path modularJar(Path file, String name) throws IOException {
        Path classes = compile(file.getParent().resolve(name + "-classes"),
                "module-info.java",
                "module " + name + " {\n}\n");
        jarOf(file, classes);
        return file;
    }

    private Path compile(Path classes, String name, String content) throws IOException {
        Path source = classes.resolveSibling(classes.getFileName() + "-sources").resolve(name);
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, content);
        StringWriter errors = new StringWriter();
        int result = ToolProvider.findFirst("javac").orElseThrow().run(
                new PrintWriter(Writer.nullWriter()),
                new PrintWriter(errors),
                "-d",
                classes.toString(),
                source.toString());
        if (result != 0) {
            throw new IllegalStateException("Compilation failed: " + errors);
        }
        return classes;
    }

    private void jarOf(Path file, Path classes) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file));
             Stream<Path> stream = Files.walk(classes)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(path).toString().replace(File.separatorChar, '/')));
                output.write(Files.readAllBytes(path));
                output.closeEntry();
            }
        }
    }
}