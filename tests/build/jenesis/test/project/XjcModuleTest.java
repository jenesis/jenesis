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
import build.jenesis.project.XjcModule;

import static org.assertj.core.api.Assertions.assertThat;

public class XjcModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_step_emits_the_xjc_maven_coordinate() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("xjc", new XjcModule(Map.of(), Map.of()), "project");
        executor.execute("xjc/required");

        Path requiredOutput = root.resolve("xjc").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("xjc/runtime/maven/org.glassfish.jaxb/jaxb-xjc/RELEASE");
    }

    @Test
    public void tool_emits_an_independent_resolution_trail() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.example/library/1.0", "");
        requires.store(project.resolve(BuildStep.REQUIRES));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "xjc",
                new XjcModule(Map.of("maven", files()), Map.of("maven", Resolver.identity())).tool("binding"),
                "project");
        executor.execute("xjc/dependencies");

        Path resolvedOutput = root.resolve("xjc").resolve("dependencies").resolve("output");
        SequencedProperties resolved = SequencedProperties.ofFiles(resolvedOutput.resolve(BuildStep.DEPENDENCIES));
        assertThat(resolved.stringPropertyNames())
                .as("the module's own closure is resolved by the module, not again by every tool")
                .containsExactly("binding/runtime/maven/org.glassfish.jaxb/jaxb-xjc/RELEASE");
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
