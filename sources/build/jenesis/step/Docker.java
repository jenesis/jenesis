package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
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
        for (BuildStepArgument argument : arguments.values()) {
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
        }
        if (jars.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, Path> classpath = new LinkedHashMap<>(), modulepath = new LinkedHashMap<>();
        boolean selfContainedModuleGraph = true;
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            if (mainModule != null) {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(entry.getValue());
                (descriptor != null ? modulepath : classpath).put(entry.getKey(), entry.getValue());
                selfContainedModuleGraph &= descriptor != null && !descriptor.isAutomatic();
            } else {
                classpath.put(entry.getKey(), entry.getValue());
            }
        }
        Path folder = Files.createDirectory(context.next().resolve(DOCKER));
        copy(folder.resolve("classpath"), classpath);
        copy(folder.resolve("modulepath"), modulepath);
        Files.writeString(folder.resolve("Dockerfile"), dockerfile(mainClass,
                modulepath.isEmpty() ? null : mainModule,
                selfContainedModuleGraph,
                classpath.sequencedKeySet(),
                modulepath.sequencedKeySet()));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static void copy(Path folder, SequencedMap<String, Path> jars) throws IOException {
        if (jars.isEmpty()) {
            return;
        }
        Files.createDirectory(folder);
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            BuildStep.linkOrCopy(folder.resolve(entry.getKey()), entry.getValue());
        }
    }

    private String dockerfile(String mainClass,
                              String mainModule,
                              boolean selfContainedModuleGraph,
                              SequencedSet<String> classpath,
                              SequencedSet<String> modulepath) {
        StringBuilder builder = new StringBuilder("FROM ").append(from).append("\nWORKDIR /app\n");
        if (!modulepath.isEmpty()) {
            builder.append("COPY modulepath/ /app/modulepath/\n");
        }
        if (!classpath.isEmpty()) {
            builder.append("COPY classpath/ /app/classpath/\n");
        }
        List<String> command = new ArrayList<>();
        command.add("java");
        if (!classpath.isEmpty()) {
            command.add("--class-path");
            command.add("/app/classpath/*");
        }
        if (mainModule == null) {
            command.add(mainClass);
        } else {
            command.add("--module-path");
            command.add("/app/modulepath");
            if (!selfContainedModuleGraph) {
                command.add("--add-modules");
                command.add("ALL-MODULE-PATH");
            }
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
