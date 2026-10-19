package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.InferredSourceFormattingModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredSourceFormattingModuleTest {

    @TempDir
    private Path root, project, dependencies;

    @Test
    public void checks_the_license_header_when_license_header_txt_is_present() throws IOException {
        Files.writeString(project.resolve("licenseheader.properties"), "text=Copyright 2026 the authors.\n");
        Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(project.resolve(BuildStep.SOURCES + "sample").resolve("Sample.java"), "package sample; class Sample {}");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Sample.java: no license header");
    }

    @Test
    public void the_license_override_switches_off_the_header_check() throws IOException {
        Files.writeString(project.resolve("licenseheader.properties"), "text=Copyright 2026 the authors.\n");
        Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(project.resolve(BuildStep.SOURCES + "sample").resolve("Sample.java"), "package sample; class Sample {}");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).license(null),
                "project");
        executor.execute();

        assertThat(root.resolve("format").resolve("license")).doesNotExist();
    }

    @Test
    public void writes_the_header_the_text_of_licenseheader_properties_holds() throws IOException {
        Files.writeString(project.resolve("licenseheader.properties"), """
                text=Copyright 2026 the original author or authors.\\n\\n\\
                  Licensed under the Apache License, Version 2.0.
                """);
        Path sample = Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"))
                .resolve("Sample.java"), "package sample;\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).verify(false),
                "project");
        executor.execute();

        assertThat(sample).hasContent("""
                /*
                 * Copyright 2026 the original author or authors.
                 *
                 * Licensed under the Apache License, Version 2.0.
                 */

                package sample;
                """);
    }

    @Test
    public void refuses_a_licenseheader_properties_with_another_key_than_text() throws IOException {
        Files.writeString(project.resolve("licenseheader.properties"), "text=Copyright 2026 the authors.\nyear=2026\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("licenseheader.properties names year, but takes text=<the header> alone");
    }

    @Test
    public void formats_with_a_custom_module_once_the_license_header_is_written() throws IOException {
        Files.writeString(project.resolve("licenseheader.properties"), "text=Copyright 2026 the authors.\n");
        Path sample = Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"))
                .resolve("Sample.java"), "package sample;\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of())
                        .verify(false)
                        .custom("formatter", new Formatter(sample.toString())),
                "project");
        SequencedMap<String, Path> steps = executor.execute();

        assertThat(steps.get("format/custom/formatter").resolve("seen.txt"))
                .as("a formatter that rewrites the sources runs after the header is written, rather than beside it")
                .content()
                .contains("Copyright 2026 the authors.");
    }

    @Test
    public void hands_the_dependencies_of_a_module_to_no_formatter_but_openrewrite() throws IOException {
        Files.writeString(project.resolve("licenseheader.properties"), "text=Copyright 2026 the authors.\n");
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.java"),
                "/*\n * Copyright 2026 the authors.\n */\n\npackage sample;\n");
        Files.writeString(Files.createDirectories(dependencies.resolve(BuildStep.SOURCES + "other")).resolve("Other.java"),
                "package other;\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addSource("dependencies", dependencies);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of())
                        .dependencies(new LinkedHashSet<>(List.of("dependencies"))),
                "project",
                "dependencies");
        SequencedMap<String, Path> steps = executor.execute();

        assertThat(steps.keySet())
                .as("the header check reads the module's own sources, never those of what it depends on")
                .anyMatch(step -> step.startsWith("format/license/"));
    }

    @Test
    public void binds_the_rewrite_yml_that_openrewrite_properties_names() throws IOException {
        Files.writeString(project.resolve("cleanup.yml"), "type: specs.openrewrite.org/v1beta/recipe\nname: demo.Cleanup\n");
        Files.writeString(project.resolve("openrewrite.properties"), "configuration=cleanup.yml\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()),
                "project");
        executor.execute("format/" + InferredSourceFormattingModule.OPENREWRITE + "/inputs");

        assertThat(root.resolve("format/" + InferredSourceFormattingModule.OPENREWRITE + "/inputs/rules/bound/output/rewrite.yml"))
                .as("the file the properties name is handed to OpenRewrite under the name it reads")
                .content()
                .contains("demo.Cleanup");
    }

    @Test
    public void switches_openrewrite_on_by_a_rewrite_yml_where_no_properties_are_found() throws IOException {
        Files.writeString(project.resolve("rewrite.yml"), "type: specs.openrewrite.org/v1beta/recipe\nname: demo.Cleanup\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()),
                "project");
        executor.execute("format/" + InferredSourceFormattingModule.OPENREWRITE + "/inputs");

        assertThat(root.resolve("format/" + InferredSourceFormattingModule.OPENREWRITE + "/inputs/rules/bound/output/rewrite.yml"))
                .as("the conventional file of a configuration folder switches OpenRewrite on and is handed to it")
                .content()
                .contains("demo.Cleanup");
    }

    @Test
    public void refuses_openrewrite_properties_that_name_neither_recipes_nor_their_file() throws IOException {
        Files.writeString(project.resolve("openrewrite.properties"), "");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()),
                "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names neither configuration nor recipes")
                .hasMessageContaining("configuration=rewrite.yml");
    }

    @Test
    public void refuses_ktlint_properties_that_name_no_configuration_naming_the_key() throws IOException {
        Files.writeString(project.resolve("ktlint.properties"), "");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()),
                "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names no configuration - add configuration=.editorconfig");
    }

    @Test
    public void runs_no_openrewrite_once_switched_off() throws IOException {
        Files.writeString(project.resolve("openrewrite.properties"), "recipes=org.openrewrite.java.RemoveUnusedImports\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).openrewrite(false),
                "project");
        SequencedMap<String, Path> steps = executor.execute();

        assertThat(steps.keySet()).noneMatch(step -> step.startsWith("format/" + InferredSourceFormattingModule.OPENREWRITE));
    }

    private record Formatter(String file) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Files.writeString(context.next().resolve("seen.txt"), Files.readString(Path.of(file)));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
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
