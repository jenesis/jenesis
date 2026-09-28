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
    private static final String EXTENSIONS = "/app/extensions", ARGUMENTS = "/app/arguments";

    private final String from;
    private final String group;
    private final List<String> options;
    private final String diff;

    public Docker(String from) {
        this(from, "main", List.of(), null);
    }

    private Docker(String from, String group, List<String> options, String diff) {
        this.from = from;
        this.group = group;
        this.options = options;
        this.diff = diff;
    }

    public Docker group(String group) {
        return new Docker(from, group, options, diff);
    }

    public Docker options(List<String> options) {
        return new Docker(from, group, List.copyOf(options), diff);
    }

    public Docker diff(String diff) {
        return new Docker(from, group, options, diff);
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
                            + jar.getFileName() + " - name --enable-native-access in docker.options instead");
                }
            }
            agents.keySet().retainAll(jars.sequencedKeySet());
            List<String> command = new ArrayList<>();
            agents.forEach((jar, parameters) -> command.add("-javaagent:" + EXTENSIONS + "/" + jar
                    + (parameters.isEmpty() ? "" : "=" + parameters)));
            command.addAll(options);
            if (jars.isEmpty() && command.isEmpty()) {
                throw new IllegalStateException("The image adds nothing to " + from
                        + ": every module it holds is declared by docker.diff already, and docker.options is empty");
            }
            Path folder = Files.createDirectory(context.next().resolve(DOCKER));
            StringBuilder dockerfile = new StringBuilder("FROM ").append(from).append('\n');
            if (!jars.isEmpty()) {
                Path store = Files.createDirectory(folder.resolve("extensions"));
                for (Map.Entry<String, Path> entry : jars.entrySet()) {
                    BuildStep.linkOrCopy(store.resolve(entry.getKey()), entry.getValue());
                }
                dockerfile.append("COPY extensions/ ").append(EXTENSIONS).append("/\n");
            }
            if (!command.isEmpty()) {
                String name;
                try {
                    name = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(String.join("\n", command).getBytes(StandardCharsets.UTF_8)), 0, 8) + ".args";
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("The JDK offers no SHA-256 digest", e);
                }
                ProcessBuildStep.argumentFile(Files.createDirectory(folder.resolve("arguments")).resolve(name), command);
                dockerfile.append("COPY arguments/ ").append(ARGUMENTS).append("/\n")
                        .append("ENV JDK_JAVA_OPTIONS=\"${JDK_JAVA_OPTIONS} @").append(ARGUMENTS).append('/')
                        .append(name).append("\"\n");
            }
            Files.writeString(folder.resolve("Dockerfile"), dockerfile.toString());
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
        agents.forEach((jar, parameters) -> command.add("-javaagent:/app/jars/" + jar
                + (parameters.isEmpty() ? "" : "=" + parameters)));
        layers.forEach((layer, membership) -> {
            command.add("-Djlayer.modulepath." + layer + "=" + path(membership.modulepath()));
            if (!membership.classpath().isEmpty()) {
                command.add("-Djlayer.classpath." + layer + "=" + path(membership.classpath()));
            }
        });
        command.addAll(options);
        if (!classpath.isEmpty()) {
            command.add("--class-path");
            command.add(modulepath.isEmpty()
                    ? path(classpath.sequencedKeySet()) + ":" + EXTENSIONS + "/*"
                    : path(classpath.sequencedKeySet()));
        }
        if (modulepath.isEmpty()) {
            command.addAll(graph.arguments());
            command.add(mainClass);
        } else {
            command.add("--module-path");
            command.add(path(modulepath.sequencedKeySet()) + ":" + EXTENSIONS);
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
