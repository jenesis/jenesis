package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public abstract class Java extends EnvironmentalProcessBuildStep {

    private static final String MODULE_PATH = "--module-path", CLASS_PATH = "--class-path";

    protected final PathPlacement pathPlacement;
    protected final boolean jarsOnly;
    protected final String group;

    protected Java(Function<List<String>, ? extends ProcessHandler> factory,
                   PathPlacement pathPlacement,
                   boolean jarsOnly,
                   String group) {
        this(factory, pathPlacement, jarsOnly, group, new Terms());
    }

    protected Java(Function<List<String>, ? extends ProcessHandler> factory,
                   PathPlacement pathPlacement,
                   boolean jarsOnly,
                   String group,
                   Terms terms) {
        super("java", factory, terms);
        this.pathPlacement = pathPlacement;
        this.jarsOnly = jarsOnly;
        this.group = group;
    }

    public static Java of(String... commands) {
        return of(List.of(commands));
    }

    public static Java of(PathPlacement pathPlacement, boolean jarsOnly, String... commands) {
        return of(pathPlacement, jarsOnly, List.of(commands));
    }

    public static Java of(List<String> commands) {
        return of(PathPlacement.CLASS_PATH, true, commands);
    }

    public static Java of(PathPlacement pathPlacement, boolean jarsOnly, List<String> commands) {
        return new Java(ProcessHandler.OfProcess.ofJavaHome("bin/java"), pathPlacement, jarsOnly, "main") {
            @Override
            protected CompletionStage<List<String>> commands(Executor executor,
                                                             BuildStepContext context,
                                                             SequencedMap<String, BuildStepArgument> arguments) {
                return CompletableFuture.completedStage(commands);
            }
        };
    }

    public static Java of(Function<List<String>, ProcessHandler.OfProcess> factory, String... commands) {
        return of(factory, List.of(commands));
    }

    public static Java of(Function<List<String>, ProcessHandler.OfProcess> factory,
                          PathPlacement pathPlacement,
                          boolean jarsOnly,
                          String... commands) {
        return of(factory, pathPlacement, jarsOnly, List.of(commands));
    }

    public static Java of(Function<List<String>, ProcessHandler.OfProcess> factory, List<String> commands) {
        return of(factory, PathPlacement.CLASS_PATH, true, commands);
    }

    public static Java of(Function<List<String>, ProcessHandler.OfProcess> factory,
                          PathPlacement pathPlacement,
                          boolean jarsOnly,
                          List<String> commands) {
        return new Java(factory, pathPlacement, jarsOnly, "main") {
            @Override
            protected CompletionStage<List<String>> commands(Executor executor,
                                                             BuildStepContext context,
                                                             SequencedMap<String, BuildStepArgument> arguments) {
                return CompletableFuture.completedStage(commands);
            }
        };
    }

    public Java group(String group) {
        Java self = this;
        return new Java(factory, pathPlacement, jarsOnly, group, terms) {
            @Override
            protected CompletionStage<List<String>> commands(Executor executor,
                                                             BuildStepContext context,
                                                             SequencedMap<String, BuildStepArgument> arguments)
                    throws IOException {
                return self.commands(executor, context, arguments);
            }
        };
    }

    public Java verbose(BiConsumer<Boolean, String> printing) {
        Java self = this;
        return new Java(factory, pathPlacement, jarsOnly, group, terms.printing(printing)) {
            @Override
            protected CompletionStage<List<String>> commands(Executor executor,
                                                             BuildStepContext context,
                                                             SequencedMap<String, BuildStepArgument> arguments)
                    throws IOException {
                return self.commands(executor, context, arguments);
            }
        };
    }

    protected abstract CompletionStage<List<String>> commands(Executor executor,
                                                              BuildStepContext context,
                                                              SequencedMap<String, BuildStepArgument> arguments)
            throws IOException;

    @Override
    public CompletionStage<List<String>> process(Executor executor,
                                                 BuildStepContext context,
                                                 SequencedMap<String, BuildStepArgument> arguments,
                                                 SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        List<String> classPath = new ArrayList<>(), modulePath = new ArrayList<>();
        SequencedMap<String, Layers.Membership> layers = new TreeMap<>();
        SequencedMap<String, Path> pool = new LinkedHashMap<>();
        SequencedMap<Path, Boolean> placed = new LinkedHashMap<>();
        SequencedSet<String> natives = new LinkedHashSet<>();
        Set<String> selected = new HashSet<>();
        ModuleGraph graph = new ModuleGraph();
        SequencedMap<Path, Set<String>> contents = new LinkedHashMap<>();
        if (!jarsOnly) {
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (String folder : List.of(Javac.CLASSES, Bind.RESOURCES)) {
                    Path candidate = argument.folder().resolve(folder);
                    if (Files.isDirectory(candidate)) {
                        try (Stream<Path> files = Files.walk(candidate)) {
                            contents.put(candidate, files.filter(Files::isRegularFile)
                                    .map(file -> candidate.relativize(file).toString().replace(File.separatorChar, '/'))
                                    .collect(Collectors.toSet()));
                        }
                    }
                }
            }
        }
        List<Path> candidates = List.copyOf(contents.sequencedKeySet());
        Set<Path> superseded = new HashSet<>();
        for (int index = 0; index < candidates.size(); index++) {
            Set<String> held = contents.get(candidates.get(index));
            for (int other = 0; other < candidates.size(); other++) {
                Set<String> covering = contents.get(candidates.get(other));
                if (other != index && covering.containsAll(held) && (other > index || covering.size() > held.size())) {
                    superseded.add(candidates.get(index));
                }
            }
        }
        Set<String> placedEntries = new HashSet<>();
        contents.forEach((folder, entries) -> {
            if (!superseded.contains(folder)) {
                placedEntries.addAll(entries);
            }
        });
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            BuildStepArgument argument = entry.getValue();
            if (argument.removed()) {
                continue;
            }
            for (String folder : List.of(Javac.CLASSES, Bind.RESOURCES)) {
                Path candidate = argument.folder().resolve(folder);
                if (contents.containsKey(candidate) && !superseded.contains(candidate)) {
                    graph.place(pathPlacement, candidate, modulePath, classPath);
                }
            }
            Path artifacts = argument.folder().resolve(ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        if (placedEntries.isEmpty() || !packages(file, placedEntries)) {
                            graph.place(pathPlacement, file, modulePath, classPath);
                        }
                    }
                }
            }
            for (Path file : Dependencies.select(argument.folder(), group, "runtime")) {
                if (!selected.add(file.getFileName().toString())) {
                    continue;
                }
                boolean module = graph.place(pathPlacement, file);
                (module ? modulePath : classPath).add(file.toString());
                placed.putIfAbsent(file, module);
            }
            Path moduleFile = argument.folder().resolve(MODULE);
            if (Files.isRegularFile(moduleFile)) {
                SequencedProperties module = SequencedProperties.ofFiles(moduleFile);
                if (module.flag("native")) {
                    graph.enableNativeAccess(pathPlacement.modular() ? module.value("module") : null);
                }
            }
            Path javacFile = argument.folder().resolve(PROCESS + "javac.properties");
            if (Files.isRegularFile(javacFile) && SequencedProperties.ofFiles(javacFile).containsKey("--enable-preview")) {
                graph.enablePreview();
            }
            Path nativesFile = argument.folder().resolve(NATIVES);
            if (Files.isRegularFile(nativesFile)) {
                natives.addAll(SequencedProperties.ofFiles(nativesFile).stringPropertyNames());
            }
            for (Path jar : Dependencies.all(argument.folder())) {
                pool.putIfAbsent(jar.getFileName().toString(), jar);
            }
            layers.putAll(Layers.membership(argument.folder()));
            SequencedMap<String, String> folders = properties.get(entry.getKey());
            if (folders != null) {
                for (Map.Entry<String, List<String>> paths : List.of(
                        Map.entry(MODULE_PATH, modulePath),
                        Map.entry(CLASS_PATH, classPath)
                )) {
                    String value = folders.remove(paths.getKey());
                    if (value != null) {
                        for (String part : value.split("\n")) {
                            if (!part.isEmpty()) {
                                Path resolved = argument.folder().resolve(part);
                                paths.getValue().add(resolved.toString());
                                if (paths.getKey().equals(MODULE_PATH)) {
                                    graph.module(resolved);
                                } else {
                                    graph.unnamed();
                                }
                            }
                        }
                    }
                }
            }
        }
        if (layers.values().stream().anyMatch(membership -> !membership.classpath().isEmpty())) {
            graph.unnamed();
        }
        for (String key : natives) {
            Path jar = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (jar == null && !argument.removed()) {
                    jar = Dependencies.runtime(argument.folder(), key);
                }
            }
            int second = key.indexOf('/', key.indexOf('/') + 1);
            if (jar == null && key.startsWith("module/", second + 1)) {
                for (Path candidate : Stream.concat(placed.sequencedKeySet().stream(), pool.values().stream()).toList()) {
                    ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(candidate);
                    if (jar == null && descriptor != null && descriptor.name().equals(key.substring(second + 8))) {
                        jar = candidate;
                    }
                }
            }
            String layer = null;
            for (Map.Entry<String, Layers.Membership> membership : layers.entrySet()) {
                if (jar != null && membership.getValue().all().contains(jar.getFileName().toString())) {
                    layer = membership.getKey();
                }
            }
            if (layer != null) {
                graph.enableNativeAccess(layer, jar, layers.get(layer).modulepath().contains(jar.getFileName().toString()));
            } else if (jar != null && placed.containsKey(jar)) {
                graph.enableNativeAccess(jar, placed.get(jar));
            } else {
                throw new IllegalStateException("@jenesis.native grants "
                        + key.substring(0, key.indexOf('/')) + key.substring(second)
                        + " native access, but this run holds it neither on its path nor in a layer");
            }
        }
        List<String> options = new ArrayList<>();
        for (Map.Entry<String, List<String>> path : List.of(
                Map.entry(MODULE_PATH, modulePath),
                Map.entry(CLASS_PATH, classPath)
        )) {
            if (!path.getValue().isEmpty()) {
                options.add(path.getKey());
                options.add(String.join(File.pathSeparator, path.getValue()));
            }
        }
        options.addAll(graph.arguments());
        layers.forEach((name, membership) -> {
            options.add("-Djlayer.modulepath." + name + "=" + path(membership.modulepath(), pool));
            if (!membership.classpath().isEmpty()) {
                options.add("-Djlayer.classpath." + name + "=" + path(membership.classpath(), pool));
            }
        });
        return commands(executor, context, arguments).thenComposeAsync(commands -> {
            if (commands == null) {
                return CompletableFuture.completedStage(null);
            }
            List<String> invocation = Stream.concat(options.stream(), commands.stream()).toList();
            if (invocation.isEmpty()) {
                return CompletableFuture.completedStage(List.of());
            }
            try {
                return CompletableFuture.completedStage(List.of("@"
                        + argumentFile(context.supplement().resolve("java.args"), invocation)));
            } catch (IOException e) {
                return CompletableFuture.failedStage(e);
            }
        }, executor);
    }

    private static boolean packages(Path file, Set<String> entries) throws IOException {
        if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".jar")) {
            return false;
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return zip.stream().anyMatch(entry -> !entry.isDirectory()
                    && !entry.getName().startsWith("META-INF/")
                    && !entry.getName().equals("module-info.class")
                    && entries.contains(entry.getName()));
        }
    }

    private static String path(SequencedSet<String> names, SequencedMap<String, Path> pool) {
        return names.stream()
                .map(pool::get)
                .filter(Objects::nonNull)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }
}
