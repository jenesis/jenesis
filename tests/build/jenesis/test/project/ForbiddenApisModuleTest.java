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
import build.jenesis.project.ForbiddenApisModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ForbiddenApisModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_step_emits_the_forbiddenapis_maven_coordinate() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("forbiddenapis", new ForbiddenApisModule(Map.of(), Map.of()), "project");
        executor.execute("forbiddenapis/required");

        Path requiredOutput = root.resolve("forbiddenapis").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("forbiddenapis/runtime/maven/de.thetaphi/forbiddenapis/RELEASE");
    }

    @Test
    public void hands_over_the_signatures_files_beside_the_configuration() throws IOException {
        Path configuration = Files.writeString(project.resolve(ForbiddenApisModule.CONFIGURATION), """
                bundled=jdk-unsafe
                signatures=forbidden.txt, rules/more.txt
                """);
        Files.writeString(project.resolve("forbidden.txt"), "java.lang.Thread#<init>(java.lang.Runnable)\n");
        Files.writeString(Files.createDirectories(project.resolve("rules")).resolve("more.txt"), "java.util.Vector\n");

        assertThat(ForbiddenApisModule.siblings(configuration))
                .containsExactly(Path.of("forbidden.txt"), Path.of("rules", "more.txt"));
    }

    @Test
    public void refuses_a_configuration_with_an_unknown_key_or_without_signatures() throws IOException {
        Path configuration = project.resolve(ForbiddenApisModule.CONFIGURATION);

        Files.writeString(configuration, "bundled=jdk-unsafe\ntarget=17\n");
        assertThatThrownBy(() -> ForbiddenApisModule.siblings(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names target");

        Files.writeString(configuration, "suppress=**.SuppressForbidden\n");
        assertThatThrownBy(() -> ForbiddenApisModule.siblings(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no signatures");

        Files.writeString(configuration, "bundled=jdk-unsafe\nallowmissingclasses=yes\n");
        assertThatThrownBy(() -> ForbiddenApisModule.siblings(configuration))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void refuses_a_signatures_file_that_is_missing_or_outside_the_configuration_folder() throws IOException {
        Path configuration = project.resolve(ForbiddenApisModule.CONFIGURATION);

        Files.writeString(configuration, "signatures=missing.txt\n");
        assertThatThrownBy(() -> ForbiddenApisModule.siblings(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing.txt, which does not exist");

        Files.writeString(configuration, "signatures=../outside.txt\n");
        assertThatThrownBy(() -> ForbiddenApisModule.siblings(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not beside or below it");
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
