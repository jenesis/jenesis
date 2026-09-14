package build.jenesis;

import module java.base;

public class PgpRepository implements Repository {

    public static final URI OFFICIAL = URI.create("https://keyserver.ubuntu.com/");

    private final URI server;
    private final Path local;
    private final Repository.Retry retry;

    public PgpRepository(URI server) {
        this(server, null, new Repository.Retry());
    }

    private PgpRepository(URI server, Path local, Repository.Retry retry) {
        this.server = server;
        this.local = local;
        this.retry = retry;
    }

    public PgpRepository local(Path local) {
        return new PgpRepository(server, local, retry);
    }

    public PgpRepository retry(Repository.Retry retry) {
        return new PgpRepository(server, local, retry);
    }

    public static Repository of() {
        String property = System.getProperty("jenesis.signature.keys");
        String text = property == null ? System.getenv("JENESIS_SIGNATURE_KEYS") : property;
        Path local = Path.of(System.getProperty("jenesis.signature.cache", ".jenesis/keys"));
        List<URI> servers = new ArrayList<>();
        servers(text == null ? "@" : text, new HashSet<>(), servers);
        Repository repository = (_, coordinate) -> cached(local, coordinate);
        for (int index = servers.size() - 1; index >= 0; index--) {
            repository = repository.prepend(new PgpRepository(servers.get(index)).local(local));
        }
        return repository;
    }

    private static Optional<RepositoryItem> cached(Path local, String coordinate) {
        Path candidate = local.resolve(coordinate + ".gpg");
        return Files.isRegularFile(candidate) ? Optional.of(RepositoryItem.ofFile(candidate)) : Optional.empty();
    }

    private static void servers(String text, Set<String> visited, List<URI> target) {
        for (String entry : text.split(",")) {
            String candidate = entry.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.startsWith("@")) {
                String name = candidate.substring(1);
                if (name.isEmpty()) {
                    String environment = System.getenv("JENESIS_SIGNATURE_KEYS");
                    if (environment != null && visited.add("JENESIS_SIGNATURE_KEYS")) {
                        servers(environment, visited, target);
                        visited.remove("JENESIS_SIGNATURE_KEYS");
                    } else {
                        target.add(OFFICIAL);
                    }
                } else {
                    String value = System.getProperty(name, System.getenv(name));
                    if (value == null) {
                        throw new IllegalStateException("Unresolved key server reference: @" + name);
                    }
                    if (!visited.add(name)) {
                        throw new IllegalStateException("Circular key server reference: @" + name);
                    }
                    servers(value, visited, target);
                    visited.remove(name);
                }
            } else {
                target.add(URI.create(candidate.endsWith("/") ? candidate : candidate + "/"));
            }
        }
    }

    @Override
    public Optional<RepositoryItem> fetch(Executor executor, String coordinate) throws IOException {
        if (local != null) {
            Optional<RepositoryItem> candidate = cached(local, coordinate);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        byte[] key;
        try (InputStream stream = Repository.open(
                server.resolve("pks/lookup?op=get&options=mr&search=0x" + coordinate),
                null,
                retry)) {
            key = stream.readAllBytes();
        } catch (IOException _) {
            return Optional.empty();
        }
        if (local == null) {
            return Optional.of(() -> new ByteArrayInputStream(key));
        }
        Files.createDirectories(local);
        Path target = local.resolve(coordinate + ".gpg");
        Path temporary = Files.createTempFile(local, "key", ".gpg");
        Files.write(temporary, key);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return Optional.of(RepositoryItem.ofFile(target));
    }
}
