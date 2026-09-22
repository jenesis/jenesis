package build.jenesis;

import module java.base;

public enum Pinning {

    STRICT,

    VERSIONS,

    IGNORE;

    private static final ConcurrentMap<Integer, Semaphore> PERMITS = new ConcurrentHashMap<>();

    public static Semaphore permits() {
        return permits(SequencedProperties.NONE);
    }

    public static Semaphore permits(Function<String, String> keys) {
        int concurrency = SequencedProperties.number(keys, "pin.concurrency",
                Runtime.getRuntime().availableProcessors());
        if (concurrency < 0) {
            throw new IllegalArgumentException("Pin concurrency must not be negative: " + concurrency);
        }
        return concurrency == 0 ? null : PERMITS.computeIfAbsent(concurrency, Semaphore::new);
    }

    public static Pinning ofKeys(Function<String, String> keys) {
        String property = SequencedProperties.getProperty(keys, "dependency.pin");
        if (property == null) {
            return null;
        }
        try {
            return Pinning.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.dependency.pin '" + property
                    + "', expected one of: strict, versions, ignore");
        }
    }
}
