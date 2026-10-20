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
import build.jenesis.project.InferredTestObservationModule;
import build.jenesis.project.Jfr;
import build.jenesis.project.TestModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredTestObservationModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void hands_the_test_run_everything_but_what_it_is_told_to_leave_untested() throws IOException {
        Path classes = Files.createDirectory(root.resolve("compiled")), artifacts = Files.createDirectory(root.resolve("packaged"));
        List<String> tested = new ArrayList<>(), observed = new ArrayList<>();
        BuildExecutor executor = newExecutor();
        executor.addModule("binary", (binary, _) -> {
            binary.addSource("classes", classes);
            binary.addSource("artifacts", artifacts);
        });
        executor.addModule("observed", observation()
                .untested(new LinkedHashSet<>(List.of("binary/artifacts")))
                .test(_ -> (_, inherited) -> tested.addAll(inherited.sequencedKeySet()))
                .custom("report", (_, inherited) -> observed.addAll(inherited.sequencedKeySet())), "binary");
        executor.execute();

        assertThat(tested)
                .as("the tests run against the classes alone, never beside the jar packaging them")
                .anyMatch(identity -> identity.endsWith("binary/classes"))
                .noneMatch(identity -> identity.endsWith("binary/artifacts"));
        assertThat(observed)
                .as("what observes the run still reads both")
                .anyMatch(identity -> identity.endsWith("binary/classes"))
                .anyMatch(identity -> identity.endsWith("binary/artifacts"));
    }

    @Test
    public void does_not_wire_an_engine_without_a_config_file() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}), "project");
        executor.execute();

        assertThat(root.resolve("observed").resolve("native-image"))
                .as("no observation engine is wired without its config file")
                .doesNotExist();
    }

    @Test
    public void observes_nothing_of_a_module_its_description_declares_no_or_an_abstract_test_module()
            throws IOException {
        Files.writeString(project.resolve("graal.properties"), "");
        Path main = Files.createDirectory(project.resolve("main")), base = Files.createDirectory(project.resolve("base"));
        Files.writeString(main.resolve(BuildStep.MODULE), "module=sample\n");
        Files.writeString(base.resolve(BuildStep.MODULE), "test=sample\nabstract=true\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("main", main);
        executor.addSource("base", base);
        executor.addModule("main-observed", observation().test(_ -> (_, _) -> {}), "main");
        executor.addModule("base-observed", observation().test(_ -> (_, _) -> {}), "base");
        executor.execute();

        assertThat(root.resolve("main-observed").resolve("native-image"))
                .as("a module that declares no tests is not observed")
                .doesNotExist();
        assertThat(root.resolve("base-observed").resolve("native-image"))
                .as("an abstract test module runs no tests of its own")
                .doesNotExist();
    }

    @Test
    public void reads_the_recording_options_of_a_jfr_properties_file() throws IOException {
        Files.writeString(project.resolve("jfr.properties"), "filename=elsewhere.jfr");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}), "project");
        assertThatThrownBy(executor::execute)
                .as("the file is read, and the build places the recording itself")
                .hasStackTraceContaining("remove the filename line");
    }

    @Test
    public void refuses_a_recording_option_of_a_jfr_properties_file_that_has_no_value_or_holds_a_comma()
            throws IOException {
        Files.writeString(project.resolve("jfr.properties"), "settings");
        BuildExecutor empty = newExecutor();
        empty.addSource("project", project);
        empty.addModule("observed", observation().test(_ -> (_, _) -> {}), "project");
        assertThatThrownBy(empty::execute)
                .hasStackTraceContaining("jfr.properties gives settings no value");
        Files.writeString(project.resolve("jfr.properties"), "settings=default,profile");
        BuildExecutor split = newExecutor();
        split.addSource("project", project);
        split.addModule("observed", observation().test(_ -> (_, _) -> {}), "project");
        assertThatThrownBy(split::execute)
                .hasStackTraceContaining("jfr.properties gives settings the value default,profile")
                .hasStackTraceContaining("holds no comma");
    }

    @Test
    public void hands_the_recording_options_of_a_jfr_properties_file_to_the_recording() throws IOException {
        Files.writeString(project.resolve("jfr.properties"), "settings=profile\nmaxsize=10M\n");
        List<Jfr> recorded = new ArrayList<>();
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}).jfr(jfr -> {
            recorded.add(jfr);
            return jfr;
        }), "project");
        executor.execute();

        assertThat(recorded).singleElement()
                .extracting(Jfr::options)
                .isEqualTo(Map.of("settings", "profile", "maxsize", "10M"));
    }

    @Test
    public void the_observe_override_switches_off_the_recording() throws IOException {
        Files.writeString(project.resolve("jfr.properties"), "filename=elsewhere.jfr");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}).jfr(null), "project");
        executor.execute();
    }

    @Test
    public void an_asyncprofiler_properties_file_wires_the_official_async_profiler_plugin() throws IOException {
        Files.writeString(project.resolve("asyncprofiler.properties"), "event=cpu\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}), "project");

        assertThatThrownBy(executor::execute)
                .as("the plugin is resolved by its module name from repositories this build does not have")
                .hasStackTraceContaining("build.jenesis.plugin.asyncprofiler");
    }

    @Test
    public void the_observe_override_switches_off_the_async_profiler_plugin() throws IOException {
        Files.writeString(project.resolve("asyncprofiler.properties"), "event=cpu\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}).asyncProfiler(false), "project");
        executor.execute();

        assertThat(root.resolve("observed").resolve(InferredTestObservationModule.OBSERVERS))
                .as("no plugin is resolved where its switch is off")
                .doesNotExist();
    }

    @Test
    public void the_test_configurator_decorates_the_inferred_test_module() throws IOException {
        Files.writeString(project.resolve("graal.properties"), "");
        List<TestModule> decorated = new ArrayList<>();
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(module -> {
            decorated.add(module);
            return (_, _) -> {};
        }), "project");
        executor.execute();

        assertThat(decorated)
                .as("the observation module hands its own test module to the configurator")
                .hasSize(1);
    }

    @Test
    public void the_test_override_switches_off_the_test_run_and_its_reports() throws IOException {
        Files.writeString(project.resolve("graal.properties"), "");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(null), "project");
        executor.execute();

        assertThat(root.resolve("observed").resolve("test")).doesNotExist();
        assertThat(root.resolve("observed").resolve("native-image"))
                .as("an observation report is not wired without the test run it observes")
                .doesNotExist();
    }

    @Test
    public void a_test_properties_file_declares_the_framework() throws IOException {
        Files.writeString(project.resolve("test.properties"), "framework=junit-platform");
        Files.createDirectories(project.resolve(BuildStep.ARTIFACTS));
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", resolving().test(module -> module.jarsOnly(false)), "project");
        executor.execute("observed/test/resolved");

        Path output = root.resolve("observed").resolve("test").resolve("resolved").resolve("output");
        assertThat(SequencedProperties.ofFiles(output.resolve(BuildStep.REQUIRES)).stringPropertyNames())
                .as("a declared framework resolves its runner although nothing was detected")
                .containsExactly("main/runtime/maven/org.junit.platform/junit-platform-console");
    }

    @Test
    public void a_test_properties_file_rejects_an_unknown_property() throws IOException {
        Files.writeString(project.resolve("test.properties"), "runner=junit-platform");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation(), "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Unknown test property: runner");
    }

    @Test
    public void a_test_properties_file_rejects_an_unknown_framework() throws IOException {
        Files.writeString(project.resolve("test.properties"), "framework=does-not-exist");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation(), "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("Unknown test framework")
                .hasMessageContaining("expected junit-platform, junit4, or testng");
    }

    @Test
    public void a_test_properties_file_names_the_dependencies_whose_tests_run_among_the_module_own()
            throws IOException {
        Files.writeString(project.resolve("test.properties"), "framework=junit-platform\nscan=org.example/tck\n");
        Files.createDirectories(project.resolve(BuildStep.ARTIFACTS));
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", resolving().test(module -> module.jarsOnly(false)), "project");

        assertThatThrownBy(executor::execute)
                .as("the test run looks for the jar the file names among the dependencies of the tests")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("scan org.example/tck for test classes");
    }

    @Test
    public void the_scan_of_a_test_properties_file_wins_over_the_one_of_the_module_description() throws IOException {
        Files.writeString(project.resolve(BuildStep.MODULE), "test=\nscan=org.example/surefire\n");
        Files.writeString(project.resolve("test.properties"), "framework=junit-platform\nscan=org.example/tck\n");
        Files.createDirectories(project.resolve(BuildStep.ARTIFACTS));
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", resolving().test(module -> module.jarsOnly(false)), "project");

        assertThatThrownBy(executor::execute)
                .as("the module's own file replaces what its pom.xml declares")
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("scan org.example/tck for test classes")
                .hasMessageNotContaining("surefire for test classes");
    }

    @Test
    public void a_test_properties_file_rejects_a_scan_that_names_no_dependency() throws IOException {
        Files.writeString(project.resolve("test.properties"), "scan=org.example/tck/1.0");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation(), "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("by its module name or as <groupId>/<artifactId>")
                .hasMessageContaining("org.example/tck/1.0");
    }

    @Test
    public void an_empty_test_properties_file_leaves_the_engine_inferred() throws IOException {
        Files.writeString(project.resolve("test.properties"), "");
        Files.createDirectories(project.resolve(BuildStep.ARTIFACTS));
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(module -> module.jarsOnly(false).requireFramework(false)), "project");
        executor.execute();

        assertThat(root.resolve("observed").resolve("test").resolve("resolved"))
                .as("nothing is detected here, so no engine is wired")
                .doesNotExist();
    }

    private InferredTestObservationModule resolving() {
        return new InferredTestObservationModule(new LinkedHashSet<>(List.of(project)),
                Map.of(),
                Map.of("maven", (_, _, _, _, _, _) -> new Resolver.Resolution(
                        new LinkedHashMap<>(), List.of(), new LinkedHashMap<>())));
    }

    private InferredTestObservationModule observation() {
        return new InferredTestObservationModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of());
    }

    private BuildExecutor newExecutor() throws IOException {
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
}
