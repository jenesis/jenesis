package build.jenesis;

import module java.base;

public final class LruCache<K, V> {

    private final int capacity;
    private final ConcurrentMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final AtomicBoolean sweeping = new AtomicBoolean();

    public LruCache(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("A cache holds at least one entry, not " + capacity);
        }
        this.capacity = capacity;
    }

    public V get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        entry.used().set(true);
        return entry.value();
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
        entries.put(key, new Entry<>(value, new AtomicBoolean(true)));
        if (entries.size() > capacity && sweeping.compareAndSet(false, true)) {
            try {
                entries.values().removeIf(entry -> !entry.used().getAndSet(false));
            } finally {
                sweeping.set(false);
            }
        }
    }

    public int size() {
        return entries.size();
    }

    @FunctionalInterface
    public interface Loader<K, V> {

        V load(K key) throws IOException;
    }

    private record Entry<V>(V value, AtomicBoolean used) {
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
