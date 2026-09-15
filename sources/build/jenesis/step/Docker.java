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
        SequencedMap<String, Layers.Membership> layers = new TreeMap<>();
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
        Path folder = Files.createDirectory(context.next().resolve(DOCKER)), store = folder.resolve("jars");
        Files.createDirectories(store);
        for (Map.Entry<String, Path> entry : stored.entrySet()) {
            BuildStep.linkOrCopy(store.resolve(entry.getKey()), entry.getValue());
        }
        // The entry point names an argument file rather than the whole command line: a path or a layer
        // that names many jars would otherwise grow the `ENTRYPOINT` past reading, and past what the
        // platform accepts.
        List<String> command = new ArrayList<>();
        // A layer splits the two paths as the application does, and names both: what carries a module is
        // resolved, and the rest is the unnamed module its automatic modules read.
        layers.forEach((layer, membership) -> {
            command.add("-Djenesis.layer.modulepath." + layer + "=" + path(membership.modulepath()));
            if (!membership.classpath().isEmpty()) {
                command.add("-Djenesis.layer.classpath." + layer + "=" + path(membership.classpath()));
            }
        });
        if (!classpath.isEmpty()) {
            command.add("--class-path");
            command.add(path(classpath.sequencedKeySet()));
        }
        if (modulepath.isEmpty()) {
            command.add(mainClass);
        } else {
            command.add("--module-path");
            command.add(path(modulepath.sequencedKeySet()));
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
