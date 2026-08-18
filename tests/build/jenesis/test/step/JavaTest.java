package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.PathPlacement;
import build.jenesis.step.Java;
import build.jenesis.step.Javac;
import build.jenesis.step.ProcessHandler;
import sample.Sample;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class JavaTest {

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

    @Test
    public void can_execute_java() throws IOException {
        Path folder = Files.createDirectories(classes.resolve(Javac.CLASSES + "sample"));
        try (InputStream input = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(input), folder.resolve("Sample.class"));
        }
        BuildStepResult result = Java.of(PathPlacement.CLASS_PATH, false, "sample.Sample").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(
                        classes,
                        Map.of(Path.of("sample/Sample.class"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(supplement.resolve("output")).content().isEqualTo("Hello world!");
        assertThat(supplement.resolve("error")).isEmptyFile();
    }

    @Test
    public void modular_run_beside_a_class_path_jar_roots_the_module_path() throws IOException {
        Path folder = Files.createDirectories(classes.resolve(Javac.CLASSES + "sample"));
        try (InputStream input = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(input), folder.resolve("Sample.class"));
        }
        Path artifacts = Files.createDirectories(classes.resolve(BuildStep.ARTIFACTS));
        Files.writeString(Files.createDirectories(root.resolve("named-sources")).resolve("module-info.java"),
                "module sample.run {\n}\n");
        Path compiled = Files.createDirectories(root.resolve("named-classes"));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", compiled.toString(),
                root.resolve("named-sources/module-info.java").toString())).isZero();
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifacts.resolve("named.jar")))) {
            jar.putNextEntry(new JarEntry("module-info.class"));
            jar.write(Files.readAllBytes(compiled.resolve("module-info.class")));
            jar.closeEntry();
        }
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifacts.resolve("plain.jar")))) {
            jar.putNextEntry(new JarEntry("plain/Lib.class"));
            jar.write(new byte[]{1, 2, 3});
            jar.closeEntry();
        }
        AtomicReference<List<String>> captured = new AtomicReference<>();
        Function<List<String>, ProcessHandler.OfProcess> base = ProcessHandler.OfProcess.ofJavaHome("bin/java");
        Function<List<String>, ProcessHandler.OfProcess> factory = arguments -> {
            captured.set(arguments);
            return base.apply(arguments);
        };
        BuildStepResult result = Java.of(factory, PathPlacement.INFERRED, false, "-version").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(
                        classes,
                        Map.of(Path.of("artifacts/named.jar"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("artifacts/plain.jar"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        assertThat(captured.get())
                .as("a jar without a module name lands on the class path, expecting the whole platform")
                .containsSubsequence("--add-modules", "ALL-MODULE-PATH,ALL-DEFAULT")
                .doesNotContain("--add-reads");
    }

    @Test
    public void classpath_only_run_does_not_add_all_module_path() throws IOException {
        Path folder = Files.createDirectories(classes.resolve(Javac.CLASSES + "sample"));
        try (InputStream input = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(input), folder.resolve("Sample.class"));
        }
        AtomicReference<List<String>> captured = new AtomicReference<>();
        Function<List<String>, ProcessHandler.OfProcess> base = ProcessHandler.OfProcess.ofJavaHome("bin/java");
        Function<List<String>, ProcessHandler.OfProcess> factory = arguments -> {
            captured.set(arguments);
            return base.apply(arguments);
        };
        BuildStepResult result = Java.of(factory, PathPlacement.CLASS_PATH, false, "sample.Sample").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(
                        classes,
                        Map.of(Path.of("sample/Sample.class"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(captured.get())
                .as("a classpath-only run has no module path, so ALL-MODULE-PATH must not be emitted")
                .contains("--class-path")
                .doesNotContain("--add-modules", "ALL-MODULE-PATH");
    }

    @Test
    public void an_overlong_path_moves_into_an_argument_file() throws IOException {
        Path folder = Files.createDirectories(classes.resolve(Javac.CLASSES + "sample"));
        try (InputStream input = Sample.class.getResourceAsStream(Sample.class.getSimpleName() + ".class")) {
            Files.copy(requireNonNull(input), folder.resolve("Sample.class"));
        }
        Path artifacts = Files.createDirectories(classes.resolve(BuildStep.ARTIFACTS));
        Map<Path, Checksum> tracked = new LinkedHashMap<>();
        tracked.put(Path.of("sample/Sample.class"), Checksum.of(ChecksumStatus.ADDED));
        for (int index = 0; index < 120; index++) {
            String name = "a-jar-with-a-name-long-enough-to-add-up-" + index + ".jar";
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(artifacts.resolve(name)))) {
                jar.putNextEntry(new JarEntry("plain/Lib.class"));
                jar.write(new byte[]{1, 2, 3});
                jar.closeEntry();
            }
            tracked.put(Path.of(BuildStep.ARTIFACTS + name), Checksum.of(ChecksumStatus.ADDED));
        }
        AtomicReference<List<String>> captured = new AtomicReference<>();
        Function<List<String>, ProcessHandler.OfProcess> base = ProcessHandler.OfProcess.ofJavaHome("bin/java");
        Function<List<String>, ProcessHandler.OfProcess> factory = arguments -> {
            captured.set(arguments);
            return base.apply(arguments);
        };
        BuildStepResult result = Java.of(factory, PathPlacement.CLASS_PATH, false, "sample.Sample").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(classes, tracked))))
                .toCompletableFuture()
                .join();
        assertThat(result.next()).isTrue();
        assertThat(captured.get())
                .as("the path options are handed over as an @-file rather than as one enormous argument")
                .doesNotContain("--class-path", "--module-path")
                .anySatisfy(argument -> assertThat(argument).startsWith("@").endsWith("java.args"));
        assertThat(supplement.resolve("java.args"))
                .content()
                .startsWith("--class-path\n\"")
                .contains("a-jar-with-a-name-long-enough-to-add-up-119.jar");
        assertThat(supplement.resolve("output")).content().isEqualTo("Hello world!");
    }

}
