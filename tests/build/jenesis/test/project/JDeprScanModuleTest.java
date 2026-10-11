package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.JDeprScanModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JDeprScanModuleTest {

    @TempDir
    private Path root, project, sources;

    @Test
    public void reports_the_apis_deprecated_for_removal_alone_by_default() throws IOException {
        compile("");

        newExecutor(new JDeprScanModule()).execute();

        assertThat(report())
                .content()
                .contains("sample/Sample uses deprecated method java/lang/Thread::stop()V (forRemoval=true)")
                .as("an API deprecated without being scheduled for removal is no finding by default")
                .doesNotContain("getYear");
    }

    @Test
    public void reports_every_deprecated_api_when_removal_is_switched_off() throws IOException {
        compile("removal=false\n");

        newExecutor(new JDeprScanModule()).execute();

        assertThat(report())
                .content()
                .contains("java/lang/Thread::stop()V")
                .contains("java/util/Date::getYear()I");
    }

    @Test
    public void fails_the_build_on_an_api_deprecated_for_removal_when_strict() throws IOException {
        compile("");

        assertThatThrownBy(newExecutor(new JDeprScanModule().strict(true))::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jdeprscan found 1 finding")
                .hasMessageContaining("jenesis.validator.jdeprscan=strict");
    }

    @Test
    public void refuses_an_unknown_key_and_a_release_that_is_no_number() throws IOException {
        compile("target=21\n");
        assertThatThrownBy(newExecutor(new JDeprScanModule())::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jdeprscan.properties names target");

        Files.writeString(project.resolve(JDeprScanModule.CONFIGURATION), "release=latest\n");
        assertThatThrownBy(newExecutor(new JDeprScanModule())::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names the release latest");
    }

    private void compile(String configuration) throws IOException {
        Files.writeString(project.resolve(JDeprScanModule.CONFIGURATION), configuration);
        Path source = Files.createDirectories(sources.resolve("sample")).resolve("Sample.java");
        Files.writeString(source, """
                package sample;
                public class Sample {
                    public int halt(Thread thread, java.util.Date date) {
                        thread.stop();
                        return date.getYear();
                    }
                }
                """);
        Path classes = Files.createDirectories(project.resolve(BuildStep.CLASSES));
        int rc = javax.tools.ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classes.toString(), source.toString());
        assertThat(rc).as("the sample compiles").isZero();
    }

    private Path report() {
        return root.resolve("jdeprscan").resolve("check").resolve("output")
                .resolve("reports").resolve("jdeprscan").resolve("jdeprscan.txt");
    }

    private BuildExecutor newExecutor(JDeprScanModule module) throws IOException {
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
        executor.addModule("jdeprscan", module, "project");
        return executor;
    }
}
