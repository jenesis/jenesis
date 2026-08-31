package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.InferredJavaToolchainModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredJavaToolchainModuleTest {

    @TempDir
    private Path input, root;

    @Test
    public void compiles_and_archives_through_the_inferred_chain() throws IOException {
        Path sources = Files.createDirectories(input.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Sample.java"), "package sample; public class Sample { }");

        BuildExecutor executor = newExecutor();
        executor.addSource("input", input);
        executor.addModule("output", toolchain(), "input");
        SequencedMap<String, Path> steps = executor.execute();

        assertThat(steps).containsKeys("output/classes", "output/artifacts");
        assertThat(steps.get("output/classes").resolve(BuildStep.CLASSES).resolve("sample/Sample.class")).exists();
    }

    @Test
    public void the_generator_override_switches_off_source_generation() throws IOException {
        Path sources = Files.createDirectories(input.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sources.resolve("Sample.java"), "package sample; public class Sample { }");

        BuildExecutor executor = newExecutor();
        executor.addSource("input", input);
        executor.addModule("output", toolchain().generator(null), "input");
        executor.execute();

        assertThat(root.resolve("output").resolve("generated"))
                .as("no generation stage is wired once the generator configurator is dropped")
                .doesNotExist();
        assertThat(root.resolve("output").resolve("artifacts")).exists();
    }

    @Test
    public void a_toolchain_without_a_compiler_names_what_is_missing() {
        assertThatThrownBy(() -> toolchain().compiler(null).accept(null, new LinkedHashMap<>()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("compiler");
    }

    private InferredJavaToolchainModule toolchain() {
        return new InferredJavaToolchainModule(new LinkedHashSet<>(List.of(input)), Map.of(), Map.of());
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
