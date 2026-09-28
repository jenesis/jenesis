package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public class Docker implements BuildStep {

    public static final String DOCKER = "docker/";
    private static final String EXTENSIONS = "/app/extensions", MODULE_PATH = "modulepath", CLASS_PATH = "classpath";

    private final String from;
    private final String group;
    private final String diff;

    public Docker(String from) {
        this(from, "main", null);
    }

    private Docker(String from, String group, String diff) {
        this.from = from;
        this.group = group;
        this.diff = diff;
    }

    public Docker group(String group) {
        return new Docker(from, group, diff);
    }

    public Docker diff(String diff) {
        return new Docker(from, group, diff);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String mainClass = null, mainModule = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path properties = argument.folder().resolve("launcher.properties");
            if (!Files.isRegularFile(properties)) {
                continue;
            }
            SequencedProperties launcher = SequencedProperties.ofFiles(properties);
            if (mainClass == null) {
                mainClass = launcher.getProperty("mainClass");
            }
            if (mainModule == null) {
                mainModule = launcher.getProperty("mainModule");
            }
        }
        if (mainClass == null && diff == null) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, Path> jars = new TreeMap<>();
        SequencedSet<Path> granted = new LinkedHashSet<>();
        SequencedMap<String, Layers.Membership> layers = new TreeMap<>();
        SequencedMap<String, String> agents = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        jars.putIfAbsent(file.getFileName().toString(), file);
                    }
                }
            }
            for (Path file : Dependencies.select(argument.folder(), group, "runtime")) {
                jars.putIfAbsent(file.getFileName().toString(), file);
            }
            granted.addAll(Inventory.nativeAccess(argument.folder()));
            layers.putAll(Layers.membership(argument.folder()));
            agents.putAll(Inventory.agents(argument.folder()));
        }
        if (diff != null) {
            Set<String> declared = new HashSet<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(argument.folder(), diff, "runtime")) {
                    declared.add(identity(jar));
                }
            }
            jars.values().removeIf(jar -> declared.contains(identity(jar)));
            if (!layers.isEmpty()) {
                throw new IllegalStateException("An image that extends " + from + " cannot hold the layers "
                        + layers.sequencedKeySet() + ", which only the image that launches the application defines");
            }
            for (Path jar : jars.values()) {
                if (granted.contains(jar.toAbsolutePath().normalize())) {
                    throw new IllegalStateException("An image that extends " + from + " cannot grant native access to "
                            + jar.getFileName() + " - name --enable-native-access in JDK_JAVA_OPTIONS instead");
                }
            }
            agents.keySet().retainAll(jars.sequencedKeySet());
            if (!agents.isEmpty()) {
                throw new IllegalStateException("An image that extends " + from + " cannot attach the agents "
                        + agents.sequencedKeySet() + " - name -javaagent:<jar> in JDK_JAVA_OPTIONS instead");
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("The image adds nothing to " + from
                        + ": every module it holds is declared by docker.diff already");
            }
            Path folder = Files.createDirectory(context.next().resolve(DOCKER)),
                    store = Files.createDirectory(folder.resolve("extensions"));
            for (Map.Entry<String, Path> entry : jars.entrySet()) {
                Path target = Files.createDirectories(store.resolve(PathPlacement.INFERRED.test(entry.getValue())
                        ? MODULE_PATH
                        : CLASS_PATH));
                BuildStep.linkOrCopy(target.resolve(entry.getKey()), entry.getValue());
            }
            Files.writeString(folder.resolve("Dockerfile"), "FROM " + from + "\nCOPY extensions/ " + EXTENSIONS + "/\n");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        if (jars.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, Path> classpath = new LinkedHashMap<>(), modulepath = new LinkedHashMap<>();
        ModuleGraph graph = new ModuleGraph();
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            boolean placed = graph.place(mainModule == null ? PathPlacement.CLASS_PATH : PathPlacement.INFERRED, entry.getValue());
            (placed ? modulepath : classpath).put(entry.getKey(), entry.getValue());
            if (granted.contains(entry.getValue().toAbsolutePath().normalize())) {
                graph.enableNativeAccess(entry.getValue(), placed);
            }
        }
        if (layers.values().stream().anyMatch(membership -> !membership.classpath().isEmpty())) {
            graph.unnamed();
        }
        SequencedMap<String, Path> stored = new TreeMap<>(classpath);
        stored.putAll(modulepath);
        SequencedMap<String, Path> resolved = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.all(argument.folder())) {
                resolved.putIfAbsent(jar.getFileName().toString(), jar);
            }
        }
        for (Map.Entry<String, Layers.Membership> layer : layers.entrySet()) {
            for (String name : layer.getValue().all()) {
                Path jar = resolved.get(name);
                if (jar == null) {
                    throw new IllegalStateException("Layer " + layer.getKey() + " names " + name
                            + ", which was not resolved for this application");
                }
                stored.putIfAbsent(name, jar);
            }
        }
        layers.forEach((layer, membership) -> membership.nativeAccess(resolved, granted)
                .forEach((jar, module) -> graph.enableNativeAccess(layer, jar, module)));
        agents.keySet().retainAll(stored.sequencedKeySet());
        Path folder = Files.createDirectory(context.next().resolve(DOCKER)), store = folder.resolve("jars");
        Files.createDirectories(store);
        for (Map.Entry<String, Path> entry : stored.entrySet()) {
            BuildStep.linkOrCopy(store.resolve(entry.getKey()), entry.getValue());
        }
        List<String> command = new ArrayList<>();
        agents.forEach((jar, options) -> command.add("-javaagent:/app/jars/" + jar
                + (options.isEmpty() ? "" : "=" + options)));
        layers.forEach((layer, membership) -> {
            command.add("-Djlayer.modulepath." + layer + "=" + path(membership.modulepath()));
            if (!membership.classpath().isEmpty()) {
                command.add("-Djlayer.classpath." + layer + "=" + path(membership.classpath()));
            }
        });
        command.add("--class-path");
        command.add(classpath.isEmpty()
                ? EXTENSIONS + "/" + CLASS_PATH + "/*"
                : path(classpath.sequencedKeySet()) + ":" + EXTENSIONS + "/" + CLASS_PATH + "/*");
        command.add("--module-path");
        if (modulepath.isEmpty()) {
            command.add(EXTENSIONS + "/" + MODULE_PATH);
            command.addAll(graph.arguments());
            command.add(mainClass);
        } else {
            command.add(path(modulepath.sequencedKeySet()) + ":" + EXTENSIONS + "/" + MODULE_PATH);
            command.addAll(graph.arguments());
            command.add("--module");
            command.add(mainModule + "/" + mainClass);
        }
        ProcessBuildStep.argumentFile(folder.resolve("application.args"), command);
        Files.writeString(folder.resolve("Dockerfile"), new StringBuilder("FROM ").append(from)
                .append("\nWORKDIR /app\nCOPY jars/ /app/jars/\nCOPY application.args /app/\n")
                .append("ENTRYPOINT [")
                .append(quoted(List.of("java", "@/app/application.args")))
                .append("]\n")
                .toString());
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static String identity(Path jar) {
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
        if (descriptor != null) {
            return descriptor.name();
        }
        try {
            return ModuleFinder.of(jar).findAll().stream()
                    .map(reference -> reference.descriptor().name())
                    .findFirst()
                    .orElse(jar.getFileName().toString());
        } catch (FindException _) {
            return jar.getFileName().toString();
        }
    }

    private static String path(SequencedSet<String> names) {
        return names.stream().map(name -> "/app/jars/" + name).collect(Collectors.joining(":"));
    }

    private static String quoted(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append('"');
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                switch (character) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    default -> builder.append(character);
                }
            }
            builder.append('"');
        }
        return builder.toString();
    }
}
