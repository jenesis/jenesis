package build.jenesis;

import module java.base;

@FunctionalInterface
public interface BuildExecutorCache {

    Optional<Map<Path, byte[]>> fetch(Executor executor,
                                      String identity,
                                      byte[] step,
                                      SequencedMap<String, Map<Path, byte[]>> inputs,
                                      boolean remote,
                                      Path target,
                                      String digest) throws IOException;

    default void store(Executor executor,
                       String identity,
                       byte[] step,
                       SequencedMap<String, Map<Path, byte[]>> inputs,
                       boolean remote,
                       Path output,
                       String digest,
                       Map<Path, byte[]> checksums) throws IOException {
    }

    default boolean stores() {
        return false;
    }

    default void touch(Executor executor,
                       String identity,
                       byte[] step,
                       SequencedMap<String, Map<Path, byte[]>> inputs,
                       boolean remote,
                       String digest) throws IOException {
    }

    static BuildExecutorCache nop() {
        return (_, _, _, _, _, _, _) -> Optional.empty();
    }
}
