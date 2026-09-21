package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.project.JUnit4;
import build.jenesis.project.JUnitPlatform;
import build.jenesis.project.TestFramework;
import build.jenesis.project.TestModule;
import build.jenesis.project.TestNG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestFrameworkAdapterTest {

    @TempDir
    private Path root, dependencies;

    @Test
    public void junit_platform_declares_console_runner_and_launcher_main_class() {
        JUnitPlatform engine = new JUnitPlatform();
        assertThat(engine.runnerModule()).isEqualTo("org.junit.platform.console");
        assertThat(engine.runnerClass()).isEqualTo("org.junit.platform.console.ConsoleLauncher");
    }

    @Test
    public void junit_platform_declares_dumb_terminal_system_property() {
        assertThat(new JUnitPlatform().systemProperties())
                .hasSize(1)
                .containsEntry("org.jline.terminal.dumb", "true");
    }

    @Test
    public void junit_platform_recognizes_the_engine_and_the_jupiter_api_but_not_the_console() {
        JUnitPlatform engine = new JUnitPlatform();
        assertThat(engine.isMarkedBy(ModuleDescriptor.newAutomaticModule("org.junit.platform.engine").build()))
                .isTrue();
        assertThat(engine.isMarkedBy(ModuleDescriptor.newAutomaticModule("org.junit.jupiter.api").build())).isTrue();
        assertThat(engine.isMarkedBy(ModuleDescriptor.newAutomaticModule("org.junit.platform.console").build()))
                .isFalse();
    }

    @Test
    public void junit4_declares_junit_module_as_runner_and_core_main_class() {
        JUnit4 engine = new JUnit4();
        assertThat(engine.runnerModule()).isEqualTo("junit");
        assertThat(engine.runnerClass()).isEqualTo("org.junit.runner.JUnitCore");
    }

    @Test
    public void junit4_recognizes_the_junit_module_as_its_framework() {
        JUnit4 engine = new JUnit4();
        assertThat(engine.isMarkedBy(ModuleDescriptor.newAutomaticModule("junit").build())).isTrue();
        assertThat(engine.isMarkedBy(ModuleDescriptor.newAutomaticModule("org.testng").build())).isFalse();
    }

    @Test
    public void junit4_declares_no_runner_coordinates_or_system_properties() {
        JUnit4 engine = new JUnit4();
        assertThat(engine.missingCoordinates(List.of(ModuleDescriptor.newAutomaticModule("junit").build()))).isEmpty();
        assertThat(engine.systemProperties()).isEmpty();
    }

    @Test
    public void junit4_produces_no_commands_for_empty_selection() {
        assertThat(new JUnit4().arguments(root,
                root,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                false,
                false))
                .isEmpty();
    }

    @Test
    public void testng_declares_testng_module_and_main_class() {
        TestNG engine = new TestNG();
        assertThat(engine.runnerModule()).isEqualTo("org.testng");
        assertThat(engine.runnerClass()).isEqualTo("org.testng.TestNG");
    }

    @Test
    public void testng_recognizes_the_testng_module_as_its_framework() {
        TestNG engine = new TestNG();
        assertThat(engine.isMarkedBy(ModuleDescriptor.newAutomaticModule("org.testng").build())).isTrue();
        assertThat(engine.isMarkedBy(ModuleDescriptor.newAutomaticModule("junit").build())).isFalse();
    }

    @Test
    public void testng_declares_no_runner_coordinates_or_system_properties() {
        TestNG engine = new TestNG();
        assertThat(engine.missingCoordinates(List.of(
                ModuleDescriptor.newAutomaticModule("org.testng").build()))).isEmpty();
        assertThat(engine.systemProperties()).isEmpty();
    }

    @Test
    public void testng_writes_output_directory_header_for_empty_selection() {
        assertThat(new TestNG().arguments(root,
                root,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                false,
                false))
                .containsExactly("-d", root.resolve("test-output").toString());
    }

    @Test
    public void testng_orders_groups_parallel_classes_and_methods_after_output_header() {
        SequencedMap<String, SequencedSet<String>> methods = new LinkedHashMap<>();
        methods.put("sample.AlphaTest", new LinkedHashSet<>(List.of("first")));
        assertThat(new TestNG().arguments(root,
                root,
                new LinkedHashSet<>(List.of("sample.AlphaTest", "sample.BetaTest")),
                methods,
                new LinkedHashSet<>(List.of("slow", "flaky")),
                true,
                false))
                .containsExactly(
                        "-d", root.resolve("test-output").toString(),
                        "-groups", "slow,flaky",
                        "-parallel", "methods",
                        "-testclass", "sample.AlphaTest,sample.BetaTest",
                        "-methods", "sample.AlphaTest.first");
    }

    @Test
    public void selects_junit_platform_ahead_of_junit4_and_testng() {
        assertThat(TestFramework.detect(List.of(
                ModuleDescriptor.newAutomaticModule("junit").build(),
                ModuleDescriptor.newAutomaticModule("org.testng").build(),
                ModuleDescriptor.newAutomaticModule("org.junit.platform.engine").build())))
                .get().isInstanceOf(JUnitPlatform.class);
    }

    @Test
    public void selects_junit4_ahead_of_testng() {
        assertThat(TestFramework.detect(List.of(
                ModuleDescriptor.newAutomaticModule("org.testng").build(),
                ModuleDescriptor.newAutomaticModule("junit").build())))
                .get().isInstanceOf(JUnit4.class);
    }

    @Test
    public void a_declared_framework_rejects_an_unknown_name() {
        assertThatThrownBy(() -> TestFramework.named("does-not-exist"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown test framework")
                .hasMessageContaining("expected junit-platform, junit4, or testng");
    }

    @Test
    public void a_declared_framework_is_named_case_insensitively() {
        assertThat(TestFramework.named("JUnit-Platform")).isInstanceOf(JUnitPlatform.class);
        assertThat(TestFramework.named("JUnit4")).isInstanceOf(JUnit4.class);
        assertThat(TestFramework.named("TestNG")).isInstanceOf(TestNG.class);
    }

    @Test
    public void a_declared_junit_platform_resolves_its_runner_without_any_dependency() throws IOException {
        Files.createDirectories(dependencies.resolve(BuildStep.ARTIFACTS));
        BuildExecutor executor = newExecutor(root);
        executor.addSource("dependencies", dependencies);
        executor.addModule("test",
                new TestModule(Map.of(),
                        Map.of("maven", (_, _, _, _, _, _) -> new Resolver.Resolution(
                                new LinkedHashMap<>(), List.of(), new LinkedHashMap<>())))
                        .framework(new JUnitPlatform())
                        .jarsOnly(false),
                "dependencies");
        executor.execute("test/resolved");

        assertThat(readRequires(root).stringPropertyNames())
                .containsExactly("main/runtime/maven/org.junit.platform/junit-platform-console");
    }

    @Test
    public void a_declared_junit4_bypasses_dependency_detection() throws IOException {
        Files.createDirectories(dependencies.resolve(BuildStep.ARTIFACTS));
        BuildExecutor executor = newExecutor(root);
        executor.addSource("dependencies", dependencies);
        executor.addModule("test",
                new TestModule(Map.of(), Map.of()).framework(new JUnit4()).jarsOnly(false),
                "dependencies");
        executor.execute("test/resolved");

        assertThat(readRequires(root)).isEmpty();
    }

    @Test
    public void a_declared_testng_bypasses_dependency_detection() throws IOException {
        Files.createDirectories(dependencies.resolve(BuildStep.ARTIFACTS));
        BuildExecutor executor = newExecutor(root);
        executor.addSource("dependencies", dependencies);
        executor.addModule("test",
                new TestModule(Map.of(), Map.of()).framework(new TestNG()).jarsOnly(false),
                "dependencies");
        executor.execute("test/resolved");

        assertThat(readRequires(root)).isEmpty();
    }

    private static BuildExecutor newExecutor(Path root) throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
    }

    private static SequencedProperties readRequires(Path root) throws IOException {
        return SequencedProperties.ofFiles(root.resolve("test")
                .resolve("resolved")
                .resolve("output")
                .resolve(BuildStep.REQUIRES));
    }
}
