package build.jenesis;

import module java.base;

public final class BuildExecutorLayeredCache implements BuildExecutorCache {

    private final BuildExecutorCache front;
    private final BuildExecutorCache back;

    public BuildExecutorLayeredCache(BuildExecutorCache front, BuildExecutorCache back) {
        this.front = front;
        this.back = back;
    }

    @Override
    public Optional<Map<Path, byte[]>> fetch(Executor executor,
                                             String identity,
                                             byte[] step,
                                             SequencedMap<String, Map<Path, byte[]>> inputs,
                                             boolean remote,
                                             Path target,
                                             String digest) throws IOException {
        Optional<Map<Path, byte[]>> local = front.fetch(executor, identity, step, inputs, remote, target, digest);
        if (local.isPresent()) {
            try {
                back.touch(executor, identity, step, inputs, remote, digest);
            } catch (IOException | RuntimeException _) {
            }
            return local;
        }
        Optional<Map<Path, byte[]>> shared = back.fetch(executor, identity, step, inputs, remote, target, digest);
        if (shared.isPresent()) {
            try {
                front.store(executor, identity, step, inputs, remote, target, digest, shared.get());
            } catch (IOException | RuntimeException _) {
            }
        }
        return shared;
    }

    @Override
    public void store(Executor executor,
                      String identity,
                      byte[] step,
                      SequencedMap<String, Map<Path, byte[]>> inputs,
                      boolean remote,
                      Path output,
                      String digest,
                      Map<Path, byte[]> checksums) throws IOException {
        try {
            front.store(executor, identity, step, inputs, remote, output, digest, checksums);
        } catch (IOException | RuntimeException _) {
        }
        back.store(executor, identity, step, inputs, remote, output, digest, checksums);
    }

    @Override
    public boolean stores() {
        return front.stores() || back.stores();
    }

    @Override
    public void touch(Executor executor,
                      String identity,
                      byte[] step,
                      SequencedMap<String, Map<Path, byte[]>> inputs,
                      boolean remote,
                      String digest) {
        try {
            front.touch(executor, identity, step, inputs, remote, digest);
        } catch (IOException | RuntimeException _) {
        }
        try {
            back.touch(executor, identity, step, inputs, remote, digest);
        } catch (IOException | RuntimeException _) {
        }
    }
}
