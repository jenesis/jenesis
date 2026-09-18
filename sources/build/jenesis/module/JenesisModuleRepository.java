package build.jenesis.module;

import module java.base;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;
import build.jenesis.SequencedProperties;

public class JenesisModuleRepository implements JenesisRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final URI root;
    private final String token;
    private final Repository.Retry retry;
    private final URI maven;
    private final Boolean prerelease;
    private final Boolean speculative;

    public static JenesisRepository of(Scope scope) {
        String token = System.getProperty("jenesis.module.token", System.getenv("JENESIS_REPOSITORY_TOKEN"));
        if (token != null && token.isBlank()) {
            token = null;
        }
        String property = System.getProperty("jenesis.module.uri");
        String environment = System.getenv("JENESIS_REPOSITORY_URI");
        Set<String> visited = new HashSet<>();
        String text;
        if (property != null) {
            text = property;
        } else if (environment != null) {
            text = environment;
            visited.add("JENESIS_REPOSITORY_URI");
        } else {
            text = "https://repo.jenesis.build/";
        }
        JenesisRepository repository = chain(text, visited, scope, token, null);
        if (repository == null) {
            throw new IllegalStateException("No Jenesis module repository is configured by: " + text);
        }
        return repository.prepend(ofLocal());
    }

    private static JenesisRepository chain(String text,
                                           Set<String> visited,
                                           Scope scope,
                                           String token,
                                           JenesisRepository repository) {
        for (String entry : text.split(",")) {
            String candidate = entry.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            int separator = candidate.indexOf('|');
            String location = (separator < 0 ? candidate : candidate.substring(0, separator)).strip();
            if (location.isEmpty()) {
                throw new IllegalStateException("No URI in Jenesis module repository entry: " + candidate);
            }
            String entryToken = repository == null ? token : null;
            JenesisRepository current;
            if (location.startsWith("@")) {
                String name = location.substring(1);
                String value;
                if (name.isEmpty()) {
                    String environment = System.getenv("JENESIS_REPOSITORY_URI");
                    if (environment != null && visited.add("JENESIS_REPOSITORY_URI")) {
                        name = "JENESIS_REPOSITORY_URI";
                        value = environment;
                    } else {
                        name = null;
                        value = "https://repo.jenesis.build/";
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
                current = chain(value, visited, scope, entryToken, null);
                if (name != null) {
                    visited.remove(name);
                }
                if (current == null) {
                    throw new IllegalStateException("No Jenesis module repository is configured by: " + value);
                }
            } else {
                current = new JenesisModuleRepository(
                        URI.create((location.endsWith("/") ? location : location + "/")
                                + (scope == Scope.MODULE ? "module/" : "artifact/")),
                        entryToken);
            }
            List<String> modules = new ArrayList<>();
            if (separator >= 0) {
                for (String argument : candidate.substring(separator + 1).split("\\|")) {
                    String module = argument.strip();
                    if (!module.isEmpty()) {
                        modules.add(module);
                    }
                }
            }
            if (!modules.isEmpty()) {
                current = current.filter(value -> {
                    for (String module : modules) {
                        if (value.equals(module) || value.startsWith(module + ".")) {
                            return true;
                        }
                    }
                    return false;
                });
            }
            repository = repository == null ? current : current.prepend(repository);
        }
        return repository;
    }

    public JenesisModuleRepository(URI root) {
        this(root, null);
    }

    public JenesisModuleRepository(URI root, String token) {
        this(root,
                token,
                new Repository.Retry(),
                toMavenRepository(System.getProperty("jenesis.maven.uri", System.getenv("MAVEN_REPOSITORY_URI"))),
                SequencedProperties.systemFlagOrNull("jenesis.module.prerelease"),
                SequencedProperties.systemFlagOrNull("jenesis.module.speculative"));
    }

    private JenesisModuleRepository(URI root,
                                    String token,
                                    Repository.Retry retry,
                                    URI maven,
                                    Boolean prerelease,
                                    Boolean speculative) {
        String text = root.toString();
        this.root = text.endsWith("/") ? root : URI.create(text + "/");
        this.token = token;
        this.retry = retry;
        this.maven = maven;
        this.prerelease = prerelease;
        this.speculative = speculative;
    }

    public JenesisModuleRepository retry(Repository.Retry retry) {
        return new JenesisModuleRepository(root, token, retry, maven, prerelease, speculative);
    }

    public JenesisModuleRepository maven(URI maven) {
        return new JenesisModuleRepository(root, token, retry, maven, prerelease, speculative);
    }

    public JenesisModuleRepository prerelease(Boolean prerelease) {
        return new JenesisModuleRepository(root, token, retry, maven, prerelease, speculative);
    }

    public JenesisModuleRepository speculative(Boolean speculative) {
        return new JenesisModuleRepository(root, token, retry, maven, prerelease, speculative);
    }

    private static URI toMavenRepository(String declaration) {
        if (declaration == null) {
            return null;
        }
        for (String entry : declaration.split(",")) {
            String candidate = entry.strip();
            if (candidate.isEmpty() || candidate.indexOf('|') >= 0 || candidate.startsWith("@")) {
                continue;
            }
            URI uri = URI.create(candidate.endsWith("/") ? candidate : candidate + "/");
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
                continue;
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                continue;
            }
            return uri;
        }
        return null;
    }

    private Map<String, String> toHeaders(URI uri) {
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
            return Map.of();
        }
        SequencedMap<String, String> headers = new LinkedHashMap<>();
        if (maven != null) {
            headers.put("Jenesis-Repository", maven.toString());
        }
        if (prerelease != null) {
            headers.put("Jenesis-Prerelease", prerelease.toString());
        }
        if (speculative != null) {
            headers.put("Jenesis-BestEffort", speculative.toString());
        }
        return headers;
    }

    public static JenesisModuleRepository ofLocal() {
        String override = System.getProperty("jenesis.module.local", System.getenv("JENESIS_REPOSITORY_LOCAL"));
        Path path = override == null
                ? Path.of(System.getProperty("user.home")).resolve(".jenesis")
                : Path.of(override);
        return new JenesisModuleRepository(path.toUri());
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
        String fileName = (classifier == null ? module : module + "-" + classifier) + "." + type;
        String relative = version == null
                ? module + "/" + fileName
                : module + "/" + version + "/" + fileName;
        URI base = root.normalize();
        URI uri = base.resolve(relative).normalize();
        URI contained = base.relativize(uri);
        if (contained.isAbsolute() || contained.getPath().startsWith("..")) {
            throw new IllegalArgumentException("Resolved location " + uri + " escapes repository root " + root);
        }
        if ("file".equals(uri.getScheme())) {
            Path file = Path.of(uri);
            return Files.isRegularFile(file)
                    ? Optional.of(RepositoryItem.ofFile(file, true))
                    : Optional.empty();
        }
        Map<String, String> headers = toHeaders(uri);
        InputStream stream;
        try {
            stream = Repository.open(uri, token, retry, headers);
        } catch (FileNotFoundException _) {
            return Optional.empty();
        }
        AtomicReference<InputStream> first = new AtomicReference<>(stream);
        return Optional.of(() -> {
            InputStream reopened = first.getAndSet(null);
            return reopened != null ? reopened : Repository.open(uri, token, retry, headers);
        });
    }

}
