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
import build.jenesis.project.InferredByteCodeQualityModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredByteCodeQualityModuleTest {

    @TempDir
    private Path root, project, sources;

    @Test
    public void wires_jdeps_and_jdeprscan_when_their_configurations_are_present() throws IOException {
        Files.writeString(project.resolve("jdeps.properties"), "");
        Files.writeString(project.resolve("jdeprscan.properties"), "");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("jdeps").resolve("check")).isDirectory();
        assertThat(root.resolve("quality").resolve("jdeprscan").resolve("check")).isDirectory();
    }

    @Test
    public void hands_jdeps_the_internal_apis_its_configuration_accepts() throws IOException {
        Files.writeString(project.resolve("jdeps.properties"), "accept=sun.misc\n");
        compile("""
                package sample;
                public class Sample {
                    public Object unsafe() throws ReflectiveOperationException {
                        return sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                    }
                }
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality",
                new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of())
                        .jdeps(jdeps -> jdeps.strict(true)),
                "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("jdeps").resolve("check").resolve("output")
                .resolve("reports").resolve("jdeps").resolve("jdeps.txt"))
                .as("an accepted package is no finding")
                .isEmptyFile();
    }

    @Test
    public void hands_jdeprscan_the_scope_its_configuration_names() throws IOException {
        Files.writeString(project.resolve("jdeprscan.properties"), "removal=false\n");
        compile("""
                package sample;
                public class Sample {
                    public int year(java.util.Date date) {
                        return date.getYear();
                    }
                }
                """);

        quality().execute();

        assertThat(root.resolve("quality").resolve("jdeprscan").resolve("check").resolve("output")
                .resolve("reports").resolve("jdeprscan").resolve("jdeprscan.txt"))
                .as("removal=false reports an API deprecated without being scheduled for removal")
                .content()
                .contains("java/util/Date::getYear()I");
    }

    @Test
    public void refuses_a_jdeps_configuration_key_other_than_accept() throws IOException {
        Files.writeString(project.resolve("jdeps.properties"), "ignore=sun.misc\n");

        assertThatThrownBy(quality()::execute)
                .hasStackTraceContaining("jdeps.properties names ignore, but takes accept=");
    }

    @Test
    public void refuses_an_unknown_jdeprscan_key_and_a_release_that_is_no_number() throws IOException {
        Files.writeString(project.resolve("jdeprscan.properties"), "target=21\n");
        assertThatThrownBy(quality()::execute).hasStackTraceContaining("jdeprscan.properties names target");

        Files.writeString(project.resolve("jdeprscan.properties"), "release=latest\n");
        assertThatThrownBy(quality()::execute).hasStackTraceContaining("names the release latest");
    }

    @Test
    public void the_jdeps_and_jdeprscan_overrides_switch_them_off() throws IOException {
        Files.writeString(project.resolve("jdeps.properties"), "");
        Files.writeString(project.resolve("jdeprscan.properties"), "");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality",
                new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of())
                        .jdeps(null)
                        .jdeprscan(null),
                "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("jdeps")).doesNotExist();
        assertThat(root.resolve("quality").resolve("jdeprscan")).doesNotExist();
    }

    private BuildExecutor quality() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        return executor;
    }

    private void compile(String sample) throws IOException {
        Path source = Files.createDirectories(sources.resolve("sample")).resolve("Sample.java");
        Files.writeString(source, sample);
        Path classes = Files.createDirectories(project.resolve(BuildStep.CLASSES));
        int rc = javax.tools.ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-XDignore.symbol.file", "-d", classes.toString(), source.toString());
        assertThat(rc).as("the sample compiles").isZero();
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
