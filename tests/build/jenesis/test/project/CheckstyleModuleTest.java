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
import build.jenesis.project.CheckstyleModule;

import static org.assertj.core.api.Assertions.assertThat;

public class CheckstyleModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void requires_step_emits_the_checkstyle_maven_coordinate() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("checkstyle", new CheckstyleModule(Map.of(), Map.of()), "project");
        executor.execute("checkstyle/required");

        Path requiredOutput = root.resolve("checkstyle").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("checkstyle/runtime/maven/com.puppycrawl.tools/checkstyle/RELEASE");
    }

    @Test
    public void tool_emits_an_independent_resolution_trail() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("main/compile/maven/org.example/library/1.0", "");
        requires.store(project.resolve(BuildStep.REQUIRES));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "checkstyle",
                new CheckstyleModule(Map.of("maven", files()), Map.of("maven", Resolver.identity()))
                        .tool("custom"),
                "project");
        executor.execute("checkstyle/dependencies");

        Path resolvedOutput = root.resolve("checkstyle").resolve("dependencies").resolve("resolve").resolve("output");
        SequencedProperties resolved = SequencedProperties.ofFiles(resolvedOutput.resolve(BuildStep.DEPENDENCIES));
        assertThat(resolved.stringPropertyNames())
                .as("the module's own closure is resolved by the module, not again by every tool")
                .containsExactly("custom/runtime/maven/com.puppycrawl.tools/checkstyle/RELEASE");
    }

    @Test
    public void puts_the_jars_of_a_checkstyle_plugin_on_the_class_path_of_checkstyle() throws IOException {
        SequencedProperties requires = new SequencedProperties();
        requires.setProperty("checkstyle/plugin/maven/org.example/checks/1.0", "");
        requires.store(project.resolve(BuildStep.REQUIRES));
        Files.writeString(project.resolve("checkstyle.xml"), "<module name=\"Checker\"/>");
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.java"),
                "package sample; public class Sample { }");
        Path served = Files.createDirectories(root.resolve("served"));
        jar(served.resolve("checkstyle.jar"), "com.puppycrawl.tools.checkstyle.Main", ClassFile.of().build(
                ClassDesc.of("com.puppycrawl.tools.checkstyle.Main"),
                type -> type.withFlags(ClassFile.ACC_PUBLIC).withMethodBody("main",
                        MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String.arrayType()),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        code -> code.ldc("org.example.Check")
                                .invokestatic(ConstantDescs.CD_Class,
                                        "forName",
                                        MethodTypeDesc.of(ConstantDescs.CD_Class, ConstantDescs.CD_String))
                                .pop()
                                .return_())));
        jar(served.resolve("checks.jar"), "org.example.Check", ClassFile.of().build(
                ClassDesc.of("org.example.Check"),
                type -> type.withFlags(ClassFile.ACC_PUBLIC)));
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("checkstyle",
                new CheckstyleModule(Map.of("maven", (_, coordinate, _) -> Optional.of(RepositoryItem.ofFile(
                        served.resolve(coordinate.contains("checkstyle") ? "checkstyle.jar" : "checks.jar")))),
                        Map.of("maven", Resolver.identity())),
                "project");
        assertThat(executor.execute("checkstyle/check"))
                .as("a custom check that @jenesis.plugin checkstyle names loads beside Checkstyle")
                .containsKey("checkstyle/check");
    }

    private static void jar(Path file, String type, byte[] bytes) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
            out.putNextEntry(new JarEntry(type.replace('.', '/') + ".class"));
            out.write(bytes);
            out.closeEntry();
        }
    }

    @Test
    public void hands_over_a_checkstyle_properties_beside_the_configuration() throws IOException {
        Path configuration = Files.writeString(project.resolve("checkstyle.xml"), "<module name=\"Checker\"/>");
        assertThat(CheckstyleModule.siblings(configuration)).isEmpty();
        Files.writeString(project.resolve("checkstyle.properties"), "type.format=^[a-z]+$\n");
        assertThat(CheckstyleModule.siblings(configuration))
                .as("the properties a configuration expands are an input of the check")
                .containsExactly(Path.of("checkstyle.properties"));
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

    private Repository files() {
        return (_, coordinate, _) -> {
            Path file = Files.write(
                    Files.createDirectories(root.resolve("served")).resolve(coordinate.replace('/', '-') + ".jar"),
                    coordinate.getBytes(StandardCharsets.UTF_8));
            return Optional.of(RepositoryItem.ofFile(file));
        };
    }
}
