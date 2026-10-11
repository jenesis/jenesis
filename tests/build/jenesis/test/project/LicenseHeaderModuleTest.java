package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.project.LicenseHeaderModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LicenseHeaderModuleTest {

    private static final String TEMPLATE = """
            Copyright $YEAR the original author or authors.

            Licensed under the Apache License, Version 2.0.
            """;
    private static final String HEADER = """
            /*
             * Copyright %s the original author or authors.
             *
             * Licensed under the Apache License, Version 2.0.
             */""";

    @TempDir
    private Path root, project;

    @Test
    public void accepts_a_header_naming_any_year_or_range_of_years() throws IOException {
        Files.writeString(project.resolve(LicenseHeaderModule.HEADER), TEMPLATE);
        source("sample/Sample.java", HEADER.formatted("2016-2023") + "\n\npackage sample;\n\nclass Sample {}\n");
        source("sample/Other.kt", HEADER.formatted("2024") + "\npackage sample\n");

        newExecutor(new LicenseHeaderModule().verify(true)).execute();
    }

    @Test
    public void verify_fails_naming_each_source_without_the_header_and_rewrites_nothing() throws IOException {
        Files.writeString(project.resolve(LicenseHeaderModule.HEADER), TEMPLATE);
        Path missing = source("sample/Missing.java", "package sample;\n\nclass Missing {}\n");
        Path different = source("sample/Different.scala", "/*\n * Copyright 2020 Someone Else, licensed otherwise.\n */\npackage sample\n");
        source("sample/Present.groovy", HEADER.formatted("2020") + "\npackage sample\n");

        assertThatThrownBy(newExecutor(new LicenseHeaderModule().verify(true))::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Not every source starts with the license header")
                .hasMessageContaining(Path.of("sample", "Missing.java") + ": no license header")
                .hasMessageContaining(Path.of("sample", "Different.scala") + ": a different license header")
                .hasMessageNotContaining("Present.groovy");
        assertThat(missing).as("verify mode never rewrites the source").hasContent("package sample;\n\nclass Missing {}\n");
        assertThat(different).content().contains("Someone Else");
    }

    @Test
    public void rewrite_inserts_a_header_that_names_its_year() throws IOException {
        Files.writeString(project.resolve(LicenseHeaderModule.HEADER), TEMPLATE.replace("$YEAR", "2026"));
        Path missing = source("sample/package-info.java", "/** The sample package. */\npackage sample;\n");
        Path descriptor = source("module-info.java", "\n\nmodule sample {\n}\n");

        newExecutor(new LicenseHeaderModule()).execute();

        assertThat(missing)
                .as("a leading comment that is no license header stays below the inserted header")
                .hasContent(HEADER.formatted("2026") + "\n\n/** The sample package. */\npackage sample;\n");
        assertThat(descriptor).hasContent(HEADER.formatted("2026") + "\n\nmodule sample {\n}\n");
        newExecutor(new LicenseHeaderModule().verify(true)).execute();
    }

    @Test
    public void rewrite_replaces_a_different_header_keeping_the_year_it_names() throws IOException {
        Files.writeString(project.resolve(LicenseHeaderModule.HEADER), TEMPLATE);
        Path different = source("sample/Different.kt", "// Copyright 2019-2021 Someone Else\n// All rights reserved.\npackage sample\n");
        Path windows = source("sample/Windows.java", "/* Copyright 2018 Someone Else */\r\npackage sample;\r\n");

        newExecutor(new LicenseHeaderModule()).execute();

        assertThat(different)
                .as("a header of line comments is replaced, and keeps the years it names")
                .hasContent(HEADER.formatted("2019-2021") + "\npackage sample\n");
        assertThat(windows)
                .as("a source keeps its line separator")
                .hasContent(HEADER.formatted("2018").replace("\n", "\r\n") + "\r\npackage sample;\r\n");

        newExecutor(new LicenseHeaderModule().verify(true)).execute();
    }

    @Test
    public void takes_a_template_that_is_a_comment_already_as_it_stands() throws IOException {
        Files.writeString(project.resolve(LicenseHeaderModule.HEADER), "// SPDX-License-Identifier: Apache-2.0\n");
        Path sample = source("sample/Sample.java", "package sample;\n");

        newExecutor(new LicenseHeaderModule()).execute();

        assertThat(sample).hasContent("// SPDX-License-Identifier: Apache-2.0\n\npackage sample;\n");
        newExecutor(new LicenseHeaderModule().verify(true)).execute();
    }

    @Test
    public void writes_no_year_of_its_own() throws IOException {
        Files.writeString(project.resolve(LicenseHeaderModule.HEADER), TEMPLATE);
        Path sample = source("sample/Sample.java", "package sample;\n");

        assertThatThrownBy(newExecutor(new LicenseHeaderModule())::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("writes no year of its own")
                .hasMessageContaining(LicenseHeaderModule.HEADER);
        assertThat(sample).hasContent("package sample;\n");
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
