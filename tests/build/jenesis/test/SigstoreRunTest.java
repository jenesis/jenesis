package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Sigstore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SigstoreRunTest {

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
        return downloaded("https://repo1.maven.org/maven2/dev/sigstore/sigstore-java/2.3.0/sigstore-java-2.3.0.pom",
                "artifact.pom");
    }

    private Path bundle() throws IOException {
        return downloaded("https://repo1.maven.org/maven2/dev/sigstore/sigstore-java/2.3.0/"
                + "sigstore-java-2.3.0.pom.sigstore.json", "bundle.sigstore.json");
    }


    private Path downloaded(String uri, String name) throws IOException {
        Path target = root.resolve(name);
        if (Files.exists(target)) {
            return target;
        }
        URLConnection connection = URI.create(uri).toURL().openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(30_000);
        try (InputStream stream = connection.getInputStream()) {
            return Files.write(target, stream.readAllBytes());
        }
    }
}
