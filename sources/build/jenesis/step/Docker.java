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

    private final String from;
    private final String group;

    public Docker(String from) {
        this(from, "main");
    }

    private Docker(String from, String group) {
        this.from = from;
        this.group = group;
    }

    public Docker group(String group) {
        return new Docker(from, group);
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
        if (mainClass == null) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, Path> jars = new TreeMap<>();
        SequencedMap<String, SequencedSet<String>> layers = new TreeMap<>();
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
            layers.putAll(Layers.membership(argument.folder()));
        }
        if (jars.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, Path> classpath = new LinkedHashMap<>(), modulepath = new LinkedHashMap<>();
        ModuleGraph graph = new ModuleGraph();
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            if (mainModule != null) {
                (graph.place(PathPlacement.INFERRED, entry.getValue()) ? modulepath : classpath)
                        .put(entry.getKey(), entry.getValue());
            } else {
                classpath.put(entry.getKey(), entry.getValue());
            }
        }
        Path folder = Files.createDirectory(context.next().resolve(DOCKER));
        copy(folder.resolve("classpath"), classpath);
        copy(folder.resolve("modulepath"), modulepath);
        // A layer's modules are copied among the application's, so a jar both need is stored once; the
        // entry point then names each path rather than handing over a folder.
        SequencedMap<String, Path> isolated = new LinkedHashMap<>();
        layers.values().forEach(names -> names.forEach(name -> {
            Path jar = jars.get(name);
            if (jar != null) {
                isolated.putIfAbsent(name, jar);
            }
        }));
        copy(folder.resolve("modulepath"), isolated);
        SequencedSet<String> application = new LinkedHashSet<>(modulepath.sequencedKeySet());
        application.removeIf(name -> layers.values().stream().anyMatch(names -> names.contains(name)));
        Files.writeString(folder.resolve("Dockerfile"), dockerfile(mainClass,
                modulepath.isEmpty() ? null : mainModule,
                graph.arguments(),
                classpath.sequencedKeySet(),
                application,
                layers));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static void copy(Path folder, SequencedMap<String, Path> jars) throws IOException {
        if (jars.isEmpty()) {
            return;
        }
        Files.createDirectories(folder);
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            BuildStep.linkOrCopy(folder.resolve(entry.getKey()), entry.getValue());
        }
    }

    private String dockerfile(String mainClass,
                              String mainModule,
                              List<String> relaxations,
                              SequencedSet<String> classpath,
                              SequencedSet<String> modulepath,
                              SequencedMap<String, SequencedSet<String>> layers) {
        StringBuilder builder = new StringBuilder("FROM ").append(from).append("\nWORKDIR /app\n");
        if (!modulepath.isEmpty()) {
            builder.append("COPY modulepath/ /app/modulepath/\n");
        }
        if (!classpath.isEmpty()) {
            builder.append("COPY classpath/ /app/classpath/\n");
        }
        List<String> command = new ArrayList<>();
        command.add("java");
        layers.forEach((layer, names) -> command.add("-Djenesis.layer." + layer + "=" + names.stream()
                .map(name -> "/app/modulepath/" + name)
                .collect(Collectors.joining(":"))));
        if (!classpath.isEmpty()) {
            command.add("--class-path");
            command.add("/app/classpath/*");
        }
        if (mainModule == null) {
            command.add(mainClass);
        } else {
            command.add("--module-path");
            // Named rather than handed over as a folder: a layer's modules sit among these, and the
            // application must not read them - two versions of one module cannot share one path.
            command.add(layers.isEmpty()
                    ? "/app/modulepath"
                    : modulepath.stream().map(name -> "/app/modulepath/" + name).collect(Collectors.joining(":")));
            command.addAll(relaxations);
            command.add("--module");
            command.add(mainModule + "/" + mainClass);
        }
        return builder.append("ENTRYPOINT [").append(quoted(command)).append("]\n").toString();
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
