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
import build.jenesis.project.KtlintFormatModule;

import static org.assertj.core.api.Assertions.assertThat;

public class KtlintFormatModuleRunTest {

    private static final String PINS = """
            ktlint-format/maven/ch.qos.logback/logback-classic 1.3.14 SHA-256/f9b07a6dba4df3899381df7e597df450134e1879b1f3a757456b3cd1c8d94e2f
            ktlint-format/maven/ch.qos.logback/logback-core 1.3.14 SHA-256/9f53159af18a9d438bc398c970db3bb7e17ddb07b04bbb3b01dfe3454dd18862
            ktlint-format/maven/com.github.ajalt.clikt/clikt-core-jvm 5.0.2 SHA-256/18b2df30a3395a37823ec432375a15b485d292b6575e30ef184cc9e7c07d8b87
            ktlint-format/maven/com.github.ajalt.clikt/clikt-jvm 5.0.2 SHA-256/0835107b51d57214d5a2e15d4b214fdd66b3eee5bb3acf96ff35b23bd4a36cde
            ktlint-format/maven/com.github.ajalt.colormath/colormath-jvm 3.6.0 SHA-256/59f741adfe62053066782d8b1a45afd06685a4bc64b33277e54876b993ed885c
            ktlint-format/maven/com.github.ajalt.mordant/mordant-core-jvm 3.0.1 SHA-256/9cf9b46d1f49f2d6cf2635462b29dc59e0c29b1fb2f085b3312888bbe9c7cd31
            ktlint-format/maven/com.github.ajalt.mordant/mordant-jvm 3.0.1 SHA-256/9ed3b976fcccc78da746d49866fa8ebb8f10530a93c544ea0420259a607dd95e
            ktlint-format/maven/com.github.ajalt.mordant/mordant-jvm-ffm-jvm 3.0.1 SHA-256/2041c2f5f7b87095b115cb19155ee9257451854d08e5b8671165b43bc7e114cf
            ktlint-format/maven/com.github.ajalt.mordant/mordant-jvm-graal-ffi-jvm 3.0.1 SHA-256/6dd4bebc164aeacddacc8f98e8f871e00fd21ce7bc2eb0d18230ea83bddfc86a
            ktlint-format/maven/com.github.ajalt.mordant/mordant-jvm-jna-jvm 3.0.1 SHA-256/41063442c8891b2774536a9b87a5062a7fd20e6f1949974c6da72f49472d6f4d
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli 1.5.0 SHA-256/df984c24eecebc2c0bc5f20c4676dbbb341795dccf0e1820052157022da0e6df
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-baseline 1.5.0 SHA-256/e1cb88427d52cbb398b555ae90854d4a56581daf149abdf81e318b165448aae1
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-checkstyle 1.5.0 SHA-256/be3359300acb8c12bea8e971388d28c2c9d08cc2d4f972bf6532de1bf4a1f024
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-core 1.5.0 SHA-256/cc3f47e736051f36ad6525931c057971a158bd4c39b6d34fc03cd328ae0c5746
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-format 1.5.0 SHA-256/0ce520848775801dba2413e58903ca5efe0084698228e4e57c6dfa7350093543
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-html 1.5.0 SHA-256/5b666af72b28e9a3ad043049fb2158c83a39e2224f0674491b899b0ead174331
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-json 1.5.0 SHA-256/dcb8a9109b452f0934cf04d56b0e98031595be6cad062ff61ed4c0c6c703c519
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-plain 1.5.0 SHA-256/bd664d42947e1006ff402364ba53a20d2221b83cd666c74da7969d09858af265
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-plain-summary 1.5.0 SHA-256/a2375aa50052093c40a0fce8d094f27e36a11d04526163035aec49d149a733d3
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-sarif 1.5.0 SHA-256/a6e2721d202363a8c4b609255842e615844bfe42d45febba9a4d5bb980331742
            ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-ruleset-core 1.5.0 SHA-256/64d39075bdeda74782fc5c1ba95284c116f336e5606f4d1c645012d003224e1b
            ktlint-format/maven/com.pinterest.ktlint/ktlint-logger 1.5.0 SHA-256/c067c999baa41a68041ba04f9cbfa4c53ba5320eaaddc8dd3d29e2401463c202
            ktlint-format/maven/com.pinterest.ktlint/ktlint-rule-engine 1.5.0 SHA-256/7cdaee5290f8c24eff02aa7e831d98d9cb584176854376bca2c6977ab0f466be
            ktlint-format/maven/com.pinterest.ktlint/ktlint-rule-engine-core 1.5.0 SHA-256/750cf41707927f59290d07191dacad418e101f468dbfb3b0a9f29cae2af75903
            ktlint-format/maven/com.pinterest.ktlint/ktlint-ruleset-standard 1.5.0 SHA-256/ecae0c3356612244a8e0d901fe431c4a25a1edf5d7c674783b947511ebd167a8
            ktlint-format/maven/dev.drewhamilton.poko/poko-annotations-jvm 0.18.0 SHA-256/ae01637db8e38af0d1848595aa64c8cda77e980cdab737e45d3838e218c540a6
            ktlint-format/maven/io.github.detekt.sarif4k/sarif4k-jvm 0.6.0 SHA-256/b3ac96dd97acba8318dbe26f6a432d6c6db91c46c780805e8928b8103e5763dc
            ktlint-format/maven/io.github.oshai/kotlin-logging-jvm 7.0.3 SHA-256/241daa21665dd0ca55576a4bc4d8e9ace8891ae3c698cc77f13bb4bdac372e94
            ktlint-format/maven/net.java.dev.jna/jna 5.14.0 SHA-256/34ed1e1f27fa896bca50dbc4e99cf3732967cec387a7a0d5e3486c09673fe8c6
            ktlint-format/maven/org.ec4j.core/ec4j-core 1.1.0 SHA-256/3424a9c2632a8433c00ab0b7cc10489eb732675c97fe333aff5b5272c5e861f6
            ktlint-format/maven/org.jetbrains.intellij.deps/trove4j 1.0.20200330 SHA-256/c5fd725bffab51846bf3c77db1383c60aaaebfe1b7fe2f00d23fe1b7df0a439d
            ktlint-format/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable 2.1.0 SHA-256/c1b139a6f251c3b99e92befa326cb75d93a001d74c3ac601155a8cdb0d253783
            ktlint-format/maven/org.jetbrains.kotlin/kotlin-daemon-embeddable 2.1.0 SHA-256/6aa581bd53c3500e380e4bb6b2407f6d233910012f425349c2ed5a8ddbe29eac
            ktlint-format/maven/org.jetbrains.kotlin/kotlin-reflect 1.6.10 SHA-256/3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203
            ktlint-format/maven/org.jetbrains.kotlin/kotlin-script-runtime 2.1.0 SHA-256/15a2b82119e9f145ea028029bd31166584648a419157c20948c124fa33d40e50
            ktlint-format/maven/org.jetbrains.kotlin/kotlin-stdlib 2.1.0 SHA-256/d6f91b7b0f306cca299fec74fb7c34e4874d6f5ec5b925a0b4de21901e119c3f
            ktlint-format/maven/org.jetbrains.kotlin/kotlin-stdlib-common 1.7.20 SHA-256/e0e91962bc0007338bf5b1739f62927ac32d14ba3d827fa608ab4e5351729d5d
            ktlint-format/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk7 1.7.20 SHA-256/524da3c1a2ad56fd52c4ae2272ef3de421de8d2047ab1c51fc306d351243f2f5
            ktlint-format/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk8 1.7.20 SHA-256/1da0d306c995945e1f807240ef64b5cd2dd5ac58612afb1a8596143d10b7ded5
            ktlint-format/maven/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.6.4 SHA-256/c24c8bb27bb320c4a93871501a7e5e0c61607638907b197aef675513d4c820be
            ktlint-format/maven/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm 1.4.1 SHA-256/eba7f1c854296e4ce1418fb01360f8f10c5683e7c45aa3472018417a067636f3
            ktlint-format/maven/org.jetbrains.kotlinx/kotlinx-serialization-json-jvm 1.4.1 SHA-256/af604c46737121d4225fdb60ef0e17766a3c94b7c1c9ef76b4e3a5c7733d557e
            ktlint-format/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
            ktlint-format/maven/org.slf4j/slf4j-api 2.0.7 SHA-256/5d6298b93a1905c32cda6478808ac14c2d4a47e91535e53c41f7feeb85d946f4
            """;

    private static final String VERSION = "1.5.0";

    @TempDir
    private Path root, project;

    @Test
    public void formats_in_place_then_skips_an_unchanged_file_on_a_second_run() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(project.resolve(".editorconfig"), """
                root = true
                [*.kt]
                indent_size = 4
                """);
        Path sample = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.kt");
        Files.writeString(sample, """
                package sample

                fun main() {
                println("hello")
                }
                """);

        newExecutor().execute();

        Path resolved = root.resolve("ktlint-format").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned ktlint version resolves")
                    .anyMatch(name -> name.contains("ktlint-cli") && name.contains(VERSION));
        }
        String formatted = Files.readString(sample);
        assertThat(formatted)
                .as("the source is reformatted in place")
                .contains("    println(\"hello\")");
        Path command = root.resolve("ktlint-format").resolve("format").resolve("supplement").resolve("command");
        assertThat(command).as("the first run forks the formatter").exists();
        Path hashes = root.resolve("ktlint-format").resolve("format").resolve("output").resolve("formatted.properties");
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
                "ktlint-format",
                new KtlintFormatModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT),
                "project");
        return executor;
    }
}
