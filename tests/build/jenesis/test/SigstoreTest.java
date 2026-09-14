package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Sigstore;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class SigstoreTest {

    @TempDir
    private Path root;

    @Test
    public void refuses_a_document_that_is_not_a_bundle() throws Exception {
        assertThatThrownBy(() -> new Sigstore().trustedRoot(anchors("{}").toUri()).verify(
                artifact("released"),
                bundle("{\"mediaType\":\"application/json\"}")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("application/vnd.dev.sigstore.bundle");
    }

    @Test
    public void refuses_a_bundle_that_no_transparency_log_recorded() throws Exception {
        assertThatThrownBy(() -> new Sigstore().trustedRoot(anchors("{}").toUri()).verify(
                artifact("released"),
                bundle("""
                        {"mediaType":"application/vnd.dev.sigstore.bundle.v0.3+json",
                         "verificationMaterial":{"tlogEntries":[]}}""")))
                .isInstanceOf(IllegalArgumentException.class)
                .as("a certificate that lived ten minutes says nothing without the time a log recorded")
                .hasMessageContaining("records no transparency log entry");
    }

    @Test
    public void names_the_field_a_bundle_leaves_out() throws Exception {
        assertThatThrownBy(() -> new Sigstore().trustedRoot(anchors("{}").toUri()).verify(
                artifact("released"),
                bundle("""
                        {"mediaType":"application/vnd.dev.sigstore.bundle.v0.3+json",
                         "verificationMaterial":{"tlogEntries":[{"logIndex":"1","integratedTime":"2"}]}}""")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messageSignature.messageDigest.digest");
    }

    @Test
    public void refuses_an_artifact_that_does_not_hash_to_the_recorded_digest() throws Exception {
        assertThatThrownBy(() -> new Sigstore().trustedRoot(anchors("{}").toUri()).verify(
                artifact("swapped"),
                bundle("""
                        {"mediaType":"application/vnd.dev.sigstore.bundle.v0.3+json",
                         "verificationMaterial":{"tlogEntries":[{"logIndex":"1","integratedTime":"2"}]},
                         "messageSignature":{"messageDigest":{"algorithm":"SHA2_256","digest":"%s"}}}"""
                        .formatted(Base64.getEncoder().encodeToString(
                                MessageDigest.getInstance("SHA-256").digest("released".getBytes(StandardCharsets.UTF_8)))))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not hash to the digest recorded in");
    }

    private Path artifact(String content) throws IOException {
        return Files.writeString(root.resolve("artifact"), content);
    }

    private Path bundle(String content) throws IOException {
        return Files.writeString(root.resolve("bundle.sigstore.json"), content);
    }

    private Path anchors(String content) throws IOException {
        return Files.writeString(root.resolve("trusted-root.json"), content);
    }
}
