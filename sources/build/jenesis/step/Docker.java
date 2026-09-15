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
        // Every path is spelled out rather than handed over as a folder, which is what lets one store hold
        // the application's jars and every layer's alike: a jar more than one path names is stored once.
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
        for (Map.Entry<String, SequencedSet<String>> layer : layers.entrySet()) {
            for (String name : layer.getValue()) {
                Path jar = resolved.get(name);
                if (jar == null) {
                    throw new IllegalStateException("Layer " + layer.getKey() + " names " + name
                            + ", which was not resolved for this application");
                }
                stored.putIfAbsent(name, jar);
            }
        }
        Path folder = Files.createDirectory(context.next().resolve(DOCKER)), store = folder.resolve("jars");
        Files.createDirectories(store);
        for (Map.Entry<String, Path> entry : stored.entrySet()) {
            BuildStep.linkOrCopy(store.resolve(entry.getKey()), entry.getValue());
        }
        Files.writeString(folder.resolve("Dockerfile"), dockerfile(mainClass,
                modulepath.isEmpty() ? null : mainModule,
                graph.arguments(),
                classpath.sequencedKeySet(),
                modulepath.sequencedKeySet(),
                layers));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private String dockerfile(String mainClass,
                              String mainModule,
                              List<String> relaxations,
                              SequencedSet<String> classpath,
                              SequencedSet<String> modulepath,
                              SequencedMap<String, SequencedSet<String>> layers) {
        StringBuilder builder = new StringBuilder("FROM ").append(from)
                .append("\nWORKDIR /app\nCOPY jars/ /app/jars/\n");
        List<String> command = new ArrayList<>();
        command.add("java");
        layers.forEach((layer, names) -> command.add("-Djenesis.layer." + layer + "=" + path(names)));
        if (!classpath.isEmpty()) {
            command.add("--class-path");
            command.add(path(classpath));
        }
        if (mainModule == null) {
            command.add(mainClass);
        } else {
            command.add("--module-path");
            command.add(path(modulepath));
            command.addAll(relaxations);
            command.add("--module");
            command.add(mainModule + "/" + mainClass);
        }
        return builder.append("ENTRYPOINT [").append(quoted(command)).append("]\n").toString();
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
