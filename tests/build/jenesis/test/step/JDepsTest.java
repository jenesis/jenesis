package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.JDeps;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JDepsTest {

    @TempDir
    private Path root;
    private Path next, supplement, staged, analyzed, modules;

    @BeforeEach
    public void setUp() throws Exception {
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        staged = Files.createDirectory(root.resolve("staged"));
        analyzed = Files.createDirectory(staged.resolve(JDeps.ANALYZED));
        modules = Files.createDirectory(staged.resolve(JDeps.MODULES));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void generates_a_descriptor_for_an_analyzed_jar(boolean process) throws IOException {
        Files.writeString(Files.createDirectories(root.resolve("sources/demo/plain")).resolve("Use.java"),
                "package demo.plain; public class Use { public org.w3c.dom.Document document; }\n");
        archive(compile(null), analyzed.resolve("demo.plain.jar"));
        assertThat(execute(process).next()).isTrue();
        assertThat(descriptor("demo.plain")).contains("requires", "java.xml");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void resolves_an_analyzed_jar_against_the_module_path(boolean process) throws IOException {
        Files.writeString(root.resolve("library.java"), "module demo.library { exports demo.library; }\n");
        Files.writeString(Files.createDirectories(root.resolve("library/demo/library")).resolve("Api.java"),
                "package demo.library; public class Api { }\n");
        Files.move(root.resolve("library.java"), root.resolve("library/module-info.java"));
        Path libraryClasses = Files.createDirectory(root.resolve("library-classes"));
        compile(root.resolve("library"), libraryClasses, null);
        archive(libraryClasses, modules.resolve("demo.library.jar"));
        Files.writeString(Files.createDirectories(root.resolve("sources/demo/plain")).resolve("Use.java"),
                "package demo.plain; public class Use { public demo.library.Api api; }\n");
        archive(compile(libraryClasses.toString()), analyzed.resolve("demo.plain.jar"));
        assertThat(execute(process).next()).isTrue();
        assertThat(descriptor("demo.plain")).contains("demo.library");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void fails_on_a_missing_dependency_unless_an_option_is_injected(boolean process) throws IOException {
        Files.writeString(root.resolve("library.java"), "module demo.library { exports demo.library; }\n");
        Files.writeString(Files.createDirectories(root.resolve("library/demo/library")).resolve("Api.java"),
                "package demo.library; public class Api { }\n");
        Files.move(root.resolve("library.java"), root.resolve("library/module-info.java"));
        Path libraryClasses = Files.createDirectory(root.resolve("library-classes"));
        compile(root.resolve("library"), libraryClasses, null);
        Files.writeString(Files.createDirectories(root.resolve("sources/demo/plain")).resolve("Use.java"),
                "package demo.plain; public class Use { public demo.library.Api api; }\n");
        archive(compile(libraryClasses.toString()), analyzed.resolve("demo.plain.jar"));
        assertThatThrownBy(() -> execute(process)).hasStackTraceContaining("Missing dependencies");
        SequencedProperties options = new SequencedProperties();
        options.setProperty("--ignore-missing-deps", "");
        options.store(Files.createDirectory(staged.resolve(ProcessBuildStep.PROCESS)).resolve("jdeps.properties"));
        assertThat(execute(process).next()).isTrue();
        assertThat(descriptor("demo.plain")).contains("module demo.plain");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void skips_when_nothing_is_analyzed(boolean process) throws IOException {
        assertThat(execute(process).next()).isTrue();
        assertThat(next.resolve(JDeps.DESCRIPTORS)).doesNotExist();
    }

    private BuildStepResult execute(boolean process) throws IOException {
        try (Stream<Path> walked = Files.walk(next)) {
            walked.sorted(Comparator.reverseOrder()).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        Files.createDirectory(next);
        return new JDeps(process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(null, next, supplement),
                new LinkedHashMap<>(Map.of("staged", new BuildStepArgument(
                        staged,
                        Map.of(Path.of(JDeps.ANALYZED), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
    }

    private String descriptor(String module) throws IOException {
        try (Stream<Path> walked = Files.walk(next.resolve(JDeps.DESCRIPTORS + module))) {
            Path file = walked.filter(candidate -> candidate.getFileName().toString().equals("module-info.java"))
                    .findFirst()
                    .orElseThrow();
            return Files.readString(file);
        }
    }

    private Path compile(String classPath) throws IOException {
        Path classes = Files.createDirectory(root.resolve("classes"));
        compile(root.resolve("sources"), classes, classPath);
        return classes;
    }

    private static void compile(Path sources, Path classes, String classPath) throws IOException {
        List<String> commands = new ArrayList<>(List.of("-d", classes.toString()));
        if (classPath != null) {
            commands.add("--class-path");
            commands.add(classPath);
        }
        try (Stream<Path> walked = Files.walk(sources)) {
            walked.filter(file -> file.toString().endsWith(".java")).map(Path::toString).forEach(commands::add);
        }
        assertThat(ToolProvider.findFirst("javac")
                .orElseThrow()
                .run(System.out, System.err, commands.toArray(String[]::new))).isZero();
    }

    private static void archive(Path classes, Path jar) {
        assertThat(ToolProvider.findFirst("jar").orElseThrow().run(System.out, System.err,
                "--create", "--file", jar.toString(), "-C", classes.toString(), ".")).isZero();
    }
}
