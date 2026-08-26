package build.jenesis.test.project;

import module java.base;
import module java.compiler;
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
import build.jenesis.project.JaCoCoModule;
import build.jenesis.step.Dependencies;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class JaCoCoModuleTest {

    @TempDir
    private Path root, project, tool;

    @Test
    public void requires_step_emits_the_jacoco_cli_coordinate() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("jacoco", new JaCoCoModule(Map.of(), Map.of()), "project");
        executor.execute("jacoco/required");

        Path requiredOutput = root.resolve("jacoco").resolve("required").resolve("output");
        SequencedProperties requires = SequencedProperties.ofFiles(requiredOutput.resolve(BuildStep.REQUIRES));
        assertThat(requires.stringPropertyNames())
                .containsExactly("jacoco/runtime/maven/org.jacoco/org.jacoco.cli/RELEASE");
    }

    @Test
    public void report_reads_the_first_party_jar_from_the_resolved_closure() throws IOException {
        Path resolved = Files.createDirectory(project.resolve(Dependencies.RESOLVED));
        Path first = Files.write(resolved.resolve("maven%2Forg.example%2Flibrary%2F1.0.jar"), new byte[0]);
        Path third = Files.write(resolved.resolve("maven%2Forg.example%2Fexternal%2F2.0.jar"), new byte[0]);
        Files.write(project.resolve("jacoco.exec"), new byte[0]);
        SequencedProperties index = new SequencedProperties();
        index.setProperty("main/compile/maven/org.example/library/1.0",
                Dependencies.RESOLVED + first.getFileName());
        index.setProperty("main/compile/maven/org.example/external/2.0",
                Dependencies.RESOLVED + third.getFileName());
        index.store(project.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties graph = new SequencedProperties();
        graph.setProperty("vertex/main/compile/maven/org.example/library", "1.0\t\tfalse\ttrue");
        graph.setProperty("vertex/main/compile/maven/org.example/external", "2.0\t\tfalse\tfalse");
        graph.store(project.resolve(Dependencies.GRAPH));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "jacoco",
                new JaCoCoModule(Map.of("maven", serving(cli())), Map.of("maven", Resolver.identity())),
                "project");
        executor.execute("jacoco/report");

        Path command = root.resolve("jacoco").resolve("report").resolve("supplement").resolve("command");
        assertThat(command)
                .as("the report runs once the closure yields code under test")
                .exists();
        assertThat(Files.readString(command))
                .as("a first-party jar is recognised by its coordinate, whatever its file name")
                .contains("--classfiles " + first)
                .as("a third-party jar is not code under test")
                .doesNotContain(third.toString());
    }

    private Path cli() throws IOException {
        Path source = tool.resolve("Main.java");
        Files.writeString(source, """
                package org.jacoco.cli.internal;
                public class Main {
                    public static void main(String[] args) {
                    }
                }
                """);
        Path classes = Files.createDirectory(tool.resolve("classes"));
        if (ToolProvider.getSystemJavaCompiler().run(null,
                null,
                null,
                "-d", classes.toString(),
                source.toString()) != 0) {
            throw new IllegalStateException("Failed to compile the JaCoCo CLI double");
        }
        Path jar = tool.resolve("cli.jar");
        try (JarOutputStream stream = new JarOutputStream(Files.newOutputStream(jar))) {
            stream.putNextEntry(new JarEntry("org/jacoco/cli/internal/Main.class"));
            stream.write(Files.readAllBytes(classes.resolve("org")
                    .resolve("jacoco")
                    .resolve("cli")
                    .resolve("internal")
                    .resolve("Main.class")));
            stream.closeEntry();
        }
        return jar;
    }

    private static Repository serving(Path jar) {
        return (_, _) -> Optional.of(RepositoryItem.ofFile(jar));
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
