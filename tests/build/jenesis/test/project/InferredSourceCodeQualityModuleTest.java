package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.InferredSourceCodeQualityModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredSourceCodeQualityModuleTest {

    private final Map<String, String> settings = new HashMap<>();

    @TempDir
    private Path root, project;

    @Test
    public void wires_checkstyle_when_its_config_file_is_present() throws IOException {
        Files.writeString(project.resolve("checkstyle.xml"), "<module name=\"Checker\"/>");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("quality/checkstyle/tool/required");

        Path requiredOutput = root.resolve("quality").resolve("checkstyle").resolve("tool").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("checkstyle/runtime/maven/com.puppycrawl.tools/checkstyle/RELEASE");
    }

    @Test
    public void binds_the_files_checkstyle_xml_references_by_config_loc_beside_it() throws IOException {
        Files.writeString(project.resolve("checkstyle.xml"), """
                <module name="Checker">
                    <module name="SuppressionFilter"><property name="file" value="${config_loc}/suppressions.xml"/></module>
                    <module name="SuppressionFilter"><property name="file" value="${config_loc}/rules/more.xml"/></module>
                </module>
                """);
        Files.writeString(project.resolve("suppressions.xml"), "<suppressions/>");
        Files.writeString(Files.createDirectory(project.resolve("rules")).resolve("more.xml"), "<suppressions/>");
        Files.writeString(project.resolve("unrelated.xml"), "<unrelated/>");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("quality/checkstyle/configuration");

        Path configuration = root.resolve("quality").resolve("checkstyle").resolve("configuration").resolve("output");
        assertThat(configuration.resolve("checkstyle.xml")).exists();
        assertThat(configuration.resolve("suppressions.xml")).hasContent("<suppressions/>");
        assertThat(configuration.resolve("rules").resolve("more.xml")).hasContent("<suppressions/>");
        assertThat(configuration.resolve("unrelated.xml"))
                .as("only what the configuration references becomes an input of Checkstyle")
                .doesNotExist();
    }

    @Test
    public void refuses_a_config_loc_reference_outside_the_folder_of_checkstyle_xml() throws IOException {
        Files.writeString(project.resolve("checkstyle.xml"), """
                <module name="Checker">
                    <module name="SuppressionFilter"><property name="file" value="${config_loc}/../suppressions.xml"/></module>
                </module>
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("${config_loc}/../suppressions.xml");
    }

    @Test
    public void skips_checkstyle_when_its_config_file_is_absent() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("checkstyle"))
                .as("Checkstyle is not wired when checkstyle.xml is absent from the project")
                .doesNotExist();
    }

    @Test
    public void the_checkstyle_override_switches_off_checkstyle() throws IOException {
        Files.writeString(project.resolve("checkstyle.xml"), "<module name=\"Checker\"/>");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality",
                new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).checkstyle(null),
                "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("checkstyle"))
                .as("Checkstyle is not wired once its configurator is dropped, even with checkstyle.xml present")
                .doesNotExist();
    }

    @Test
    public void a_provider_switches_off_the_tool_it_names() throws IOException {
        Files.writeString(project.resolve("checkstyle.xml"), "<module name=\"Checker\"/>");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", InferredSourceCodeQualityModule.ofEnvironment(new Environment(Map.of("source.checkstyle", "false")),
                new LinkedHashSet<>(List.of(project)),
                Map.of(),
                Map.of()), "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("checkstyle"))
                .as("a build is switched off through the provider it was handed, not through the JVM it runs in")
                .doesNotExist();
    }

    @Test
    public void wires_every_tool_when_it_is_given_no_provider() throws IOException {
        Files.writeString(project.resolve("checkstyle.xml"), "<module name=\"Checker\"/>");
        settings.put("source.checkstyle", "false");
        try {
            BuildExecutor executor = newExecutor();
            executor.addSource("project", project);
            executor.addModule("quality",
                    new InferredSourceCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()),
                    "project");
            executor.execute("quality/checkstyle/tool/required");

            assertThat(root.resolve("quality").resolve("checkstyle"))
                    .as("a module a caller builds itself takes its defaults, whatever the environment says")
                    .exists();
        } finally {
            settings.remove("source.checkstyle");
        }
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
