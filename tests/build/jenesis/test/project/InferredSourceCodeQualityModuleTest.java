package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.InferredSourceCodeQualityModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredSourceCodeQualityModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void runs_the_checks_it_is_handed_over_the_sources() throws IOException {
        Files.writeString(project.resolve("Sample.java"), "class Sample {}");
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(), Map.of(), Map.of()).custom("lines", (_, context, arguments) -> {
            Files.writeString(context.next().resolve("checked.txt"), String.valueOf(arguments.size()));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }), "project");

        assertThat(executor.execute().get("quality/custom/lines").resolve("checked.txt")).hasContent("1");
    }

    @Test
    public void refuses_a_check_whose_name_is_taken() {
        InferredSourceCodeQualityModule module = new InferredSourceCodeQualityModule(new LinkedHashSet<>(), Map.of(), Map.of())
                .custom("lines", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true)));

        assertThatThrownBy(() -> module.custom("lines", (_, _, _) -> CompletableFuture.completedStage(new BuildStepResult(true))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is added already");
    }

    @Test
    public void switches_a_linter_on_by_its_conventional_file_where_no_properties_are_found() throws IOException {
        Files.writeString(project.resolve("pmd.xml"), "<ruleset/>");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("quality/" + InferredSourceCodeQualityModule.PMD + "/inputs");

        assertThat(root.resolve("quality/" + InferredSourceCodeQualityModule.PMD + "/inputs/rules/bound/output/pmd.xml"))
                .as("the conventional file of a configuration folder switches its tool on and is handed to it")
                .hasContent("<ruleset/>");
    }

    @Test
    public void switches_a_linter_off_by_its_boolean_setting() throws IOException {
        Files.writeString(project.resolve("pmd.xml"), "<ruleset/>");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", InferredSourceCodeQualityModule.ofEnvironment(new Environment(Map.of("check.pmd", "false")),
                new LinkedHashSet<>(List.of(project)),
                Map.of(),
                Map.of()), "project");
        executor.execute();

        assertThat(root.resolve("quality/" + InferredSourceCodeQualityModule.PMD))
                .as("a linter switched off is not wired, whatever file would switch it on")
                .doesNotExist();
    }

    @Test
    public void refuses_a_plugin_setting_that_is_no_boolean_naming_the_valid_values() throws IOException {
        assertThatThrownBy(() -> InferredSourceCodeQualityModule.ofEnvironment(new Environment(Map.of("check.pmd", "ignore")),
                new LinkedHashSet<>(List.of(project)),
                Map.of(),
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.check.pmd")
                .hasMessageContaining("expected true, false");
    }

    @Test
    public void reads_no_conventional_file_where_properties_are_found() throws IOException {
        Files.writeString(project.resolve("pmd.xml"), "<ruleset/>");
        Files.writeString(project.resolve("pmd.properties"), "");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .as("properties name the file themselves, so a conventional file beside them is never taken for it")
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pmd.properties names no configuration - add configuration=pmd.xml");
    }

    @Test
    public void binds_the_file_checkstyle_properties_names_with_the_siblings_it_references() throws IOException {
        Path rules = Files.createDirectories(project.resolve("rules"));
        Files.writeString(rules.resolve("company.xml"), """
                <module name="Checker">
                  <module name="SuppressionFilter">
                    <property name="file" value="${config_loc}/suppressions.xml"/>
                  </module>
                </module>
                """);
        Files.writeString(rules.resolve("suppressions.xml"), "<suppressions/>");
        Files.writeString(project.resolve("checkstyle.properties"), "configuration=rules/company.xml\nseverity=warning\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("quality/" + InferredSourceCodeQualityModule.CHECKSTYLE + "/inputs");

        Path bound = root.resolve("quality/" + InferredSourceCodeQualityModule.CHECKSTYLE + "/inputs/rules/bound/output");
        assertThat(bound.resolve("checkstyle.xml"))
                .as("the file the properties name is handed to Checkstyle under the name it reads")
                .content()
                .contains("SuppressionFilter");
        assertThat(bound.resolve("suppressions.xml"))
                .as("what the configuration references beside it is handed along")
                .exists();
    }

    @Test
    public void refuses_linter_properties_that_name_no_configuration_naming_the_key() throws IOException {
        Files.writeString(project.resolve("pmd.properties"), "");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pmd.properties names no configuration - add configuration=pmd.xml");
    }

    @Test
    public void refuses_a_configuration_that_names_no_file() throws IOException {
        Files.writeString(project.resolve("detekt.properties"), "configuration=missing.yml\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names configuration=missing.yml")
                .hasMessageContaining("is no file");
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
