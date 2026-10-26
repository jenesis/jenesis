package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.HashDigestFunction;
import build.jenesis.HashFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class HashFunctionTest {

    @TempDir
    private Path folder;

    @Test
    public void hashes_a_file_reached_through_several_links_once() throws IOException {
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        Path file = Files.writeString(folder.resolve("foo"), "bar"), link = Files.createLink(folder.resolve("qux"), file);
        AtomicInteger hashed = new AtomicInteger();
        HashFunction hash = HashFunction.ofFileKeys(counted(hashed));
        assertThat(hash.hash(link)).isEqualTo(hash.hash(file)).isEqualTo(new HashDigestFunction("MD5").hash(file));
        assertThat(hashed)
                .as("links share their contents, so a build reads the file behind them once")
                .hasValue(1);
    }

    @Test
    public void hashes_a_file_without_a_second_link_every_time() throws IOException {
        Path file = Files.writeString(folder.resolve("foo"), "bar");
        AtomicInteger hashed = new AtomicInteger();
        HashFunction hash = HashFunction.ofFileKeys(counted(hashed));
        hash.hash(file);
        hash.hash(file);
        assertThat(hashed)
                .as("a file with one link can be replaced by another reusing its inode, so it is read again")
                .hasValue(2);
    }

    @Test
    public void hashes_a_linked_file_again_once_it_changed() throws IOException {
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        Path file = Files.writeString(folder.resolve("foo"), "bar");
        Files.createLink(folder.resolve("qux"), file);
        AtomicInteger hashed = new AtomicInteger();
        HashFunction hash = HashFunction.ofFileKeys(counted(hashed));
        hash.hash(file);
        Files.writeString(file, "barbaz");
        assertThat(hash.hash(file)).isEqualTo(new HashDigestFunction("MD5").hash(file));
        assertThat(hashed).hasValue(2);
    }

    @Test
    public void can_write_file_and_read_file() throws IOException {
        Path file = folder.resolve("foo");
        HashFunction.write(file, Map.of(Path.of("foo"), new byte[]{1, 2, 3}));
        Map<Path, byte[]> checksums = HashFunction.read(file);
        assertThat(checksums).containsOnlyKeys(Path.of("foo"));
        assertThat(checksums.get(Path.of("foo"))).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    public void can_extract_folder() throws IOException {
        Files.writeString(folder.resolve("foo"), "bar");
        Map<Path, byte[]> checksums = HashFunction.read(folder, _ -> new byte[]{1, 2, 3}, Runnable::run);
        assertThat(checksums).containsOnlyKeys(Path.of("foo"));
        assertThat(checksums.get(Path.of("foo"))).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    public void can_extract_nested_folder() throws IOException {
        Files.writeString(Files.createDirectory(folder.resolve("bar")).resolve("foo"), "bar");
        Map<Path, byte[]> checksums = HashFunction.read(folder, _ -> new byte[]{1, 2, 3}, Runnable::run);
        assertThat(checksums).containsOnlyKeys(Path.of("bar/foo"));
        assertThat(checksums.get(Path.of("bar/foo"))).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    public void can_extract_empty_folder() throws IOException {
        Map<Path, byte[]> checksums = HashFunction.read(folder, _ -> {
            throw new UnsupportedOperationException();
        }, Runnable::run);
        assertThat(checksums).isEmpty();
    }

    @Test
    public void names_the_target_of_a_symbolic_link_that_points_to_nothing() throws IOException {
        Files.createSymbolicLink(folder.resolve("foo"), Path.of("../missing"));
        assertThatThrownBy(() -> HashFunction.read(folder, _ -> new byte[]{1, 2, 3}, Runnable::run))
                .isInstanceOf(NoSuchFileException.class)
                .hasMessageContaining("foo")
                .hasMessageContaining(Path.of("..", "missing").toString())
                .hasMessageContaining("symbolic link");
    }

    private static HashFunction counted(AtomicInteger hashed) {
        HashFunction digest = new HashDigestFunction("MD5");
        return file -> {
            hashed.incrementAndGet();
            return digest.hash(file);
        };
    }
}
