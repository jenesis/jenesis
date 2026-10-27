package build.jenesis;

import module java.base;

record FileKeyHashFunction(HashFunction delegate) implements HashFunction {

    private static final LruCache<List<Object>, CompletableFuture<byte[]>> HASHES = new LruCache<>(16384);

    @Override
    public byte[] hash(Path file) throws IOException {
        Object links;
        try {
            links = Files.getAttribute(file, "unix:nlink");
        } catch (UnsupportedOperationException | IllegalArgumentException _) {
            return delegate.hash(file);
        }
        if (!(links instanceof Integer count) || count < 2) {
            return delegate.hash(file);
        }
        List<Object> key = List.of(delegate, LruCache.FileKey.of(file));
        CompletableFuture<byte[]> created = new CompletableFuture<>(), hash = HASHES.putIfAbsent(key, created);
        if (hash == null) {
            try {
                created.complete(delegate.hash(file));
            } catch (IOException | RuntimeException e) {
                HASHES.remove(key);
                created.completeExceptionally(e);
                throw e;
            }
            hash = created;
        }
        try {
            return hash.join().clone();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException exception) {
                throw exception;
            }
            throw e;
        }
    }
}
