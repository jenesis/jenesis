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
import build.jenesis.project.DetektModule;

import static org.assertj.core.api.Assertions.assertThat;

public class DetektModuleRunTest {

    private static final String PINS = """
            detekt/maven/dev.drewhamilton.poko/poko-annotations-jvm 0.16.0 SHA-256/940e6d50445bc6b0ae26ad414ec7b953a3e4e802dc7756cc14d56958bc97cc31
            detekt/maven/io.github.davidburstrom.contester/contester-breakpoint 0.2.0 SHA-256/672cbebb5d45a72b35dd81fd6127e187451bb6fb7fba35315bbdf2f57cfce835
            detekt/maven/io.github.detekt.sarif4k/sarif4k-jvm 0.6.0 SHA-256/b3ac96dd97acba8318dbe26f6a432d6c6db91c46c780805e8928b8103e5763dc
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-api 1.23.7 SHA-256/93fd0c9cdb22dd97ecfc843d55abfc52c9d9c6a0db32abdf9c85735e1bc8047d
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-cli 1.23.7 SHA-256/d7ddded00163f57ffc81d740ed6e194d0660eb8c60008af3acc3b9561d907875
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-core 1.23.7 SHA-256/a100da3ff7cc41c9a4ee1cfb6baf875da04714359a518e369673f1896ccd1e01
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-metrics 1.23.7 SHA-256/a3592ec4f927f4101b85782a47b94a19aec9c1c75a9054430d8898190d6d0065
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-parser 1.23.7 SHA-256/e217ab883e2e0f0e8a69b1fce7a131ff93a8bf2e06bd5fd395b38cde6b9c9504
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-psi-utils 1.23.7 SHA-256/6a054c91da27aac77db3ed509ff9f408c45410e1a5e989dc380aae3c09d7fb6e
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-html 1.23.7 SHA-256/bd0dcd0cccb72fe86f665c700d9c95dbfae33db35c09fc331b71c1896c8737fc
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-md 1.23.7 SHA-256/7c3ad77113fb8821b5adde900d318bb39d4d2361eae30f0de2adbe8fea2ea648
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-sarif 1.23.7 SHA-256/0bb1ecf6dd43758d193d99dd02333da3afc2bfc21238139fe319f586714b9b1d
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-txt 1.23.7 SHA-256/34a7bee9da3c414bb69547b72ceaeab799b98a63b082b2c826460b5421c64c70
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-xml 1.23.7 SHA-256/c4b78909c4ef03f0dc1bbd807207aa4d75524f4a29e747ffeb4997d0f7c8e09b
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules 1.23.7 SHA-256/b94ae25b9bc5c99bb1b9b289ea9b8138e220428f92d74d2216bc278b6cfa5e46
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-complexity 1.23.7 SHA-256/d8f9098f42548785ea26bd08fd23551104fe4357be1e00ad567b626f86766d66
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-coroutines 1.23.7 SHA-256/c5123c9fbcbe4ed494ea8826ec3102285a4e479e1c0ddbb8071bbe4984524402
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-documentation 1.23.7 SHA-256/864f1e4dc8c605bfbb894ed67402ded9a23b1ac17c73c6aaf8e8f0514c3c3962
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-empty 1.23.7 SHA-256/abe9a00622e63d6c9123a57b79281e3e951d3a6498ddc758564bbdfeb988f0ff
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-errorprone 1.23.7 SHA-256/431f016bbf3275a5ba6c7b9c6090f63b878df3e801b05190939cc2863402bf04
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-exceptions 1.23.7 SHA-256/02c715b2107a7223f203405bb8e4b5b418c3643e264029c750e7a3e504c1be78
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-naming 1.23.7 SHA-256/c99c026cc45578703353b9eb2108b8bc1cd445170cabb3057163f45e53926886
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-performance 1.23.7 SHA-256/c00fd0ae45b4c35fed4c3b180666eda9b5eeb1912b6d27efead0d2926c93920a
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-style 1.23.7 SHA-256/9effb12bece270a623c6719f57c0e93547d3c5bf00b221e77dd461aab9981169
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-tooling 1.23.7 SHA-256/7a148ecfd983ee258a67eb8bb194bda7d628a3b85fa9283a29c5b1d4a7e9d02d
            detekt/maven/io.gitlab.arturbosch.detekt/detekt-utils 1.23.7 SHA-256/0b5c211c72f63985a3e2e0f12f9a7733c7b3411b983a1c7f3175f3bd68d72194
            detekt/maven/org.jcommander/jcommander 1.85 SHA-256/fa7552d2831a2b20778d86851d093edca68fbc0a77f792b6223110e4fae67a70
            detekt/maven/org.jetbrains.intellij.deps/trove4j 1.0.20200330 SHA-256/c5fd725bffab51846bf3c77db1383c60aaaebfe1b7fe2f00d23fe1b7df0a439d
            detekt/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable 2.0.10 SHA-256/ea76850ffe3b84c92654a73f8e366964857f7e95687ca158882241cebef591e8
            detekt/maven/org.jetbrains.kotlin/kotlin-daemon-embeddable 2.0.10 SHA-256/a500d6f7bb2152a8387a9e24b886d0654fdbdb6662ec264c544ce9346f186141
            detekt/maven/org.jetbrains.kotlin/kotlin-reflect 1.6.10 SHA-256/3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203
            detekt/maven/org.jetbrains.kotlin/kotlin-script-runtime 2.0.10 SHA-256/58f4f7ad99a4a045964b44fe55f0b2604d2c5f51ff4d97c7e6817983fdf92ea7
            detekt/maven/org.jetbrains.kotlin/kotlin-stdlib 2.0.10 SHA-256/60f1cefbf1c101676abc9f413f569dad464929d7dc63d5e70e15ae26986e08ac
            detekt/maven/org.jetbrains.kotlin/kotlin-stdlib-common 1.7.20 SHA-256/e0e91962bc0007338bf5b1739f62927ac32d14ba3d827fa608ab4e5351729d5d
            detekt/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk7 1.7.20 SHA-256/524da3c1a2ad56fd52c4ae2272ef3de421de8d2047ab1c51fc306d351243f2f5
            detekt/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk8 1.7.20 SHA-256/1da0d306c995945e1f807240ef64b5cd2dd5ac58612afb1a8596143d10b7ded5
            detekt/maven/org.jetbrains.kotlinx/kotlinx-html-jvm 0.8.1 SHA-256/98bda1c78a5028a134ceb25b63f5c130c89349730d35fd47ef7490b6bf0b63b3
            detekt/maven/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm 1.4.1 SHA-256/eba7f1c854296e4ce1418fb01360f8f10c5683e7c45aa3472018417a067636f3
            detekt/maven/org.jetbrains.kotlinx/kotlinx-serialization-json-jvm 1.4.1 SHA-256/af604c46737121d4225fdb60ef0e17766a3c94b7c1c9ef76b4e3a5c7733d557e
            detekt/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
            detekt/maven/org.snakeyaml/snakeyaml-engine 2.7 SHA-256/4053f878c171692aab8782f53a3974f43e55e2b6ed12c3682b36a46968c5ded1
            """;

    private static final String VERSION = "1.23.7";

    private static final String CONFIG = """
            style:
              active: true
              MaxLineLength:
                active: true
                maxLineLength: 10
            """;

    @TempDir
    private Path root, project;

    @Test
    public void downloads_the_pinned_detekt_and_writes_a_report() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(project.resolve("detekt.yml"), CONFIG);
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.kt"), """
                package sample
                fun greetTheEntireWorld(): Int = 42
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "detekt",
                new DetektModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT),
                "project");
        executor.execute();

        Path resolved = root.resolve("detekt").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned detekt version resolves")
                    .anyMatch(name -> name.contains("detekt-cli") && name.contains(VERSION));
        }
        Path report = root.resolve("detekt").resolve("check").resolve("output").resolve("reports").resolve("detekt").resolve("detekt-report.xml");
        assertThat(report).isNotEmptyFile();
        assertThat(report).content().contains("MaxLineLength");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
