package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.InferredTestObservationModule;
import build.jenesis.project.TestModule;

import static org.assertj.core.api.Assertions.assertThat;

public class InferredTestObservationModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void wires_jacoco_when_a_jacoco_properties_file_is_present() throws IOException {
        Files.writeString(project.resolve("jacoco.properties"), "");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}), "project");
        executor.execute("observed/jacoco/required");

        Path requiredOutput = root.resolve("observed").resolve("jacoco").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("jacoco/runtime/maven/org.jacoco/org.jacoco.cli/RELEASE");
    }

    @Test
    public void does_not_wire_an_engine_without_a_config_file() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}), "project");
        executor.execute();

        assertThat(root.resolve("observed").resolve("jacoco"))
                .as("no observation engine is wired without its config file")
                .doesNotExist();
    }

    @Test
    public void the_observe_override_switches_off_jacoco() throws IOException {
        Files.writeString(project.resolve("jacoco.properties"), "");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}).jacoco(null), "project");
        executor.execute();

        assertThat(root.resolve("observed").resolve("jacoco")).doesNotExist();
    }

    @Test
    public void the_test_configurator_decorates_the_inferred_test_module() throws IOException {
        Files.writeString(project.resolve("jacoco.properties"), "");
        List<TestModule> decorated = new ArrayList<>();
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(module -> {
            decorated.add(module);
            return (_, _) -> {};
        }), "project");
        executor.execute("observed/jacoco/required");

        assertThat(decorated)
                .as("the observation module hands its own test module to the configurator")
                .hasSize(1);
    }

    @Test
    public void the_test_override_switches_off_the_test_run_and_its_reports() throws IOException {
        Files.writeString(project.resolve("jacoco.properties"), "");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(null), "project");
        executor.execute();

        assertThat(root.resolve("observed").resolve("test")).doesNotExist();
        assertThat(root.resolve("observed").resolve("jacoco"))
                .as("an observation report is not wired without the test run it observes")
                .doesNotExist();
    }

    @Test
    public void wires_mutate_when_a_pitest_config_is_present() throws IOException {
        Files.writeString(project.resolve("pitest.properties"), "targetClasses=sample.*\ntargetTests=sample.*\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}), "project");
        executor.execute("observed/mutate/required");

        Path requiredOutput = root.resolve("observed").resolve("mutate").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactlyInAnyOrder(
                        "pitest/runtime/maven/org.pitest/pitest-command-line/RELEASE",
                        "pitest/runtime/maven/org.pitest/pitest-junit5-plugin/RELEASE");
    }

    @Test
    public void does_not_wire_mutate_without_a_pitest_config() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}), "project");
        executor.execute();

        assertThat(root.resolve("observed").resolve("mutate"))
                .as("mutation testing is not wired without pitest.properties")
                .doesNotExist();
    }

    @Test
    public void the_mutate_override_switches_off_pitest() throws IOException {
        Files.writeString(project.resolve("pitest.properties"), "targetClasses=sample.*\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("observed", observation().test(_ -> (_, _) -> {}).pitest(null), "project");
        executor.execute();

        assertThat(root.resolve("observed").resolve("mutate")).doesNotExist();
    }

    private InferredTestObservationModule observation() {
        return new InferredTestObservationModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of());
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
