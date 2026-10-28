package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.Sigstore;
import build.jenesis.maven.MavenDefaultRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SigstoreRunTest {

    private static final String POM = "SHA-256/3160712b8a77054c86b2eea9c0d0234ea48900b933ddb87e21bc2147acba5e08",
            BUNDLE = "SHA-256/c60ac0052a263d5440c74049ef53630ef2cbb08e7dd5f7a0fdba4b2880a24dcc";

    @TempDir
    private Path root;

    @Test
    public void verifies_what_a_release_workflow_published_to_maven_central() throws Exception {
        Sigstore.Attestation attestation = new Sigstore()
                .issuer("https://token.actions.githubusercontent.com")
                .verify(artifact(), bundle());
        assertThat(attestation.identity())
                .as("the identity is the workflow that ran the release, at the tag it released")
                .isEqualTo("https://github.com/sigstore/sigstore-java/.github/workflows/"
                        + "release-sigstore-java-from-tag.yaml@refs/tags/v2.3.0");
        assertThat(attestation.log()).isEqualTo("https://rekor.sigstore.dev");
        assertThat(attestation.recorded()).isBefore(Instant.now());
    }

    @Test
    public void verifies_against_the_trust_root_the_tool_carries() throws Exception {
        Sigstore.Attestation attestation = new Sigstore().verify(artifact(), bundle());
        assertThat(attestation.log())
                .as("a bundle of the public instance needs no trust root from the project")
                .isEqualTo("https://rekor.sigstore.dev");
    }

    @Test
    public void refuses_a_signing_time_the_certificate_does_not_cover() throws Exception {
        Path bundle = bundle();
        String text = Files.readString(bundle);
        int index = text.indexOf('"', text.indexOf(':', text.indexOf("\"integratedTime\"")) + 1) + 1;
        int end = text.indexOf('"', index);
        Files.writeString(bundle, text.substring(0, index)
                + (Long.parseLong(text.substring(index, end)) + 3_600)
                + text.substring(end));
        assertThatThrownBy(() -> new Sigstore().verify(artifact(), bundle))
                .as("an expired certificate is accepted only for the moment the log recorded")
                .isInstanceOf(GeneralSecurityException.class);
    }

    private Path artifact() throws IOException {
        return downloaded(null, POM, "artifact.pom");
    }

    private Path bundle() throws IOException {
        return downloaded("sigstore.json", BUNDLE, "bundle.sigstore.json");
    }

    private Path downloaded(String extension, String checksum, String name) throws IOException {
        Path target = root.resolve(name);
        if (Files.exists(target)) {
            return target;
        }
        String coordinate = "maven/dev.sigstore/sigstore-java/pom/2.3.0" + (extension == null ? "" : ":" + extension);
        RepositoryItem item = MavenDefaultRepository.ofEnvironment(Environment.NONE)
                .fetch(Runnable::run, "dev.sigstore", "sigstore-java", "2.3.0", "pom", null, extension)
                .orElseThrow(() -> new IllegalStateException("The configured Maven repository holds no " + coordinate));
        try (InputStream stream = item.toInputStream()) {
            Files.write(target, stream.readAllBytes());
        }
        Resolver.validate(target, checksum, coordinate);
        return target;
    }
}
