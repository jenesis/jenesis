package build.jenesis;

import module java.base;

@FunctionalInterface
public interface BuildStepHashFunction {

    byte[] hash(BuildStep step) throws IOException;

    static BuildStepHashFunction ofSerializationDigest(String algorithm) {
        return step -> {
            SortedSet<Path> origins = new TreeSet<>();
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                try (ObjectOutputStream out = new ObjectOutputStream(bytes) {
                    {
                        enableReplaceObject(true);
                    }

                    @Override
                    protected void annotateClass(Class<?> type) {
                        Path origin = ClassOrigins.of(type);
                        if (origin != null) {
                            origins.add(origin);
                        }
                    }

                    @Override
                    protected void annotateProxyClass(Class<?> type) {
                        for (Class<?> implemented : type.getInterfaces()) {
                            annotateClass(implemented);
                        }
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
                MessageDigest digest;
                try {
                    digest = MessageDigest.getInstance(algorithm);
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
                digest.update(bytes.toByteArray());
                for (Path origin : origins) {
                    digest.update(ClassOrigins.digest(origin, algorithm));
                }
                return digest.digest();
            }
        };
    }
}
