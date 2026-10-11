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
import build.jenesis.Pinning;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.KtfmtFormatModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KtfmtFormatModuleRunTest {

    private static final String PINS = """
            ktfmt-format/maven/com.facebook/ktfmt 0.64 SHA-256/7e4dbb5392b99fba5b5ccbe24a2eb4a6d88e419b97887520380c1f2550779b62
            ktfmt-format/maven/com.google.errorprone/error_prone_annotations 2.41.0 SHA-256/a56e782b5b50811ac204073a355a21d915a2107fce13ec711331ad036f660fcc
            ktfmt-format/maven/com.google.googlejavaformat/google-java-format 1.23.0 SHA-256/f9c5f181faee5c7b380feb96c1a94bec0c55859baeb2d14e9a47d92d40bef021
            ktfmt-format/maven/com.google.guava/failureaccess 1.0.3 SHA-256/cbfc3906b19b8f55dd7cfd6dfe0aa4532e834250d7f080bd8d211a3e246b59cb
            ktfmt-format/maven/com.google.guava/guava 33.5.0-jre SHA-256/1e301f0c52ac248b0b14fdc3d12283c77252d4d6f48521d572e7d8c4c2cc4ac7
            ktfmt-format/maven/com.google.guava/listenablefuture 9999.0-empty-to-avoid-conflict-with-guava SHA-256/b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99
            ktfmt-format/maven/com.google.j2objc/j2objc-annotations 3.1 SHA-256/84d3a150518485f8140ea99b8a985656749629f6433c92b80c75b36aba3b099b
            ktfmt-format/maven/net.java.dev.jna/jna 4.2.2 SHA-256/1f38af54e06c6e6f6dbf39ba2c052b952dea5dddb4871127b34639ddeb11bdbe
            ktfmt-format/maven/org.ec4j.core/ec4j-core 1.2.0 SHA-256/7517c08e91ba0925f9470b21e538bc0feac48398fc650eb1bae8af7c92492117
            ktfmt-format/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable 2.3.20 SHA-256/976f989d0b5f5d80e8e8a8ad4b73da0bfc27fdd965b9fa38362b2be79ecc1337
            ktfmt-format/maven/org.jetbrains.kotlin/kotlin-daemon-embeddable 2.3.20 SHA-256/8870bab840b8087c96c4ddc06088b4aedf5131c408af3674306304f1f96af3f4
            ktfmt-format/maven/org.jetbrains.kotlin/kotlin-reflect 1.6.10 SHA-256/3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203
            ktfmt-format/maven/org.jetbrains.kotlin/kotlin-script-runtime 2.3.20 SHA-256/6fcdb7da6e65cf8cc43e5aabab94bdcc48825e7933686f8a1bf694eb88f8e00e
            ktfmt-format/maven/org.jetbrains.kotlin/kotlin-stdlib 2.3.20 SHA-256/0ae12504a5040ebaf37703908483420d1a5624dd1d93f357665f8c77c848a01e
            ktfmt-format/maven/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.8.0 SHA-256/9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5
            ktfmt-format/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
            ktfmt-format/maven/org.jspecify/jspecify 1.0.0 SHA-256/1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab
            """;

    @TempDir
    private Path root, project;

    @Test
    public void formats_in_place_in_the_style_named_keeping_unused_imports_as_told() throws IOException {
        Path sample = sample("""
                package sample
                import java.util.List
                fun main() {
                println("hello")
                }
                """);

        newExecutor(false, module -> module.style(KtfmtFormatModule.Style.KOTLINLANG).removeUnusedImports(false)).execute();

        String formatted = Files.readString(sample);
        assertThat(formatted)
                .as("the Kotlin language style indents by four, and the unused import stays")
                .contains("\n    println(\"hello\")")
                .contains("import java.util.List");
        Path command = root.resolve("ktfmt-format").resolve("format").resolve("supplement").resolve("command");
        assertThat(command).as("the first run forks the formatter").exists();

        newExecutor(false, module -> module.style(KtfmtFormatModule.Style.KOTLINLANG).removeUnusedImports(false)).execute();

        assertThat(Files.readString(sample)).as("the already-formatted file is left byte-identical").isEqualTo(formatted);
        assertThat(command).as("the formatter is not forked again when nothing changed").doesNotExist();
    }

    @Test
    public void verify_mode_fails_once_the_style_changes_and_leaves_the_file_untouched() throws IOException {
        String formatted = """
                package sample

                fun main() {
                  println("hello")
                }
                """;
        Path sample = sample(formatted);

        newExecutor(true, module -> module).execute();

        assertThatThrownBy(newExecutor(true, module -> module.style(KtfmtFormatModule.Style.KOTLINLANG))::execute)
                .as("the unchanged source is verified again once the style changes")
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unexpected exit code");
        assertThat(sample).as("verify mode never rewrites the source").hasContent(formatted);
    }

    private Path sample(String source) throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.kt");
        Files.writeString(sample, source);
        return sample;
    }

    private BuildExecutor newExecutor(boolean verify, UnaryOperator<KtfmtFormatModule> configuration) throws IOException {
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
        executor.addModule(
                "ktfmt-format",
                configuration.apply(new KtfmtFormatModule(Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE)), Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE)))
                        .pinning(Pinning.STRICT)
                        .verify(verify)),
                "project");
        return executor;
    }
}
