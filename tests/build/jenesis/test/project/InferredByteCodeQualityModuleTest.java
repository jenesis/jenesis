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
import build.jenesis.project.InferredByteCodeQualityModule;

import static org.assertj.core.api.Assertions.assertThat;

public class InferredByteCodeQualityModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void wires_spotbugs_when_its_filter_file_is_present() throws IOException {
        Files.writeString(project.resolve("spotbugs-exclude.xml"), "<FindBugsFilter/>");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("quality/spotbugs/tool/required");

        Path requiredOutput = root.resolve("quality").resolve("spotbugs").resolve("tool").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("spotbugs/runtime/maven/com.github.spotbugs/spotbugs/RELEASE");
    }

    @Test
    public void skips_spotbugs_when_no_filter_file_is_present() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("spotbugs"))
                .as("SpotBugs is not wired when neither spotbugs-exclude.xml nor spotbugs.xml is present")
                .doesNotExist();
    }

    @Test
    public void the_spotbugs_override_switches_off_spotbugs() throws IOException {
        Files.writeString(project.resolve("spotbugs-exclude.xml"), "<FindBugsFilter/>");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality",
                new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).spotbugs(null),
                "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("spotbugs"))
                .as("SpotBugs is not wired once its configurator is dropped, even with a filter file present")
                .doesNotExist();
    }

    @Test
    public void wires_forbiddenapis_with_its_signatures_when_its_configuration_is_present() throws IOException {
        Files.writeString(project.resolve("forbiddenapis.properties"), "bundled=jdk-unsafe\nsignatures=forbidden.txt\n");
        Files.writeString(project.resolve("forbidden.txt"), "java.util.Vector\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("quality/forbiddenapis/configuration", "quality/forbiddenapis/tool/required");

        assertThat(root.resolve("quality").resolve("forbiddenapis").resolve("configuration").resolve("output"))
                .as("the signatures file is handed over beside the configuration")
                .isDirectoryContaining(path -> path.getFileName().toString().equals("forbidden.txt"))
                .isDirectoryContaining(path -> path.getFileName().toString().equals("forbiddenapis.properties"));
        SequencedProperties requires = SequencedProperties.ofFiles(root.resolve("quality").resolve("forbiddenapis")
                .resolve("tool").resolve("required").resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("forbiddenapis/runtime/maven/de.thetaphi/forbiddenapis/RELEASE");
    }

    @Test
    public void the_forbiddenapis_override_switches_off_forbiddenapis() throws IOException {
        Files.writeString(project.resolve("forbiddenapis.properties"), "bundled=jdk-unsafe\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality",
                new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).forbiddenapis(null),
                "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("forbiddenapis")).doesNotExist();
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
