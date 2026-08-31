package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.OpenApiModule;

import static org.assertj.core.api.Assertions.assertThat;

public class OpenApiModuleRunTest {

    private static final String VERSION = "7.25.0";

    private static final String SPECIFICATION = """
            openapi: 3.0.3
            info:
              title: Greeting API
              version: 1.0.0
            paths: {}
            components:
              schemas:
                Greeting:
                  type: object
                  properties:
                    text:
                      type: string
            """;

    @TempDir
    private Path root, project;

    @BeforeEach
    public void writeProject() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.setProperty("openapi/maven/org.openapitools/openapi-generator-cli", VERSION);
        versions.store(project.resolve(BuildStep.VERSIONS));
    }

    @Test
    public void collects_only_the_generated_sources_out_of_the_generated_project() throws IOException {
        Files.writeString(
                Files.createDirectories(project.resolve(OpenApiModule.FOLDER)).resolve("greeting.yaml"),
                SPECIFICATION);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("openapi", newModule().packageName("test.api"), "project");
        executor.execute();

        Path collected = root.resolve("openapi").resolve(OpenApiModule.COLLECT).resolve("output").resolve("sources");
        assertThat(collected.resolve("test").resolve("api").resolve("model").resolve("Greeting.java"))
                .as("a schema in the specification becomes a generated model class")
                .isNotEmptyFile();
        assertThat(collected.resolve("pom.xml"))
                .as("the generator writes a whole project, of which only the source folder is collected")
                .doesNotExist();
        assertThat(collected.resolve("docs"))
                .as("documentation the generator writes beside the sources stays out of the module")
                .doesNotExist();
        assertThat(collected.resolve("test").resolve("api").resolve("model").resolve("Greeting.java")).content()
                .as("the generator stamps the wall clock and the local zone into @Generated unless told not to, "
                        + "which would give the step a different output on every run")
                .doesNotContain("date = ");
    }

    @Test
    public void generates_nothing_when_the_specification_is_not_among_the_inputs() throws IOException {
        Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule("openapi", newModule(), "project");
        executor.execute();

        assertThat(root.resolve("openapi").resolve(OpenApiModule.COLLECT).resolve("output").resolve("sources"))
                .as("a shared openapi.properties activates the module for every project module, "
                        + "so one without the specification contributes no sources rather than failing")
                .doesNotExist();
    }

    private OpenApiModule newModule() {
        return new OpenApiModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver()));
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
