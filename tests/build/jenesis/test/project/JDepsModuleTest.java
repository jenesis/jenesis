package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.JDepsModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JDepsModuleTest {

    @TempDir
    private Path root, project, sources;

    @Test
    public void reports_a_jdk_internal_api_with_the_replacement_jdeps_suggests() throws IOException {
        compile();

        newExecutor(new JDepsModule()).execute();

        assertThat(report()).hasContent(
                "sample.Sample -> sun.misc.Unsafe: JDK internal API (jdk.unsupported), instead: See https://openjdk.org/jeps/260");
    }

    @Test
    public void fails_the_build_on_a_jdk_internal_api_when_strict() throws IOException {
        compile();

        assertThatThrownBy(newExecutor(new JDepsModule().strict(true))::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jdeps found 1 finding")
                .hasMessageContaining("jenesis.strict.jdeps is true");
    }

    @Test
    public void accepts_the_jdk_internal_apis_it_is_given() throws IOException {
        compile();

        newExecutor(new JDepsModule().strict(true).accepted(List.of("sun.misc"))).execute();

        assertThat(report()).as("an accepted package is no finding").isEmptyFile();
    }

    private void compile() throws IOException {
        Path source = Files.createDirectories(sources.resolve("sample")).resolve("Sample.java");
        Files.writeString(source, """
                package sample;
                public class Sample {
                    public Object unsafe() throws ReflectiveOperationException {
                        return sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                    }
                }
                """);
        Path classes = Files.createDirectories(project.resolve(BuildStep.CLASSES));
        int rc = javax.tools.ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-XDignore.symbol.file", "-d", classes.toString(), source.toString());
        assertThat(rc).as("the sample compiles").isZero();
    }

    private Path report() {
        return root.resolve("jdeps").resolve("check").resolve("output")
                .resolve("reports").resolve("jdeps").resolve("jdeps.txt");
    }

    private BuildExecutor newExecutor(JDepsModule module) throws IOException {
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
        executor.addModule("jdeps", module, "project");
        return executor;
    }
}
