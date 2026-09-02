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
import build.jenesis.project.ScalastyleModule;

import static org.assertj.core.api.Assertions.assertThat;

public class ScalastyleModuleRunTest {

    private static final String PINS = """
            scalastyle/maven/com.beautiful-scala/scalastyle_2.13 1.5.1 SHA-256/469254118554648963ebeb2e8c0be0f4c093b8099ed05794b70de3c37e3ba7a1
            scalastyle/maven/com.typesafe/config 1.4.1 SHA-256/4c0aa7e223c75c8840c41fc183d4cd3118140a1ee503e3e08ce66ed2794c948f
            scalastyle/maven/org.scala-lang.modules/scala-collection-compat_2.13 2.5.0 SHA-256/93f8bf202ac28c4ca13562e31f6881a7770768e12b056b568139f37c025a3841
            scalastyle/maven/org.scala-lang.modules/scala-parser-combinators_2.13 1.1.2 SHA-256/5c285b72e6dc0a98e99ae0a1ceeb4027dab9adfa441844046bd3f19e0efdcb54
            scalastyle/maven/org.scala-lang.modules/scala-xml_2.13 1.2.0 SHA-256/213d2b7840bed5d1a1d5abfa1d72d7c7454473a6f77ea329fff0574910056fd3
            scalastyle/maven/org.scala-lang/scala-library 2.13.6 SHA-256/f19ed732e150d3537794fd3fe42ee18470a3f707efd499ecd05a99e727ff6c8a
            scalastyle/maven/org.scalariform/scalariform_2.13 0.2.10 SHA-256/76b6266960750e560b5a3cbbaa58074e909d0da50adf138b6e83555781bb2596
            """;

    private static final String VERSION = "1.5.1";

    private static final String CONFIG = """
            <scalastyle>
                <name>test</name>
                <check level="error" class="org.scalastyle.file.FileLineLengthChecker" enabled="true">
                    <parameters>
                        <parameter name="maxLineLength">10</parameter>
                    </parameters>
                </check>
            </scalastyle>
            """;

    @TempDir
    private Path root, project;

    @Test
    public void downloads_the_pinned_scalastyle_and_writes_a_report() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(project.resolve("scalastyle-config.xml"), CONFIG);
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.scala"), """
                package sample
                class Sample {
                  def greetTheEntireWorld(): Int = 42
                }
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "scalastyle",
                new ScalastyleModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT),
                "project");
        executor.execute();

        Path resolved = root.resolve("scalastyle").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned Scalastyle version resolves")
                    .anyMatch(name -> name.contains("scalastyle") && name.contains(VERSION));
        }
        Path report = root.resolve("scalastyle").resolve("check").resolve("output").resolve("reports").resolve("scalastyle").resolve("scalastyle-report.xml");
        assertThat(report).isNotEmptyFile();
        assertThat(report).content().contains("FileLineLengthChecker");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
