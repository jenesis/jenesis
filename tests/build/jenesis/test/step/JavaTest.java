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
        assertThat(reportedErrors(supplement)).isEmpty();
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
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out,
                System.err,
                "-d",
                compiled.toString(),
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
        assertThat(supplement.resolve("java.args"))
                .as("a jar without a module name lands on the class path, expecting the whole platform")
                .content()
                .contains("\"--add-modules\"\n\"ALL-MODULE-PATH,ALL-DEFAULT\"")
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
                .anySatisfy(argument -> assertThat(argument).startsWith("@").endsWith("java.args"))
                .doesNotContain("--add-modules", "ALL-MODULE-PATH");
        assertThat(supplement.resolve("java.args"))
                .as("the class path is in the file, and a run without a module path names no module path at all")
                .content()
                .contains("\"--class-path\"")
                .doesNotContain("--module-path");
    }

    @Test
    public void the_whole_invocation_moves_into_an_argument_file() throws IOException {
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
                .as("the invocation is handed over as an @-file rather than as one enormous command line")
                .containsExactly("@" + supplement.resolve("java.args"));
        assertThat(supplement.resolve("java.args"))
                .as("the file holds the paths and the main class, so it reproduces the run on its own")
                .content()
                .startsWith("\"--class-path\"\n\"")
                .contains("a-jar-with-a-name-long-enough-to-add-up-119.jar")
                .endsWith("\"sample.Sample\"\n");
        assertThat(supplement.resolve("output")).content().isEqualTo("Hello world!");
    }

    @Test
    public void a_jar_two_predecessors_resolved_is_named_once_on_the_class_path() throws IOException {
        SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
        for (String name : List.of("module", "observed")) {
            Path folder = Files.createDirectories(root.resolve(name).resolve("resolved"));
            for (String jar : List.of("first-1.0.jar", "second-1.0.jar")) {
                try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(folder.resolve(jar)))) {
                    output.putNextEntry(new JarEntry("db/data.sql"));
                    output.write(new byte[]{1});
                    output.closeEntry();
                }
            }
            Files.writeString(root.resolve(name).resolve(BuildStep.DEPENDENCIES), name.equals("module")
                    ? "main/runtime/maven/sample/first/1.0=resolved/first-1.0.jar\n"
                            + "main/runtime/maven/sample/second/1.0=resolved/second-1.0.jar\n"
                    : "main/runtime/maven/sample/second/1.0=resolved/second-1.0.jar\n"
                            + "main/runtime/maven/sample/first/1.0=resolved/first-1.0.jar\n");
            arguments.put(name, new BuildStepArgument(root.resolve(name), Map.of()));
        }
        BuildStepResult result = Java.of(PathPlacement.CLASS_PATH, true, "-version").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                arguments).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(supplement.resolve("java.args"))
                .as("a jar resolved by two predecessors is one jar, and naming it twice doubles every resource it holds")
                .content()
                .contains("\"--class-path\"\n\""
                        + (root.resolve("module/resolved/first-1.0.jar")
                                + File.pathSeparator
                                + root.resolve("module/resolved/second-1.0.jar")).replace("\\", "\\\\")
                        + "\"\n")
                .doesNotContain("observed");
    }

    @Test
    public void folders_name_the_classes_and_resources_once_without_the_jar_they_were_packaged_into() throws IOException {
        SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
        Map<String, List<String>> folders = new LinkedHashMap<>();
        folders.put("compiled", List.of(Javac.CLASSES + "module-info.class", Javac.CLASSES + "sample/Sample.class"));
        folders.put("merged", List.of(Javac.CLASSES + "module-info.class",
                Javac.CLASSES + "sample/Sample.class",
                Javac.CLASSES + "sample/Other.class"));
        folders.put("resources", List.of(BuildStep.RESOURCES + "sample.properties"));
        for (Map.Entry<String, List<String>> folder : folders.entrySet()) {
            for (String file : folder.getValue()) {
                Files.write(Files.createDirectories(root.resolve(folder.getKey()).resolve(file).getParent())
                        .resolve(Path.of(file).getFileName()), new byte[]{1});
            }
            arguments.put(folder.getKey(), new BuildStepArgument(root.resolve(folder.getKey()), Map.of()));
        }
        Map<String, List<String>> jars = new LinkedHashMap<>();
        jars.put("packaged", List.of("sample/Sample.class", "sample.properties", "META-INF/maven/sample/pom.properties"));
        jars.put("dependency", List.of("module-info.class", "other/Lib.class"));
        for (Map.Entry<String, List<String>> jar : jars.entrySet()) {
            Path artifacts = Files.createDirectories(root.resolve(jar.getKey()).resolve(BuildStep.ARTIFACTS));
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(artifacts.resolve(jar.getKey() + ".jar")))) {
                for (String entry : jar.getValue()) {
                    output.putNextEntry(new JarEntry(entry));
                    output.write(new byte[]{1});
                    output.closeEntry();
                }
            }
            arguments.put(jar.getKey(), new BuildStepArgument(root.resolve(jar.getKey()), Map.of()));
        }
        BuildStepResult result = Java.of(PathPlacement.CLASS_PATH, false, "-version").apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                arguments).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(supplement.resolve("java.args"))
                .as("a folder another one holds entirely and a jar packaging the folders would double what a scan finds")
                .content()
                .contains("\"--class-path\"\n\""
                        + String.join(File.pathSeparator,
                                root.resolve("merged").resolve(Javac.CLASSES).toString(),
                                root.resolve("resources").resolve(BuildStep.RESOURCES).toString(),
                                root.resolve("dependency").resolve(BuildStep.ARTIFACTS + "dependency.jar").toString())
                        .replace("\\", "\\\\")
                        + "\"\n");
    }

    private static String reportedErrors(Path supplement) throws IOException {
        return Files.readString(supplement.resolve("error"))
                .lines()
                .filter(line -> !line.startsWith("Picked up "))
                .collect(Collectors.joining("\n"));
    }
}
