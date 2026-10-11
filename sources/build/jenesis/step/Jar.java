package build.jenesis.step;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;
import java.util.jar.Attributes;

public class Jar extends ProcessBuildStep {

    private static final Attributes.Name CREATED_BY = new Attributes.Name("Created-By");

    private final Sort sort;
    private final OffsetDateTime timestamp;

    public Jar(ProcessHandler.Factory factory,
               Sort sort) {
        this(factory.apply("jar", "bin/jar"),
             sort,
             BuildStep.timestamp(),
             new Terms());
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
    protected List<String> configurations() {
        return sort == Sort.CLASSES ? super.configurations() : List.of();
    }

    @Override
    public CompletionStage<List<String>> process(Executor executor,
                                                 BuildStepContext context,
                                                 SequencedMap<String, BuildStepArgument> arguments,
                                                 SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        for (SequencedMap<String, String> configured : properties.values()) {
            for (String option : List.of("--manifest", "-m")) {
                if (configured.containsKey(option)) {
                    throw new IllegalArgumentException("process-jar.properties sets " + option + ", but the jar step"
                            + " writes the manifest itself - place the attributes in a META-INF/MANIFEST.MF among"
                            + " the module's resources instead, which is the basis of the jar's manifest");
                }
            }
        }
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
            for (String name : sort.folders) {
                Path candidate = argument.folder().resolve(name).resolve(JarFile.MANIFEST_NAME);
                if (Files.isRegularFile(candidate)) {
                    manifestFiles.add(candidate);
                }
            }
        }
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
                if (!Files.exists(folder)) {
                    continue;
                }
                Path metaInf = folder.resolve("META-INF");
                if (!Files.isDirectory(metaInf.resolve("build.jenesis"))) {
                    commands.add("-C");
                    commands.add(folder.toString());
                    commands.add(".");
                    continue;
                }
                List<Path> entries = new ArrayList<>();
                try (Stream<Path> files = Files.list(folder)) {
                    files.filter(file -> !file.equals(metaInf)).forEach(entries::add);
                }
                try (Stream<Path> files = Files.list(metaInf)) {
                    files.filter(file -> !BuildStep.underBuildJenesis(folder.relativize(file))).forEach(entries::add);
                }
                entries.sort(null);
                for (Path entry : entries) {
                    commands.add("-C");
                    commands.add(folder.toString());
                    commands.add(folder.relativize(entry).toString());
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
            String version = metadata == null
                    ? null
                    : metadata.value("version", metadata.value("project") == null ? null : "0-SNAPSHOT");
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
