package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;

public abstract class Java extends ProcessBuildStep {

    private static final String MODULE_PATH = "--module-path", CLASS_PATH = "--class-path";

    protected final PathPlacement pathPlacement;
    protected final boolean jarsOnly;
    protected final String group;

    protected Java(Function<List<String>, ? extends ProcessHandler> factory,
                   PathPlacement pathPlacement,
                   boolean jarsOnly,
                   String group) {
        this(factory, pathPlacement, jarsOnly, group, Terms.of("java"));
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
        ModuleGraph graph = new ModuleGraph();
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            BuildStepArgument argument = entry.getValue();
            if (argument.removed()) {
                continue;
            }
            if (!jarsOnly) {
                for (String folder : List.of(Javac.CLASSES, Bind.RESOURCES)) {
                    Path candidate = argument.folder().resolve(folder);
                    if (Files.isDirectory(candidate)) {
                        graph.place(pathPlacement, candidate, modulePath, classPath);
                    }
                }
            }
            Path artifacts = argument.folder().resolve(ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        graph.place(pathPlacement, file, modulePath, classPath);
                    }
                }
            }
            for (Path file : Dependencies.select(argument.folder(), group, "runtime")) {
                graph.place(pathPlacement, file, modulePath, classPath);
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

    private static String path(SequencedSet<String> names, SequencedMap<String, Path> pool) {
        return names.stream()
                .map(pool::get)
                .filter(Objects::nonNull)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }
}
