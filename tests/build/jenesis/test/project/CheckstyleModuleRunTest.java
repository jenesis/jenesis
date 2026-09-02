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
import build.jenesis.project.CheckstyleModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CheckstyleModuleRunTest {

    private static final String PINS = """
            checkstyle/maven/com.google.code.findbugs/jsr305 3.0.2 SHA-256/766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7
            checkstyle/maven/com.google.errorprone/error_prone_annotations 2.28.0 SHA-256/f3fc8a3a0a4020706a373b00e7f57c2512dd26d1f83d28c7d38768f8682b231e
            checkstyle/maven/com.google.guava/failureaccess 1.0.2 SHA-256/8a8f81cf9b359e3f6dfa691a1e776985c061ef2f223c9b2c80753e1b458e8064
            checkstyle/maven/com.google.guava/guava 33.3.1-jre SHA-256/4bf0e2c5af8e4525c96e8fde17a4f7307f97f8478f11c4c8e35a0e3298ae4e90
            checkstyle/maven/com.google.guava/listenablefuture 9999.0-empty-to-avoid-conflict-with-guava SHA-256/b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99
            checkstyle/maven/com.google.j2objc/j2objc-annotations 3.0.0 SHA-256/88241573467ddca44ffd4d74aa04c2bbfd11bf7c17e0c342c94c9de7a70a7c64
            checkstyle/maven/com.puppycrawl.tools/checkstyle 10.21.0 SHA-256/50a553ca004d048ff7b455bc902cf6501b3005a68e31f55348c221387a2823b5
            checkstyle/maven/commons-beanutils/commons-beanutils 1.9.4 SHA-256/7d938c81789028045c08c065e94be75fc280527620d5bd62b519d5838532368a
            checkstyle/maven/commons-codec/commons-codec 1.15 SHA-256/b3e9f6d63a790109bf0d056611fbed1cf69055826defeb9894a71369d246ed63
            checkstyle/maven/commons-collections/commons-collections 3.2.2 SHA-256/eeeae917917144a68a741d4c0dff66aa5c5c5fd85593ff217bced3fc8ca783b8
            checkstyle/maven/commons-logging/commons-logging 1.2 SHA-256/daddea1ea0be0f56978ab3006b8ac92834afeefbd9b7e4e6316fca57df0fa636
            checkstyle/maven/info.picocli/picocli 4.7.6 SHA-256/ed441183f309b93f104ca9e071e314a4062a893184e18a3c7ad72ec9cba12ba0
            checkstyle/maven/net.sf.saxon/Saxon-HE 12.5 SHA-256/98c3a91e6e5aaf9b3e2b37601e04b214a6e67098493cdd8232fcb705fddcb674
            checkstyle/maven/org.antlr/antlr4-runtime 4.13.2 SHA-256/dd3e8a13a2d669bf84fb8d834de35ce4875f27157698d206241ec8488aadcaf7
            checkstyle/maven/org.apache.commons/commons-lang3 3.8.1 SHA-256/dac807f65b07698ff39b1b07bfef3d87ae3fd46d91bbf8a2bc02b2a831616f68
            checkstyle/maven/org.apache.commons/commons-text 1.3 SHA-256/8185b3a5311092d83ed1f184c2d093b3105d726bbd76867c32b3511542bb99a8
            checkstyle/maven/org.apache.httpcomponents.client5/httpclient5 5.1.3 SHA-256/28c759254f4e35319e078bb6ffea75676608dc12cb243b24fb3c8732522977fe
            checkstyle/maven/org.apache.httpcomponents.core5/httpcore5 5.1.3 SHA-256/f2bf2f2c7772169c9e30699719667ad30f9b46c4e9d7841907deb2d12d9923fe
            checkstyle/maven/org.apache.httpcomponents.core5/httpcore5-h2 5.1.3 SHA-256/d0e78ba15aa8ebe77982b660ac4b09a95d6e035dbdbea762577dc1c8e2935807
            checkstyle/maven/org.apache.httpcomponents/httpclient 4.5.13 SHA-256/6fe9026a566c6a5001608cf3fc32196641f6c1e5e1986d1037ccdbd5f31ef743
            checkstyle/maven/org.apache.httpcomponents/httpcore 4.4.14 SHA-256/f956209e450cb1d0c51776dfbd23e53e9dd8db9a1298ed62b70bf0944ba63b28
            checkstyle/maven/org.apache.maven.doxia/doxia-core 1.12.0 SHA-256/5e49cd827bebbcea5829d3b3883d17ad1ce15ebd6394aeb50ad50d7dfd939fcd
            checkstyle/maven/org.apache.maven.doxia/doxia-logging-api 1.12.0 SHA-256/985306162c0a9f4c309d46109447f30f02bf6fc9bc16a3e039d59e1dabd0192f
            checkstyle/maven/org.apache.maven.doxia/doxia-module-xdoc 1.12.0 SHA-256/e8731ba00a4edd34b20eff9e4a729c2045c62cb796c3e491692607de4476ab01
            checkstyle/maven/org.apache.maven.doxia/doxia-sink-api 1.12.0 SHA-256/5dca6aaaa9e70d8a0766e143ddcf9db09de5fde0fbcc78cb635d74e764dfcca5
            checkstyle/maven/org.apache.xbean/xbean-reflect 3.7 SHA-256/104e5e9bb5a669f86722f32281960700f7ec8e3209ef51b23eb9b6d23d1629cb
            checkstyle/maven/org.checkerframework/checker-qual 3.48.3 SHA-256/443685b1b232803baaf803c15d6f5a425473c6f7b81c5f276dfcf93288e389a5
            checkstyle/maven/org.codehaus.plexus/plexus-classworlds 2.6.0 SHA-256/52f77c5ec49f787c9c417ebed5d6efd9922f44a202f217376e4f94c0d74f3549
            checkstyle/maven/org.codehaus.plexus/plexus-component-annotations 2.1.0 SHA-256/bde3617ce9b5bcf9584126046080043af6a4b3baea40a3b153f02e7bbc32acac
            checkstyle/maven/org.codehaus.plexus/plexus-container-default 2.1.0 SHA-256/6dceb1246b188153bdcb6f962d543d51ddb672cca07cad94a78fbabc9edf0a39
            checkstyle/maven/org.codehaus.plexus/plexus-utils 3.3.0 SHA-256/76d174792540e2775af94d03d10fb2d3c776e2cd0ac0ebf427d3e570072bb9ce
            checkstyle/maven/org.javassist/javassist 3.28.0-GA SHA-256/57d0a9e9286f82f4eaa851125186997f811befce0e2060ff0a15a77f5a9dd9a7
            checkstyle/maven/org.reflections/reflections 0.10.2 SHA-256/938a2d08fe54050d7610b944d8ddc3a09355710d9e6be0aac838dbc04e9a2825
            checkstyle/maven/org.slf4j/slf4j-api 1.7.32 SHA-256/3624f8474c1af46d75f98bc097d7864a323c81b3808aa43689a6e1c601c027be
            checkstyle/maven/org.xmlresolver/xmlresolver 5.2.2 SHA-256/efc92bd7ed32b3e57095e0b3e872051ccfbbdcc980831ef33e89e38161a85222
            checkstyle/maven/org.xmlresolver/xmlresolver/jar/data 5.2.2 SHA-256/173904bdbd783ba0fac92c5bcc05da5d09f0ce7eed24346666ea0a239461f9b4
            """;

    private static final String VERSION = "10.21.0";

    private static final String CONFIG = """
            <?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <property name="severity" value="error"/>
                <module name="TreeWalker">
                    <module name="TypeName"/>
                </module>
            </module>
            """;

    @TempDir
    private Path root, project;

    @BeforeEach
    public void writeProject() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(project.resolve("checkstyle.xml"), CONFIG);
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("badName.java"), """
                package sample;
                public class badName {
                }
                """);
    }

    @Test
    public void writes_a_report_without_failing_the_build_in_report_only_mode() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "checkstyle",
                new CheckstyleModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT),
                "project");
        executor.execute();

        Path resolved = root.resolve("checkstyle").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned Checkstyle version is the one that resolves, not a floated RELEASE")
                    .anyMatch(name -> name.contains("checkstyle") && name.contains(VERSION));
        }
        Path report = root.resolve("checkstyle").resolve("check").resolve("output").resolve("reports").resolve("checkstyle").resolve("checkstyle-report.xml");
        assertThat(report)
                .as("report-only run still produces the Checkstyle XML report")
                .isNotEmptyFile();
        assertThat(report).content().contains("badName");
    }

    @Test
    public void strict_mode_fails_the_build_on_a_violation() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "checkstyle",
                new CheckstyleModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT)
                        .strict(true),
                "project");

        assertThatThrownBy(executor::execute)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause()
                .hasMessageContaining("Unexpected exit code");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
