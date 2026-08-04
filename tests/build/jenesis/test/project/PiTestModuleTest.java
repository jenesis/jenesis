package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.project.PiTestModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PiTestModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_step_emits_the_pitest_maven_coordinates() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("pitest", new PiTestModule(Map.of(), Map.of()), "project");
        executor.execute("pitest/required");

        SequencedProperties requires = requiresOutput();
        assertThat(requires.stringPropertyNames()).containsExactly(
                "pitest/runtime/maven/org.pitest/pitest-command-line/RELEASE",
                "pitest/runtime/maven/org.pitest/pitest-junit5-plugin/RELEASE");
    }

    @Test
    public void requires_step_honours_a_custom_tool() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("pitest", new PiTestModule(Map.of(), Map.of()).tool("custom"), "project");
        executor.execute("pitest/required");

        SequencedProperties requires = requiresOutput();
        assertThat(requires.stringPropertyNames()).containsExactly(
                "custom/runtime/maven/org.pitest/pitest-command-line/RELEASE",
                "custom/runtime/maven/org.pitest/pitest-junit5-plugin/RELEASE");
    }

    @Test
    public void requires_step_pins_the_junit_platform_launcher_from_the_engine_version() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("main/maven/org.junit.platform/junit-platform-engine", "1.11.0");
        versions.store(project.resolve(BuildStep.VERSIONS));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("pitest", new PiTestModule(Map.of(), Map.of()), "project");
        executor.execute("pitest/required");

        SequencedProperties requires = requiresOutput();
        assertThat(requires.stringPropertyNames()).containsExactly(
                "pitest/runtime/maven/org.pitest/pitest-command-line/RELEASE",
                "pitest/runtime/maven/org.pitest/pitest-junit5-plugin/RELEASE",
                "pitest/classpath/maven/org.junit.platform/junit-platform-launcher/1.11.0");
    }

    @Test
    public void requires_step_pins_the_launcher_from_commons_and_trims_at_the_first_space() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("main/maven/org.junit.platform/junit-platform-commons", "1.11.0 SHA-256/abc");
        versions.store(project.resolve(BuildStep.VERSIONS));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("pitest", new PiTestModule(Map.of(), Map.of()), "project");
        executor.execute("pitest/required");

        SequencedProperties requires = requiresOutput();
        assertThat(requires.stringPropertyNames()).containsExactly(
                "pitest/runtime/maven/org.pitest/pitest-command-line/RELEASE",
                "pitest/runtime/maven/org.pitest/pitest-junit5-plugin/RELEASE",
                "pitest/classpath/maven/org.junit.platform/junit-platform-launcher/1.11.0");
    }

    @Test
    public void mutate_step_rejects_a_jar_entry_that_escapes_the_extraction_root() throws IOException {
        SequencedProperties index = new SequencedProperties();
        index.setProperty("main/runtime/maven/org.evil/evil", "evil.jar");
        index.store(project.resolve(BuildStep.DEPENDENCIES));
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(project.resolve("evil.jar")))) {
            jar.putNextEntry(new JarEntry("../escape.class"));
            jar.write(new byte[] {1, 2, 3});
            jar.closeEntry();
        }

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "pitest",
                new PiTestModule(Map.of("maven", files()), Map.of("maven", Resolver.identity())),
                "project");

        assertThatThrownBy(() -> executor.execute("pitest/mutate"))
                .hasStackTraceContaining("escapes the extraction root");
    }

    private Repository files() {
        return (_, coordinate) -> {
            Path file = Files.write(
                    Files.createDirectories(root.resolve("served")).resolve(coordinate.replace('/', '-') + ".jar"),
                    coordinate.getBytes(StandardCharsets.UTF_8));
            return Optional.of(RepositoryItem.ofFile(file));
        };
    }

    private SequencedProperties requiresOutput() throws IOException {
        Path requiredOutput = root.resolve("pitest").resolve("required").resolve("output");
        return SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false);
    }
}
