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
import build.jenesis.project.PalantirJavaFormatModule;

import static org.assertj.core.api.Assertions.assertThat;

public class PalantirJavaFormatModuleRunTest {

    private static final String PINS = """
            palantir-java-format/maven/com.fasterxml.jackson.core/jackson-annotations 2.21 SHA-256/53ca085f4a150f703f49e1aabd935bd03b43e1ea3d55d135438292af22cef56b
            palantir-java-format/maven/com.fasterxml.jackson.core/jackson-core 2.21.1 SHA-256/1edd5f2e49dca5f8e4519957c24b7b3050bd1c7ee883920da33cff031ff1f7c0
            palantir-java-format/maven/com.fasterxml.jackson.core/jackson-databind 2.21.1 SHA-256/b011eb5202d9ec889e27f1dcbdf6c63f06a76e7a16c0a1b30c6048d556c9a28e
            palantir-java-format/maven/com.fasterxml.jackson.datatype/jackson-datatype-guava 2.21.1 SHA-256/4c1b5f7034a3179c6ade358627c5007631188ac807ebb2b0c7fcb06287527b75
            palantir-java-format/maven/com.fasterxml.jackson.datatype/jackson-datatype-jdk8 2.21.1 SHA-256/e39839e058fa982d3899a5698107d4ed1425c08ebade43e9a1cd308472a80587
            palantir-java-format/maven/com.fasterxml.jackson.module/jackson-module-parameter-names 2.21.1 SHA-256/1eb87d27c27fade5a21414c57fe9fcd2b11f54b8c43549ae5e25731b5c304234
            palantir-java-format/maven/com.google.code.findbugs/jsr305 3.0.2 SHA-256/766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7
            palantir-java-format/maven/com.google.errorprone/error_prone_annotations 2.47.0 SHA-256/5364bc6f22e72e98195e406a58d3ba1c09ffa11dea0729592cb870dc2de4056d
            palantir-java-format/maven/com.google.guava/failureaccess 1.0.3 SHA-256/cbfc3906b19b8f55dd7cfd6dfe0aa4532e834250d7f080bd8d211a3e246b59cb
            palantir-java-format/maven/com.google.guava/guava 33.6.0-jre SHA-256/dc573e1fca4fd5454f4a5fd3d7da2df03002876a4175bafc14a95980dd7713b3
            palantir-java-format/maven/com.google.guava/listenablefuture 9999.0-empty-to-avoid-conflict-with-guava SHA-256/b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99
            palantir-java-format/maven/com.google.j2objc/j2objc-annotations 3.1 SHA-256/84d3a150518485f8140ea99b8a985656749629f6433c92b80c75b36aba3b099b
            palantir-java-format/maven/com.palantir.javaformat/palantir-java-format 2.91.0 SHA-256/488dfce17065c9204103ee216028389b2551c00d0bab660a78dd4a736cd20f91
            palantir-java-format/maven/com.palantir.javaformat/palantir-java-format-spi 2.91.0 SHA-256/eb30a72f5e123887bf0fd1ebc8a222fe0a844dbd4b258a5ac75df575da888f13
            palantir-java-format/maven/org.functionaljava/functionaljava 5.0 SHA-256/377ad140e7d26ba04fadf219b09d7e1c74bc0232fa4010b20c1c79db11f9670e
            palantir-java-format/maven/org.jspecify/jspecify 1.0.0 SHA-256/1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab
            """;

    private static final String VERSION = "2.91.0";

    @TempDir
    private Path root, project;

    @Test
    public void formats_in_place_then_skips_an_unchanged_file_on_a_second_run() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.java");
        Files.writeString(sample, "package sample;class Sample{int f(){return 42;}}\n");

        newExecutor().execute();

        Path resolved = root.resolve("palantir-java-format").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned palantir-java-format version resolves")
                    .anyMatch(name -> name.contains("palantir-java-format") && name.contains(VERSION));
        }
        String formatted = Files.readString(sample);
        assertThat(formatted)
                .as("the source is reformatted in place")
                .contains("class Sample {")
                .contains("int f() {");
        Path command = root.resolve("palantir-java-format").resolve("format").resolve("supplement").resolve("command");
        assertThat(command).as("the first run forks the formatter").exists();
        Path hashes = root.resolve("palantir-java-format").resolve("format").resolve("output").resolve("formatted.properties");
        assertThat(hashes).isNotEmptyFile();

        newExecutor().execute();

        assertThat(Files.readString(sample)).as("the already-formatted file is left byte-identical").isEqualTo(formatted);
        assertThat(command).as("the formatter is not forked again when nothing changed").doesNotExist();
    }

    private BuildExecutor newExecutor() throws IOException {
        BuildExecutor executor = BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
        executor.addSource("project", project);
        executor.addModule(
                "palantir-java-format",
                new PalantirJavaFormatModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT),
                "project");
        return executor;
    }
}
