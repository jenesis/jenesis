package build.jenesis.step;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;
import java.util.jar.Attributes;

public record Classifiers(SequencedMap<String, SequencedMap<String, Path>> folders,
                          SequencedMap<String, SequencedSet<String>> dormant,
                          OffsetDateTime timestamp) implements BuildExecutorModule {

    public static final String FILE = "classifiers.properties", CLASSIFIERS = "classifiers/", DORMANT = "dormant.properties";
    public static final Set<String> RESERVED = Set.of("tests", "sources", "javadoc");
    private static final String FOLDERS = "folders/", CLASSIFY = "classify";
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]*");

    public Classifiers {
        folders = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(folders));
        dormant = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(dormant));
    }

    public static Classifiers configured(SequencedSet<Path> configuration, Path location, Path root) throws IOException {
        Path file = BuildStep.locate(configuration, FILE);
        SequencedMap<String, SequencedMap<String, Path>> folders = new LinkedHashMap<>();
        if (file != null) {
            Path base = location == null ? file.getParent() : location;
            Path project = (root == null ? base : root).toAbsolutePath().normalize();
            SequencedProperties.ofFiles(file).forEachProperty((classifier, value) -> {
                String line = classifier + "=" + value;
                validate(classifier, line, file);
                SequencedMap<String, Path> declared = new LinkedHashMap<>();
                for (String entry : value.split(",", -1)) {
                    String folder = entry.strip();
                    if (folder.isEmpty()) {
                        if (value.isBlank()) {
                            break;
                        }
                        throw new IllegalArgumentException("The line " + line + " of " + file + " names an empty"
                                + " folder - write <classifier>=<folder>[,<folder>...], or <classifier>= to publish"
                                + " the module's jar as it is under that classifier");
                    }
                    Path resolved = base.resolve(folder).toAbsolutePath().normalize(),
                            relative = Path.of(folder).normalize();
                    boolean inside = resolved.startsWith(project);
                    if (inside && Files.exists(resolved)) {
                        try {
                            inside = resolved.toRealPath().startsWith(project.toRealPath());
                        } catch (IOException e) {
                            throw new UncheckedIOException("Cannot resolve " + resolved, e);
                        }
                    }
                    if (!inside) {
                        throw new IllegalArgumentException("The line " + line + " of " + file + " names " + resolved
                                + ", which lies outside the project " + project + " - name a folder the project holds");
                    } else if (Files.exists(resolved) && !Files.isDirectory(resolved)) {
                        throw new IllegalArgumentException("The line " + line + " of " + file + " names " + resolved
                                + ", which is no folder - name a folder whose files the classifier adds to the"
                                + " module's jar");
                    } else if (!Files.exists(resolved) && (relative.isAbsolute() || relative.startsWith(".."))) {
                        throw new IllegalArgumentException("The line " + line + " of " + file + " names " + resolved
                                + ", which does not exist, and a plugin writes no folder above its own output - name"
                                + " a folder the project holds, or the folder a plugin of the module writes");
                    } else if (declared.containsKey(folder)) {
                        throw new IllegalArgumentException("The line " + line + " of " + file + " names " + folder
                                + " twice - name each folder once");
                    }
                    declared.put(folder, Files.isDirectory(resolved) ? resolved : null);
                }
                folders.put(classifier, declared);
            });
        }
        SequencedMap<String, SequencedSet<String>> dormant = new LinkedHashMap<>();
        for (Path folder : configuration) {
            if (configuration.contains(folder.getParent()) || !Files.isDirectory(folder)) {
                continue;
            }
            try (DirectoryStream<Path> profiles = Files.newDirectoryStream(folder, Files::isDirectory)) {
                for (Path profile : profiles) {
                    Path candidate = profile.resolve(FILE);
                    if (configuration.contains(profile) || !Files.isRegularFile(candidate)) {
                        continue;
                    }
                    for (String classifier : SequencedProperties.ofFiles(candidate).stringPropertyNames()) {
                        if (!folders.containsKey(classifier)) {
                            dormant.computeIfAbsent(classifier, _ -> new TreeSet<>()).add(profile.getFileName().toString());
                        }
                    }
                }
            }
        }
        return folders.isEmpty() && dormant.isEmpty() ? null : new Classifiers(folders, dormant, BuildStep.timestamp());
    }

    public static void validate(String classifier, String line, Path file) {
        if (!NAME.matcher(classifier).matches()) {
            throw new IllegalArgumentException("The line " + line + " of " + file + " declares the classifier "
                    + classifier + " - write a classifier with letters, digits, _, . and -, starting with a letter or"
                    + " digit, as Maven names one");
        } else if (RESERVED.contains(classifier)) {
            throw new IllegalArgumentException("The line " + line + " of " + file + " declares the classifier "
                    + classifier + ", which the build publishes itself for " + RESERVED.stream().sorted().toList()
                    + " - give the variant a classifier of its own");
        }
    }

    public Classifiers timestamp(OffsetDateTime timestamp) {
        return new Classifiers(folders, dormant, timestamp);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        SequencedSet<String> bound = new LinkedHashSet<>();
        SequencedMap<String, List<String>> declared = new LinkedHashMap<>();
        for (Map.Entry<String, SequencedMap<String, Path>> classifier : folders.entrySet()) {
            List<String> names = new ArrayList<>();
            for (Map.Entry<String, Path> folder : classifier.getValue().entrySet()) {
                if (folder.getValue() != null) {
                    String source = "folder-" + bound.size();
                    buildExecutor.addSource(source,
                            new Bind(Map.of(Path.of(""), Path.of(FOLDERS + classifier.getKey() + "/" + names.size()))),
                            folder.getValue());
                    bound.add(source);
                }
                names.add(folder.getKey());
            }
            declared.put(classifier.getKey(), List.copyOf(names));
        }
        SequencedMap<String, String> profiles = new LinkedHashMap<>();
        dormant.forEach((classifier, names) -> profiles.put(classifier, String.join(",", names)));
        buildExecutor.addStep(CLASSIFY,
                new Classify(declared, profiles, timestamp),
                Stream.concat(bound.stream(), inherited.sequencedKeySet().stream()));
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(CLASSIFY) ? Optional.of("") : Optional.empty();
    }

    private record Classify(SequencedMap<String, List<String>> classifiers,
                            SequencedMap<String, String> dormant,
                            OffsetDateTime timestamp) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedSet<Path> jars = new TreeSet<>();
            SequencedMap<String, SequencedMap<Integer, Path>> bound = new LinkedHashMap<>();
            List<Path> written = new ArrayList<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path module = argument.folder().resolve(MODULE);
                if (Files.isRegularFile(module) && SequencedProperties.ofFiles(module).getProperty("test") != null) {
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                }
                if (!Files.isDirectory(argument.folder().resolve(FOLDERS))) {
                    written.add(argument.folder());
                }
                Path artifacts = argument.folder().resolve(ARTIFACTS);
                if (Files.isDirectory(artifacts)) {
                    try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts, "*.jar")) {
                        files.forEach(jars::add);
                    }
                }
                for (String classifier : classifiers.keySet()) {
                    Path folder = argument.folder().resolve(FOLDERS + classifier);
                    if (Files.isDirectory(folder)) {
                        try (DirectoryStream<Path> indices = Files.newDirectoryStream(folder)) {
                            for (Path index : indices) {
                                bound.computeIfAbsent(classifier, _ -> new TreeMap<>())
                                        .put(Integer.parseInt(index.getFileName().toString()), index);
                            }
                        }
                    }
                }
            }
            if (!dormant.isEmpty()) {
                SequencedProperties properties = new SequencedProperties();
                dormant.forEach(properties::setProperty);
                properties.store(context.next().resolve(DORMANT));
            }
            if (classifiers.isEmpty()) {
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }
            if (jars.size() != 1) {
                throw new IllegalStateException("A classifier publishes a variant of the module's jar, but the module"
                        + " builds " + (jars.isEmpty() ? "none" : jars.size() + " jars: " + jars)
                        + " - declare classifiers only for a module that builds one jar");
            }
            Path jar = jars.getFirst(), target = Files.createDirectory(context.next().resolve(CLASSIFIERS));
            for (Map.Entry<String, List<String>> classifier : classifiers.entrySet()) {
                Path variant = target.resolve(classifier.getKey() + ".jar");
                if (classifier.getValue().isEmpty()) {
                    BuildStep.linkOrCopy(variant, jar);
                } else {
                    SequencedMap<Path, String> folders = new LinkedHashMap<>();
                    SequencedMap<Integer, Path> projected = bound.getOrDefault(classifier.getKey(), Collections.emptyNavigableMap());
                    for (int index = 0; index < classifier.getValue().size(); index++) {
                        String name = classifier.getValue().get(index);
                        int found = folders.size();
                        if (projected.containsKey(index)) {
                            folders.put(projected.get(index), name);
                        }
                        for (Path folder : written) {
                            Path candidate = folder.resolve(name).normalize();
                            if (candidate.startsWith(folder) && !candidate.equals(folder) && Files.isDirectory(candidate)) {
                                folders.put(candidate, name + " of " + folder);
                            }
                        }
                        if (folders.size() == found) {
                            throw new IllegalStateException("The classifier " + classifier.getKey() + " names the folder "
                                    + name + ", which neither the project holds nor a step of the module wrote - create"
                                    + " it in the project, or let the plugin that builds the variant's files write them"
                                    + " below " + name + " in its output");
                        }
                    }
                    write(classifier.getKey(), folders, jar, variant);
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private void write(String classifier, SequencedMap<Path, String> folders, Path jar, Path variant)
                throws IOException {
            SequencedMap<String, Path> added = new TreeMap<>();
            Map<String, String> origins = new HashMap<>();
            Manifest overlay = null;
            for (Map.Entry<Path, String> origin : folders.entrySet()) {
                Path folder = origin.getKey();
                String name = origin.getValue();
                List<Path> files;
                try (Stream<Path> walk = Files.walk(folder)) {
                    files = walk.filter(Files::isRegularFile).sorted().toList();
                }
                for (Path file : files) {
                    String entry = folder.relativize(file).toString().replace(File.separatorChar, '/');
                    if (entry.equals(JarFile.MANIFEST_NAME)) {
                        Manifest manifest;
                        try (InputStream in = Files.newInputStream(file)) {
                            manifest = new Manifest(in);
                        }
                        if (overlay == null) {
                            overlay = manifest;
                        } else {
                            merge(overlay, manifest);
                        }
                        continue;
                    }
                    String previous = origins.putIfAbsent(entry, name);
                    if (previous != null) {
                        throw new IllegalArgumentException("The classifier " + classifier + " adds " + entry
                                + " from both " + previous + " and " + name + " - keep the file in one of them");
                    }
                    added.put(entry, file);
                }
            }
            try (JarFile main = new JarFile(jar.toFile(), false);
                 JarOutputStream out = new JarOutputStream(Files.newOutputStream(variant))) {
                Manifest manifest = main.getManifest() == null ? new Manifest() : new Manifest(main.getManifest());
                manifest.getMainAttributes().putIfAbsent(Attributes.Name.MANIFEST_VERSION, "1.0");
                if (overlay != null) {
                    merge(manifest, overlay);
                }
                Set<String> held = main.stream().map(JarEntry::getName).collect(Collectors.toSet());
                for (String entry : added.keySet()) {
                    if (held.contains(entry)) {
                        throw new IllegalArgumentException("The classifier " + classifier + " adds " + entry
                                + " from " + origins.get(entry) + ", which the module's jar holds already - a"
                                + " classifier adds to the jar and replaces nothing, so remove the file from the"
                                + " folder or from the module");
                    }
                }
                put(out, "META-INF/", null, null);
                put(out, JarFile.MANIFEST_NAME, null, output -> manifest.write(output));
                Set<String> written = new HashSet<>(Set.of("META-INF/", JarFile.MANIFEST_NAME));
                for (JarEntry entry : main.stream().toList()) {
                    if (written.add(entry.getName())) {
                        put(out, entry.getName(), entry, output -> {
                            try (InputStream in = main.getInputStream(entry)) {
                                in.transferTo(output);
                            }
                        });
                    }
                }
                for (Map.Entry<String, Path> entry : added.entrySet()) {
                    String name = entry.getKey();
                    for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
                        if (written.add(name.substring(0, slash + 1))) {
                            put(out, name.substring(0, slash + 1), null, null);
                        }
                    }
                    written.add(name);
                    put(out, name, null, output -> Files.copy(entry.getValue(), output));
                }
            }
        }

        private void put(JarOutputStream out, String name, JarEntry original, Content content) throws IOException {
            JarEntry entry = new JarEntry(name);
            if (timestamp != null) {
                entry.setTimeLocal(timestamp.toLocalDateTime());
            } else if (original != null) {
                entry.setTime(original.getTime());
            }
            out.putNextEntry(entry);
            if (content != null) {
                content.write(out);
            }
            out.closeEntry();
        }

        private static void merge(Manifest target, Manifest source) {
            target.getMainAttributes().putAll(source.getMainAttributes());
            source.getEntries().forEach((name, attributes) -> target.getEntries()
                    .computeIfAbsent(name, _ -> new Attributes())
                    .putAll(attributes));
        }

        @FunctionalInterface
        private interface Content {

            void write(OutputStream out) throws IOException;
        }
    }
}
