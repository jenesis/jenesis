package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStepResult;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.InferredComplianceModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredComplianceModuleTest {

    @TempDir
    private Path root;

    @Test
    public void license_check_is_wired_when_a_licensing_properties_file_exists() throws Exception {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        Files.writeString(configuration.resolve("licensing.properties"), "allowed=Apache\n");
        assertThat(execute(new InferredComplianceModule(new LinkedHashSet<>(List.of(configuration))), "compliance/license/check"))
                .containsKey("compliance/license/check");
    }

    @Test
    public void license_check_is_omitted_without_a_licensing_properties_file() throws Exception {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        assertThatThrownBy(() -> execute(new InferredComplianceModule(new LinkedHashSet<>(List.of(configuration))), "compliance/license/check"))
                .rootCause()
                .hasMessageContaining("license");
    }

    @Test
    public void vulnerability_check_is_wired_when_a_vulnerability_properties_file_exists() throws Exception {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        Files.writeString(configuration.resolve("vulnerability.properties"), "severity=high\n");
        assertThat(execute(new InferredComplianceModule(new LinkedHashSet<>(List.of(configuration))), "compliance/vulnerability/check"))
                .containsKey("compliance/vulnerability/check");
    }

    @Test
    public void checks_are_omitted_when_compliance_is_disabled() throws Exception {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        Files.writeString(configuration.resolve("licensing.properties"), "allowed=Apache\n");
        Files.writeString(configuration.resolve("vulnerability.properties"), "severity=high\n");
        InferredComplianceModule disabled = new InferredComplianceModule(new LinkedHashSet<>(List.of(configuration)))
                .license(null)
                .vulnerability(null);
        assertThatThrownBy(() -> execute(disabled, "compliance/license/check"))
                .rootCause()
                .hasMessageContaining("license");
        assertThatThrownBy(() -> execute(disabled, "compliance/vulnerability/check"))
                .rootCause()
                .hasMessageContaining("vulnerability");
    }

    @Test
    public void a_check_is_omitted_when_its_properties_file_is_empty() throws Exception {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        Files.writeString(configuration.resolve("licensing.properties"), "");
        assertThatThrownBy(() -> execute(new InferredComplianceModule(new LinkedHashSet<>(List.of(configuration))), "compliance/license/check"))
                .rootCause()
                .hasMessageContaining("license");
    }

    @Test
    public void an_unknown_licensing_property_fails_the_build() throws Exception {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        Files.writeString(configuration.resolve("licensing.properties"), "allowed=Apache\nbogus=x\n");
        assertThatThrownBy(() -> execute(new InferredComplianceModule(new LinkedHashSet<>(List.of(configuration))), "compliance/license/check"))
                .rootCause()
                .hasMessageContaining("bogus");
    }

    @Test
    public void an_unknown_vulnerability_property_fails_the_build() throws Exception {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        Files.writeString(configuration.resolve("vulnerability.properties"), "severity=high\nbogus=x\n");
        assertThatThrownBy(() -> execute(new InferredComplianceModule(new LinkedHashSet<>(List.of(configuration))), "compliance/vulnerability/check"))
                .rootCause()
                .hasMessageContaining("bogus");
    }

    @Test
    public void a_custom_module_runs_beside_a_stock_one_of_the_same_name_with_the_inputs_of_the_module() throws Exception {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        Files.writeString(configuration.resolve("licensing.properties"), "allowed=Apache\n");
        SequencedMap<String, BuildExecutorModule> custom = new LinkedHashMap<>();
        custom.put("license", (executor, inherited) -> executor.addStep("report", (_, context, arguments) -> {
            Files.writeString(context.next().resolve("inputs.txt"), String.join("\n", arguments.sequencedKeySet()));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }, inherited.sequencedKeySet()));
        SequencedMap<String, Path> outputs = execute(new InferredComplianceModule(new LinkedHashSet<>(List.of(configuration))).custom(custom),
                "compliance/license/check",
                "compliance/custom/license/report");
        assertThat(outputs).containsKeys("compliance/license/check", "compliance/custom/license/report");
        assertThat(Files.readAllLines(outputs.get("compliance/custom/license/report").resolve("inputs.txt")))
                .as("a custom module reads what the module it is added to reads")
                .singleElement()
                .satisfies(input -> assertThat(input).endsWith("input"));
    }

    @Test
    public void a_custom_step_is_added_by_name_and_answers_for_its_output() throws Exception {
        Path configuration = Files.createDirectories(root.resolve("configuration"));
        SequencedMap<String, Path> outputs = execute(new InferredComplianceModule(new LinkedHashSet<>(List.of(configuration)))
                        .custom("report", (_, context, _) -> {
                            Files.writeString(context.next().resolve("report.txt"), "reported");
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        }),
                "compliance/custom/report");
        assertThat(outputs.get("compliance/custom/report").resolve("report.txt")).content().isEqualTo("reported");
    }

    @Test
    public void refuses_a_custom_module_under_a_name_that_is_added_already() {
        SequencedMap<String, BuildExecutorModule> custom = new LinkedHashMap<>();
        custom.put("first", (_, _) -> {});
        InferredComplianceModule module = new InferredComplianceModule(new LinkedHashSet<>())
                .custom(custom)
                .custom("second", (_, _) -> {});
        assertThatThrownBy(() -> module.custom("first", (_, _) -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A custom module named first is added already");
    }

    private SequencedMap<String, Path> execute(InferredComplianceModule module, String... selectors) throws IOException {
        Path build = Files.createDirectories(root.resolve("build"));
        Path input = Files.createDirectories(root.resolve("input"));
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
        executor.addSource("input", input);
        executor.addModule("compliance", module, "input");
        return executor.execute(Runnable::run, selectors).toCompletableFuture().join();
    }
}
