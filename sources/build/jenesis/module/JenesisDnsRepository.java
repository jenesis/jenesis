package build.jenesis.module;

import module java.base;
import build.jenesis.DnsLocation;
import build.jenesis.DnsLookup;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDependencyKey;
import build.jenesis.maven.MavenModuleRepository;
import build.jenesis.maven.MavenRepository;

public class JenesisDnsRepository implements JenesisRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final Scope scope;
    private final DnsLookup dns;
    private final MavenRepository maven;
    private final Repository.Connection connection;
    private final Consumer<String> printing;
    private final Palette palette;
    private final Map<URI, JenesisRepository> repositories = new ConcurrentHashMap<>();
    private final Map<Map.Entry<String, MavenDependencyKey>, JenesisRepository> coordinates = new ConcurrentHashMap<>();

    public JenesisDnsRepository() {
        this(Scope.MODULE,
             new DnsLookup(),
             MavenDefaultRepository.of(),
             new Repository.Connection(),
             null,
             Palette.NONE);
    }

    public static JenesisDnsRepository ofEnvironment(Environment environment, Scope scope) {
        return new JenesisDnsRepository(scope,
                                        DnsLookup.ofEnvironment(environment),
                                        MavenDefaultRepository.ofEnvironment(environment),
                                        Repository.Connection.ofEnvironment(environment),
                                        environment.flag("print.fetch") ? environment.out() : null,
                                        Palette.ofEnvironment(environment));
    }

    public JenesisDnsRepository(Scope scope,
                                DnsLookup dns,
                                MavenRepository maven,
                                Repository.Connection connection,
                                Consumer<String> printing,
                                Palette palette) {
        this.scope = scope;
        this.dns = dns;
        this.maven = maven;
        this.connection = connection;
        this.printing = printing;
        this.palette = palette;
    }

    public JenesisDnsRepository scope(Scope scope) {
        return new JenesisDnsRepository(scope, dns, maven, connection, printing, palette);
    }

    public JenesisDnsRepository dns(DnsLookup dns) {
        return new JenesisDnsRepository(scope, dns, maven, connection, printing, palette);
    }

    public JenesisDnsRepository maven(MavenRepository maven) {
        return new JenesisDnsRepository(scope, dns, maven, connection, printing, palette);
    }

    public JenesisDnsRepository connection(Repository.Connection connection) {
        return new JenesisDnsRepository(scope, dns, maven, connection, printing, palette);
    }

    public JenesisDnsRepository printing(Consumer<String> printing, Palette palette) {
        return new JenesisDnsRepository(scope, dns, maven, connection, printing, palette);
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
        SAFE_SEGMENT.accept("type", type);
        DnsLocation location = dns.lookup(module, "javamodule", "coordinate").orElse(null);
        if (location == null || !location.admits(version)) {
            return Optional.empty();
        }
        if (location.key().equals("coordinate")) {
            if (!location.template() && !location.name().equals(DnsLookup.name(module))) {
                return Optional.empty();
            }
            String text = location.expand(Map.of("module", module, "-suffix", location.suffix(module))).orElseThrow();
            String[] elements = text.split(":", -1);
            if (elements.length < 2 || elements.length > 4 || Arrays.stream(elements).anyMatch(String::isEmpty)) {
                throw new IllegalArgumentException("The TXT record of " + location.name() + " maps " + module
                        + " to " + text + ", where it expects <groupId>:<artifactId>[:<extension>[:<classifier>]]");
            }
            MavenDependencyKey coordinate = new MavenDependencyKey(elements[0],
                    elements[1],
                    elements.length > 2 ? elements[2] : "jar",
                    elements.length > 3 ? elements[3] : null);
            return coordinates.computeIfAbsent(Map.entry(module, coordinate), _ -> new MavenModuleRepository(maven)
                    .mapping(Map.of(module, coordinate))).fetch(executor, module, classifier, version, type);
        }
        if (!location.template()) {
            URI root = location.root(connection).resolve(scope == Scope.MODULE ? "module/" : "artifact/");
            return repositories.computeIfAbsent(root, _ -> new JenesisModuleRepository(root)
                    .connection(connection)
                    .printing(printing, palette)).fetch(executor, module, classifier, version, type);
        }
        Map<String, String> values = new HashMap<>();
        values.put("module", module);
        values.put("-suffix", location.suffix(module));
        values.put("version", version);
        values.put("-classifier", classifier == null ? "" : "-" + classifier);
        values.put("type", type);
        return location.fetch(values, type.indexOf('.') < 0, connection, printing, palette);
    }
}
