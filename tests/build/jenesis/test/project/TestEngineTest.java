package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import java.util.jar.Attributes;
import build.jenesis.BuildStep;
import build.jenesis.SequencedProperties;
import build.jenesis.project.JUnit4;
import build.jenesis.project.JUnitPlatform;
import build.jenesis.project.TestEngine;
import build.jenesis.project.TestNG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestEngineTest {

    @TempDir
    private Path root;

    @Test
    public void detects_junit_platform_from_engine_module() throws IOException {
        writeJar(root.resolve("artifacts"), "engine.jar", "org.junit.platform.engine");
        assertThat(TestEngine.of(List.of(root))).get().isInstanceOf(JUnitPlatform.class);
    }

    @Test
    public void detects_junit_platform_from_jupiter_api_without_an_engine() throws IOException {
        writeJar(root.resolve("artifacts"), "api.jar", "org.junit.jupiter.api");
        assertThat(TestEngine.of(List.of(root))).get().isInstanceOf(JUnitPlatform.class);
    }

    @Test
    public void prefers_junit_platform_over_an_incidental_junit4_jar() throws IOException {
        writeJar(root.resolve("artifacts"), "api.jar", "org.junit.jupiter.api");
        writeJar(root.resolve("artifacts"), "junit.jar", "junit");
        assertThat(TestEngine.of(List.of(root)))
                .as("a transitive junit:junit must not outrank the Jupiter API of the tests themselves")
                .get()
                .isInstanceOf(JUnitPlatform.class);
    }

    @Test
    public void detects_junit4_from_module() throws IOException {
        writeJar(root.resolve("artifacts"), "junit.jar", "junit");
        assertThat(TestEngine.of(List.of(root))).get().isInstanceOf(JUnit4.class);
    }

    @Test
    public void detects_testng_from_module() throws IOException {
        writeJar(root.resolve("artifacts"), "testng.jar", "org.testng");
        assertThat(TestEngine.of(List.of(root))).get().isInstanceOf(TestNG.class);
    }

    @Test
    public void detects_engine_from_resolved_dependencies() throws IOException {
        writeJar(root.resolve("resolved"), "engine.jar", "org.junit.platform.engine");
        SequencedProperties index = new SequencedProperties();
        index.setProperty("runtime/maven/engine", "resolved/engine.jar");
        index.store(root.resolve(BuildStep.DEPENDENCIES));
        assertThat(TestEngine.of(List.of(root))).get().isInstanceOf(JUnitPlatform.class);
    }

    @Test
    public void detects_no_engine_for_unrelated_module() throws IOException {
        writeJar(root.resolve("artifacts"), "plain.jar", "com.example.something");
        assertThat(TestEngine.of(List.of(root))).isEmpty();
    }

    @Test
    public void detects_no_engine_from_file_name_alone() throws IOException {
        writeJar(root.resolve("artifacts"), "org.junit.platform.engine.jar", null);
        assertThat(TestEngine.of(List.of(root))).isEmpty();
    }

    @Test
    public void detects_no_engine_without_jars() throws IOException {
        assertThat(TestEngine.of(List.of(root))).isEmpty();
    }

    @Test
    public void resolves_nothing_where_the_console_jar_is_on_the_path() throws IOException {
        writeJar(root.resolve("artifacts"), "console.jar", "org.junit.platform.console");
        assertThat(new JUnitPlatform().missingCoordinates(TestEngine.scan(List.of(root)))).isEmpty();
    }

    @Test
    public void resolves_the_console_where_only_the_engine_jar_is_on_the_path() throws IOException {
        writeJar(root.resolve("artifacts"), "engine.jar", "org.junit.platform.engine");
        assertThat(new JUnitPlatform().missingCoordinates(TestEngine.scan(List.of(root))))
                .containsOnlyKeys("module/org.junit.platform.console",
                        "maven/org.junit.platform/junit-platform-console");
    }

    @Test
    public void derives_console_default_version_from_engine_module() {
        assertThat(new JUnitPlatform().missingCoordinates(List.of(automatic("org.junit.platform.engine", "1.11.3"))))
                .containsEntry("maven/org.junit.platform/junit-platform-console", "1.11.3")
                .containsEntry("module/org.junit.platform.console", "1.11.3");
    }

    @Test
    public void console_floats_without_a_derived_engine_version() {
        SequencedMap<String, String> coordinates = new JUnitPlatform().missingCoordinates(List.of());
        assertThat(coordinates).containsEntry("maven/org.junit.platform/junit-platform-console", "RELEASE");
        assertThat(coordinates).containsKey("module/org.junit.platform.console");
        assertThat(coordinates.get("module/org.junit.platform.console")).isNull();
    }

    @Test
    public void resolves_a_jupiter_engine_aligned_to_the_jupiter_api_version() {
        SequencedMap<String, String> coordinates = new JUnitPlatform().missingCoordinates(List.of(
                automatic("org.junit.jupiter.api", "5.11.3"),
                automatic("org.junit.platform.commons", "1.11.3")));
        assertThat(coordinates)
                .containsEntry("maven/org.junit.jupiter/junit-jupiter-engine", "5.11.3")
                .containsEntry("module/org.junit.jupiter.engine", "5.11.3");
    }

    @Test
    public void derives_the_console_version_from_platform_commons_without_an_engine_module() {
        SequencedMap<String, String> coordinates = new JUnitPlatform().missingCoordinates(List.of(
                automatic("org.junit.jupiter.api", "5.11.3"),
                automatic("org.junit.platform.commons", "1.11.3")));
        assertThat(coordinates)
                .as("the console follows the platform version line, not the Jupiter one")
                .containsEntry("maven/org.junit.platform/junit-platform-console", "1.11.3")
                .containsEntry("module/org.junit.platform.console", "1.11.3");
    }

    @Test
    public void resolves_a_vintage_engine_where_junit4_shares_the_path_with_jupiter() {
        SequencedMap<String, String> coordinates = new JUnitPlatform().missingCoordinates(List.of(
                automatic("junit", "4.13.2"),
                automatic("org.junit.jupiter.api", "5.11.3"),
                automatic("org.junit.platform.commons", "1.11.3")));
        assertThat(coordinates)
                .as("JUnit 4 classes in a module routed to the platform still need an engine")
                .containsEntry("maven/org.junit.vintage/junit-vintage-engine", "5.11.3")
                .containsEntry("module/org.junit.vintage.engine", "5.11.3");
    }

    @Test
    public void resolves_no_vintage_engine_without_junit4_on_the_path() {
        assertThat(new JUnitPlatform().missingCoordinates(List.of(
                automatic("org.junit.jupiter.api", "5.11.3"),
                automatic("org.junit.platform.commons", "1.11.3"))))
                .doesNotContainKey("maven/org.junit.vintage/junit-vintage-engine");
    }

    @Test
    public void resolves_no_vintage_engine_where_the_jupiter_version_line_is_unknown() {
        assertThat(new JUnitPlatform().missingCoordinates(List.of(
                automatic("junit", "4.13.2"),
                automatic("org.junit.platform.engine", "1.11.3"))))
                .as("vintage tracks the Jupiter version line, so it is not guessed without one")
                .doesNotContainKey("maven/org.junit.vintage/junit-vintage-engine");
    }

    @Test
    public void resolves_only_the_console_where_a_jupiter_engine_is_present() {
        SequencedMap<String, String> coordinates = new JUnitPlatform().missingCoordinates(List.of(
                automatic("org.junit.jupiter.api", "5.11.3"),
                automatic("org.junit.jupiter.engine", "5.11.3"),
                automatic("org.junit.platform.engine", "1.11.3")));
        assertThat(coordinates)
                .containsOnlyKeys("module/org.junit.platform.console",
                        "maven/org.junit.platform/junit-platform-console");
    }

    @Test
    public void resolves_only_the_jupiter_engine_where_the_console_is_present() {
        SequencedMap<String, String> coordinates = new JUnitPlatform().missingCoordinates(List.of(
                automatic("org.junit.jupiter.api", "5.11.3"),
                automatic("org.junit.platform.console", "1.11.3")));
        assertThat(coordinates)
                .containsOnlyKeys("module/org.junit.jupiter.engine",
                        "maven/org.junit.jupiter/junit-jupiter-engine");
    }

    @Test
    public void resolves_nothing_where_the_runner_and_the_engine_are_both_present() {
        assertThat(new JUnitPlatform().missingCoordinates(List.of(
                automatic("org.junit.jupiter.api", "5.11.3"),
                automatic("org.junit.jupiter.engine", "5.11.3"),
                automatic("org.junit.platform.console", "1.11.3")))).isEmpty();
    }

    @Test
    public void resolves_no_jupiter_engine_for_a_platform_project_without_jupiter() {
        assertThat(new JUnitPlatform().missingCoordinates(List.of(automatic("org.junit.platform.engine", "1.11.3"))))
                .as("a non-Jupiter engine on the platform must not pull Jupiter in")
                .containsOnlyKeys("module/org.junit.platform.console",
                        "maven/org.junit.platform/junit-platform-console");
    }

    @Test
    public void resolves_nothing_for_junit4_and_testng() {
        assertThat(new JUnit4().missingCoordinates(List.of(automatic("junit", "4.13.2")))).isEmpty();
        assertThat(new TestNG().missingCoordinates(List.of(automatic("org.testng", "7.10.2")))).isEmpty();
    }

    @Test
    public void junit4_emits_class_names_positionally() {
        assertThat(new JUnit4().commands(root,
                root,
                new LinkedHashSet<>(List.of("sample.AlphaTest", "sample.BetaTest")),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                false,
                false))
                .containsExactly("sample.AlphaTest", "sample.BetaTest");
    }

    @Test
    public void junit4_rejects_method_selectors() {
        SequencedMap<String, SequencedSet<String>> methods = new LinkedHashMap<>();
        methods.put("sample.AlphaTest", new LinkedHashSet<>(List.of("first")));
        assertThatThrownBy(() -> new JUnit4().commands(root,
                root,
                Collections.emptyNavigableSet(),
                methods,
                Collections.emptyNavigableSet(),
                false,
                false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void junit_platform_emits_select_class_and_method_arguments() {
        SequencedMap<String, SequencedSet<String>> methods = new LinkedHashMap<>();
        methods.put("sample.AlphaTest", new LinkedHashSet<>(List.of("first", "second")));
        assertThat(new JUnitPlatform().commands(root,
                root,
                new LinkedHashSet<>(List.of("sample.BetaTest")),
                methods,
                Collections.emptyNavigableSet(),
                false,
                false))
                .containsExactly("execute", "--disable-banner", "--disable-ansi-colors",
                        "--select-class=sample.BetaTest",
                        "--select-method=sample.AlphaTest#first",
                        "--select-method=sample.AlphaTest#second");
    }

    @Test
    public void testng_joins_classes_and_methods() {
        SequencedMap<String, SequencedSet<String>> methods = new LinkedHashMap<>();
        methods.put("sample.AlphaTest", new LinkedHashSet<>(List.of("first")));
        assertThat(new TestNG().commands(root,
                root,
                new LinkedHashSet<>(List.of("sample.AlphaTest", "sample.BetaTest")),
                methods,
                Collections.emptyNavigableSet(),
                false,
                false))
                .containsSubsequence("-testclass", "sample.AlphaTest,sample.BetaTest",
                        "-methods", "sample.AlphaTest.first");
    }

    @Test
    public void junit_platform_commands_add_one_tag_per_group_and_parallel_config() {
        assertThat(new JUnitPlatform().commands(root,
                root,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                new LinkedHashSet<>(List.of("slow", "flaky")),
                true,
                false))
                .contains("--include-tag=slow",
                        "--include-tag=flaky",
                        "--config=junit.jupiter.execution.parallel.enabled=true",
                        "--config=junit.jupiter.execution.parallel.mode.default=concurrent");
        assertThat(new JUnitPlatform().commands(root,
                root,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                false,
                false))
                .doesNotContain("--include-tag=slow",
                        "--config=junit.jupiter.execution.parallel.enabled=true");
    }

    @Test
    public void junit_platform_commands_add_both_report_formats_when_enabled() {
        assertThat(new JUnitPlatform().commands(root,
                root,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                false,
                true))
                .contains("--reports-dir=" + root.resolve(BuildStep.REPORTS + "tests"),
                        "--config=junit.platform.reporting.open.xml.enabled=true",
                        "--config=junit.platform.reporting.output.dir=" + root.resolve(BuildStep.REPORTS + "tests"));
        assertThat(new JUnitPlatform().commands(root,
                root,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                false,
                false))
                .noneMatch(command -> command.startsWith("--reports-dir")
                        || command.startsWith("--config=junit.platform.reporting."));
    }

    @Test
    public void testng_writes_its_report_into_the_reports_folder_when_enabled() {
        assertThat(new TestNG().commands(root,
                root,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                false,
                true))
                .containsExactly("-d", root.resolve(BuildStep.REPORTS + "tests").toString());
    }

    @Test
    public void testng_joins_groups_with_commas_and_adds_parallel() {
        assertThat(new TestNG().commands(root,
                root,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                new LinkedHashSet<>(List.of("slow", "flaky")),
                true,
                false))
                .containsSubsequence("-groups", "slow,flaky")
                .containsSubsequence("-parallel", "methods");
    }

    @Test
    public void junit4_rejects_groups() {
        assertThatThrownBy(() -> new JUnit4().commands(root,
                root,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                new LinkedHashSet<>(List.of("slow")),
                false,
                false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void junit4_ignores_parallel() {
        assertThat(new JUnit4().commands(root,
                root,
                new LinkedHashSet<>(List.of("sample.AlphaTest")),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                true,
                false))
                .containsExactly("sample.AlphaTest");
    }

    private static ModuleDescriptor automatic(String name, String version) {
        return ModuleDescriptor.newAutomaticModule(name).version(version).build();
    }

    private static void writeJar(Path folder, String name, String moduleName) throws IOException {
        Files.createDirectories(folder);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (moduleName != null) {
            manifest.getMainAttributes().putValue("Automatic-Module-Name", moduleName);
        }
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(folder.resolve(name)), manifest)) {
            output.putNextEntry(new JarEntry("sample/resource.txt"));
            output.closeEntry();
        }
    }
}
