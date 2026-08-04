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
import build.jenesis.project.TestEngine;
import build.jenesis.project.TestModule;
import build.jenesis.project.TestNG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestEngineAdapterTest {

    @TempDir
    private Path root, dependencies;

    @Test
    public void junit_platform_declares_console_runner_and_launcher_main_class() {
        JUnitPlatform engine = new JUnitPlatform();
        assertThat(engine.runnerModule()).isEqualTo("org.junit.platform.console");
        assertThat(engine.mainClass()).isEqualTo("org.junit.platform.console.ConsoleLauncher");
    }

    @Test
    public void junit_platform_declares_dumb_terminal_system_property() {
        assertThat(new JUnitPlatform().properties())
                .hasSize(1)
                .containsEntry("org.jline.terminal.dumb", "true");
    }

    @Test
    public void junit_platform_recognizes_engine_module_but_not_console_as_engine() {
        JUnitPlatform engine = new JUnitPlatform();
        assertThat(engine.isEngine(ModuleDescriptor.newAutomaticModule("org.junit.platform.engine").build())).isTrue();
        assertThat(engine.isEngine(ModuleDescriptor.newAutomaticModule("org.junit.platform.console").build())).isFalse();
        assertThat(engine.isRunner(ModuleDescriptor.newAutomaticModule("org.junit.platform.console").build())).isTrue();
        assertThat(engine.isRunner(ModuleDescriptor.newAutomaticModule("org.junit.platform.engine").build())).isFalse();
    }

    @Test
    public void junit4_declares_junit_module_as_runner_and_core_main_class() {
        JUnit4 engine = new JUnit4();
        assertThat(engine.runnerModule()).isEqualTo("junit");
        assertThat(engine.mainClass()).isEqualTo("org.junit.runner.JUnitCore");
    }

    @Test
    public void junit4_treats_engine_module_as_its_own_runner() {
        JUnit4 engine = new JUnit4();
        ModuleDescriptor junit = ModuleDescriptor.newAutomaticModule("junit").build();
        assertThat(engine.isEngine(junit)).isTrue();
        assertThat(engine.isRunner(junit)).isTrue();
        assertThat(engine.isEngine(ModuleDescriptor.newAutomaticModule("org.testng").build())).isFalse();
    }

    @Test
    public void junit4_declares_no_runner_coordinates_or_system_properties() {
        JUnit4 engine = new JUnit4();
        assertThat(engine.coordinates(ModuleDescriptor.newAutomaticModule("junit").build())).isEmpty();
        assertThat(engine.properties()).isEmpty();
    }

    @Test
    public void junit4_produces_no_commands_for_empty_selection() {
        assertThat(new JUnit4().commands(root,
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
        assertThat(engine.mainClass()).isEqualTo("org.testng.TestNG");
    }

    @Test
    public void testng_treats_module_as_its_own_engine_and_runner() {
        TestNG engine = new TestNG();
        ModuleDescriptor testng = ModuleDescriptor.newAutomaticModule("org.testng").build();
        assertThat(engine.isEngine(testng)).isTrue();
        assertThat(engine.isRunner(testng)).isTrue();
        assertThat(engine.isEngine(ModuleDescriptor.newAutomaticModule("junit").build())).isFalse();
    }

    @Test
    public void testng_declares_no_runner_coordinates_or_system_properties() {
        TestNG engine = new TestNG();
        assertThat(engine.coordinates(ModuleDescriptor.newAutomaticModule("org.testng").build())).isEmpty();
        assertThat(engine.properties()).isEmpty();
    }

    @Test
    public void testng_writes_output_directory_header_for_empty_selection() {
        assertThat(new TestNG().commands(root,
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
        assertThat(new TestNG().commands(root,
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
        assertThat(TestEngine.of(List.of(
                ModuleDescriptor.newAutomaticModule("junit").build(),
                ModuleDescriptor.newAutomaticModule("org.testng").build(),
                ModuleDescriptor.newAutomaticModule("org.junit.platform.engine").build())))
                .get().isInstanceOf(JUnitPlatform.class);
    }

    @Test
    public void selects_junit4_ahead_of_testng() {
        assertThat(TestEngine.of(List.of(
                ModuleDescriptor.newAutomaticModule("org.testng").build(),
                ModuleDescriptor.newAutomaticModule("junit").build())))
                .get().isInstanceOf(JUnit4.class);
    }

    @Test
    public void jenesis_test_engine_property_rejects_unknown_value() {
        System.setProperty("jenesis.test.engine", "does-not-exist");
        try {
            assertThatThrownBy(() -> new TestModule(Map.of(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown test engine")
                    .hasMessageContaining("expected junit-platform, junit4, or testng");
        } finally {
            System.clearProperty("jenesis.test.engine");
        }
    }

    @Test
    public void jenesis_test_engine_property_selects_junit_platform_case_insensitively() throws IOException {
        Files.createDirectories(dependencies.resolve(BuildStep.ARTIFACTS));
        System.setProperty("jenesis.test.engine", "JUnit-Platform");
        try {
            BuildExecutor executor = newExecutor(root);
            executor.addSource("dependencies", dependencies);
            executor.addModule("test",
                    new TestModule(Map.of(),
                            Map.of("maven", (_, _, _, _, _, _) -> new Resolver.Resolution(
                                    new LinkedHashMap<>(), List.of(), new LinkedHashMap<>())))
                            .jarsOnly(false),
                    "dependencies");
            executor.execute("test/resolved");

            assertThat(readRequires(root).stringPropertyNames())
                    .containsExactly("main/runtime/maven/org.junit.platform/junit-platform-console");
        } finally {
            System.clearProperty("jenesis.test.engine");
        }
    }

    @Test
    public void jenesis_test_engine_property_bypasses_dependency_detection_for_junit4() throws IOException {
        Files.createDirectories(dependencies.resolve(BuildStep.ARTIFACTS));
        System.setProperty("jenesis.test.engine", "junit4");
        try {
            BuildExecutor executor = newExecutor(root);
            executor.addSource("dependencies", dependencies);
            executor.addModule("test",
                    new TestModule(Map.of(), Map.of()).jarsOnly(false),
                    "dependencies");
            executor.execute("test/resolved");

            assertThat(readRequires(root)).isEmpty();
        } finally {
            System.clearProperty("jenesis.test.engine");
        }
    }

    @Test
    public void jenesis_test_engine_property_bypasses_dependency_detection_for_testng() throws IOException {
        Files.createDirectories(dependencies.resolve(BuildStep.ARTIFACTS));
        System.setProperty("jenesis.test.engine", "testng");
        try {
            BuildExecutor executor = newExecutor(root);
            executor.addSource("dependencies", dependencies);
            executor.addModule("test",
                    new TestModule(Map.of(), Map.of()).jarsOnly(false),
                    "dependencies");
            executor.execute("test/resolved");

            assertThat(readRequires(root)).isEmpty();
        } finally {
            System.clearProperty("jenesis.test.engine");
        }
    }

    private static BuildExecutor newExecutor(Path root) throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false);
    }

    private static SequencedProperties readRequires(Path root) throws IOException {
        return SequencedProperties.ofFiles(root.resolve("test")
                .resolve("resolved")
                .resolve("output")
                .resolve(BuildStep.REQUIRES));
    }
}
