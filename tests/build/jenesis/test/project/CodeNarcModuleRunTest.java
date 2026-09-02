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
import build.jenesis.project.CodeNarcModule;

import static org.assertj.core.api.Assertions.assertThat;

public class CodeNarcModuleRunTest {

    private static final String PINS = """
            codenarc/maven/com.github.javaparser/javaparser-core 3.23.0 SHA-256/1d732f1b057b9e2ea69935180b5a6abf2c776f5e311a96651c3c03b0616032cc
            codenarc/maven/com.thoughtworks.qdox/qdox 1.12.1 SHA-256/21fba22f830e9268f07cf4ab2d99e8181abbdcb0cb91ee0228eb3cb918dcdd1d
            codenarc/maven/org.apache.ant/ant 1.10.11 SHA-256/88c0b89bbbaae01e0d9fcae93be792f5abbe3409106f8eee858fdf365dbc0754
            codenarc/maven/org.apache.ant/ant-antlr 1.10.11 SHA-256/ff253b3699f859115378c0c4322c94d591499eac97285942722f53a7e85182f1
            codenarc/maven/org.apache.ant/ant-junit 1.10.11 SHA-256/02267c1233cde1bc196f5aed40b6b8b61b79c47c2f6729ae1acf24ebfe89e815
            codenarc/maven/org.apache.ant/ant-launcher 1.10.11 SHA-256/dab530df7a980b5ac8fd7e8d208243ae0d3ebd6de09b1aa2ce756360cc2ed256
            codenarc/maven/org.apache.groovy/groovy 4.0.23 SHA-256/b26ee90507fecda8c6da6d3fdbeb8b2c99979ac8b8aa2459a4813e6bee7ae6e6
            codenarc/maven/org.codehaus.groovy/groovy 3.0.9 SHA-256/77bf86897f295f8cae2e1f46b1eca109f487ba81b66ef24a2b6dcba1eb7d6ce7
            codenarc/maven/org.codehaus.groovy/groovy-ant 3.0.9 SHA-256/b22c2f153a67af2abbbbccf768cfb333181c7d089aee113b36d8ef04e5ae8581
            codenarc/maven/org.codehaus.groovy/groovy-docgenerator 3.0.9 SHA-256/861647dfbd80077090c0b9eb14a695f25eaf02ed422e0b12daf5473ac2cb9609
            codenarc/maven/org.codehaus.groovy/groovy-groovydoc 3.0.9 SHA-256/73710df42537355e9831add7cdc4393e036f040b1ae5f1927c25635e8f8baeee
            codenarc/maven/org.codehaus.groovy/groovy-json 3.0.9 SHA-256/fb8edd9f367e0a5debc03f31523755a95e9eedae8bd1c0e9df1493188e350263
            codenarc/maven/org.codehaus.groovy/groovy-templates 3.0.9 SHA-256/6188c3be1f2db4c562976339464847345c38ae43136c9334c9e2c027c6f38059
            codenarc/maven/org.codehaus.groovy/groovy-xml 3.0.9 SHA-256/2bcecd848caed3975358a5c556aa49154b2d66f0c8a5c17043dd7b7d8985b6fe
            codenarc/maven/org.codenarc/CodeNarc 3.5.0 SHA-256/444e61901aa646d9a980e4c12197e2d1c859e3b7a0c6737dfe80f67725282a27
            codenarc/maven/org.gmetrics/GMetrics 2.1.0 SHA-256/2aa46be0478c46e921b2d9e699ae408ec82b5fc2516abecf56c42682ee2914b3
            codenarc/maven/org.slf4j/slf4j-api 1.7.35 SHA-256/84cbd60deaf9e18db8cb181e43db4e63f7de353cfcaf654a76d85b22da4d2762
            codenarc/maven/org.slf4j/slf4j-simple 2.0.16 SHA-256/effc32018658bea09d1e08c7d1060ccad46c086960f583d07dd7ffe9c1172a47
            """;

    private static final String CONFIG = """
            <ruleset xmlns="http://codenarc.org/ruleset/1.0">
                <rule class="org.codenarc.rule.basic.EmptyIfStatementRule"/>
            </ruleset>
            """;

    @TempDir
    private Path root, project;

    @Test
    public void downloads_the_pinned_codenarc_and_writes_a_report() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(project.resolve("codenarc.xml"), CONFIG);
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.groovy"), """
                package sample
                class Sample {
                    void run() {
                        if (true) { }
                    }
                }
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "codenarc",
                new CodeNarcModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT),
                "project");
        executor.execute();

        Path resolved = root.resolve("codenarc").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned CodeNarc version resolves")
                    .anyMatch(name -> name.contains("CodeNarc") && name.contains("3.5.0"));
        }
        Path report = root.resolve("codenarc").resolve("check").resolve("output").resolve("reports").resolve("codenarc").resolve("codenarc-report.xml");
        assertThat(report).isNotEmptyFile();
        assertThat(report).content().contains("EmptyIfStatement");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
