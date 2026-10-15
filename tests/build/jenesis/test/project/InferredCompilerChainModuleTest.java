package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.InferredCompilerChainModule;
import build.jenesis.step.ProcessBuildStep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredCompilerChainModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void java_only_project_runs_only_javac() throws IOException {
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("OnlyJava.java"), """
                package sample;
                public class OnlyJava {
                    public String greet() { return "Hello from Java"; }
                }
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "chain",
                new InferredCompilerChainModule(Map.of(), Map.of()),
                "project");
        executor.execute();

        Path javaClasses = root
                .resolve("chain")
                .resolve(InferredCompilerChainModule.COMPILE)
                .resolve(InferredCompilerChainModule.JAVAC)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        assertThat(javaClasses.resolve("sample/OnlyJava.class")).isNotEmptyFile();
    }

    @Test
    public void resource_step_is_not_wired_when_a_single_compiler_is_present() throws IOException {
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("OnlyJava.java"), "package sample; public class OnlyJava { }\n");
        Files.writeString(sampleDir.resolve("app.properties"), "key=value");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "chain",
                new InferredCompilerChainModule(Map.of(), Map.of()),
                "project");
        executor.execute();

        assertThat(root.resolve("chain").resolve(InferredCompilerChainModule.COMPILE).resolve(InferredCompilerChainModule.RESOURCE))
                .as("with a single compiler the resource step is not wired; Javac copies resources itself")
                .doesNotExist();
        Path javaClasses = root
                .resolve("chain")
                .resolve(InferredCompilerChainModule.COMPILE)
                .resolve(InferredCompilerChainModule.JAVAC)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        assertThat(javaClasses.resolve("sample/app.properties")).content().isEqualTo("key=value");
    }

    @Test
    public void javac_runs_in_a_process_of_its_own_when_the_process_factory_is_fork() throws IOException {
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("OnlyJava.java"), "package sample; public class OnlyJava { }\n");
        SequencedProperties javac = new SequencedProperties();
        javac.setProperty("-J-Xss4M", "");
        ProcessBuildStep.store(project, ProcessBuildStep.PROCESS, "javac", javac);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "chain",
                InferredCompilerChainModule.ofEnvironment(new Environment(Map.of("process.factory", "fork")), Map.of(), Map.of()),
                "project");
        executor.execute();

        assertThat(chainCompile()
                .resolve(InferredCompilerChainModule.JAVAC)
                .resolve("output")
                .resolve(BuildStep.CLASSES)
                .resolve("sample/OnlyJava.class"))
                .as("a -J option reaches only a forked javac, which the process factory selects")
                .isNotEmptyFile();
    }

    @Test
    public void javac_copies_no_file_beside_the_sources_when_resources_are_not_included() throws IOException {
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("OnlyJava.java"), "package sample; public class OnlyJava { }\n");
        Files.writeString(sampleDir.resolve("OnlyJava.java.in"), "template");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "chain",
                new InferredCompilerChainModule(Map.of(), Map.of())
                        .includeResources(false),
                "project");
        executor.execute();

        Path javaClasses = chainCompile()
                .resolve(InferredCompilerChainModule.JAVAC)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        assertThat(javaClasses.resolve("sample/OnlyJava.class")).isNotEmptyFile();
        assertThat(javaClasses.resolve("sample/OnlyJava.java.in")).doesNotExist();
    }

    @Test
    public void resource_step_copies_resources_when_no_compilers_are_wired() throws IOException {
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("app.properties"), "key=value");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "chain",
                new InferredCompilerChainModule(Map.of(), Map.of()),
                "project");
        executor.execute();

        Path resourceOutput = root
                .resolve("chain")
                .resolve(InferredCompilerChainModule.COMPILE)
                .resolve(InferredCompilerChainModule.RESOURCE)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        assertThat(resourceOutput.resolve("sample/app.properties")).content().isEqualTo("key=value");
    }

    @Test
    public void resource_step_leaves_the_configuration_folder_out() throws IOException {
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("app.properties"), "key=value");
        Path configuration = Files.createDirectories(project.resolve(BuildStep.SOURCES + "META-INF/build.jenesis"));
        Files.writeString(configuration.resolve("project.properties"), "artifact=sample");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("chain", new InferredCompilerChainModule(Map.of(), Map.of()), "project");

        executor.execute();

        Path resourceOutput = chainCompile()
                .resolve(InferredCompilerChainModule.RESOURCE)
                .resolve("output")
                .resolve(BuildStep.CLASSES);
        assertThat(resourceOutput.resolve("sample/app.properties")).content().isEqualTo("key=value");
        assertThat(resourceOutput.resolve("META-INF/build.jenesis")).doesNotExist();
    }

    @Test
    public void refuses_sources_of_a_language_whose_plugin_is_switched_off() throws IOException {
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.kt"),
                "package sample\nclass Sample\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("chain", new InferredCompilerChainModule(Map.of(), Map.of()).kotlin(false), "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining(".kt")
                .hasMessageContaining("plugin kotlin");
    }

    @Test
    public void resolves_no_language_plugin_for_a_module_without_its_sources() throws IOException {
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.java"),
                "package sample; public class Sample {}");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("chain", new InferredCompilerChainModule(Map.of(), Map.of()), "project");

        executor.execute();

        assertThat(root.resolve("chain/languages/kotlin"))
                .as("the Kotlin plugin is resolved only where a module holds a .kt source")
                .doesNotExist();
        assertThat(chainCompile().resolve(InferredCompilerChainModule.JAVAC).resolve("output")
                .resolve(BuildStep.CLASSES).resolve("sample/Sample.class")).isNotEmptyFile();
    }

    private Path chainCompile() {
        return root.resolve("chain").resolve(InferredCompilerChainModule.COMPILE);
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
