package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.LruCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LruCacheTest {

    @TempDir
    private Path files;

    @Test
    public void evicts_the_entries_not_read_since_the_last_eviction() {
        LruCache<String, String> cache = new LruCache<>(2);
        cache.put("first", "1");
        cache.put("second", "2");
        cache.put("third", "3");
        assertThat(cache.size()).as("an entry added or read since the last eviction is kept by the next").isEqualTo(3);
        assertThat(cache.get("first")).isEqualTo("1");
        cache.put("fourth", "4");
        assertThat(cache.get("second")).as("an entry not read since the last eviction gives way").isNull();
        assertThat(cache.get("third")).isNull();
        assertThat(cache.get("first")).isEqualTo("1");
        assertThat(cache.get("fourth")).isEqualTo("4");
        assertThat(cache.size()).isEqualTo(2);
    }

    @Test
    public void loads_a_missing_entry_once() throws IOException {
        LruCache<String, String> cache = new LruCache<>(2);
        List<String> loaded = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            assertThat(cache.get("key", key -> {
                loaded.add(key);
                return "value";
            })).isEqualTo("value");
        }
        assertThat(loaded).containsExactly("key");
    }

    @Test
    public void keeps_no_absent_value() throws IOException {
        LruCache<String, String> cache = new LruCache<>(2);
        assertThat(cache.get("key", _ -> null)).isNull();
        assertThat(cache.size()).isZero();
    }

    @Test
    public void refuses_a_capacity_below_one() {
        assertThatThrownBy(() -> new LruCache<>(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not 0");
    }

    @Test
    public void a_file_key_changes_once_the_file_is_rewritten() throws IOException {
        Path file = Files.writeString(files.resolve("file"), "foo");
        LruCache.FileKey key = LruCache.FileKey.of(file);
        assertThat(LruCache.FileKey.of(file)).isEqualTo(key);
        Files.writeString(file, "bar");
        Files.setLastModifiedTime(file, FileTime.from(key.modified().toInstant().plusSeconds(1)));
        assertThat(LruCache.FileKey.of(file)).isNotEqualTo(key);
    }

    @Test
    public void a_file_key_is_shared_by_the_links_of_a_file() throws IOException {
        Assumptions.assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));
        Path file = Files.writeString(files.resolve("file"), "foo");
        Path link = Files.createLink(files.resolve("link"), file);
        assertThat(LruCache.FileKey.of(link)).isEqualTo(LruCache.FileKey.of(file));
    }

    @Test
    public void a_file_key_identifies_a_file_on_a_file_system_without_inodes() throws IOException {
        Path archive = files.resolve("archive.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("file"));
            output.write("foo".getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        try (FileSystem zip = FileSystems.newFileSystem(archive)) {
            Path file = zip.getPath("file");
            LruCache.FileKey key = LruCache.FileKey.of(file);
            assertThat(key.identity())
                    .as("a zip file system names no file key, as Windows does not, so the path stands in")
                    .isEqualTo(file.toAbsolutePath());
            assertThat(key.size()).isEqualTo(3);
            assertThat(LruCache.FileKey.of(file)).isEqualTo(key);
        }
    }
}
