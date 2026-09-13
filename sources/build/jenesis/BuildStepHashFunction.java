package build.jenesis;

import module java.base;

@FunctionalInterface
public interface BuildStepHashFunction {

    byte[] hash(BuildStep step) throws IOException;

    static BuildStepHashFunction ofSerializationDigest(String algorithm) {
        return ofSerializationDigest(algorithm, Engine.identity());
    }

    static BuildStepHashFunction ofSerializationDigest(String algorithm, byte[] engine) {
        return step -> {
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                try (ObjectOutputStream out = new ObjectOutputStream(bytes) {
                    {
                        enableReplaceObject(true);
                    }

                    @Override
                    protected Object replaceObject(Object value) {
                        return value instanceof Path path
                                ? path.toString().replace('\\', '/')
                                : value;
                    }
                }) {
                    out.writeObject(step);
                }
                try {
                    MessageDigest digest = MessageDigest.getInstance(algorithm);
                    digest.update(engine);
                    return digest.digest(bytes.toByteArray());
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }

    final class Engine {

        private static final String PROPERTY = "jenesis.executor.engine";

        private static byte[] identity;

        private Engine() {
            throw new UnsupportedOperationException();
        }

        public static synchronized byte[] identity() {
            if (identity == null) {
                String declared = System.getProperty(PROPERTY);
                identity = declared != null
                        ? declared.getBytes(StandardCharsets.UTF_8)
                        : discovered();
            }
            return identity;
        }

        private static byte[] discovered() {
            ModuleDescriptor descriptor = BuildStepHashFunction.class.getModule().getDescriptor();
            if (descriptor != null && descriptor.version().isPresent()) {
                return descriptor.version().get().toString().getBytes(StandardCharsets.UTF_8);
            }
            CodeSource source = BuildStepHashFunction.class.getProtectionDomain().getCodeSource();
            URL location = source == null ? null : source.getLocation();
            if (location == null) {
                return new byte[0];
            }
            try {
                Path path = Path.of(location.toURI());
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                if (Files.isDirectory(path)) {
                    SequencedSet<Path> files = new TreeSet<>();
                    try (Stream<Path> walk = Files.walk(path)) {
                        walk.filter(Files::isRegularFile)
                                .filter(file -> file.getFileName().toString().endsWith(".class"))
                                .forEach(files::add);
                    }
                    for (Path file : files) {
                        digest.update(path.relativize(file).toString()
                                .replace('\\', '/')
                                .getBytes(StandardCharsets.UTF_8));
                        digest.update(Files.readAllBytes(file));
                    }
                } else if (Files.isRegularFile(path)) {
                    digest.update(Files.readAllBytes(path));
                } else {
                    return new byte[0];
                }
                return digest.digest();
            } catch (IOException | URISyntaxException | NoSuchAlgorithmException | IllegalArgumentException _) {
                return new byte[0];
            }
        }
    }
}
