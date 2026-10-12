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
import build.jenesis.project.ForbiddenApisModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ForbiddenApisModuleRunTest {

    private static final String PINS = """
            forbiddenapis/maven/de.thetaphi/forbiddenapis 3.11 SHA-256/2d9243b165c566a5ca140be8dfd11ce7bf4ce00e07ccdc31d2c0719cd061c9c6
            """;

    @TempDir
    private Path root, project, sources;

    @Test
    public void reports_the_calls_that_the_bundled_and_the_project_signatures_forbid() throws IOException {
        compile();

        newExecutor(forbidding().pinning(Pinning.STRICT)).execute();

        Path report = root.resolve("forbiddenapis").resolve("check").resolve("output")
                .resolve("reports").resolve("forbiddenapis").resolve("forbiddenapis.txt");
        assertThat(report)
                .as("jdk-unsafe resolves against the release the classes were compiled for")
                .content()
                .contains("java.lang.String#toUpperCase() [Uses default locale] in sample.Sample (Sample.java:4)")
                .contains("java.lang.Thread#<init>(java.lang.Runnable) [Name the thread] in sample.Sample (Sample.java:5)")
                .doesNotContain("System#out");
    }

    @Test
    public void fails_the_build_on_a_finding_when_strict() throws IOException {
        compile();

        assertThatThrownBy(newExecutor(forbidding()
                .pinning(Pinning.STRICT)
                .strict(true))::execute)
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("forbiddenapis found 2 findings")
                .hasMessageContaining("jenesis.validator.forbiddenapis=strict");
    }

    private void compile() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(project.resolve("forbidden.txt"), """
                @defaultMessage Name the thread
                java.lang.Thread#<init>(java.lang.Runnable)
                """);
        Path source = Files.createDirectories(sources.resolve("sample")).resolve("Sample.java");
        Files.writeString(source, """
                package sample;
                public class Sample {
                    public Thread start(Runnable task) {
                        System.out.println("start".toUpperCase());
                        return new Thread(task);
                    }
                }
                """);
        Path classes = Files.createDirectories(project.resolve(BuildStep.CLASSES));
        int rc = javax.tools.ToolProvider.getSystemJavaCompiler()
                .run(null, null, null, "-d", classes.toString(), "--release", "17", source.toString());
        assertThat(rc).as("the sample compiles").isZero();
    }

    private static ForbiddenApisModule forbidding() {
        return new ForbiddenApisModule(repositories(), resolvers())
                .bundled(List.of("jdk-unsafe"))
                .signatures(List.of(Path.of("forbidden.txt")));
    }

    private static Map<String, build.jenesis.Repository> repositories() {
        return Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE));
    }

    private static Map<String, build.jenesis.Resolver> resolvers() {
        return Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE));
    }

    private BuildExecutor newExecutor(ForbiddenApisModule module) throws IOException {
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
        executor.addModule("forbiddenapis", module, "project");
        return executor;
    }
}
