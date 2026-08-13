package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.params;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Javac;
import build.jenesis.step.Javadoc;

import static org.assertj.core.api.Assertions.assertThat;

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
        BuildStepResult result = new Javadoc(process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("sources", new BuildStepArgument(
                        sources,
                        Map.of(Path.of("sample/Sample.java"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(Javadoc.JAVADOC)).isNotEmptyDirectory();
        assertThat(next.resolve(Javadoc.JAVADOC + "sample/Sample.html")).content().contains("This is a javadoc.");
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

        BuildStepResult result = new Javadoc(process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
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
                .contains("--module-path\n\"" + named)
                .contains("--class-path\n\"" + plain)
                .doesNotContain("--add-reads");
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
        Path classes = compile(file.getParent().resolve(name + "-classes"), "module-info.java",
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
                "-d", classes.toString(),
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