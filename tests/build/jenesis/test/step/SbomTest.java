package build.jenesis.test.step;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Sbom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SbomTest {

    @TempDir
    private Path root;
    private Path previous, next, supplement, argument;

    @BeforeEach
    public void setUp() throws Exception {
        previous = root.resolve("previous");
        next = Files.createDirectory(root.resolve("next"));
        supplement = Files.createDirectory(root.resolve("supplement"));
        argument = Files.createDirectory(root.resolve("argument"));
    }

    @Test
    public void shouldRun_fires_when_licenses_or_graph_changed() {
        for (Path changed : List.of(Path.of("licenses.properties"), Path.of("graph.properties"))) {
            BuildStepArgument argument = new BuildStepArgument(root, Map.of(
                    changed, Checksum.of(ChecksumStatus.ADDED)));
            SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>();
            arguments.put("input", argument);
            assertThat(new Sbom().shouldRun(arguments))
                    .as("change to " + changed + " triggers Sbom")
                    .isTrue();
        }
    }

    @Test
    public void embeds_a_cyclonedx_sbom_with_dependency_licenses() throws Exception {
        byte[] jarBytes = "jar-bytes".getBytes(StandardCharsets.UTF_8);
        Files.write(Files.createDirectories(argument.resolve("resolved")).resolve("lib.jar"), jarBytes);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(jarBytes));

        SequencedProperties dependencies = new SequencedProperties();
        dependencies.setProperty("main/compile/maven/org.example/lib/1.2.3", "resolved/lib.jar");
        dependencies.setProperty("main/runtime/maven/org.example/lib/1.2.3", "resolved/lib.jar");
        dependencies.store(argument.resolve(BuildStep.DEPENDENCIES));
        SequencedProperties licenses = new SequencedProperties();
        licenses.setProperty("maven/org.example/lib/1.2.3#0#id", "Apache-2.0");
        licenses.setProperty("maven/org.example/lib/1.2.3#0#category", "permissive");
        licenses.setProperty("maven/org.example/lib/1.2.3#0#name", "Apache License 2.0");
        licenses.setProperty("maven/org.example/lib/1.2.3#0#url", "https://www.apache.org/licenses/LICENSE-2.0.txt");
        licenses.store(argument.resolve("licenses.properties"));
        SequencedProperties graph = new SequencedProperties();
        graph.setProperty("edge/0", "main\tcompile\tmaven\ttrue\tcompile\t1.2.3\t\tmaven/org.example/lib/1.2.3");
        graph.setProperty("vertex/main/compile/maven/org.example/lib", "1.2.3\t\tfalse");
        graph.store(argument.resolve("graph.properties"));
        SequencedProperties metadata = new SequencedProperties();
        metadata.setProperty("project", "build.jenesis");
        metadata.setProperty("artifact", "demo");
        metadata.setProperty("version", "1.0.0");
        metadata.setProperty("name", "Demo");
        metadata.setProperty("description", "A demo project");
        metadata.setProperty("url", "https://example.com/demo");
        metadata.setProperty("license.apache.name", "Apache-2.0");
        metadata.setProperty("license.apache.url", "https://www.apache.org/licenses/LICENSE-2.0.txt");
        metadata.setProperty("developer.raphw.name", "Rafael Winterhalter");
        metadata.setProperty("developer.raphw.email", "rafael.wth@gmail.com");
        metadata.store(argument.resolve(BuildStep.METADATA));

        BuildStepResult result = new Sbom().apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                                argument,
                                Map.of(
                                        Path.of(BuildStep.DEPENDENCIES), Checksum.of(ChecksumStatus.ADDED),
                                        Path.of(BuildStep.METADATA), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        assertThat(result.next()).isTrue();

        Path embedded = next.resolve("resources").resolve("META-INF").resolve("sbom").resolve("demo.cdx.json");
        assertThat(embedded).isNotEmptyFile();
        String sbom = Files.readString(embedded);
        assertThat(sbom)
                .contains("\"purl\": \"pkg:maven/build.jenesis/demo@1.0.0\"")
                .contains("\"purl\": \"pkg:maven/org.example/lib@1.2.3\"")
                .contains("\"content\": \"" + sha256 + "\"")
                .contains("\"id\": \"Apache-2.0\"");
        assertThat(sbom)
                .as("the resolved dependency graph is emitted as CycloneDX relationships")
                .contains("\"bom-ref\": \"org.example/lib/1.2.3\"")
                .contains("{ \"ref\": \"build.jenesis/demo/1.0.0\", \"dependsOn\": [\"org.example/lib/1.2.3\"] }");
        assertThat(sbom)
                .as("the subject component carries the project's own description, developers and website")
                .contains("\"description\": \"A demo project\"")
                .contains("\"name\": \"Rafael Winterhalter\", \"email\": \"rafael.wth@gmail.com\"")
                .contains("{ \"type\": \"website\", \"url\": \"https://example.com/demo\" }")
                .as("a deterministic serial number is derived from the document content")
                .contains("\"serialNumber\": \"urn:uuid:");

        SequencedProperties manifest = SequencedProperties.ofFiles(next.resolve("manifest.mf"));
        assertThat(manifest.getProperty("Sbom-Format")).isEqualTo("CycloneDX");
        assertThat(manifest.getProperty("Sbom-Location")).isEqualTo("META-INF/sbom/demo.cdx.json");

        assertThat(next.resolve("reports").resolve("sbom").resolve("demo-1.0.0.cdx.json")).isNotEmptyFile();
    }

    @Test
    public void records_the_tag_and_the_revision_and_locates_the_source_at_the_revision() throws Exception {
        String sbom = sbom(Map.of(
                "scm.url", "https://example.com/demo",
                "scm.connection", "scm:git:https://example.com/demo.git",
                "scm.tag", "v1.0.0",
                "scm.revision", "0123abcd"));
        assertThat(sbom)
                .contains("{ \"type\": \"vcs\", \"url\": \"https://example.com/demo\" }")
                .contains("{ \"type\": \"vcs\", \"url\": \"git+https://example.com/demo.git@0123abcd\" }")
                .contains("{ \"name\": \"jenesis:scm:tag\", \"value\": \"v1.0.0\" }")
                .contains("{ \"name\": \"jenesis:scm:revision\", \"value\": \"0123abcd\" }");
    }

    @Test
    public void locates_the_source_at_the_tag_without_a_revision() throws Exception {
        assertThat(sbom(Map.of("scm.connection", "scm|git|https://example.com/demo.git", "scm.tag", "v1.0.0")))
                .contains("{ \"type\": \"vcs\", \"url\": \"git+https://example.com/demo.git@v1.0.0\" }");
    }

    @Test
    public void takes_the_maven_head_tag_for_no_tag() throws Exception {
        assertThat(sbom(Map.of("scm.connection", "scm:git:https://example.com/demo.git", "scm.tag", "HEAD")))
                .as("a pom.xml declares HEAD for the root of the repository rather than for a tag")
                .doesNotContain("git+https://")
                .doesNotContain("jenesis:scm:tag");
    }

    @Test
    public void locates_no_source_for_a_connection_without_a_url() throws Exception {
        assertThat(sbom(Map.of("scm.connection", "scm:git:git@example.com:demo.git", "scm.revision", "0123abcd")))
                .contains("{ \"name\": \"jenesis:scm:revision\", \"value\": \"0123abcd\" }")
                .doesNotContain("git+");
    }

    @Test
    public void records_the_tree_of_a_release_as_a_swhid() throws Exception {
        assertThat(sbom(Map.of("scm.tree", "b293ceb1896f112828a70184317caf4f87f7d327")))
                .contains("{ \"name\": \"jenesis:scm:swhid\", \"value\": \"swh:1:dir:b293ceb1896f112828a70184317caf4f87f7d327\" }");
    }

    @Test
    public void rejects_a_tree_that_is_not_a_git_tree_id() {
        assertThatThrownBy(() -> sbom(Map.of("scm.tree", "HEAD")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("git rev-parse HEAD^{tree}")
                .hasMessageEndingWith("HEAD");
    }

    @Test
    public void computes_one_swhid_over_all_source_roots() throws Exception {
        Files.writeString(Files.createDirectories(argument.resolve(BuildStep.SOURCES + "demo")).resolve("Greeting.java"),
                "package demo;\n");
        Files.writeString(argument.resolve(BuildStep.SOURCES + "module-info.java"), "module demo {}\n");
        Files.writeString(Files.createDirectories(argument.resolve(BuildStep.RESOURCES + "demo")).resolve("greeting.properties"),
                "text=hello\n");
        Files.writeString(argument.resolve(BuildStep.RESOURCES + "demo.txt"), "a text file beside the demo folder\n");
        assertThat(sbom(new Sbom().swhid(true), Map.of()))
                .as("git write-tree over the same four files names the tree b293ceb1, listing demo.txt before demo/")
                .contains("{ \"name\": \"jenesis:source:swhid\", \"value\": \"swh:1:dir:b293ceb1896f112828a70184317caf4f87f7d327\" }");
    }

    @Test
    public void computes_no_swhid_unless_configured() throws Exception {
        Files.writeString(Files.createDirectories(argument.resolve(BuildStep.SOURCES)).resolve("module-info.java"),
                "module demo {}\n");
        assertThat(sbom(Map.of())).doesNotContain("jenesis:source:swhid");
    }

    @Test
    public void rejects_source_roots_that_disagree_on_a_file() throws Exception {
        Files.writeString(Files.createDirectories(argument.resolve(BuildStep.SOURCES)).resolve("notes.txt"), "one\n");
        Files.writeString(Files.createDirectories(argument.resolve(BuildStep.RESOURCES)).resolve("notes.txt"), "two\n");
        assertThatThrownBy(() -> sbom(new Sbom().swhid(true), Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("notes.txt");
    }

    @Test
    public void runs_again_for_a_changed_source_only_when_computing_the_swhid() throws Exception {
        Path configuration = root.resolve("sbom.properties");
        Files.writeString(configuration, "swhid=true\n");
        SequencedMap<String, BuildStepArgument> arguments = new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                argument,
                Map.of(Path.of(BuildStep.SOURCES + "demo/Greeting.java"), Checksum.of(ChecksumStatus.ALTERED)))));
        assertThat(Sbom.configured(configuration).shouldRun(arguments)).isTrue();
        assertThat(Sbom.configured(null).shouldRun(arguments)).isFalse();
    }

    @Test
    public void describes_the_runtime_a_release_file_names_as_a_platform_of_the_project() throws Exception {
        Files.writeString(argument.resolve(BuildStep.RELEASE), "IMPLEMENTOR=\"Oracle Corporation\"\n"
                + "JAVA_RUNTIME_VERSION=\"25.0.3+9-LTS-jvmci-b01\"\nGRAALVM_VERSION=\"25.0.3\"\n");

        assertThat(sbom(Map.of()))
                .contains("\"type\": \"platform\",\n"
                        + "      \"bom-ref\": \"Oracle Corporation/GraalVM/25.0.3\",\n"
                        + "      \"group\": \"Oracle Corporation\",\n"
                        + "      \"name\": \"GraalVM\",\n"
                        + "      \"version\": \"25.0.3\"")
                .contains("{ \"ref\": \"build.jenesis/demo/1.0.0\", \"dependsOn\": [\"Oracle Corporation/GraalVM/25.0.3\"] }");
    }

    @Test
    public void records_the_licence_configured_for_the_graalvm() throws Exception {
        Files.writeString(argument.resolve(BuildStep.RELEASE), "IMPLEMENTOR=\"GraalVM Community\"\nGRAALVM_VERSION=\"25.0.2\"\n");

        assertThat(sbom(new Sbom().graalvmLicense("GPL-2.0-with-classpath-exception"), Map.of()))
                .contains("\"name\": \"GraalVM\",\n"
                        + "      \"version\": \"25.0.2\",\n"
                        + "      \"licenses\": [\n"
                        + "        { \"license\": { \"id\": \"GPL-2.0-with-classpath-exception\" } }");
        assertThat(sbom(new Sbom(), Map.of()))
                .as("the release file names no licence, so none is recorded unless one is configured")
                .doesNotContain("licenses");
    }

    @Test
    public void describes_the_supplier_and_the_copyright_the_project_declares() throws Exception {
        assertThat(sbom(Map.of("organization.name", "Example Ltd",
                        "organization.url", "https://example.com",
                        "copyright", "Copyright 2020 Example Ltd")))
                .contains("\"supplier\": { \"name\": \"Example Ltd\", \"url\": [\"https://example.com\"] }")
                .contains("\"copyright\": \"Copyright 2020 Example Ltd\"");
        assertThat(sbom(Map.of()))
                .as("neither is invented when the project declares none")
                .doesNotContain("supplier", "copyright");
    }

    private String sbom(Map<String, String> scm) throws Exception {
        return sbom(new Sbom(), scm);
    }

    private String sbom(Sbom step, Map<String, String> scm) throws Exception {
        SequencedProperties metadata = new SequencedProperties();
        metadata.setProperty("project", "build.jenesis");
        metadata.setProperty("artifact", "demo");
        metadata.setProperty("version", "1.0.0");
        scm.forEach(metadata::setProperty);
        metadata.store(argument.resolve(BuildStep.METADATA));
        BuildStepResult result = step.apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                                argument,
                                Map.of(Path.of(BuildStep.METADATA), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        assertThat(result.next()).isTrue();
        return Files.readString(next.resolve("resources").resolve("META-INF").resolve("sbom").resolve("demo.cdx.json"));
    }

    @Test
    public void emits_without_a_version_where_a_project_names_none() throws Exception {
        SequencedProperties metadata = new SequencedProperties();
        metadata.setProperty("project", "build.jenesis");
        metadata.setProperty("artifact", "demo");
        metadata.store(argument.resolve(BuildStep.METADATA));

        BuildStepResult result = new Sbom().apply(Runnable::run,
                        new BuildStepContext(previous, next, supplement),
                        new LinkedHashMap<>(Map.of("argument", new BuildStepArgument(
                                argument,
                                Map.of(Path.of(BuildStep.METADATA), Checksum.of(ChecksumStatus.ADDED))))))
                .toCompletableFuture()
                .join();
        assertThat(result.next()).isTrue();

        Path embedded = next.resolve("resources").resolve("META-INF").resolve("sbom").resolve("demo.cdx.json");
        assertThat(embedded).isNotEmptyFile();
        assertThat(Files.readString(embedded))
                .as("a module that declares no version reports none")
                .doesNotContain("SNAPSHOT")
                .doesNotContain("\"version\": \"")
                .as("the subject purl carries no version when none is set")
                .contains("\"purl\": \"pkg:maven/build.jenesis/demo\"");
        assertThat(next.resolve("reports").resolve("sbom").resolve("demo.cdx.json"))
                .as("the standalone report is named without a version suffix")
                .isNotEmptyFile();
    }
}
