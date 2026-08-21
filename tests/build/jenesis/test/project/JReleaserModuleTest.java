package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.project.JReleaserModule;
import build.jenesis.project.ReleaseModule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JReleaserModuleTest {

    @TempDir
    private Path root;
    private Path source;

    @BeforeEach
    public void setUp() throws Exception {
        source = Files.createDirectory(root.resolve("source"));
    }

    @AfterEach
    public void tearDown() {
        System.clearProperty("jenesis.jreleaser.config");
        System.clearProperty("jenesis.jreleaser.executable");
        System.clearProperty("jenesis.jreleaser.dryRun");
    }

    @Test
    public void discovers_no_configuration_by_default() {
        assertThat(JReleaserModule.configured(root)).isNull();
    }

    @Test
    public void discovers_each_configuration_flavour() throws IOException {
        for (String name : List.of("jreleaser.json", "jreleaser.toml", "jreleaser.yaml", "jreleaser.yml")) {
            Files.writeString(root.resolve(name), "");
            assertThat(JReleaserModule.configured(root))
                    .as("the most preferred remaining flavour wins")
                    .isEqualTo(root.resolve(name));
        }
    }

    @Test
    public void honours_an_explicitly_configured_file() throws IOException {
        Files.writeString(root.resolve("jreleaser.yml"), "");
        Files.writeString(root.resolve("elsewhere.yml"), "");
        System.setProperty("jenesis.jreleaser.config", "elsewhere.yml");
        assertThat(JReleaserModule.configured(root)).isEqualTo(root.resolve("elsewhere.yml"));
    }

    @Test
    public void rejects_an_explicitly_configured_file_that_is_missing() {
        System.setProperty("jenesis.jreleaser.config", "absent.yml");
        assertThatThrownBy(() -> JReleaserModule.configured(root))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absent.yml");
    }

    @Test
    public void wires_nothing_without_a_configuration() throws IOException {
        assertThat(release("release")).doesNotContainKey("release/jreleaser");
    }

    @Test
    public void emits_the_project_version_for_jreleaser() throws IOException {
        Files.writeString(root.resolve("jreleaser.yml"), "");
        Path emitted = release("release/jreleaser/environment").get("release/jreleaser/environment");
        assertThat(SequencedProperties.ofFiles(emitted.resolve(JReleaserModule.VARIABLES))
                .getProperty("JRELEASER_PROJECT_VERSION"))
                .as("the version the build stamped is the version JReleaser releases")
                .isEqualTo("1.2.3");
    }

    @Test
    public void emits_no_version_when_the_project_declares_none() throws IOException {
        Files.writeString(root.resolve("jreleaser.yml"), "");
        Path emitted = release("release/jreleaser/environment", null).get("release/jreleaser/environment");
        assertThat(SequencedProperties.ofFiles(emitted.resolve(JReleaserModule.VARIABLES)).stringPropertyNames())
                .as("nothing is invented for a project that never declared a version")
                .isEmpty();
    }

    @Test
    public void reports_a_missing_executable_by_name() throws IOException {
        Files.writeString(root.resolve("jreleaser.yml"), "");
        System.setProperty("jenesis.jreleaser.executable", "jreleaser-is-not-installed");
        assertThatThrownBy(() -> release("release/jreleaser/execute"))
                .hasStackTraceContaining("jreleaser-is-not-installed");
    }

    private SequencedMap<String, Path> release(String selector) throws IOException {
        return release(selector, "1.2.3");
    }

    private SequencedMap<String, Path> release(String selector, String version) throws IOException {
        BuildExecutor buildExecutor = BuildExecutor.of(Files.createDirectory(root.resolve("build-" + UUID.randomUUID())),
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
        buildExecutor.addSource("source", source);
        buildExecutor.addModule("release", new ReleaseModule(root, version), "source");
        return buildExecutor.execute(selector);
    }
}
