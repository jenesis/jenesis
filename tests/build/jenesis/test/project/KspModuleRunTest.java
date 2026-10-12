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
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.KspModule;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class KspModuleRunTest {

    private static final Map<String, Repository> REPOSITORIES = Map.of("maven", MavenDefaultRepository.ofEnvironment(Environment.NONE));
    private static final Map<String, Resolver> RESOLVERS = Map.of("maven", MavenPomResolver.ofEnvironment(Environment.NONE));

    @TempDir
    private Path root, project;

    @BeforeEach
    public void writeProject() throws IOException {
        Files.writeString(Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample")).resolve("Point.kt"), """
                package sample

                import com.squareup.moshi.JsonClass

                @JsonClass(generateAdapter = true)
                data class Point(val x: Int, val y: Int)
                """);
    }

    @Test
    public void generates_the_sources_and_resources_of_the_declared_processors() throws IOException {
        Files.writeString(project.resolve(BuildStep.REQUIRES), """
                main/compile/maven/com.squareup.moshi/moshi/1.15.2=
                ksp/plugin/maven/com.squareup.moshi/moshi-kotlin-codegen/1.15.2=
                """);
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("libraries", new Dependencies(REPOSITORIES, RESOLVERS).group("main"), "project");
        executor.addModule("ksp", new KspModule(REPOSITORIES, RESOLVERS), "project", "libraries");
        executor.execute();

        Path output = root.resolve("ksp").resolve(KspModule.GENERATE).resolve("output");
        assertThat(output.resolve(BuildStep.SOURCES + "sample/PointJsonAdapter.kt"))
                .as("the processor resolved the annotation against the module's libraries")
                .content()
                .contains("class PointJsonAdapter");
        assertThat(output.resolve(BuildStep.RESOURCES + "META-INF/proguard/moshi-sample.Point.pro")).isNotEmptyFile();
    }

    @Test
    public void refuses_to_run_without_a_declared_processor() throws IOException {
        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("ksp", new KspModule(REPOSITORIES, RESOLVERS), "project");
        assertThatThrownBy(executor::execute)
                .hasStackTraceContaining("no symbol processor is declared")
                .hasStackTraceContaining("@jenesis.plugin ksp <coordinate>");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(),
                BuildExecutorCache.nop(),
                false,
                false,
                0);
    }
}
