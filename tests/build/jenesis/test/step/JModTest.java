package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import module org.junit.jupiter.params;
import build.jenesis.Environment;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.JMod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JModTest {

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
    public void can_execute_jmod(boolean process) throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { }\n");
        Path classes = Files.createDirectory(bundle.resolve(BuildStep.CLASSES));
        int code = ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString());
        assertThat(code).isZero();
        BuildStepResult result = JMod.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("classes/module-info.class"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(JMod.JMODS + "sample.jmod")).isNotEmptyFile();
    }

    @Test
    public void stamps_every_entry_with_a_fixed_time() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Path classes = Files.createDirectory(bundle.resolve(BuildStep.CLASSES));
        int code = ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString());
        assertThat(code).isZero();
        BuildStepResult result = JMod.ofEnvironment(Environment.NONE, ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("classes/module-info.class"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        try (ZipFile jmod = new ZipFile(next.resolve(JMod.JMODS + "sample.jmod").toFile())) {
            assertThat(jmod.stream().map(ZipEntry::getTimeLocal))
                    .as("a jmod created at another moment carries the same bytes")
                    .containsOnly(BuildStep.timestamp(Environment.NONE).toLocalDateTime());
        }
    }

    @Test
    public void passes_no_date_when_the_archive_timestamp_is_empty() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Path classes = Files.createDirectory(bundle.resolve(BuildStep.CLASSES));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString())).isZero();
        JMod jmod;
        jmod = JMod.ofEnvironment(new Environment(Map.of("archive.timestamp", "")), ProcessHandler.Factory.TOOL);
        BuildStepResult result = jmod.apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("classes/module-info.class"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(supplement.resolve("command")).content().doesNotContain("--date");
    }

    @Test
    public void config_directory_is_packaged_and_reaches_a_jlinked_runtime() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Files.writeString(Files.createDirectory(sources.resolve("sample")).resolve("Sample.java"),
                "package sample; public class Sample { }\n");
        Path classes = Files.createDirectory(bundle.resolve(BuildStep.CLASSES));
        int code = ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString(),
                sources.resolve("sample/Sample.java").toString());
        assertThat(code).isZero();
        Files.writeString(Files.createDirectory(bundle.resolve(JMod.CONFIG)).resolve("app.properties"), "greeting=configured");

        BuildStepResult result = JMod.ofEnvironment(Environment.NONE, ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("classes/module-info.class"), Checksum.of(ChecksumStatus.ADDED),
                                Path.of("jmodconfig/app.properties"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        Path jmod = next.resolve(JMod.JMODS + "sample.jmod");
        assertThat(jmod).isNotEmptyFile();

        Path runtime = root.resolve("runtime");
        int linked = ToolProvider.findFirst("jlink").orElseThrow().run(System.out, System.err,
                "--module-path", jmod.toString(),
                "--add-modules", "sample",
                "--output", runtime.toString());
        assertThat(linked).isZero();
        assertThat(runtime.resolve("conf/app.properties")).hasContent("greeting=configured");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void skips_when_no_module_is_present(boolean process) throws IOException {
        Files.createDirectory(bundle.resolve(BuildStep.CLASSES));
        BuildStepResult result = JMod.ofEnvironment(Environment.NONE, process ? ProcessHandler.Factory.FORK : ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                new LinkedHashMap<>(Map.of("classes", new BuildStepArgument(
                        bundle,
                        Map.of(Path.of("classes/Sample.class"), Checksum.of(ChecksumStatus.ADDED)))))).toCompletableFuture().join();
        assertThat(result.next()).isTrue();
        assertThat(next.resolve(JMod.JMODS)).doesNotExist();
    }
    @Test
    public void carries_the_legal_notices_of_the_module_and_of_each_runtime_dependency() throws IOException {
        compileModule();
        jar(Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS)).resolve("classes.jar"), "META-INF/LICENSE", "own licence");
        Path dependencies = Files.createDirectory(root.resolve("dependencies"));
        jar(Files.createDirectory(dependencies.resolve("resolved")).resolve("org.dep-1.0.jar"),
                "META-INF/LICENSE.txt", "dependency licence");
        Files.writeString(dependencies.resolve(BuildStep.DEPENDENCIES), "main/runtime/module/org.dep/1.0=resolved/org.dep-1.0.jar\n");

        BuildStepResult result = JMod.ofEnvironment(Environment.NONE, ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                arguments(dependencies)).toCompletableFuture().join();

        assertThat(result.next()).isTrue();
        try (ZipFile jmod = new ZipFile(next.resolve(JMod.JMODS + "sample.jmod").toFile())) {
            assertThat(new String(jmod.getInputStream(jmod.getEntry("legal/LICENSE")).readAllBytes()))
                    .isEqualTo("own licence");
            assertThat(new String(jmod.getInputStream(jmod.getEntry("legal/org.dep-1.0/LICENSE.txt")).readAllBytes()))
                    .as("META-INF/LICENSE also matches with an extension")
                    .isEqualTo("dependency licence");
        }
    }

    @Test
    public void carries_notices_of_any_case_whole_legal_folders_and_an_about_page() throws IOException {
        compileModule();
        jar(Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS)).resolve("classes.jar"), "about.html", "<html/>");
        Path dependencies = Files.createDirectory(root.resolve("dependencies"));
        jar(Files.createDirectory(dependencies.resolve("resolved")).resolve("org.dep-1.0.jar"),
                "META-INF/notice.txt", "lower case notice",
                "META-INF/license/LICENSE.bundled.txt", "bundled licence");
        Files.writeString(dependencies.resolve(BuildStep.DEPENDENCIES), "main/runtime/module/org.dep/1.0=resolved/org.dep-1.0.jar\n");

        JMod.ofEnvironment(Environment.NONE, ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                arguments(dependencies)).toCompletableFuture().join();

        try (ZipFile jmod = new ZipFile(next.resolve(JMod.JMODS + "sample.jmod").toFile())) {
            assertThat(jmod.getEntry("legal/about.html")).isNotNull();
            assertThat(jmod.getEntry("legal/org.dep-1.0/notice.txt")).isNotNull();
            assertThat(jmod.getEntry("legal/org.dep-1.0/LICENSE.bundled.txt"))
                    .as("META-INF/license/ takes the folder below it")
                    .isNotNull();
        }
    }

    @Test
    public void refuses_a_dependency_without_legal_notices_when_strict() throws IOException {
        compileModule();
        jar(Files.createDirectory(bundle.resolve(BuildStep.ARTIFACTS)).resolve("classes.jar"), "META-INF/LICENSE", "own licence");
        Path dependencies = Files.createDirectory(root.resolve("dependencies"));
        jar(Files.createDirectory(dependencies.resolve("resolved")).resolve("org.dep-1.0.jar"), "org/dep/Dep.class", "");
        Files.writeString(dependencies.resolve(BuildStep.DEPENDENCIES), "main/runtime/module/org.dep/1.0=resolved/org.dep-1.0.jar\n");

        assertThatThrownBy(() -> JMod.ofEnvironment(new Environment(Map.of("jmod.strict", "true")), ProcessHandler.Factory.TOOL).apply(
                Runnable::run,
                new BuildStepContext(previous, next, supplement),
                arguments(dependencies)).toCompletableFuture().join())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("org.dep-1.0.jar carries none of [META-INF/NOTICE, META-INF/LICENSE,");
    }

    private void compileModule() throws IOException {
        Path sources = Files.createDirectory(root.resolve("sources"));
        Files.writeString(sources.resolve("module-info.java"), "module sample { }\n");
        Path classes = Files.createDirectory(bundle.resolve(BuildStep.CLASSES));
        assertThat(ToolProvider.findFirst("javac").orElseThrow().run(System.out, System.err,
                "-d", classes.toString(),
                sources.resolve("module-info.java").toString())).isZero();
    }

    private SequencedMap<String, BuildStepArgument> arguments(Path dependencies) {
        SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
        arguments.put("classes", new BuildStepArgument(bundle, Map.of(Path.of("classes/module-info.class"), Checksum.of(ChecksumStatus.ADDED))));
        arguments.put("dependencies", new BuildStepArgument(dependencies, Map.of(Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED))));
        return arguments;
    }

    private static void jar(Path file, String... entries) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            for (int index = 0; index < entries.length; index += 2) {
                out.putNextEntry(new JarEntry(entries[index]));
                out.write(entries[index + 1].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
    }
}
