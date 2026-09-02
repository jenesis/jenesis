package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.GoogleJavaFormatModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GoogleJavaFormatModuleRunTest {

    private static final String PINS = """
            google-java-format/maven/com.google.code.findbugs/jsr305 3.0.2 SHA-256/766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7
            google-java-format/maven/com.google.errorprone/error_prone_annotations 2.21.1 SHA-256/d1f3c66aa91ac52549e00ae3b208ba4b9af7d72d68f230643553beb38e6118ac
            google-java-format/maven/com.google.googlejavaformat/google-java-format 1.35.0 SHA-256/b2a52e363d51dd86b3b38d1a8dd51538541b42ff1048769684681c676550f043
            google-java-format/maven/com.google.guava/failureaccess 1.0.1 SHA-256/a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26
            google-java-format/maven/com.google.guava/guava 32.1.3-jre SHA-256/6d4e2b5a118aab62e6e5e29d185a0224eed82c85c40ac3d33cf04a270c3b3744
            google-java-format/maven/com.google.guava/listenablefuture 9999.0-empty-to-avoid-conflict-with-guava SHA-256/b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99
            google-java-format/maven/com.google.j2objc/j2objc-annotations 2.8 SHA-256/f02a95fa1a5e95edb3ed859fd0fb7df709d121a35290eff8b74dce2ab7f4d6ed
            google-java-format/maven/org.checkerframework/checker-qual 3.37.0 SHA-256/e4ce1376cc2735e1dde220b62ad0913f51297704daad155a33f386bc5db0d9f7
            """;

    private static final String VERSION = "1.35.0";

    @TempDir
    private Path root, project;

    @Test
    public void formats_in_place_then_skips_an_unchanged_file_on_a_second_run() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.java");
        Files.writeString(sample, "package sample;class Sample{int f(){return 42;}}\n");

        newExecutor(false).execute();

        Path resolved = root.resolve("google-java-format").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned google-java-format version resolves")
                    .anyMatch(name -> name.contains("google-java-format") && name.contains(VERSION));
        }
        String formatted = Files.readString(sample);
        assertThat(formatted)
                .as("the source is reformatted in place")
                .contains("class Sample {")
                .contains("int f() {");
        Path command = root.resolve("google-java-format").resolve("format").resolve("supplement").resolve("command");
        assertThat(command).as("the first run forks the formatter").exists();
        Path hashes = root.resolve("google-java-format").resolve("format").resolve("output").resolve("formatted.properties");
        assertThat(hashes).isNotEmptyFile();

        newExecutor(false).execute();

        assertThat(Files.readString(sample)).as("the already-formatted file is left byte-identical").isEqualTo(formatted);
        assertThat(command).as("the formatter is not forked again when nothing changed").doesNotExist();
    }

    @Test
    public void verify_mode_fails_the_build_and_leaves_the_file_untouched() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.java");
        String unformatted = "package sample;class Sample{int f(){return 42;}}\n";
        Files.writeString(sample, unformatted);

        assertThatThrownBy(newExecutor(true)::execute)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("Unexpected exit code");
        assertThat(Files.readString(sample)).as("verify mode never rewrites the source").isEqualTo(unformatted);
        assertThat(root.resolve("google-java-format").resolve("format").resolve("output").resolve("formatted.properties"))
                .as("verify mode does not persist the incremental hash state")
                .doesNotExist();
    }

    private BuildExecutor newExecutor(boolean verify) throws IOException {
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
        executor.addSource("project", project);
        executor.addModule(
                "google-java-format",
                new GoogleJavaFormatModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT)
                        .verify(verify),
                "project");
        return executor;
    }
}
