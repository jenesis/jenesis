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
import build.jenesis.project.LauncherModule;

import static org.assertj.core.api.Assertions.assertThat;

public class LauncherModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_step_emits_the_launcher_maven_coordinate() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("launcher", new LauncherModule(Map.of(), Map.of()), "project");
        executor.execute("launcher/required");

        Path requiredOutput = root.resolve("launcher").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("launcher/runtime/maven/build.jenesis/build.jenesis.launcher/RELEASE");
    }

    @Test
    public void requires_step_honours_a_custom_group() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("launcher", new LauncherModule(Map.of(), Map.of()).group("bootstrap"), "project");
        executor.execute("launcher/required");

        Path requiredOutput = root.resolve("launcher").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("bootstrap/runtime/maven/build.jenesis/build.jenesis.launcher/RELEASE");
    }

    @Test
    public void dependencies_step_resolves_the_launcher_under_its_group() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "launcher",
                new LauncherModule(Map.of("maven", files()), Map.of("maven", Resolver.identity())),
                "project");
        executor.execute("launcher/dependencies");

        Path resolvedOutput = root.resolve("launcher").resolve("dependencies").resolve("output");
        SequencedProperties resolved = SequencedProperties.ofFiles(resolvedOutput.resolve(BuildStep.DEPENDENCIES));
        assertThat(resolved.stringPropertyNames())
                .containsExactly("launcher/runtime/maven/build.jenesis/build.jenesis.launcher/RELEASE");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }

    private Repository files() {
        return (_, coordinate) -> {
            Path file = Files.write(
                    Files.createDirectories(root.resolve("served")).resolve(coordinate.replace('/', '-') + ".jar"),
                    coordinate.getBytes(StandardCharsets.UTF_8));
            return Optional.of(RepositoryItem.ofFile(file));
        };
    }
}
