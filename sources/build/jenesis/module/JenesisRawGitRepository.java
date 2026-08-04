package build.jenesis.module;

import module java.base;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;
public class JenesisRawGitRepository implements JenesisRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private static final String GITHUB_DATA =
            "https://raw.githubusercontent.com/raphw/jenesis-modules/main/data/modules/";

    private final Scope scope;
    private final URI data;
    private final URI repository;
    private final String token;
    private final Predicate<String> predicate;
    private final Repository.Retry retry;
    private final Map<String, Optional<String>> tsvCache = new ConcurrentHashMap<>();

    public JenesisRawGitRepository(Scope scope, URI data, URI repository) {
        this(scope, data, repository, null);
    }

    public JenesisRawGitRepository(Scope scope, URI data, URI repository, String token) {
        this(scope, trailingSlash(data), trailingSlash(repository), token, _ -> true, new Repository.Retry());
    }

    private JenesisRawGitRepository(Scope scope,
                                    URI data,
                                    URI repository,
                                    String token,
                                    Predicate<String> predicate,
                                    Repository.Retry retry) {
        this.scope = scope;
        this.data = data;
        this.repository = repository;
        this.token = token;
        this.predicate = predicate;
        this.retry = retry;
    }

    public JenesisRawGitRepository groups(Predicate<String> predicate) {
        return new JenesisRawGitRepository(scope, data, repository, token, predicate, retry);
    }

    public JenesisRawGitRepository retry(Repository.Retry retry) {
        return new JenesisRawGitRepository(scope, data, repository, token, predicate, retry);
    }

    public static JenesisRepository of(Scope scope) {
        String token = System.getProperty("jenesis.maven.token", System.getenv("MAVEN_REPOSITORY_TOKEN"));
        String property = System.getProperty("jenesis.maven.uri");
        String environment = System.getenv("MAVEN_REPOSITORY_URI");
        Set<String> visited = new HashSet<>();
        String text;
        if (property != null) {
            text = property;
        } else if (environment != null) {
            text = environment;
            visited.add("MAVEN_REPOSITORY_URI");
        } else {
            text = "https://repo1.maven.org/maven2/";
        }
        JenesisRepository repository = chain(text, visited, scope, token, null, null);
        if (repository == null) {
            throw new IllegalStateException("No Maven repository is configured by: " + text);
        }
        return repository;
    }

    private static JenesisRepository chain(String text,
                                           Set<String> visited,
                                           Scope scope,
                                           String token,
                                           Predicate<String> inherited,
                                           JenesisRepository repository) {
        for (String entry : text.split(",")) {
            String candidate = entry.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            int separator = candidate.indexOf('|');
            String location = (separator < 0 ? candidate : candidate.substring(0, separator)).strip();
            if (location.isEmpty()) {
                throw new IllegalStateException("No URI in Maven repository entry: " + candidate);
            }
            List<String> groups = new ArrayList<>();
            if (separator >= 0) {
                for (String argument : candidate.substring(separator + 1).split("\\|")) {
                    String group = argument.strip();
                    if (!group.isEmpty()) {
                        groups.add(group);
                    }
                }
            }
            Predicate<String> own = groups.isEmpty() ? null : value -> {
                for (String group : groups) {
                    if (value.equals(group) || value.startsWith(group + ".")) {
                        return true;
                    }
                }
                return false;
            };
            Predicate<String> effective;
            if (inherited == null) {
                effective = own;
            } else if (own == null) {
                effective = inherited;
            } else {
                Predicate<String> combining = own;
                effective = value -> inherited.test(value) && combining.test(value);
            }
            // The credential is scoped to the primary repository only, so a private token is
            // never attached to a public mirror or fallback declared later in the same chain.
            String entryToken = repository == null ? token : null;
            JenesisRepository current;
            if (location.startsWith("@")) {
                String name = location.substring(1);
                String value;
                if (name.isEmpty()) {
                    String environment = System.getenv("MAVEN_REPOSITORY_URI");
                    if (environment != null && visited.add("MAVEN_REPOSITORY_URI")) {
                        name = "MAVEN_REPOSITORY_URI";
                        value = environment;
                    } else {
                        name = null;
                        value = "https://repo1.maven.org/maven2/";
                    }
                } else {
                    value = System.getProperty(name, System.getenv(name));
                    if (value == null) {
                        throw new IllegalStateException("Unresolved repository reference: @" + name);
                    }
                    if (!visited.add(name)) {
                        throw new IllegalStateException("Circular repository reference: @" + name);
                    }
                }
                current = chain(value, visited, scope, entryToken, effective, null);
                if (name != null) {
                    visited.remove(name);
                }
                if (current == null) {
                    throw new IllegalStateException("No Maven repository is configured by: " + value);
                }
            } else {
                JenesisRawGitRepository base = new JenesisRawGitRepository(scope,
                        URI.create(GITHUB_DATA),
                        URI.create(location),
                        entryToken);
                current = effective == null ? base : base.groups(effective);
            }
            repository = repository == null ? current : current.prepend(repository);
        }
        return repository;
    }

    private static URI trailingSlash(URI uri) {
        String text = uri.toString();
        return text.endsWith("/") ? uri : URI.create(text + "/");
    }

    @Override
    public Optional<RepositoryItem> fetch(Executor executor,
                                          String module,
                                          String classifier,
                                          String version,
                                          String type) throws IOException {
        SAFE_SEGMENT.accept("module name", module);
        if (classifier != null) {
            SAFE_SEGMENT.accept("classifier", classifier);
        }
        if (version != null) {
            SAFE_SEGMENT.accept("version", version);
        }
        Coordinate resolved = resolve(module, classifier, version);
        if (resolved == null || !predicate.test(resolved.groupId())) {
            return Optional.empty();
        }
        SAFE_SEGMENT.accept("resolved groupId", resolved.groupId());
        SAFE_SEGMENT.accept("resolved artifactId", resolved.artifactId());
        SAFE_SEGMENT.accept("resolved version", resolved.version());
        String path = resolved.groupId().replace('.', '/')
                + "/" + resolved.artifactId()
                + "/" + resolved.version()
                + "/" + resolved.artifactId() + "-" + resolved.version()
                + (classifier == null ? "" : "-" + classifier) + "." + type;
        URI base = repository.normalize();
        URI location = base.resolve(path).normalize();
        URI contained = base.relativize(location);
        if (contained.isAbsolute() || contained.getPath().startsWith("..")) {
            throw new IllegalArgumentException("Resolved location " + location + " escapes repository root " + repository);
        }
        return open(location, token, retry).map(stream -> {
            AtomicReference<InputStream> first = new AtomicReference<>(stream);
            return (RepositoryItem) () -> {
                InputStream reopened = first.getAndSet(null);
                return reopened != null ? reopened : Repository.open(location, token, retry);
            };
        });
    }

    private Coordinate resolve(String moduleName, String classifier, String version) throws IOException {
        String tsvName = (scope == Scope.MODULE ? "modules" : "artifacts")
                + (classifier == null ? "" : "-" + classifier) + ".tsv";
        URI tsvUri = data.resolve(moduleName.replace('.', '/') + "/" + tsvName);
        Optional<String> tsv = tsvCache.get(tsvUri.toString());
        if (tsv == null) {
            Optional<InputStream> stream = open(tsvUri, null, retry);
            if (stream.isEmpty()) {
                tsv = Optional.empty();
            } else {
                try (InputStream open = stream.get()) {
                    tsv = Optional.of(new String(open.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            tsvCache.put(tsvUri.toString(), tsv);
        }
        return tsv.isEmpty() ? null : pickRow(tsv.get(), version);
    }

    private Coordinate pickRow(String tsv, String version) {
        Coordinate newest = null;
        for (String line : tsv.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split("\t");
            if (columns.length < 4) {
                continue;
            }
            Coordinate row = scope == Scope.MODULE
                    ? new Coordinate(columns[1], columns[2], columns[3])
                    : new Coordinate(columns[2], columns[3], columns[0]);
            if (newest == null) {
                newest = row;
            }
            if (version == null || columns[0].equals(version)) {
                return row;
            }
        }
        if (version != null && newest != null) {
            return new Coordinate(newest.groupId(), newest.artifactId(), version);
        }
        return null;
    }

    private static Optional<InputStream> open(URI uri, String token, Repository.Retry retry) throws IOException {
        try {
            return Optional.of(Repository.open(uri, token, retry));
        } catch (FileNotFoundException _) {
            return Optional.empty();
        }
    }

    private record Coordinate(String groupId, String artifactId, String version) {
    }
}
