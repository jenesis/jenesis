package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;
import build.jenesis.module.JenesisRepository;

public class MavenModuleRepository implements JenesisRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final MavenRepository repository;
    private final String group;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final Map<MavenDependencyName, Optional<Metadata>> metadata = new ConcurrentHashMap<>();

    public MavenModuleRepository() {
        this(MavenDefaultRepository.of());
    }

    public MavenModuleRepository(MavenRepository repository) {
        this(repository, null, MavenDefaultVersionNegotiator.toDocumentBuilderFactory());
    }

    private MavenModuleRepository(MavenRepository repository,
                                  String group,
                                  DocumentBuilderFactory documentBuilderFactory) {
        this.repository = repository;
        this.group = group;
        this.documentBuilderFactory = documentBuilderFactory;
    }

    public MavenModuleRepository repository(MavenRepository repository) {
        return new MavenModuleRepository(repository, group, documentBuilderFactory);
    }

    public MavenModuleRepository group(String group) {
        if (group != null) {
            SAFE_SEGMENT.accept("group id", group);
        }
        return new MavenModuleRepository(repository, group, documentBuilderFactory);
    }

    public static String groupId(String module) {
        int first = module.indexOf('.');
        if (first < 0) {
            return module;
        }
        int second = module.indexOf('.', first + 1);
        return second < 0 ? module : module.substring(0, second);
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
        String groupId = group == null ? groupId(module) : group;
        String suffix = type == null ? "jar" : type;
        int dot = suffix.indexOf('.');
        String resolved;
        if (version == null || version.equals("RELEASE") || version.equals("LATEST")) {
            Metadata published = metadata(executor, groupId, module).orElse(null);
            if (published == null) {
                return Optional.empty();
            }
            resolved = published.resolve(version, groupId, module);
        } else {
            resolved = version;
        }
        return repository.fetch(executor,
                groupId,
                module,
                resolved,
                dot < 0 ? suffix : suffix.substring(0, dot),
                classifier,
                dot < 0 ? null : suffix.substring(dot + 1));
    }

    private Optional<Metadata> metadata(Executor executor, String groupId, String artifactId) throws IOException {
        MavenDependencyName name = new MavenDependencyName(groupId, artifactId);
        Optional<Metadata> cached = metadata.get(name);
        if (cached == null) {
            RepositoryItem item = repository.fetchMetadata(executor, groupId, artifactId, null).orElse(null);
            cached = item == null ? Optional.empty() : Optional.of(toMetadata(item, groupId, artifactId));
            metadata.put(name, cached);
        }
        return cached;
    }

    private Metadata toMetadata(RepositoryItem item, String groupId, String artifactId) throws IOException {
        Document document;
        try (InputStream inputStream = item.toInputStream()) {
            document = documentBuilderFactory.newDocumentBuilder().parse(inputStream);
        } catch (SAXException | ParserConfigurationException e) {
            throw new IllegalStateException("Failed to parse the Maven metadata of "
                    + groupId + ":" + artifactId, e);
        }
        Node versioning = MavenPomResolver.toChildren(document.getDocumentElement())
                .filter(node -> Objects.equals(node.getLocalName(), "versioning"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No versioning element in the Maven metadata of "
                        + groupId + ":" + artifactId));
        return new Metadata(element(versioning, "release"),
                element(versioning, "latest"),
                MavenPomResolver.toChildren(versioning)
                        .filter(node -> Objects.equals(node.getLocalName(), "versions"))
                        .flatMap(MavenPomResolver::toChildren)
                        .filter(node -> Objects.equals(node.getLocalName(), "version"))
                        .map(node -> node.getTextContent().trim())
                        .filter(value -> !value.isEmpty())
                        .toList());
    }

    private static String element(Node versioning, String name) {
        return MavenPomResolver.toChildren(versioning)
                .filter(node -> Objects.equals(node.getLocalName(), name))
                .findFirst()
                .map(node -> node.getTextContent().trim())
                .filter(value -> !value.isEmpty())
                .orElse(null);
    }

    private record Metadata(String release, String latest, List<String> versions) {

        private String resolve(String version, String groupId, String artifactId) {
            if (Objects.equals(version, "LATEST")) {
                return latest == null ? newest(version, groupId, artifactId, _ -> true) : latest;
            }
            return release == null
                    ? newest(version, groupId, artifactId, MavenDefaultVersionNegotiator::isStable)
                    : release;
        }

        private String newest(String version, String groupId, String artifactId, Predicate<String> predicate) {
            return versions.stream()
                    .filter(predicate)
                    .max(MavenDefaultVersionNegotiator::compareVersions)
                    .orElseThrow(() -> new IllegalStateException("No version of "
                            + groupId + ":" + artifactId
                            + " resolves " + (version == null ? "RELEASE" : version)
                            + " in its Maven metadata: " + versions
                            + " (pin a version of the requiring module or publish a release)"));
        }
    }
}
