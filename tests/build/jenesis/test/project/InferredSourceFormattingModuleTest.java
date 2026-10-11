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
import build.jenesis.project.InferredSourceFormattingModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredSourceFormattingModuleTest {

    @TempDir
    private Path root, project;

    @Test
    public void wires_ktlint_format_when_editorconfig_is_present() throws IOException {
        Files.writeString(project.resolve(".editorconfig"), "root = true\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/ktlint/tool/required");

        assertThat(coordinates("ktlint", "tool"))
                .containsExactly("ktlint-format/runtime/maven/com.pinterest.ktlint/ktlint-cli/RELEASE");
    }

    @Test
    public void wires_ktfmt_when_ktfmt_properties_is_present_even_when_empty() throws IOException {
        Files.writeString(project.resolve("ktfmt.properties"), "");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/ktfmt/required");

        assertThat(coordinates("ktfmt"))
                .containsExactly("ktfmt-format/runtime/maven/com.facebook/ktfmt/RELEASE");
    }

    @Test
    public void refuses_ktfmt_and_ktlint_formatting_the_same_sources() throws IOException {
        Files.writeString(project.resolve("ktfmt.properties"), "style=google\n");
        Files.writeString(project.resolve(".editorconfig"), "root = true\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jenesis.format.ktlint=false");
    }

    @Test
    public void formats_with_ktfmt_alone_once_ktlint_formatting_is_switched_off() throws IOException {
        Files.writeString(project.resolve("ktfmt.properties"), "style=kotlinlang\nimports=sort\n");
        Files.writeString(project.resolve(".editorconfig"), "root = true\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).ktlint(null),
                "project");
        executor.execute("format/ktfmt/required");

        assertThat(root.resolve("format").resolve("ktlint")).doesNotExist();
        assertThat(coordinates("ktfmt"))
                .containsExactly("ktfmt-format/runtime/maven/com.facebook/ktfmt/RELEASE");
    }

    @Test
    public void refuses_a_ktfmt_style_or_imports_ktfmt_does_not_know() throws IOException {
        Files.writeString(project.resolve("ktfmt.properties"), "style=dropbox\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("style=dropbox")
                .hasMessageContaining("meta, google or kotlinlang");

        Files.writeString(project.resolve("ktfmt.properties"), "imports=remove\n");

        BuildExecutor again = newExecutor();
        again.addSource("project", project);
        again.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(again::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("always sorts");
    }

    @Test
    public void checks_the_license_header_when_license_header_txt_is_present() throws IOException {
        Files.writeString(project.resolve("license-header.txt"), "Copyright 2026 the authors.\n");
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
        Files.writeString(project.resolve("license-header.txt"), "Copyright 2026 the authors.\n");
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
    public void wires_scalafmt_format_when_scalafmt_conf_is_present() throws IOException {
        Files.writeString(project.resolve(".scalafmt.conf"), "version = \"3.8.3\"\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/scalafmt/tool/required");

        assertThat(coordinates("scalafmt", "tool"))
                .containsExactly("scalafmt-format/runtime/maven/org.scalameta/scalafmt-cli_2.13/RELEASE");
    }

    @Test
    public void wires_google_java_format_from_the_javaformat_properties_file() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=google\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/java/required");

        assertThat(coordinates("java"))
                .containsExactly("google-java-format/runtime/maven/com.google.googlejavaformat/google-java-format/RELEASE");
    }

    @Test
    public void wires_palantir_java_format_from_the_javaformat_properties_file() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=palantir\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/java/required");

        assertThat(coordinates("java"))
                .containsExactly("palantir-java-format/runtime/maven/com.palantir.javaformat/palantir-java-format/RELEASE");
    }

    @Test
    public void wires_the_eclipse_formatter_with_its_profile_from_the_javaformat_properties_file() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=eclipse\nprofile=eclipse/formatter.xml\n");
        Files.createDirectories(project.resolve("eclipse"));
        Files.writeString(project.resolve("eclipse/formatter.xml"), "<profiles/>");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute("format/java/required", "format/java/profile");

        assertThat(coordinates("java"))
                .containsExactly("eclipse-java-format/runtime/maven/org.eclipse.jdt/org.eclipse.jdt.core/RELEASE");
        assertThat(root.resolve("format/java/profile/output/eclipse-formatter.xml"))
                .as("the profile is bound under the name the formatter reads, wherever it lives")
                .hasContent("<profiles/>");
    }

    @Test
    public void refuses_an_eclipse_profile_outside_the_configuration_folder() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=eclipse\nprofile=../formatter.xml\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profile=../formatter.xml")
                .hasMessageContaining("beside or below");
    }

    @Test
    public void refuses_an_eclipse_profile_that_does_not_exist() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=eclipse\nprofile=formatter.xml\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profile=formatter.xml")
                .hasMessageContaining("is no file");
    }

    @Test
    public void refuses_a_key_the_selected_java_formatter_does_not_take() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=palantir\njavadoc=false\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("javadoc")
                .hasMessageContaining("palantir-java-format")
                .hasMessageContaining("[formatter, imports, reflow]");
    }

    @Test
    public void refuses_a_style_google_java_format_does_not_know() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=google\nstyle=palantir\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("style=palantir")
                .hasMessageContaining("google or aosp");
    }

    @Test
    public void refuses_an_imports_value_other_than_sort_remove_or_none() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=google\nimports=sort,order\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("imports=sort,order");
    }

    @Test
    public void refuses_a_flag_that_is_neither_true_nor_false() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=google\nreflow=never\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reflow");
    }

    @Test
    public void refuses_formatter_options_that_name_no_formatter() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "style=aosp\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");

        assertThatThrownBy(executor::execute)
                .rootCause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no formatter");
    }

    @Test
    public void the_java_override_switches_off_the_formatter_from_the_file() throws IOException {
        Files.writeString(project.resolve("javaformat.properties"), "formatter=google\n");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format",
                new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()).java(null),
                "project");
        executor.execute();

        assertThat(root.resolve("format").resolve("java"))
                .as("jenesis.format.java=false switches the formatter off even with a javaformat.properties file")
                .doesNotExist();
    }

    @Test
    public void does_not_wire_a_java_formatter_without_a_javaformat_properties_file() throws IOException {
        Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(project.resolve(BuildStep.SOURCES + "sample").resolve("Sample.java"), "package sample; class Sample {}");

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute();

        assertThat(root.resolve("format").resolve("java"))
                .as("no Java formatter is wired without a javaformat.properties file, even when Java sources exist")
                .doesNotExist();
    }

    @Test
    public void does_not_wire_ktlint_format_when_editorconfig_is_absent() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("format", new InferredSourceFormattingModule(new LinkedHashSet<>(List.of(project)), Map.of(), Map.of()), "project");
        executor.execute();

        assertThat(root.resolve("format").resolve("ktlint")).doesNotExist();
    }

    private Iterable<String> coordinates(String... segments) throws IOException {
        Path output = root.resolve("format");
        for (String segment : segments) {
            output = output.resolve(segment);
        }
        output = output.resolve("required").resolve("output");
        return SequencedProperties.ofFiles(output.resolve(BuildStep.REQUIRES)).stringPropertyNames();
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
