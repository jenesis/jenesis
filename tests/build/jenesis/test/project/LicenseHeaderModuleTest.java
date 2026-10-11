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
import build.jenesis.project.LicenseHeaderModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LicenseHeaderModuleTest {

    private static final String TEMPLATE = """
            Copyright 2026 the original author or authors.

            Licensed under the Apache License, Version 2.0.
            """;
    private static final String HEADER = """
            /*
             * Copyright 2026 the original author or authors.
             *
             * Licensed under the Apache License, Version 2.0.
             */""";

    @TempDir
    private Path root, project;

    @Test
    public void accepts_a_source_that_starts_with_the_header_as_written() throws IOException {
        source("sample/Sample.java", HEADER + "\n\npackage sample;\n\nclass Sample {}\n");
        source("sample/Other.kt", HEADER + "\npackage sample\n");

        newExecutor(new LicenseHeaderModule().text(TEMPLATE).verify(true)).execute();
    }

    @Test
    public void verify_fails_naming_each_source_without_the_header_and_rewrites_nothing() throws IOException {
        Path missing = source("sample/Missing.java", "package sample;\n\nclass Missing {}\n");
        Path different = source("sample/Different.scala", "/*\n * Copyright 2020 Someone Else, licensed otherwise.\n */\npackage sample\n");
        Path dated = source("sample/Dated.java", HEADER.replace("2026", "2016-2023") + "\n\npackage sample;\n");
        source("sample/Present.groovy", HEADER + "\npackage sample\n");

        assertThatThrownBy(newExecutor(new LicenseHeaderModule().text(TEMPLATE).verify(true))::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not every source starts with the license header")
                .hasMessageContaining(Path.of("sample", "Missing.java") + ": no license header")
                .hasMessageContaining(Path.of("sample", "Different.scala") + ": a different license header")
                .as("a header is taken as written, so another year is another header")
                .hasMessageContaining(Path.of("sample", "Dated.java") + ": a different license header")
                .hasMessageNotContaining("Present.groovy");
        assertThat(missing).as("verify mode never rewrites the source").hasContent("package sample;\n\nclass Missing {}\n");
        assertThat(different).content().contains("Someone Else");
        assertThat(dated).content().contains("2016-2023");
    }

    @Test
    public void rewrite_inserts_the_header_as_written() throws IOException {
        Path missing = source("sample/package-info.java", "/** The sample package. */\npackage sample;\n");
        Path descriptor = source("module-info.java", "\n\nmodule sample {\n}\n");

        newExecutor(new LicenseHeaderModule().text(TEMPLATE)).execute();

        assertThat(missing)
                .as("a leading comment that is no license header stays below the inserted header")
                .hasContent(HEADER + "\n\n/** The sample package. */\npackage sample;\n");
        assertThat(descriptor).hasContent(HEADER + "\n\nmodule sample {\n}\n");
        newExecutor(new LicenseHeaderModule().text(TEMPLATE).verify(true)).execute();
    }

    @Test
    public void rewrite_replaces_a_different_license_header_with_the_header_as_written() throws IOException {
        Path different = source("sample/Different.kt", "// Copyright 2019-2021 Someone Else\n// All rights reserved.\npackage sample\n");
        Path windows = source("sample/Windows.java", "/* Copyright 2018 Someone Else */\r\npackage sample;\r\n");

        newExecutor(new LicenseHeaderModule().text(TEMPLATE)).execute();

        assertThat(different)
                .as("a header of line comments is replaced")
                .hasContent(HEADER + "\npackage sample\n");
        assertThat(windows)
                .as("a source keeps its line separator")
                .hasContent(HEADER.replace("\n", "\r\n") + "\r\npackage sample;\r\n");

        newExecutor(new LicenseHeaderModule().text(TEMPLATE).verify(true)).execute();
    }

    @Test
    public void takes_a_template_that_is_a_comment_already_as_it_stands() throws IOException {
        Path sample = source("sample/Sample.java", "package sample;\n");

        newExecutor(new LicenseHeaderModule().text("// SPDX-License-Identifier: Apache-2.0\n")).execute();

        assertThat(sample).hasContent("// SPDX-License-Identifier: Apache-2.0\n\npackage sample;\n");
        newExecutor(new LicenseHeaderModule().text("// SPDX-License-Identifier: Apache-2.0\n").verify(true)).execute();
    }

    @Test
    public void reads_the_header_from_the_text_of_licenseheader_properties() throws IOException {
        Path configuration = Files.writeString(project.resolve(LicenseHeaderModule.CONFIGURATION), """
                text=Copyright 2026 the original author or authors.\\n\\n\\
                  Licensed under the Apache License, Version 2.0.
                """);
        Path sample = source("sample/Sample.java", "package sample;\n");

        newExecutor(new LicenseHeaderModule().configuration(SequencedProperties.ofFiles(configuration))).execute();

        assertThat(sample).hasContent(HEADER + "\n\npackage sample;\n");
    }

    @Test
    public void refuses_a_licenseheader_properties_with_another_key_than_text() {
        SequencedProperties other = new SequencedProperties();
        other.setProperty("text", "Copyright 2026 the authors.");
        other.setProperty("year", "2026");
        assertThatThrownBy(() -> new LicenseHeaderModule().configuration(other))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names year");
    }

    @Test
    public void derives_the_header_from_the_declared_license_and_copyright_without_text() throws IOException {
        Files.writeString(project.resolve(BuildStep.METADATA), """
                license.0.name=The Apache Software License, Version 2.0
                license.0.url=https://www.apache.org/licenses/LICENSE-2.0.txt
                copyright=2026 the original author or authors
                """);
        Path sample = source("sample/Sample.java", "/* Copyright 2019 Someone Else */\npackage sample;\n");

        newExecutor(new LicenseHeaderModule().configuration(new SequencedProperties())).execute();

        assertThat(sample).hasContent("""
                // SPDX-FileCopyrightText: 2026 the original author or authors
                // SPDX-License-Identifier: Apache-2.0
                package sample;
                """);
        newExecutor(new LicenseHeaderModule().verify(true)).execute();
    }

    @Test
    public void derives_a_header_without_copyright_when_none_is_declared() throws IOException {
        Files.writeString(project.resolve(BuildStep.METADATA), "license.mit.name=MIT\n");
        Path sample = source("sample/Sample.java", "package sample;\n");

        newExecutor(new LicenseHeaderModule()).execute();

        assertThat(sample).hasContent("// SPDX-License-Identifier: MIT\n\npackage sample;\n");
    }

    @Test
    public void refuses_to_derive_a_header_from_no_several_or_an_unidentified_license() throws IOException {
        source("sample/Sample.java", "package sample;\n");

        assertThatThrownBy(newExecutor(new LicenseHeaderModule())::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares none");

        Files.writeString(project.resolve(BuildStep.METADATA), "license.0.name=MIT\nlicense.1.name=Apache-2.0\n");
        assertThatThrownBy(newExecutor(new LicenseHeaderModule())::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("declares 2 (license.0, license.1)");

        Files.writeString(project.resolve(BuildStep.METADATA), "license.0.name=Our Own License\n");
        assertThatThrownBy(newExecutor(new LicenseHeaderModule())::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Our Own License has no SPDX identifier")
                .hasMessageContaining("spdx.properties");
    }

    private Path source(String name, String content) throws IOException {
        Path file = project.resolve(BuildStep.SOURCES).resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private BuildExecutor newExecutor(LicenseHeaderModule module) throws IOException {
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
        executor.addModule("license", module, "project");
        return executor;
    }
}
