package build.jenesis.step;

import module java.base;
import java.util.jar.Attributes;
import build.jenesis.Environment;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.SequencedProperties;

public class Jar extends ProcessBuildStep {

    private static final Attributes.Name CREATED_BY = new Attributes.Name("Created-By");

    private final Sort sort;
    private final OffsetDateTime timestamp;

    public Jar(ProcessHandler.Factory factory,
               Sort sort) {
        this(factory.apply("jar", "bin/jar"),
             sort,
             BuildStep.timestamp(),
             Terms.of("jar"));
    }

    public static Jar ofEnvironment(Environment environment,
                             ProcessHandler.Factory factory,
                             Sort sort) {
        return new Jar(factory.apply("jar", "bin/jar"),
                sort,
                BuildStep.timestamp(environment),
                Terms.ofEnvironment(environment, "jar"));
    }

    private Jar(Function<List<String>, ? extends ProcessHandler> factory,
                Sort sort,
                OffsetDateTime timestamp,
                Terms terms) {
        super("jar", factory, terms);
        this.sort = sort;
        this.timestamp = timestamp;
    }

    public Jar verbose(BiConsumer<Boolean, String> printing) {
        return new Jar(factory, sort, timestamp, terms.printing(printing));
    }

    public Jar timestamp(OffsetDateTime timestamp) {
        return new Jar(factory, sort, timestamp, terms);
    }

    @Override
    public CompletionStage<List<String>> process(Executor executor,
                                                 BuildStepContext context,
                                                 SequencedMap<String, BuildStepArgument> arguments,
                                                 SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        List<String> commands = new ArrayList<>(List.of(
                "--create",
                "--file",
                Files.createDirectory(context.next().resolve(sort.folder))
                        .resolve(sort.file(arguments))
                        .toString()));
        if (timestamp != null) {
            commands.add("--date=" + timestamp);
        }
        List<Path> manifestFiles = new ArrayList<>();
        for (BuildStepArgument argument : sort == Sort.CLASSES ? arguments.values() : List.<BuildStepArgument>of()) {
            if (argument.removed()) {
                continue;
            }
            Path candidate = argument.folder().resolve(Versions.MANIFEST);
            if (Files.exists(candidate)) {
                manifestFiles.add(candidate);
            }
        }
        Manifest merged = new Manifest();
        for (Path path : manifestFiles) {
            Manifest current;
            try (InputStream in = Files.newInputStream(path)) {
                current = new Manifest(in);
            }
            mergeAttributes(merged.getMainAttributes(), current.getMainAttributes(), path);
            for (Map.Entry<String, Attributes> entry : current.getEntries().entrySet()) {
                Attributes target = merged.getEntries().computeIfAbsent(entry.getKey(), _ -> new Attributes());
                mergeAttributes(target, entry.getValue(), path);
            }
        }
        merged.getMainAttributes().putIfAbsent(Attributes.Name.MANIFEST_VERSION, "1.0");
        merged.getMainAttributes().putIfAbsent(CREATED_BY, "Jenesis");
        Path output = context.supplement().resolve(Versions.MANIFEST);
        try (OutputStream out = Files.newOutputStream(output)) {
            merged.write(out);
        }
        commands.add("--manifest");
        commands.add(output.toString());
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (String name : sort.folders) {
                Path folder = argument.folder().resolve(name);
                if (Files.exists(folder)) {
                    commands.add("-C");
                    commands.add(folder.toString());
                    commands.add(".");
                }
            }
        }
        return CompletableFuture.completedStage(commands);
    }

    private static void mergeAttributes(Attributes target, Attributes source, Path file) {
        for (Map.Entry<Object, Object> entry : source.entrySet()) {
            Object key = entry.getKey(), value = entry.getValue(), existing = target.get(key);
            if (existing == null) {
                target.put(key, value);
            } else if (!existing.equals(value)) {
                throw new IllegalStateException("Conflicting manifest attribute '"
                        + key
                        + "' in "
                        + file
                        + ": '"
                        + existing
                        + "' vs '"
                        + value
                        + "'");
            }
        }
    }

    public enum Sort {

        CLASSES("classes", "", BuildStep.ARTIFACTS, BuildStep.CLASSES, BuildStep.RESOURCES),
        SOURCES("sources", "-sources", BuildStep.SOURCES, BuildStep.SOURCES, BuildStep.RESOURCES),
        JAVADOC("javadoc", "-javadoc", BuildStep.DOCUMENTATION, Javadoc.JAVADOC);

        final String kind;
        final String suffix;
        final String folder;
        final List<String> folders;

        Sort(String kind, String suffix, String folder, String... folders) {
            this.kind = kind;
            this.suffix = suffix;
            this.folder = folder;
            this.folders = List.of(folders);
        }

        public String file(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            SequencedProperties module = null, metadata = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path moduleFile = argument.folder().resolve(BuildStep.MODULE),
                        metadataFile = argument.folder().resolve(BuildStep.METADATA);
                if (module == null && Files.isRegularFile(moduleFile)) {
                    module = SequencedProperties.ofFiles(moduleFile);
                }
                if (metadata == null && Files.isRegularFile(metadataFile)) {
                    metadata = SequencedProperties.ofFiles(metadataFile);
                }
            }
            String version = metadata == null ? null : metadata.value("version");
            if (module != null && module.flag("modular") && module.value("module") != null) {
                return BuildExecutorModule.encode(module.value("module"))
                        + (version == null ? "" : "-" + BuildExecutorModule.encode(version)) + suffix + ".jar";
            }
            String artifact = metadata == null ? null : metadata.value("artifact");
            if (artifact == null) {
                return kind + ".jar";
            }
            String group = metadata.value("project");
            return BuildExecutorModule.encode((group == null ? "" : group + "/") + artifact + (version == null ? "" : "/" + version))
                    + (module != null && module.value("test") != null ? "-tests" : "") + suffix + ".jar";
        }
    }
}
