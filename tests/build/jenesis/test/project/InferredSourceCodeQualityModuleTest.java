package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.InferredSourceCodeQualityModule;

import static org.assertj.core.api.Assertions.assertThat;

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
        executor.addModule("quality", InferredSourceCodeQualityModule.ofEnvironment(new Environment(Map.of("source.checkstyle", "false")::get),
                new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
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
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
