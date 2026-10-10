package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.project.DokkaDocumentationModule;
import build.jenesis.step.ProcessHandler;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DokkaDocumentationModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void a_failing_dokka_run_fails_the_step_naming_what_dokka_printed() throws IOException {
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sample.resolve("Sample.kt"), "package sample\nclass Sample");
        Path jar = Files.createFile(project.resolve("dokka.jar"));
        ToolProvider failing = new ToolProvider() {
            @Override
            public String name() {
                return "dokka";
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                err.println("Dokka could not analyse sample/Sample.kt");
                return 1;
            }
        };
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
        executor.addSource("project", project);
        executor.addModule("dokka",
                new DokkaDocumentationModule(
                        Map.of("maven", (_, _, _) -> Optional.of(RepositoryItem.ofFile(jar))),
                        Map.of("maven", Resolver.identity()))
                        .factory(ProcessHandler.OfTool.of(failing)),
                "project");
        assertThatThrownBy(executor::execute)
                .as("documentation that Dokka could not render is not shipped as if it had been")
                .rootCause()
                .hasMessageContaining("Unexpected exit code: 1")
                .hasMessageContaining("Dokka could not analyse sample/Sample.kt");
    }
}
