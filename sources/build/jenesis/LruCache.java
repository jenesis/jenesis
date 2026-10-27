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
        sweep();
    }

    public V putIfAbsent(K key, V value) {
        Entry<V> entry = entries.putIfAbsent(key, new Entry<>(value, new AtomicBoolean(true)));
        if (entry != null) {
            entry.used().set(true);
            return entry.value();
        }
        sweep();
        return null;
    }

    public void remove(K key) {
        entries.remove(key);
    }

    private void sweep() {
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

    public record FileKey(Object identity, long size, FileTime modified) {

        public static FileKey of(Path file) throws IOException {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            return new FileKey(attributes.fileKey() == null ? file.toAbsolutePath().normalize() : attributes.fileKey(),
                    attributes.size(),
                    attributes.lastModifiedTime());
        }
    }
}
