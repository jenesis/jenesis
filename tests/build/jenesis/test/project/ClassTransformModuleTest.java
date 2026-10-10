package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.ClassTransformModule;
import build.jenesis.step.Versions;
import java.util.jar.Attributes;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassTransformModuleTest {

    @TempDir
    private Path build;

    @Test
    public void merges_the_manifest_a_transform_writes_into_the_one_it_was_handed() throws IOException {
        Manifest manifest = transformed(Versions.MANIFEST);
        assertThat(manifest.getMainAttributes().getValue("Multi-Release"))
                .as("an attribute of the compiled manifest that the transform does not set reaches the jar")
                .isEqualTo("true");
        assertThat(manifest.getMainAttributes().getValue("Bundle-SymbolicName"))
                .as("an attribute only the transform sets is added")
                .isEqualTo("sample");
        assertThat(manifest.getMainAttributes().getValue("Implementation-Version"))
                .as("the transform's value wins for an attribute it sets")
                .isEqualTo("2");
        assertThat(manifest.getAttributes("sample/").getValue("Sealed")).isEqualTo("true");
    }

    @Test
    public void merges_a_resource_manifest_a_transform_writes_into_the_one_it_was_handed() throws IOException {
        Manifest manifest = transformed(BuildStep.CLASSES + JarFile.MANIFEST_NAME);
        assertThat(manifest.getMainAttributes().getValue("Multi-Release")).isEqualTo("true");
        assertThat(manifest.getMainAttributes().getValue("Bundle-SymbolicName")).isEqualTo("sample");
        assertThat(manifest.getMainAttributes().getValue("Implementation-Version")).isEqualTo("2");
    }

    private Manifest transformed(String file) throws IOException {
        BuildExecutor executor = BuildExecutor.of(build,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addStep("compiled", new Manifested(file, "Multi-Release: true\nImplementation-Version: 1\n"));
        executor.addModule("transform",
                new ClassTransformModule(new LinkedHashMap<>(Map.of("osgi", new Manifested(file,
                        "Bundle-SymbolicName: sample\nImplementation-Version: 2\n\nName: sample/\nSealed: true\n")
                        .asModule("osgi")))),
                "compiled");
        SequencedMap<String, Path> results = executor.execute(Runnable::run).toCompletableFuture().join();
        try (InputStream in = Files.newInputStream(results.get("transform").resolve(file))) {
            return new Manifest(in);
        }
    }

    private record Manifested(String file, String attributes) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            Files.writeString(Files.createDirectories(context.next().resolve(BuildStep.CLASSES + "sample"))
                    .resolve("Sample.class"), "sample");
            Path manifest = context.next().resolve(file);
            Files.createDirectories(manifest.getParent());
            Files.writeString(manifest, "Manifest-Version: 1.0\n" + attributes + "\n");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
