package build.jenesis.test.maven;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenModuleRepository;
import build.jenesis.module.JenesisRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MavenModuleRepositoryTest {

    @TempDir
    private Path maven;

    @Test
    public void derives_the_group_from_the_first_two_segments_of_a_module_name() throws IOException {
        writeArtifact("demo.convention", "demo.convention.greeter", "1.0.0", "jar", "greeter");

        assertThat(content(repository().fetch(Runnable::run, "demo.convention.greeter/1.0.0")))
                .isEqualTo("greeter");
    }

    @Test
    public void derives_the_group_from_the_entire_name_of_an_unqualified_module() throws IOException {
        writeArtifact("greeter", "greeter", "1.0.0", "jar", "unqualified");

        assertThat(content(repository().fetch(Runnable::run, "greeter/1.0.0"))).isEqualTo("unqualified");
    }

    @Test
    public void resolves_every_module_within_a_configured_group() throws IOException {
        writeArtifact("com.example.tools", "demo.convention.greeter", "1.0.0", "jar", "grouped");

        assertThat(content(repository().group("com.example.tools").fetch(Runnable::run, "demo.convention.greeter/1.0.0")))
                .isEqualTo("grouped");
    }

    @Test
    public void resolves_the_pom_that_a_maven_module_resolver_reads() throws IOException {
        writeArtifact("demo.convention", "demo.convention.greeter", "1.0.0", "pom", "<project/>");

        assertThat(content(repository().fetch(Runnable::run, "demo.convention.greeter/1.0.0:pom")))
                .isEqualTo("<project/>");
    }

    @Test
    public void resolves_a_classified_artifact_as_a_maven_classifier() throws IOException {
        writeArtifact("demo.convention", "demo.convention.greeter", "1.0.0", "sources", "jar", "sources");

        assertThat(content(repository().fetch(Runnable::run, "demo.convention.greeter-sources/1.0.0")))
                .isEqualTo("sources");
    }

    @Test
    public void resolves_a_signature_as_a_maven_checksum_extension() throws IOException {
        writeArtifact("demo.convention", "demo.convention.greeter", "1.0.0", "jar.asc", "signature");

        assertThat(content(repository().fetch(Runnable::run, "demo.convention.greeter/1.0.0", "asc")))
                .isEqualTo("signature");
    }

    @Test
    public void falls_back_to_a_jar_when_no_jmod_is_published() throws IOException {
        writeArtifact("demo.convention", "demo.convention.greeter", "1.0.0", "jar", "classes");

        assertThat(content(repository().fetch(Runnable::run, "demo.convention.greeter/1.0.0:jmod")))
                .isEqualTo("classes");
    }

    @Test
    public void resolves_a_floating_version_as_the_released_version_of_the_maven_metadata() throws IOException {
        writeMetadata("demo.convention", "demo.convention.greeter", "1.0.0", "2.0.0-beta", "1.0.0", "2.0.0-beta");
        writeArtifact("demo.convention", "demo.convention.greeter", "1.0.0", "jar", "released");

        assertThat(content(repository().fetch(Runnable::run, "demo.convention.greeter"))).isEqualTo("released");
    }

    @Test
    public void resolves_the_latest_version_of_the_maven_metadata_on_request() throws IOException {
        writeMetadata("demo.convention", "demo.convention.greeter", "1.0.0", "2.0.0-beta", "1.0.0", "2.0.0-beta");
        writeArtifact("demo.convention", "demo.convention.greeter", "2.0.0-beta", "jar", "prerelease");

        assertThat(content(repository().fetch(Runnable::run, "demo.convention.greeter/LATEST")))
                .isEqualTo("prerelease");
    }

    @Test
    public void resolves_a_floating_version_as_the_newest_stable_version_without_a_release_element()
            throws IOException {
        writeMetadata("demo.convention", "demo.convention.greeter", null, null, "1.0.0", "1.1.0", "2.0.0-beta");
        writeArtifact("demo.convention", "demo.convention.greeter", "1.1.0", "jar", "newest-stable");

        assertThat(content(repository().fetch(Runnable::run, "demo.convention.greeter"))).isEqualTo("newest-stable");
    }

    @Test
    public void fails_when_no_published_version_is_stable() throws IOException {
        writeMetadata("demo.convention", "demo.convention.greeter", null, null, "2.0.0-beta");

        assertThatThrownBy(() -> repository().fetch(Runnable::run, "demo.convention.greeter"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("demo.convention:demo.convention.greeter")
                .hasMessageContaining("RELEASE");
    }

    @Test
    public void returns_empty_for_a_floating_module_without_maven_metadata() throws IOException {
        assertThat(repository().fetch(Runnable::run, "demo.convention.greeter")).isEmpty();
    }

    @Test
    public void returns_empty_for_a_module_that_the_group_does_not_publish() throws IOException {
        writeArtifact("demo.convention", "demo.convention.greeter", "1.0.0", "jar", "greeter");

        assertThat(repository().fetch(Runnable::run, "demo.convention.missing/1.0.0")).isEmpty();
    }

    @Test
    public void resolves_a_module_of_a_prepended_repository_before_the_convention() throws IOException {
        writeArtifact("demo.convention", "demo.convention.greeter", "1.0.0", "jar", "convention");
        Path overlay = Files.writeString(maven.resolve("overlay.jar"), "overlay");
        Repository prepended = (_, coordinate, _) -> coordinate.equals("demo.convention.greeter/1.0.0")
                ? Optional.of(RepositoryItem.ofFile(overlay))
                : Optional.empty();

        JenesisRepository repository = repository().prepend(prepended);
        assertThat(content(repository.fetch(Runnable::run, "demo.convention.greeter/1.0.0"))).isEqualTo("overlay");
        assertThat(content(repository.fetch(Runnable::run, "demo.convention.other/1.0.0", null))).isNull();
    }

    @Test
    public void rejects_a_group_that_escapes_the_repository() {
        assertThatThrownBy(() -> repository().group("../escaped"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("group id");
    }

    @Test
    public void rejects_a_module_name_that_escapes_the_repository() {
        assertThatThrownBy(() -> repository().fetch(Runnable::run, "../escaped/1.0.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module name");
    }

    private MavenModuleRepository repository() {
        return new MavenModuleRepository(new MavenDefaultRepository(maven.toUri(), null, Map.of(), null));
    }

    private void writeArtifact(String groupId, String artifactId, String version, String type, String content)
            throws IOException {
        writeArtifact(groupId, artifactId, version, null, type, content);
    }

    private void writeArtifact(String groupId,
                               String artifactId,
                               String version,
                               String classifier,
                               String type,
                               String content) throws IOException {
        Path folder = Files.createDirectories(maven
                .resolve(Path.of(groupId.replace('.', File.separatorChar)))
                .resolve(artifactId)
                .resolve(version));
        Files.writeString(folder.resolve(artifactId
                + "-" + version
                + (classifier == null ? "" : "-" + classifier)
                + "." + type), content);
    }

    private void writeMetadata(String groupId, String artifactId, String release, String latest, String... versions)
            throws IOException {
        StringBuilder metadata = new StringBuilder("<metadata><versioning>");
        if (release != null) {
            metadata.append("<release>").append(release).append("</release>");
        }
        if (latest != null) {
            metadata.append("<latest>").append(latest).append("</latest>");
        }
        metadata.append("<versions>");
        for (String version : versions) {
            metadata.append("<version>").append(version).append("</version>");
        }
        metadata.append("</versions></versioning></metadata>");
        Path folder = Files.createDirectories(maven
                .resolve(Path.of(groupId.replace('.', File.separatorChar)))
                .resolve(artifactId));
        Files.writeString(folder.resolve("maven-metadata.xml"), metadata.toString());
    }

    private static String content(Optional<RepositoryItem> item) throws IOException {
        if (item.isEmpty()) {
            return null;
        }
        try (InputStream inputStream = item.get().toInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
