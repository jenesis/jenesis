package build.jenesis;

import module java.base;

public record HashDigestFunction(String algorithm) implements HashFunction, Serializable {

    private static final LruCache<List<Object>, byte[]> DIGESTS = new LruCache<>(16384);

    @Override
    public byte[] hash(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (FileChannel channel = FileChannel.open(file)) {
            ByteBuffer buffer = ByteBuffer.allocate(1 << 16);
            while (channel.read(buffer) != -1) {
                buffer.flip();
                digest.update(buffer);
                buffer.clear();
            }
        }
        return digest.digest();
    }

    public byte[] hashCached(Path file) throws IOException {
        return DIGESTS.get(List.of(algorithm, LruCache.FileKey.of(file)), _ -> hash(file)).clone();
    }

    public String encoded(byte[] hash) {
        return algorithm + "/" + HexFormat.of().formatHex(hash);
    }

    public String encodedHash(Path file) throws IOException {
        return encoded(hash(file));
    }
}
