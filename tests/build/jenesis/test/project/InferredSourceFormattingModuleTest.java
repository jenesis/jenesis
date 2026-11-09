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
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
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

    @Test
    public void switches_the_eclipse_formatter_on_by_its_profile_where_no_properties_are_found() throws IOException {
        Files.writeString(project.resolve("eclipse-formatter.xml"), "<profiles/>");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/" + InferredSourceFormattingModule.ECLIPSE + "/inputs");

        assertThat(root.resolve("format/" + InferredSourceFormattingModule.ECLIPSE + "/inputs/rules/bound/output/eclipse-formatter.xml"))
                .as("the profile Eclipse exports switches the Eclipse formatter on and is handed to it")
                .hasContent("<profiles/>");
    }

    @Test
    public void binds_the_profile_eclipse_properties_names() throws IOException {
        Files.writeString(project.resolve("eclipse.properties"), "configuration=team.xml\n");
        Files.writeString(project.resolve("team.xml"), "<profiles/>");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/" + InferredSourceFormattingModule.ECLIPSE + "/inputs");

        assertThat(root.resolve("format/" + InferredSourceFormattingModule.ECLIPSE + "/inputs/rules/bound/output/eclipse-formatter.xml"))
                .as("the profile is handed over under the name the Eclipse formatter reads")
                .hasContent("<profiles/>");
    }

    @Test
    public void refuses_two_java_formatters_naming_both() throws IOException {
        Files.writeString(project.resolve("googlejavaformat.properties"), "");
        Files.writeString(project.resolve("palantir.properties"), "");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[googlejavaformat, palantir]")
                .hasMessageContaining("jenesis.format.<name>=false");
    }

    @Test
    public void switches_prince_of_space_on_by_its_properties_beside_the_other_java_formatters() throws IOException {
        Files.writeString(project.resolve("palantir.properties"), "");
        Files.writeString(project.resolve("princeofspace.properties"), "wrap-style=wide\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .as("a princeofspace.properties switches Prince of Space on, which formats the Java sources Palantir would")
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[palantir, princeofspace]");
    }

    @Test
    public void switches_open_java_format_on_by_its_properties() throws IOException {
        Files.writeString(project.resolve("openjavaformat.properties"), "reflow=false\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/" + InferredSourceFormattingModule.OPENJAVAFORMAT + "/coordinate");

        assertThat(SequencedProperties.ofFiles(root.resolve("format/" + InferredSourceFormattingModule.OPENJAVAFORMAT
                        + "/coordinate/output/" + BuildStep.REQUIRES)).stringPropertyNames())
                .as("an openjavaformat.properties wires the official Open Java Format plugin, resolved by its module name")
                .anyMatch(coordinate -> coordinate.contains("module/build.jenesis.plugin.openjavaformat"));
    }

    @Test
    public void refuses_open_java_format_beside_palantir_naming_both() throws IOException {
        Files.writeString(project.resolve("palantir.properties"), "");
        Files.writeString(project.resolve("openjavaformat.properties"), "");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .as("Open Java Format formats the Java sources Palantir would")
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[palantir, openjavaformat]");
    }

    @Test
    public void leaves_open_java_format_out_where_it_is_switched_off() throws IOException {
        Files.writeString(project.resolve("palantir.properties"), "");
        Files.writeString(project.resolve("openjavaformat.properties"), "");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of())
                        .openjavaformat(false),
                "project");
        executor.execute("format/" + InferredSourceFormattingModule.PALANTIR + "/coordinate");

        assertThat(root.resolve("format/" + InferredSourceFormattingModule.PALANTIR + "/coordinate"))
                .as("with Open Java Format switched off, Palantir formats alone")
                .exists();
        assertThat(root.resolve("format/" + InferredSourceFormattingModule.OPENJAVAFORMAT))
                .doesNotExist();
    }

    @Test
    public void switches_spotless_on_by_its_properties_beside_a_java_formatter_where_no_set_formats_java()
            throws IOException {
        Files.writeString(project.resolve("googlejavaformat.properties"), "");
        Files.writeString(project.resolve("spotless.properties"), "json.target=*.json\njson.steps=jacksonJson\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/" + InferredSourceFormattingModule.SPOTLESS + "/coordinate",
                "format/" + InferredSourceFormattingModule.GOOGLEJAVAFORMAT + "/coordinate");

        assertThat(SequencedProperties.ofFiles(root.resolve("format/" + InferredSourceFormattingModule.SPOTLESS
                        + "/coordinate/output/" + BuildStep.REQUIRES)).stringPropertyNames())
                .as("a spotless.properties wires the official Spotless plugin, resolved by its module name")
                .anyMatch(coordinate -> coordinate.contains("module/build.jenesis.plugin.spotless"));
        assertThat(root.resolve("format/" + InferredSourceFormattingModule.GOOGLEJAVAFORMAT + "/coordinate"))
                .as("Spotless formatting no Java source runs beside the Java formatter")
                .exists();
    }

    @Test
    public void counts_a_spotless_set_running_a_java_formatter_as_a_java_formatter() throws IOException {
        Files.writeString(project.resolve("palantir.properties"), "");
        Files.writeString(project.resolve("spotless.properties"), "java.target=*.java\njava.steps=removeUnusedImports, googleJavaFormat\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .as("a Spotless set running google-java-format formats the Java sources Palantir would")
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[palantir, spotless]");
    }

    @Test
    public void counts_a_spotless_set_running_ktfmt_or_ktlint_as_the_kotlin_formatter() throws IOException {
        Files.writeString(project.resolve("ktfmt.properties"), "");
        Files.writeString(project.resolve("spotless.properties"), "kotlin.target=*.kt\nkotlin.steps=ktlint\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .as("a Spotless set running ktlint formats the Kotlin sources ktfmt would")
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runs a Kotlin formatter of Spotless while ktfmt formats the same Kotlin sources")
                .hasMessageContaining("jenesis.format.ktfmt=false");
    }

    @Test
    public void counts_a_spotless_set_running_scalafmt_as_the_scala_formatter() throws IOException {
        Files.writeString(project.resolve(".scalafmt.conf"), "version = \"3.11.5\"\n");
        Files.writeString(project.resolve("spotless.properties"), "scala.target=*.scala\nscala.steps=scalafmt\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runs Spotless's scalafmt step while scalafmt formats the same Scala sources");
    }

    @Test
    public void counts_a_spotless_set_running_license_header_as_the_license_header_check() throws IOException {
        Files.writeString(project.resolve("licenseheader.properties"), "text=Licensed.\n");
        Files.writeString(project.resolve("spotless.properties"), "java.target=*.java\njava.steps=licenseHeader\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runs Spotless's licenseHeader step while a licenseheader.properties checks the same header");
    }

    @Test
    public void switches_spotless_off_by_its_boolean_setting() throws IOException {
        Files.writeString(project.resolve("spotless.properties"), "json.target=*.json\njson.steps=jacksonJson\n");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                InferredSourceFormattingModule.ofEnvironment(new Environment(Map.of("format.spotless", "false")),
                        new LinkedHashSet<>(List.of(project)),
                        Map.of(),
                        Map.of()),
                "project");
        executor.execute();

        assertThat(root.resolve("format/" + InferredSourceFormattingModule.SPOTLESS))
                .as("jenesis.format.spotless=false leaves spotless.properties unread")
                .doesNotExist();
    }

    @Test
    public void switches_a_java_formatter_off_by_its_boolean_setting() throws IOException {
        Files.writeString(project.resolve("googlejavaformat.properties"), "");
        Files.writeString(project.resolve("eclipse-formatter.xml"), "<profiles/>");
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                InferredSourceFormattingModule.ofEnvironment(new Environment(Map.of("format.googlejavaformat", "false")),
                        new LinkedHashSet<>(List.of(project)),
                        Map.of(),
                        Map.of()),
                "project");
        executor.execute("format/" + InferredSourceFormattingModule.ECLIPSE + "/inputs");

        assertThat(root.resolve("format/" + InferredSourceFormattingModule.ECLIPSE + "/inputs/rules/bound/output/eclipse-formatter.xml"))
                .as("the formatter switched off is no second Java formatter beside the one that is on")
                .hasContent("<profiles/>");
        assertThat(root.resolve("format/" + InferredSourceFormattingModule.GOOGLEJAVAFORMAT)).doesNotExist();
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
