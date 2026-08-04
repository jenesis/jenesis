package build.jenesis.maven;

import module java.base;
import module java.xml;
import java.util.jar.Attributes;
import build.jenesis.DependencyScope;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;

public class MavenAliasResolver implements Resolver {

    public static final String GROUP = "jenesis.alias";

    private static final String VERSION = "0";

    private final String mavenPrefix;
    private final Resolver delegate;

    public MavenAliasResolver(String mavenPrefix, Resolver delegate) {
        this.mavenPrefix = mavenPrefix;
        this.delegate = delegate;
    }

    @Override
    public SequencedSet<String> managedPrefixes() {
        return delegate.managedPrefixes();
    }

    @Override
    public Resolver.Bom bom(Executor executor,
                            String prefix,
                            Map<String, Repository> repositories,
                            String coordinate,
                            String version,
                            String checksum,
                            boolean latest) throws IOException {
        return delegate.bom(executor, prefix, repositories, coordinate, version, checksum, latest);
    }

    @Override
    public Resolver.Resolution dependencies(Executor executor,
                                            String prefix,
                                            Map<String, Repository> repositories,
                                            SequencedMap<String, SequencedSet<String>> coordinates,
                                            SequencedMap<String, String> versions,
                                            DependencyScope scope) throws IOException {
        return delegate.dependencies(executor, prefix, repositories, coordinates, versions, scope);
    }

    @Override
    public Resolver.Resolution dependencies(Executor executor,
                                            String prefix,
                                            Map<String, Repository> repositories,
                                            SequencedMap<String, SequencedSet<String>> coordinates,
                                            SequencedMap<String, String> versions,
                                            SequencedMap<String, String> aliases,
                                            DependencyScope scope) throws IOException {
        if (aliases.isEmpty()) {
            return delegate.dependencies(executor, prefix, repositories, coordinates, versions, scope);
        }
        String base = Resolver.base(prefix);
        SequencedMap<String, byte[]> poms = new LinkedHashMap<>();
        SequencedMap<String, Target> targets = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String alias = entry.getKey(), declaration = entry.getValue();
            int space = declaration.indexOf(' ');
            String token = space < 0 ? declaration : declaration.substring(0, space);
            String inline = space < 0 ? null : declaration.substring(space + 1).trim();
            if (versions.containsKey(alias)) {
                throw new IllegalArgumentException("Module " + alias + " is an alias for " + token
                        + " - pin the target instead: @jenesis.pin " + token + " <version>");
            }
            MavenDependencyKey key;
            try {
                key = MavenDependencyKey.parseKey(token);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Malformed alias target for " + alias + ": " + token, e);
            }
            String pinned = versions.get(token);
            String version;
            if (pinned != null) {
                version = version(pinned, alias, token);
            } else if (inline != null) {
                version = version(inline, alias, token);
            } else {
                version = MavenDefaultVersionNegotiator.maven().get().resolve(executor,
                        MavenRepository.of(repositories.getOrDefault(mavenPrefix, Repository.empty())),
                        key.groupId(),
                        key.artifactId(),
                        key.type() == null ? "jar" : key.type(),
                        key.classifier(),
                        "RELEASE");
            }
            poms.put(alias, pom(alias, key, version));
            targets.put(alias, new Target(key, version));
        }
        Map<String, Repository> wrapped = new LinkedHashMap<>(repositories);
        Repository discovery = (_, coordinate) -> {
            if (coordinate.endsWith(":pom")) {
                byte[] bytes = poms.get(coordinate.substring(0, coordinate.length() - ":pom".length()));
                if (bytes != null) {
                    return Optional.of(() -> new ByteArrayInputStream(bytes));
                }
            }
            return Optional.empty();
        };
        wrapped.merge(base, discovery, (existing, overlay) -> existing.prepend(overlay));
        ConcurrentMap<String, Path> jars = new ConcurrentHashMap<>();
        Repository artifacts = (_, coordinate) -> {
            if (!coordinate.startsWith(GROUP + "/")) {
                return Optional.empty();
            }
            String tail = coordinate.substring(GROUP.length() + 1);
            int slash = tail.indexOf('/');
            String alias = slash < 0 ? tail : tail.substring(0, slash);
            if (slash < 0 || !tail.substring(slash + 1).equals(VERSION) || !poms.containsKey(alias)) {
                return Optional.empty();
            }
            Path jar;
            try {
                jar = jars.computeIfAbsent(alias, name -> {
                    try {
                        return emptyJar(name);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
            return Optional.of(RepositoryItem.ofFile(jar, true));
        };
        wrapped.merge(mavenPrefix, artifacts, (existing, overlay) -> MavenRepository.of(existing).prepend(overlay));
        Resolver.Resolution resolution = delegate.dependencies(executor, prefix, wrapped, coordinates, versions, scope);
        SequencedMap<String, Resolver.Resolved> swapped = new LinkedHashMap<>(resolution.artifacts());
        for (Map.Entry<String, Target> entry : targets.entrySet()) {
            String alias = entry.getKey();
            String synthetic = mavenPrefix + "/" + GROUP + "/" + alias + "/" + VERSION;
            String targetPrefix = entry.getValue().key().coordinate(mavenPrefix, null) + "/";
            Resolver.Resolved target = swapped.entrySet().stream()
                    .filter(candidate -> candidate.getKey().startsWith(targetPrefix)
                            && candidate.getKey().indexOf('/', targetPrefix.length()) < 0)
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (!swapped.containsKey(synthetic)
                    || target == null
                    || PathPlacement.moduleDescriptor(target.file()) != null) {
                continue;
            }
            Set<String> occupied = new HashSet<>();
            for (Map.Entry<String, Resolver.Resolved> other : swapped.entrySet()) {
                if (other.getKey().startsWith(mavenPrefix + "/" + GROUP + "/")
                        || other.getKey().startsWith(targetPrefix)) {
                    continue;
                }
                occupied.addAll(exportedPackages(other.getValue().file()));
            }
            Path copy = renamedJar(alias, target.file(), occupied);
            if (copy != null) {
                swapped.put(synthetic, new Resolver.Resolved(copy, "", true));
            }
        }
        SequencedMap<String, Resolver.Resolved> renamed = new LinkedHashMap<>();
        swapped.forEach((coordinate, resolved) ->
                renamed.put(rename(coordinate, base, poms.sequencedKeySet()), resolved));
        List<Resolver.Edge> edges = new ArrayList<>();
        for (Resolver.Edge edge : resolution.edges()) {
            edges.add(new Resolver.Edge(rename(edge.parent(), base, poms.sequencedKeySet()),
                    rename(edge.coordinate(), base, poms.sequencedKeySet()),
                    edge.version(),
                    edge.scope(),
                    edge.followed()));
        }
        SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
        resolution.vertices().forEach((coordinate, vertex) ->
                vertices.put(rename(coordinate, base, poms.sequencedKeySet()), vertex));
        return new Resolver.Resolution(renamed, edges, vertices);
    }

    private String rename(String coordinate, String base, SequencedCollection<String> aliases) {
        if (coordinate == null) {
            return null;
        }
        for (String alias : aliases) {
            String synthetic = mavenPrefix + "/" + GROUP + "/" + alias;
            if (coordinate.equals(synthetic) || coordinate.startsWith(synthetic + "/")) {
                return base + "/" + alias;
            }
        }
        return coordinate;
    }

    private static String version(String value, String alias, String target) {
        int space = value.indexOf(' ');
        String version = space < 0 ? value : value.substring(0, space);
        if (version.isEmpty() || version.startsWith(":")) {
            throw new IllegalArgumentException("Malformed version '" + value + "' for " + target
                    + ", aliased as " + alias);
        }
        return version;
    }

    private static final String POM_NAMESPACE = "http://maven.apache.org/POM/4.0.0";

    private static byte[] pom(String alias, MavenDependencyKey key, String version) {
        Document document;
        try {
            document = MavenDefaultVersionNegotiator.toDocumentBuilderFactory().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        Element project = document.createElementNS(POM_NAMESPACE, "project");
        document.appendChild(project);
        appendChild(document, project, "modelVersion", "4.0.0");
        appendChild(document, project, "groupId", GROUP);
        appendChild(document, project, "artifactId", alias);
        appendChild(document, project, "version", VERSION);
        Element dependencies = document.createElementNS(POM_NAMESPACE, "dependencies");
        project.appendChild(dependencies);
        Element dependency = document.createElementNS(POM_NAMESPACE, "dependency");
        dependencies.appendChild(dependency);
        appendChild(document, dependency, "groupId", key.groupId());
        appendChild(document, dependency, "artifactId", key.artifactId());
        appendChild(document, dependency, "version", version);
        if (key.type() != null && !key.type().equals("jar")) {
            appendChild(document, dependency, "type", key.type());
        }
        if (key.classifier() != null) {
            appendChild(document, dependency, "classifier", key.classifier());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(document), new StreamResult(out));
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static void appendChild(Document document, Element parent, String name, String value) {
        Element child = document.createElementNS(POM_NAMESPACE, name);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private static Set<String> exportedPackages(Path file) {
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(file);
        if (descriptor == null) {
            return Set.of();
        }
        if (!descriptor.isAutomatic()) {
            return descriptor.exports().stream()
                    .filter(export -> !export.isQualified())
                    .map(ModuleDescriptor.Exports::source)
                    .collect(Collectors.toSet());
        }
        try (JarFile jar = new JarFile(file.toFile(), false, ZipFile.OPEN_READ)) {
            Set<String> packages = new HashSet<>();
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                String name = versionless(entries.nextElement().getName());
                if (name == null || !name.endsWith(".class") || name.startsWith("META-INF/")) {
                    continue;
                }
                int slash = name.lastIndexOf('/');
                if (slash > 0) {
                    packages.add(name.substring(0, slash).replace('/', '.'));
                }
            }
            return packages;
        } catch (IOException _) {
            return Set.of();
        }
    }

    private static Path renamedJar(String alias, Path target, Set<String> occupied) throws IOException {
        JarFile source;
        try {
            source = new JarFile(target.toFile(), false, ZipFile.OPEN_READ);
        } catch (IOException _) {
            return null;
        }
        try (source) {
            Manifest manifest = source.getManifest() == null ? new Manifest() : new Manifest(source.getManifest());
            Attributes attributes = manifest.getMainAttributes();
            if (!attributes.containsKey(Attributes.Name.MANIFEST_VERSION)) {
                attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
            }
            attributes.putValue("Automatic-Module-Name", alias);
            Path file = Files.createTempFile("alias-" + alias, ".jar");
            file.toFile().deleteOnExit();
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file))) {
                JarEntry entry = new JarEntry(JarFile.MANIFEST_NAME);
                entry.setTime(0L);
                output.putNextEntry(entry);
                manifest.write(output);
                output.closeEntry();
                Set<String> written = new HashSet<>();
                Enumeration<JarEntry> entries = source.entries();
                while (entries.hasMoreElements()) {
                    JarEntry original = entries.nextElement();
                    String name = original.getName();
                    String bare = versionless(name);
                    if (bare == null
                            || name.equals(JarFile.MANIFEST_NAME)
                            || bare.equals("module-info.class")
                            || signature(name)
                            || !written.add(name)) {
                        continue;
                    }
                    if (bare.endsWith(".class")) {
                        int slash = bare.lastIndexOf('/');
                        if (slash > 0 && occupied.contains(bare.substring(0, slash).replace('/', '.'))) {
                            continue;
                        }
                    }
                    JarEntry copy = new JarEntry(name);
                    copy.setTime(original.getTime());
                    output.putNextEntry(copy);
                    if (!original.isDirectory()) {
                        try (InputStream input = source.getInputStream(original)) {
                            input.transferTo(output);
                        }
                    }
                    output.closeEntry();
                }
            }
            return file;
        }
    }

    private static String versionless(String name) {
        if (!name.startsWith("META-INF/versions/")) {
            return name;
        }
        int slash = name.indexOf('/', "META-INF/versions/".length());
        return slash < 0 ? null : name.substring(slash + 1);
    }

    private static boolean signature(String name) {
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        String tail = name.substring("META-INF/".length()).toUpperCase(Locale.ROOT);
        return !tail.contains("/") && (tail.endsWith(".SF")
                || tail.endsWith(".RSA")
                || tail.endsWith(".DSA")
                || tail.endsWith(".EC")
                || tail.startsWith("SIG-"));
    }

    private record Target(MavenDependencyKey key, String version) {
    }

    private static Path emptyJar(String alias) throws IOException {
        Path file = Files.createTempFile("alias-" + alias, ".jar");
        file.toFile().deleteOnExit();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Automatic-Module-Name", alias);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file))) {
            JarEntry entry = new JarEntry(JarFile.MANIFEST_NAME);
            entry.setTime(0L);
            output.putNextEntry(entry);
            manifest.write(output);
            output.closeEntry();
        }
        return file;
    }
}
