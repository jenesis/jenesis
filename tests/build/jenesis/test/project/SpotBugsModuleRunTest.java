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
import build.jenesis.project.SpotBugsModule;

import static org.assertj.core.api.Assertions.assertThat;

public class SpotBugsModuleRunTest {

    private static final String PINS = """
            spotbugs/maven/com.github.spotbugs/spotbugs 4.9.6 SHA-256/62a0def31899338200fc9013b4db8a8aedfc3536ca7d70d59038b092dfaa6819
            spotbugs/maven/com.github.spotbugs/spotbugs-annotations 4.9.6 SHA-256/523d394a6b36174ad0a22f0c1c75b105ccff42869a8b7ce86e7fd339ca6f86ce
            spotbugs/maven/com.github.stephenc.jcip/jcip-annotations 1.0-1 SHA-256/4fccff8382aafc589962c4edb262f6aa595e34f1e11e61057d1c6a96e8fc7323
            spotbugs/maven/com.google.code.findbugs/jsr305 3.0.2 SHA-256/766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7
            spotbugs/maven/com.google.code.gson/gson 2.13.2 SHA-256/dd0ce1b55a3ed2080cb70f9c655850cda86c206862310009dcb5e5c95265a5e0
            spotbugs/maven/com.google.errorprone/error_prone_annotations 2.41.0 SHA-256/a56e782b5b50811ac204073a355a21d915a2107fce13ec711331ad036f660fcc
            spotbugs/maven/jaxen/jaxen 2.0.0 SHA-256/9499e487a66268f47b8307d130cd1e13a58392105e98a51f6a525db79c615cc5
            spotbugs/maven/net.sf.saxon/Saxon-HE 12.9 SHA-256/8f3a9216a537367132293eacbba9df062eace8f8b16a184af59e2e4839d4cd41
            spotbugs/maven/org.apache.bcel/bcel 6.10.0 SHA-256/afd26d78e921d5f843f5745c44a6edede5b1f607179d8ac76797a57bcbd430e2
            spotbugs/maven/org.apache.commons/commons-lang3 3.18.0 SHA-256/4eeeae8d20c078abb64b015ec158add383ac581571cddc45c68f0c9ae0230720
            spotbugs/maven/org.apache.commons/commons-text 1.14.0 SHA-256/121fce2282910c8f0c3ba793a5436b31beb710423cbe2d574a3fb7a73c508e92
            spotbugs/maven/org.apache.logging.log4j/log4j-api 2.25.1 SHA-256/20b9c77c0a9e54d1063a39e551dcaf98c7d8e7a4994648f84d0b9e14c71f7215
            spotbugs/maven/org.apache.logging.log4j/log4j-core 2.25.1 SHA-256/78c232747855464b182f0abf78a99a22c88d4d270ff585343dab55576d7420e2
            spotbugs/maven/org.dom4j/dom4j 2.2.0 SHA-256/3fae79e081096e1410645eb3557c63b79ca266d510ab479889511109becd1690
            spotbugs/maven/org.ow2.asm/asm 9.8 SHA-256/876eab6a83daecad5ca67eb9fcabb063c97b5aeb8cf1fca7a989ecde17522051
            spotbugs/maven/org.ow2.asm/asm-analysis 9.8 SHA-256/e640732fbcd3c6271925a504f125e38384688f4dfbbf92c8622dfcee0d09edb9
            spotbugs/maven/org.ow2.asm/asm-commons 9.8 SHA-256/3301a1c1cb4c59fcc5292648dac1d7c5aed4c0f067dfbe88873b8cdfe77404f4
            spotbugs/maven/org.ow2.asm/asm-tree 9.8 SHA-256/14b7880cb7c85eed101e2710432fc3ffb83275532a6a894dc4c4095d49ad59f1
            spotbugs/maven/org.ow2.asm/asm-util 9.8 SHA-256/8ba0460ecb28fd0e2980e5f3ef3433a513a457bc077f81a53bdc75b587a08d15
            spotbugs/maven/org.slf4j/slf4j-api 2.0.17 SHA-256/7b751d952061954d5abfed7181c1f645d336091b679891591d63329c622eb832
            spotbugs/maven/org.xmlresolver/xmlresolver 5.3.3 SHA-256/1fe4d5b92f708dcdb82dbce12919e0171e6b5ca62c6dca6220483625098feb5f
            spotbugs/maven/org.xmlresolver/xmlresolver/jar/data 5.3.3 SHA-256/b0c487ad2f3e558be8d829c916d2458d10aca6a5bafa7a4d0524b70845e48a5c
            """;

    private static final String VERSION = "4.9.6";

    @TempDir
    private Path root, project, sources;

    @Test
    public void downloads_the_pinned_spotbugs_and_writes_a_report() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(project.resolve("spotbugs-exclude.xml"), "<FindBugsFilter/>");

        Path source = Files.createDirectories(sources.resolve("sample")).resolve("Sample.java");
        Files.writeString(source, """
                package sample;
                public class Sample {
                    public boolean check(String value) {
                        return value == "expected";
                    }
                }
                """);
        Path classes = Files.createDirectories(project.resolve(BuildStep.CLASSES));
        int rc = javax.tools.ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classes.toString(), "--release", "17", source.toString());
        assertThat(rc).as("the sample compiles").isZero();

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "spotbugs",
                new SpotBugsModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT),
                "project");
        executor.execute();

        Path resolved = root.resolve("spotbugs").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned SpotBugs version resolves")
                    .anyMatch(name -> name.contains("spotbugs") && name.contains(VERSION));
        }
        Path report = root.resolve("spotbugs").resolve("check").resolve("output").resolve("reports").resolve("spotbugs").resolve("spotbugs-report.xml");
        assertThat(report).isNotEmptyFile();
        assertThat(report).content().contains("ES_COMPARING");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
