package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Resolver;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ResolverTest {

    @TempDir
    private Path folder;

    @Test
    public void validate_accepts_a_matching_checksum() throws Exception {
        Path file = Files.writeString(folder.resolve("artifact.jar"), "payload");
        String hex = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest("payload".getBytes(StandardCharsets.UTF_8)));
        assertThatCode(() -> Resolver.validate(file, "SHA-256/" + hex, "group/artifact"))
                .doesNotThrowAnyException();
    }

    @Test
    public void validate_rejects_a_mismatched_checksum() throws IOException {
        Path file = Files.writeString(folder.resolve("artifact.jar"), "payload");
        assertThatThrownBy(() -> Resolver.validate(file,
                "SHA-256/0000000000000000000000000000000000000000000000000000000000000000",
                "group/artifact"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Mismatched digest for group/artifact");
    }

    @Test
    public void validate_rejects_a_checksum_without_an_algorithm_separator() throws IOException {
        Path file = Files.writeString(folder.resolve("artifact.jar"), "payload");
        assertThatThrownBy(() -> Resolver.validate(file, "deadbeef", "group/artifact"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Malformed checksum")
                .hasMessageContaining("group/artifact");
    }
}
