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
    private final String workingDirectory;
    private final String user;
    private final List<String> environment;
    private final List<String> ports;
    private final List<String> options;
    private final List<String> arguments;

    public Docker(String from) {
        this(from, "main", "/app", null, List.of(), List.of(), List.of(), List.of());
    }

    private Docker(String from,
                   String group,
                   String workingDirectory,
                   String user,
                   List<String> environment,
                   List<String> ports,
                   List<String> options,
                   List<String> arguments) {
        this.from = from;
        this.group = group;
        this.workingDirectory = workingDirectory;
        this.user = user;
        this.environment = environment;
        this.ports = ports;
        this.options = options;
        this.arguments = arguments;
    }

    public Docker group(String group) {
        return new Docker(from, group, workingDirectory, user, environment, ports, options, arguments);
    }

    public Docker workingDirectory(String workingDirectory) {
        return new Docker(from, group, workingDirectory, user, environment, ports, options, arguments);
    }

    public Docker user(String user) {
        return new Docker(from, group, workingDirectory, user, environment, ports, options, arguments);
    }

    public Docker environment(List<String> environment) {
        return new Docker(from, group, workingDirectory, user, environment, ports, options, arguments);
    }

    public Docker ports(List<String> ports) {
        return new Docker(from, group, workingDirectory, user, environment, ports, options, arguments);
    }

    public Docker options(List<String> options) {
        return new Docker(from, group, workingDirectory, user, environment, ports, options, arguments);
    }

    public Docker arguments(List<String> arguments) {
        return new Docker(from, group, workingDirectory, user, environment, ports, options, arguments);
    }

    public static Docker configured(Path properties) throws IOException {
        if (properties == null) {
            return null;
        }
        SequencedProperties configuration = SequencedProperties.ofFiles(properties);
        String from = value(configuration, "from");
        Docker docker = new Docker(from == null
                ? "eclipse-temurin:" + Runtime.version().feature() + "-jre"
                : from);
        String workingDirectory = value(configuration, "workdir");
        if (workingDirectory != null) {
            docker = docker.workingDirectory(workingDirectory);
        }
        String user = value(configuration, "user");
        if (user != null) {
            docker = docker.user(user);
        }
        return docker.environment(values(configuration, "env"))
                .ports(values(configuration, "expose"))
                .options(values(configuration, "options"))
                .arguments(values(configuration, "arguments"));
    }

    private static String value(SequencedProperties configuration, String key) {
        String value = configuration.getProperty(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> values(SequencedProperties configuration, String key) {
        String value = configuration.getProperty(key);
        if (value == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String entry : value.split("\n")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return values;
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
        StringBuilder builder = new StringBuilder("FROM ").append(from).append('\n');
        for (String variable : environment) {
            builder.append("ENV ").append(variable).append('\n');
        }
        builder.append("WORKDIR ").append(workingDirectory).append('\n');
        if (!modulepath.isEmpty()) {
            builder.append("COPY modulepath/ ").append(workingDirectory).append("/modulepath/\n");
        }
        if (!classpath.isEmpty()) {
            builder.append("COPY classpath/ ").append(workingDirectory).append("/classpath/\n");
        }
        for (String port : ports) {
            builder.append("EXPOSE ").append(port).append('\n');
        }
        if (user != null) {
            builder.append("USER ").append(user).append('\n');
        }
        List<String> command = new ArrayList<>();
        command.add("java");
        command.addAll(options);
        if (!classpath.isEmpty()) {
            command.add("--class-path");
            command.add(workingDirectory + "/classpath/*");
        }
        if (mainModule == null) {
            command.add(mainClass);
        } else {
            command.add("--module-path");
            command.add(workingDirectory + "/modulepath");
            if (!selfContainedModuleGraph) {
                command.add("--add-modules");
                command.add("ALL-MODULE-PATH");
            }
            command.add("--module");
            command.add(mainModule + "/" + mainClass);
        }
        builder.append("ENTRYPOINT [").append(quoted(command)).append("]\n");
        if (!arguments.isEmpty()) {
            builder.append("CMD [").append(quoted(arguments)).append("]\n");
        }
        return builder.toString();
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
