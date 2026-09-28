package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public class Docker implements BuildStep {

    public static final String DOCKER = "docker/";
    private static final String MODULE_PATH = "/app/extensions/modulepath", CLASS_PATH = "/app/extensions/classpath/*",
            ANNOTATION = "org.opencontainers.image.";

    private final String from;
    private final String group;
    private final SequencedMap<String, String> labels;
    private final OffsetDateTime created;

    public Docker(String from) {
        this(from, "main", new LinkedHashMap<>(), null);
    }

    public static Docker ofEnvironment(Environment environment, String from) {
        return new Docker(from).created(environment.value("archive.timestamp") == null
                ? null
                : BuildStep.timestamp(environment));
    }

    private Docker(String from, String group, SequencedMap<String, String> labels, OffsetDateTime created) {
        this.from = from;
        this.group = group;
        this.labels = labels;
        this.created = created;
    }

    public Docker group(String group) {
        return new Docker(from, group, labels, created);
    }

    public Docker labels(SequencedMap<String, String> labels) {
        return new Docker(from, group, new LinkedHashMap<>(labels), created);
    }

    public Docker created(OffsetDateTime created) {
        return new Docker(from, group, labels, created);
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
        command.add(classpath.isEmpty() ? CLASS_PATH : path(classpath.sequencedKeySet()) + ":" + CLASS_PATH);
        command.add("--module-path");
        if (modulepath.isEmpty()) {
            command.add(MODULE_PATH);
            command.addAll(graph.arguments());
            command.add(mainClass);
        } else {
            command.add(path(modulepath.sequencedKeySet()) + ":" + MODULE_PATH);
            command.addAll(graph.arguments());
            command.add("--module");
            command.add(mainModule + "/" + mainClass);
        }
        ProcessBuildStep.argumentFile(folder.resolve("application.args"), command);
        List<Path> folders = arguments.values().stream()
                .filter(argument -> !argument.removed())
                .map(BuildStepArgument::folder)
                .toList();
        SequencedProperties metadata = SequencedProperties.ofFolders(folders, METADATA);
        SequencedMap<String, String> annotations = new LinkedHashMap<>();
        annotations.put(ANNOTATION + "base.name", from);
        annotations.put(ANNOTATION + "title", metadata.value("name", metadata.value("artifact")));
        annotations.put(ANNOTATION + "description", metadata.value("description"));
        annotations.put(ANNOTATION + "version", metadata.value("version"));
        annotations.put(ANNOTATION + "created", created == null ? null : DateTimeFormatter.ISO_INSTANT.format(created));
        annotations.put(ANNOTATION + "url", metadata.value("url"));
        annotations.put(ANNOTATION + "source", metadata.value("scm.url"));
        annotations.put(ANNOTATION + "revision", metadata.value("scm.revision"));
        annotations.put(ANNOTATION + "vendor", metadata.value("organization.name"));
        SequencedSet<String> developers = new LinkedHashSet<>(), licenses = new LinkedHashSet<>();
        for (String key : metadata.stringPropertyNames()) {
            if (key.startsWith("developer.") && key.lastIndexOf('.') > "developer.".length()) {
                developers.add(key.substring(0, key.lastIndexOf('.') + 1));
            } else if (key.startsWith("license.") && key.lastIndexOf('.') > "license.".length()) {
                licenses.add(key.substring(0, key.lastIndexOf('.') + 1));
            }
        }
        List<String> authors = new ArrayList<>();
        for (String developer : developers) {
            String name = metadata.value(developer + "name"), email = metadata.value(developer + "email");
            if (name != null || email != null) {
                authors.add(name == null ? "<" + email + ">" : email == null ? name : name + " <" + email + ">");
            }
        }
        annotations.put(ANNOTATION + "authors", authors.isEmpty() ? null : String.join(", ", authors));
        Map<String, String> aliases = Dependencies.aliases(folders);
        List<String> identifiers = licenses.stream()
                .map(license -> new License(null, null, metadata.value(license + "name"), metadata.value(license + "url"))
                        .identified(aliases)
                        .id())
                .toList();
        annotations.put(ANNOTATION + "licenses", identifiers.isEmpty() || identifiers.contains(null)
                ? null
                : String.join(" OR ", identifiers));
        annotations.putAll(labels);
        annotations.values().removeIf(value -> value == null || value.isBlank());
        StringBuilder dockerfile = new StringBuilder("FROM ").append(from);
        String separator = "\nLABEL ";
        for (Map.Entry<String, String> annotation : annotations.entrySet()) {
            dockerfile.append(separator).append(label(annotation.getKey())).append('=').append(label(annotation.getValue()));
            separator = " \\\n      ";
        }
        Files.writeString(folder.resolve("Dockerfile"), dockerfile
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

    private static String label(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char character : value.strip().replaceAll("\\s+", " ").toCharArray()) {
            switch (character) {
                case '"', '\\', '$' -> builder.append('\\').append(character);
                default -> builder.append(character);
            }
        }
        return builder.append('"').toString();
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
