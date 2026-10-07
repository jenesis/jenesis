package build.jenesis.maven;

import module java.base;
import build.jenesis.DnsLocation;
import build.jenesis.DnsLookup;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;

public class MavenDnsRepository implements MavenRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final DnsLookup dns;
    private final Path local;
    private final Repository.Connection connection;
    private final Consumer<String> printing;
    private final Palette palette;
    private final Map<URI, MavenRepository> repositories = new ConcurrentHashMap<>();

    public MavenDnsRepository() {
        this(new DnsLookup(), null, new Repository.Connection(), null, Palette.NONE);
    }

    public static MavenDnsRepository ofEnvironment(Environment environment) {
        return new MavenDnsRepository(DnsLookup.ofEnvironment(environment),
                                      MavenDefaultRepository.localRepository(environment),
                                      Repository.Connection.ofEnvironment(environment),
                                      environment.flag("print.fetch") ? environment.out() : null,
                                      Palette.ofEnvironment(environment));
    }

    public MavenDnsRepository(DnsLookup dns,
                              Path local,
                              Repository.Connection connection,
                              Consumer<String> printing,
                              Palette palette) {
        this.dns = dns;
        this.local = local;
        this.connection = connection;
        this.printing = printing;
        this.palette = palette;
    }

    public MavenDnsRepository dns(DnsLookup dns) {
        return new MavenDnsRepository(dns, local, connection, printing, palette);
    }

    public MavenDnsRepository local(Path local) {
        return new MavenDnsRepository(dns, local, connection, printing, palette);
    }

    public MavenDnsRepository connection(Repository.Connection connection) {
        return new MavenDnsRepository(dns, local, connection, printing, palette);
    }

    public MavenDnsRepository printing(Consumer<String> printing, Palette palette) {
        return new MavenDnsRepository(dns, local, connection, printing, palette);
    }

    @Override
    public Optional<RepositoryItem> fetch(Executor executor,
                                          String groupId,
                                          String artifactId,
                                          String version,
                                          String type,
                                          String classifier,
                                          String checksum) throws IOException {
        DnsLocation location = dns.lookup(groupId, "maven").orElse(null);
        if (location == null || !location.admits(version)) {
            return Optional.empty();
        }
        if (!location.template()) {
            return repository(location.root(connection))
                    .fetch(executor, groupId, artifactId, version, type, classifier, checksum);
        }
        String extension = (type == null ? "jar" : type) + (checksum == null ? "" : "." + checksum);
        SAFE_SEGMENT.accept("artifact id", artifactId);
        SAFE_SEGMENT.accept("version", version);
        SAFE_SEGMENT.accept("type", extension);
        if (classifier != null) {
            SAFE_SEGMENT.accept("classifier", classifier);
        }
        Map<String, String> values = new HashMap<>();
        values.put("groupId", groupId);
        values.put("groupPath", groupId.replace('.', '/'));
        values.put("artifactId", artifactId);
        values.put("version", version);
        values.put("-classifier", classifier == null ? "" : "-" + classifier);
        values.put("type", extension);
        return location.fetch(values, checksum == null, connection, printing, palette);
    }

    @Override
    public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String checksum) throws IOException {
        DnsLocation location = dns.lookup(groupId, "maven").orElse(null);
        if (location == null || location.template()) {
            return Optional.empty();
        }
        Optional<RepositoryItem> metadata = repository(location.root(connection))
                .fetchMetadata(executor, groupId, artifactId, checksum);
        if (metadata.isEmpty() || checksum != null || location.since() == null && location.suffixes() == null) {
            return metadata;
        }
        MavenMetadata admitted = MavenMetadata.of(metadata.get()).filter(location::admits);
        return admitted.versions().isEmpty() ? Optional.empty() : Optional.of(admitted.toItem());
    }

    private MavenRepository repository(URI root) {
        return repositories.computeIfAbsent(root, _ -> new MavenDefaultRepository(
                root,
                local,
                MavenDefaultRepository.validations(root),
                null).connection(connection).printing(printing, palette));
    }
}
