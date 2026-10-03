package build.jenesis;

import module java.base;

final class ClassOrigins {

    private static final ConcurrentMap<String, byte[]> DIGESTS = new ConcurrentHashMap<>();

    private ClassOrigins() {
    }

    static Path of(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null || !"file".equals(source.getLocation().getProtocol())) {
            return null;
        }
        try {
            return Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException _) {
            return null;
        }
    }

    static byte[] digest(Path origin, String algorithm) throws IOException {
        String key = algorithm + "|" + origin + (Files.isRegularFile(origin)
                ? "|" + Files.size(origin) + "|" + Files.getLastModifiedTime(origin).toMillis()
                : "");
        byte[] cached = DIGESTS.get(key);
        if (cached != null) {
            return cached;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (Files.isDirectory(origin)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(origin)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                digest.update(origin.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
        } else if (Files.isRegularFile(origin)) {
            try (InputStream in = Files.newInputStream(origin)) {
                byte[] buffer = new byte[1 << 16];
                for (int read; (read = in.read(buffer)) != -1; ) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        byte[] computed = digest.digest();
        DIGESTS.put(key, computed);
        return computed;
    }
}
