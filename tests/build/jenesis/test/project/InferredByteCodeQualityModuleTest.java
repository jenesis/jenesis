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
    public void wires_spotbugs_when_its_filter_file_is_present() throws IOException {
        Files.writeString(project.resolve("spotbugs-exclude.xml"), "<FindBugsFilter/>");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("quality/spotbugs/tool/required");

        Path requiredOutput = root.resolve("quality").resolve("spotbugs").resolve("tool").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("spotbugs/runtime/maven/com.github.spotbugs/spotbugs/RELEASE");
    }

    @Test
    public void skips_spotbugs_when_no_filter_file_is_present() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("spotbugs"))
                .as("SpotBugs is not wired when neither spotbugs-exclude.xml nor spotbugs.xml is present")
                .doesNotExist();
    }

    @Test
    public void the_spotbugs_override_switches_off_spotbugs() throws IOException {
        Files.writeString(project.resolve("spotbugs-exclude.xml"), "<FindBugsFilter/>");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality",
                new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).spotbugs(null),
                "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("spotbugs"))
                .as("SpotBugs is not wired once its configurator is dropped, even with a filter file present")
                .doesNotExist();
    }

    @Test
    public void wires_forbiddenapis_with_its_signatures_when_its_configuration_is_present() throws IOException {
        Files.writeString(project.resolve("forbiddenapis.properties"), "bundled=jdk-unsafe\nsignatures=forbidden.txt, rules/more.txt\n");
        Files.writeString(project.resolve("forbidden.txt"), "java.util.Vector\n");
        Files.writeString(Files.createDirectories(project.resolve("rules")).resolve("more.txt"), "java.util.Stack\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality", new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("quality/forbiddenapis/signatures", "quality/forbiddenapis/tool/required");

        Path signatures = root.resolve("quality").resolve("forbiddenapis").resolve("signatures").resolve("output");
        assertThat(signatures.resolve("forbidden.txt")).as("a signatures file is handed over at its path below the configuration").hasContent("java.util.Vector");
        assertThat(signatures.resolve("rules").resolve("more.txt")).hasContent("java.util.Stack");
        assertThat(signatures.resolve("forbiddenapis.properties"))
                .as("the configuration is read by the inference and handed to the module as values, not as a file")
                .doesNotExist();
        SequencedProperties requires = SequencedProperties.ofFiles(root.resolve("quality").resolve("forbiddenapis")
                .resolve("tool").resolve("required").resolve("output").resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("forbiddenapis/runtime/maven/de.thetaphi/forbiddenapis/RELEASE");
    }

    @Test
    public void refuses_a_forbiddenapis_configuration_with_an_unknown_key_or_without_signatures() throws IOException {
        Files.writeString(project.resolve("forbiddenapis.properties"), "bundled=jdk-unsafe\ntarget=17\n");
        assertThatThrownBy(quality()::execute)
                .hasStackTraceContaining("forbiddenapis.properties names target, but takes allowmissingclasses, bundled");

        Files.writeString(project.resolve("forbiddenapis.properties"), "suppress=**.SuppressForbidden\n");
        assertThatThrownBy(quality()::execute).hasStackTraceContaining("names no signatures");

        Files.writeString(project.resolve("forbiddenapis.properties"), "bundled=jdk-unsafe\nallowmissingclasses=yes\n");
        assertThatThrownBy(quality()::execute).hasStackTraceContaining("allowmissingclasses");
    }

    @Test
    public void refuses_a_signatures_file_that_is_missing_or_outside_the_configuration_folder() throws IOException {
        Files.writeString(project.resolve("forbiddenapis.properties"), "signatures=missing.txt\n");
        assertThatThrownBy(quality()::execute).hasStackTraceContaining("missing.txt, which does not exist");

        Files.writeString(project.resolve("forbiddenapis.properties"), "signatures=../outside.txt\n");
        assertThatThrownBy(quality()::execute).hasStackTraceContaining("not beside or below it");
    }

    @Test
    public void the_forbiddenapis_override_switches_off_forbiddenapis() throws IOException {
        Files.writeString(project.resolve("forbiddenapis.properties"), "bundled=jdk-unsafe\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("quality",
                new InferredByteCodeQualityModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).forbiddenapis(null),
                "project");
        executor.execute();

        assertThat(root.resolve("quality").resolve("forbiddenapis")).doesNotExist();
    }

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
