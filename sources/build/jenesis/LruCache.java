package build.jenesis;

import module java.base;

public final class LruCache<K, V> {

    private final LinkedHashMap<K, V> entries;

    public LruCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("A cache holds at least one entry, not " + capacity);
        }
        entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > capacity;
            }
        };
    }

    public V get(K key) {
        synchronized (entries) {
            return entries.get(key);
        }
    }

    public V get(K key, Loader<? super K, ? extends V> loader) throws IOException {
        V value = get(key);
        if (value == null) {
            value = loader.load(key);
            if (value != null) {
                put(key, value);
            }
        }
        return value;
    }

    public void put(K key, V value) {
        synchronized (entries) {
            entries.put(key, value);
        }
    }

    public int size() {
        synchronized (entries) {
            return entries.size();
        }
    }

    @FunctionalInterface
    public interface Loader<K, V> {

        V load(K key) throws IOException;
    }

    public record FileKey(Path path, Object identity, long size, FileTime modified, FileTime changed) {

        public static FileKey of(Path file) throws IOException {
            Path path = file.toAbsolutePath().normalize();
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
            return new FileKey(path,
                    attributes.fileKey(),
                    attributes.size(),
                    attributes.lastModifiedTime(),
                    path.getFileSystem().supportedFileAttributeViews().contains("unix")
                            && Files.getAttribute(path, "unix:ctime") instanceof FileTime changed
                            ? changed
                            : attributes.creationTime());
        }
    }
}
