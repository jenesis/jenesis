package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.step.ErrorProne;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ErrorProneTest {

    @TempDir
    private Path root, manifests;

    @Test
    public void names_the_plugin_comment_of_a_pom_when_a_pom_module_declares_no_plugin() throws IOException {
        SequencedProperties module = new SequencedProperties();
        module.setProperty("path", "");
        module.setProperty("modular", "false");
        module.store(manifests.resolve(BuildStep.MODULE));

        assertThatThrownBy(this::execute).rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("<!--jenesis.plugin javac maven/com.google.errorprone/error_prone_core-->")
                .hasMessageContaining("pom.xml");
    }

    @Test
    public void names_the_plugin_tag_of_a_module_declaration_when_a_described_module_declares_no_plugin()
            throws IOException {
        SequencedProperties module = new SequencedProperties();
        module.setProperty("path", "");
        module.setProperty("module", "sample");
        module.setProperty("modular", "true");
        module.store(manifests.resolve(BuildStep.MODULE));

        assertThatThrownBy(this::execute).rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("@jenesis.plugin javac maven/com.google.errorprone/error_prone_core")
                .hasMessageContaining("module declaration");
    }

    private void execute() throws IOException {
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("manifests", manifests);
        executor.addStep("errorprone", new ErrorProne(), "manifests");
        executor.execute(Runnable::run).toCompletableFuture().join();
    }
}
