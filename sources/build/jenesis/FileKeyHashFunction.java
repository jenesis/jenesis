package build.jenesis;

import module java.base;

record FileKeyHashFunction(HashFunction delegate, ConcurrentMap<List<Object>, CompletableFuture<byte[]>> hashes)
        implements HashFunction {

    FileKeyHashFunction(HashFunction delegate) {
        this(delegate, new ConcurrentHashMap<>());
    }

    @Override
    public byte[] hash(Path file) throws IOException {
        Map<String, Object> attributes;
        try {
            attributes = Files.readAttributes(file, "unix:ino,dev,nlink,size,lastModifiedTime,ctime");
        } catch (UnsupportedOperationException | IllegalArgumentException _) {
            return delegate.hash(file);
        }
        if (!(attributes.get("nlink") instanceof Integer links) || links < 2) {
            return delegate.hash(file);
        }
        List<Object> values = new ArrayList<>();
        for (String name : List.of("dev", "ino", "size", "lastModifiedTime", "ctime")) {
            Object value = attributes.get(name);
            if (value == null) {
                return delegate.hash(file);
            }
            values.add(value);
        }
        List<Object> key = List.copyOf(values);
        CompletableFuture<byte[]> created = new CompletableFuture<>(), hash = hashes.putIfAbsent(key, created);
        if (hash == null) {
            try {
                created.complete(delegate.hash(file));
            } catch (IOException | RuntimeException e) {
                hashes.remove(key, created);
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
