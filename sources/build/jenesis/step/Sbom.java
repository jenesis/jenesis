package build.jenesis.step;

import module java.base;
import java.util.jar.Attributes;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.CycloneDx;
import build.jenesis.HashDigestFunction;
import build.jenesis.License;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDependencyKey;

public class Sbom implements BuildStep {

    private static final Pattern CONNECTION = Pattern.compile("scm([:|])([a-z0-9]+)\\1([a-zA-Z][a-zA-Z0-9+.-]*://.+)");

    private final CycloneDx.Format format;
    private final boolean swhid;
    private final String type;

    public Sbom() {
        this(CycloneDx.Format.JSON, false, "library");
    }

    private Sbom(CycloneDx.Format format, boolean swhid, String type) {
        this.format = format;
        this.swhid = swhid;
        this.type = type;
    }

    public Sbom format(CycloneDx.Format format) {
        return new Sbom(format, swhid, type);
    }

    public Sbom swhid(boolean swhid) {
        return new Sbom(format, swhid, type);
    }

    public Sbom type(String type) {
        return new Sbom(format, swhid, type);
    }

    public static Sbom configured(Path properties) throws IOException {
        SequencedProperties configuration = properties == null ? new SequencedProperties() : SequencedProperties.ofFiles(properties);
        String format = configuration.value("format", "json");
        boolean swhid = configuration.flag("swhid", false);
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> new Sbom().format(CycloneDx.Format.JSON).swhid(swhid);
            case "xml" -> new Sbom().format(CycloneDx.Format.XML).swhid(swhid);
            case "none" -> null;
            default -> throw new IllegalArgumentException("Unknown SBOM format: " + format);
        };
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(DEPENDENCIES),
                Path.of(METADATA),
                Path.of(Dependencies.GRAPH),
                Path.of(Dependencies.LICENSES),
                Path.of(Dependencies.RESOLVED),
                Path.of(RELEASE))
                || swhid && argument.hasChanged(Path.of(SOURCES), Path.of(RESOURCES)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        List<Path> folders = arguments.values().stream()
                .filter(argument -> !argument.removed())
                .map(BuildStepArgument::folder)
                .toList();
        SequencedProperties metadata = SequencedProperties.ofFolders(folders, METADATA);
        String groupId = metadata.getProperty("project");
        String artifactId = metadata.getProperty("artifact");
        String version = metadata.getProperty("version");
        HashDigestFunction hash = new HashDigestFunction("SHA-256");
        SequencedMap<String, CycloneDx.Component> components = new LinkedHashMap<>();
        List<Path> graphFiles = new ArrayList<>();
        SequencedSet<String> platforms = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path graphFile = argument.folder().resolve(Dependencies.GRAPH);
            if (Files.isRegularFile(graphFile)) {
                graphFiles.add(graphFile);
            }
            Path release = argument.folder().resolve(RELEASE);
            if (Files.isRegularFile(release)) {
                SequencedProperties runtime = SequencedProperties.ofFiles(release);
                String implementor = unquote(runtime.value("IMPLEMENTOR")), graalvm = unquote(runtime.value("GRAALVM_VERSION"));
                String name = graalvm == null ? "Java runtime" : "GraalVM",
                        runtimeVersion = graalvm == null ? unquote(runtime.value("JAVA_RUNTIME_VERSION")) : graalvm;
                String ref = (implementor == null ? "" : implementor + "/") + name
                        + (runtimeVersion == null ? "" : "/" + runtimeVersion);
                if (platforms.add(ref)) {
                    components.put(ref, new CycloneDx.Component("platform", ref, implementor, name, runtimeVersion, null, null,
                            List.of(), null, List.of(), List.of(), List.of()));
                }
            }
            Path index = argument.folder().resolve(DEPENDENCIES);
            if (!Files.exists(index)) {
                continue;
            }
            SequencedProperties dependencies = SequencedProperties.ofFiles(index);
            Path sidecar = argument.folder().resolve(Dependencies.LICENSES);
            SequencedProperties licenses = Files.exists(sidecar)
                    ? SequencedProperties.ofFiles(sidecar)
                    : new SequencedProperties();
            for (String key : dependencies.stringPropertyNames()) {
                int first = key.indexOf('/'), second = key.indexOf('/', first + 1), third = key.indexOf('/', second + 1);
                if (first < 0 || second < 0 || third < 0) {
                    continue;
                }
                String coordinate = key.substring(third + 1), licenseKey = key.substring(second + 1);
                if (components.containsKey(coordinate)) {
                    continue;
                }
                String value = dependencies.getProperty(key);
                int space = value.indexOf(' ');
                Path jar = argument.folder().resolve(space < 0 ? value : value.substring(0, space)).normalize();
                components.put(coordinate, component(coordinate,
                        Files.exists(jar) ? HexFormat.of().formatHex(hash.hash(jar)) : null,
                        readLicenses(licenses, licenseKey)));
            }
        }
        CycloneDx.Component project = null;
        String projectRef = null;
        if (artifactId != null) {
            projectRef = (groupId == null ? "" : groupId + "/") + artifactId + (version == null ? "" : "/" + version);
            String purl = groupId == null
                    ? null
                    : "pkg:maven/" + groupId + "/" + artifactId + (version == null ? "" : "@" + version);
            String tag = metadata.value("scm.tag"), revision = metadata.value("scm.revision");
            if ("HEAD".equals(tag)) {
                tag = null;
            }
            List<CycloneDx.Property> properties = new ArrayList<>();
            if (tag != null) {
                properties.add(new CycloneDx.Property("jenesis:scm:tag", tag));
            }
            if (revision != null) {
                properties.add(new CycloneDx.Property("jenesis:scm:revision", revision));
            }
            String tree = metadata.value("scm.tree");
            if (tree != null) {
                if (!tree.matches("[0-9a-f]{40}")) {
                    throw new IllegalArgumentException("The tree of a release must be the 40-character id of a Git"
                            + " tree, as `git rev-parse HEAD^{tree}` prints it: " + tree);
                }
                properties.add(new CycloneDx.Property("jenesis:scm:swhid", "swh:1:dir:" + tree));
            }
            if (swhid) {
                List<Path> roots = new ArrayList<>();
                for (Path folder : folders) {
                    for (Path root : List.of(folder.resolve(SOURCES), folder.resolve(RESOURCES))) {
                        if (Files.isDirectory(root)) {
                            roots.add(root);
                        }
                    }
                }
                String identifier = swhid(roots);
                if (identifier != null) {
                    properties.add(new CycloneDx.Property("jenesis:source:swhid", identifier));
                }
            }
            project = new CycloneDx.Component(type, projectRef, groupId, artifactId, version, purl, null,
                    ownLicenses(metadata), metadata.getProperty("description"), developers(metadata),
                    references(metadata, revision == null ? tag : revision), properties);
        }
        List<CycloneDx.Dependency> dependencies = relationships(projectRef, components.keySet(), platforms, graphFiles);
        String document = new CycloneDx().emit(format, project, new ArrayList<>(components.values()), dependencies);

        Path embedded = Files.createDirectories(context.next()
                .resolve(RESOURCES).resolve("META-INF").resolve("sbom"));
        String base = artifactId == null ? "bom" : artifactId;
        String fileName = base + "." + format.extension();
        Files.writeString(embedded.resolve(fileName), document);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Sbom-Format", "CycloneDX");
        manifest.getMainAttributes().putValue("Sbom-Location", "META-INF/sbom/" + fileName);
        try (OutputStream out = Files.newOutputStream(context.next().resolve(Versions.MANIFEST))) {
            manifest.write(out);
        }
        Path standalone = Files.createDirectories(context.next().resolve(REPORTS + "sbom"));
        Files.writeString(standalone.resolve(base + (version == null ? "" : "-" + version) + "." + format.extension()), document);
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static CycloneDx.Component component(String coordinate, String sha256, List<License> licenses) {
        try {
            MavenDependencyKey.Versioned parsed = MavenDependencyKey.tryParse(coordinate);
            if (parsed.version() != null) {
                MavenDependencyKey key = parsed.key();
                StringBuilder purl = new StringBuilder("pkg:maven/")
                        .append(key.groupId()).append("/").append(key.artifactId()).append("@").append(parsed.version());
                List<String> qualifiers = new ArrayList<>();
                if (key.type() != null && !key.type().equals("jar")) {
                    qualifiers.add("type=" + key.type());
                }
                if (key.classifier() != null) {
                    qualifiers.add("classifier=" + key.classifier());
                }
                if (!qualifiers.isEmpty()) {
                    purl.append("?").append(String.join("&", qualifiers));
                }
                return new CycloneDx.Component(coordinate, key.groupId(), key.artifactId(), parsed.version(),
                        purl.toString(), sha256, licenses);
            }
        } catch (RuntimeException _) {
        }
        int last = coordinate.lastIndexOf('/');
        return new CycloneDx.Component(coordinate,
                null,
                last < 0 ? coordinate : coordinate.substring(0, last),
                last < 0 ? "" : coordinate.substring(last + 1),
                null,
                sha256,
                licenses);
    }

    private static List<CycloneDx.Dependency> relationships(String projectRef,
                                                                   Set<String> componentRefs,
                                                                   SequencedSet<String> platforms,
                                                                   List<Path> graphFiles) throws IOException {
        if (graphFiles.isEmpty() && (platforms.isEmpty() || projectRef == null)) {
            return List.of();
        }
        SequencedMap<String, SequencedSet<String>> dependsOn = new LinkedHashMap<>();
        if (projectRef != null) {
            dependsOn.put(projectRef, new LinkedHashSet<>(platforms));
        }
        for (String ref : componentRefs) {
            dependsOn.put(ref, new LinkedHashSet<>());
        }
        for (Resolver.Resolution resolution : Dependencies.graph(graphFiles, List.of()).values()) {
            SequencedMap<String, Resolver.Vertex> vertices = resolution.vertices();
            Map<String, String> vertexKeyByCoordinate = new HashMap<>();
            for (Resolver.Edge edge : resolution.edges()) {
                vertexKeyByCoordinate.put(edge.coordinate(), stripVersion(edge.coordinate(), edge.version()));
            }
            for (Resolver.Edge edge : resolution.edges()) {
                if (!edge.followed()) {
                    continue;
                }
                String childRef = ref(stripVersion(edge.coordinate(), edge.version()), vertices);
                if (childRef == null || !componentRefs.contains(childRef)) {
                    continue;
                }
                String parentRef;
                if (edge.parent() == null) {
                    parentRef = projectRef;
                    if (parentRef == null) {
                        continue;
                    }
                } else {
                    String parentKey = vertexKeyByCoordinate.get(edge.parent());
                    parentRef = parentKey == null ? null : ref(parentKey, vertices);
                    if (parentRef == null || !dependsOn.containsKey(parentRef)) {
                        continue;
                    }
                }
                dependsOn.get(parentRef).add(childRef);
            }
        }
        List<CycloneDx.Dependency> result = new ArrayList<>();
        dependsOn.forEach((ref, on) -> result.add(new CycloneDx.Dependency(ref, new ArrayList<>(on))));
        return result;
    }

    private static String unquote(String value) {
        String unquoted = value != null && value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1).trim()
                : value;
        return unquoted == null || unquoted.isEmpty() ? null : unquoted;
    }

    private static String ref(String vertexKey, SequencedMap<String, Resolver.Vertex> vertices) {
        Resolver.Vertex vertex = vertices.get(vertexKey);
        if (vertex == null || vertex.resolvedVersion() == null) {
            return null;
        }
        int slash = vertexKey.indexOf('/');
        return (slash < 0 ? vertexKey : vertexKey.substring(slash + 1)) + "/" + vertex.resolvedVersion();
    }

    private static String stripVersion(String coordinate, String version) {
        return version != null && !version.isEmpty() && coordinate.endsWith("/" + version)
                ? coordinate.substring(0, coordinate.length() - version.length() - 1)
                : coordinate;
    }

    private static List<License> readLicenses(SequencedProperties licenses, String licenseKey) {
        SequencedMap<Integer, String[]> byIndex = new TreeMap<>();
        String prefix = licenseKey + "#";
        for (String key : licenses.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String rest = key.substring(prefix.length());
            int hash = rest.indexOf('#');
            if (hash < 0) {
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(rest.substring(0, hash));
            } catch (NumberFormatException _) {
                continue;
            }
            String[] entry = byIndex.computeIfAbsent(index, _ -> new String[4]);
            switch (rest.substring(hash + 1)) {
                case "id" -> entry[0] = licenses.getProperty(key);
                case "category" -> entry[1] = licenses.getProperty(key);
                case "name" -> entry[2] = licenses.getProperty(key);
                case "url" -> entry[3] = licenses.getProperty(key);
                default -> {
                }
            }
        }
        return byIndex.values().stream().map(entry -> new License(entry[0], entry[1], entry[2], entry[3])).toList();
    }

    private static List<License> ownLicenses(SequencedProperties metadata) {
        SequencedMap<String, String[]> byId = new LinkedHashMap<>();
        for (String key : metadata.stringPropertyNames()) {
            if (!key.startsWith("license.")) {
                continue;
            }
            String suffix = key.substring("license.".length());
            int dot = suffix.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String[] entry = byId.computeIfAbsent(suffix.substring(0, dot), _ -> new String[2]);
            if (suffix.substring(dot + 1).equals("name")) {
                entry[0] = metadata.getProperty(key);
            } else if (suffix.substring(dot + 1).equals("url")) {
                entry[1] = metadata.getProperty(key);
            }
        }
        return byId.values().stream().map(entry -> new License(null, null, entry[0], entry[1])).toList();
    }

    private static List<CycloneDx.Author> developers(SequencedProperties metadata) {
        SequencedMap<String, String[]> byId = new LinkedHashMap<>();
        for (String key : metadata.stringPropertyNames()) {
            if (!key.startsWith("developer.")) {
                continue;
            }
            String suffix = key.substring("developer.".length());
            int dot = suffix.lastIndexOf('.');
            if (dot <= 0) {
                continue;
            }
            String[] entry = byId.computeIfAbsent(suffix.substring(0, dot), _ -> new String[2]);
            if (suffix.substring(dot + 1).equals("name")) {
                entry[0] = metadata.getProperty(key);
            } else if (suffix.substring(dot + 1).equals("email")) {
                entry[1] = metadata.getProperty(key);
            }
        }
        return byId.values().stream()
                .filter(entry -> entry[0] != null || entry[1] != null)
                .map(entry -> new CycloneDx.Author(entry[0], entry[1]))
                .toList();
    }

    private static List<CycloneDx.ExternalReference> references(SequencedProperties metadata, String reference) {
        List<CycloneDx.ExternalReference> references = new ArrayList<>();
        String url = metadata.getProperty("url");
        if (url != null) {
            references.add(new CycloneDx.ExternalReference("website", url));
        }
        String scm = metadata.getProperty("scm.url");
        if (scm != null) {
            references.add(new CycloneDx.ExternalReference("vcs", scm));
        }
        String connection = metadata.value("scm.connection");
        if (reference != null && connection != null) {
            Matcher matcher = CONNECTION.matcher(connection);
            if (matcher.matches()) {
                references.add(new CycloneDx.ExternalReference("vcs",
                        matcher.group(2) + "+" + matcher.group(3) + "@" + reference));
            }
        }
        return references;
    }

    private static String swhid(List<Path> roots) throws IOException {
        Directory tree = new Directory();
        SequencedMap<String, Path> origins = new TreeMap<>();
        for (Path root : roots) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(Files::isRegularFile).toList();
            }
            for (Path file : files) {
                String name = root.relativize(file).toString().replace(File.separatorChar, '/');
                Path origin = origins.putIfAbsent(name, file);
                if (origin != null) {
                    if (Files.mismatch(origin, file) != -1) {
                        throw new IllegalStateException("Two source roots contain " + name + " with different content: "
                                + origin + " and " + file);
                    }
                    continue;
                }
                Directory directory = tree;
                String[] segments = name.split("/");
                for (int index = 0; index < segments.length - 1; index++) {
                    directory = directory.directories().computeIfAbsent(segments[index], _ -> new Directory());
                }
                directory.files().put(segments[segments.length - 1], file);
            }
        }
        return origins.isEmpty() ? null : "swh:1:dir:" + HexFormat.of().formatHex(tree.id());
    }

    private static byte[] blobId(Path file) throws IOException {
        MessageDigest digest = sha1();
        digest.update(("blob " + Files.size(file) + "\0").getBytes(StandardCharsets.US_ASCII));
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return digest.digest();
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required to compute a SWHID", e);
        }
    }

    private record Directory(SequencedMap<String, Directory> directories, SequencedMap<String, Path> files) {

        private Directory() {
            this(new TreeMap<>(), new TreeMap<>());
        }

        private byte[] id() throws IOException {
            List<String> names = new ArrayList<>(files.keySet());
            directories.keySet().forEach(name -> names.add(name + "/"));
            names.sort((left, right) -> Arrays.compareUnsigned(
                    left.getBytes(StandardCharsets.UTF_8),
                    right.getBytes(StandardCharsets.UTF_8)));
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            for (String name : names) {
                byte[] id;
                if (name.endsWith("/")) {
                    name = name.substring(0, name.length() - 1);
                    content.write("40000 ".getBytes(StandardCharsets.US_ASCII));
                    id = directories.get(name).id();
                } else {
                    content.write("100644 ".getBytes(StandardCharsets.US_ASCII));
                    id = blobId(files.get(name));
                }
                content.write(name.getBytes(StandardCharsets.UTF_8));
                content.write(0);
                content.write(id);
            }
            MessageDigest digest = sha1();
            digest.update(("tree " + content.size() + "\0").getBytes(StandardCharsets.US_ASCII));
            digest.update(content.toByteArray());
            return digest.digest();
        }
    }
}
