package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.HashDigestFunction;

import static org.assertj.core.api.Assertions.assertThat;

public class HashDigestFunctionTest {

    @TempDir
    private Path files;

    @Test
    public void can_compute_hash() throws IOException, NoSuchAlgorithmException {
        Path file = Files.writeString(files.resolve("file"), "bar");
        byte[] hash = new HashDigestFunction("MD5").hash(file);
        assertThat(hash).isEqualTo(MessageDigest.getInstance("MD5").digest("bar".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void a_cached_hash_follows_a_rewritten_file() throws IOException, NoSuchAlgorithmException {
        Path file = Files.writeString(files.resolve("file"), "foo");
        HashDigestFunction hash = new HashDigestFunction("MD5");
        assertThat(hash.hashCached(file)).isEqualTo(hash.hash(file));
        FileTime modified = Files.getLastModifiedTime(file);
        Files.writeString(file, "bar");
        Files.setLastModifiedTime(file, FileTime.from(modified.toInstant().plusSeconds(1)));
        assertThat(hash.hashCached(file))
                .isEqualTo(MessageDigest.getInstance("MD5").digest("bar".getBytes(StandardCharsets.UTF_8)));
    }
}
