package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Environment.Setting;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public class AggregateJavadoc extends ProcessBuildStep {

    public static final String JAR = "javadoc.jar";
    private static final String SOURCES = "sources/";

    private final boolean enabled;
    private final OffsetDateTime timestamp;

    public AggregateJavadoc(ProcessHandler.Factory factory) {
        this(factory.apply("javadoc", "bin/javadoc"), false, BuildStep.timestamp(), new Terms());
    }

    public static AggregateJavadoc ofEnvironment(Environment environment, ProcessHandler.Factory factory) {
        return new AggregateJavadoc(factory.apply("javadoc", "bin/javadoc"),
                environment.flag("documentation.aggregate", false, Setting.PROJECT),
                BuildStep.timestamp(environment),
                Terms.ofEnvironment(environment, "javadoc"));
    }

    private AggregateJavadoc(Function<List<String>, ? extends ProcessHandler> factory,
                             boolean enabled,
                             OffsetDateTime timestamp,
                             Terms terms) {
        super("javadoc", factory, terms);
        this.enabled = enabled;
        this.timestamp = timestamp;
    }

    public AggregateJavadoc enabled(boolean enabled) {
        return new AggregateJavadoc(factory, enabled, timestamp, terms);
    }

    public AggregateJavadoc timestamp(OffsetDateTime timestamp) {
        return new AggregateJavadoc(factory, enabled, timestamp, terms);
    }

    @Override
    protected SequencedMap<String, SequencedMap<String, String>> properties(
            SequencedMap<String, BuildStepArgument> arguments) {
        return new LinkedHashMap<>();
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        return super.apply(executor, context, arguments).thenCompose(result -> {
            Path documentation = context.next().resolve(Javadoc.JAVADOC);
            if (!result.next() || !Files.isDirectory(documentation)) {
                return CompletableFuture.completedStage(result);
            }
            try {
                archive(documentation, context.next().resolve(JAR));
            } catch (IOException e) {
                return CompletableFuture.failedStage(e);
            }
            return CompletableFuture.completedStage(result);
        });
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        if (!enabled) {
            return CompletableFuture.completedStage(null);
        }
        SequencedMap<String, List<Path>> supplements = Inventory.supplements(arguments.values());
        Inventory.Inventories inventories = Inventory.Inventories.read(arguments.values());
        SequencedMap<String, String> modules = new TreeMap<>();
        SequencedMap<String, Path> sources = new TreeMap<>();
        Set<String> identities = new HashSet<>();
        int release = 0;
        for (Map.Entry<Path, SequencedProperties> entry : inventories.inventories().entrySet()) {
            SequencedProperties inventory = entry.getValue();
            for (String prefix : Inventory.prefixes(inventory)) {
                for (int index = 0; inventory.getProperty(prefix + ".identity." + index) != null; index++) {
                    identities.add(inventory.getProperty(prefix + ".identity." + index));
                }
                if (inventory.getProperty(prefix + ".test") != null
                        || !inventory.flag(prefix + ".stage", true)
                        || "pom".equals(inventory.value(prefix + ".packaging"))) {
                    continue;
                }
                String path = inventory.getProperty(prefix + ".path");
                List<Path> jars = Inventory.supplemented(inventory, entry.getKey(), prefix + ".sources", supplements);
                if (jars.isEmpty()) {
                    throw new IllegalStateException("No sources jar to document module "
                            + (path.isEmpty() ? "at the project root" : path)
                            + " in the aggregate javadoc: set jenesis.project.sources=true"
                            + " beside jenesis.documentation.aggregate=true");
                }
                sources.put(path, jars.getFirst());
                modules.put(path, inventory.value(prefix + ".module"));
                String declared = inventory.value(prefix + ".release");
                if (declared != null) {
                    release = Math.max(release, Integer.parseInt(declared));
                }
            }
        }
        if (sources.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        SequencedMap<String, Path> dependencies = new LinkedHashMap<>();
        for (String path : sources.keySet()) {
            inventories.closure(path).forEach((key, dependency) -> {
                String coordinate = key.substring(key.indexOf('/') + 1);
                if (dependency.group().equals("main")
                        && !identities.contains(coordinate)
                        && (dependency.scope() == null || Stream.of(dependency.scope().split(","))
                                .anyMatch(scope -> scope.equals("compile") || scope.equals("provided")))
                        && Files.isRegularFile(dependency.jar())) {
                    dependencies.putIfAbsent(coordinate, dependency.jar());
                }
            });
        }
        SequencedSet<String> modulePath = new TreeSet<>(), classPath = new TreeSet<>();
        Set<Path> placed = new HashSet<>();
        for (Path jar : dependencies.values()) {
            if (placed.add(jar)) {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                if (descriptor == null) {
                    classPath.add(jar.toString());
                } else if (!modules.containsValue(descriptor.name())) {
                    modulePath.add(jar.toString());
                }
            }
        }
        Path extracted = Files.createDirectories(context.supplement().resolve(SOURCES));
        SequencedMap<String, Path> folders = new TreeMap<>();
        boolean modular = true;
        for (Map.Entry<String, Path> entry : sources.entrySet()) {
            Path folder = extract(entry.getValue(), extracted.resolve(Integer.toString(folders.size())));
            folders.put(entry.getKey(), folder);
            modular &= modules.get(entry.getKey()) != null && Files.isRegularFile(folder.resolve("module-info.java"));
        }
        List<String> commands = new ArrayList<>(List.of(
                "-d", Files.createDirectory(context.next().resolve(Javadoc.JAVADOC)).toString(),
                "-quiet"));
        for (String tag : Javadoc.TAGS) {
            commands.add("-tag");
            commands.add(tag);
        }
        if (timestamp != null) {
            commands.add("-notimestamp");
        }
        commands.add("-Xdoclint:none");
        if (release > 0) {
            commands.add("--release");
            commands.add(Integer.toString(release));
        }
        if (!modular) {
            classPath.addAll(modulePath);
            modulePath.clear();
        }
        for (Map.Entry<String, SequencedSet<String>> entry : List.of(
                Map.entry("--module-path", modulePath),
                Map.entry("--class-path", classPath))) {
            if (!entry.getValue().isEmpty()) {
                commands.add(entry.getKey());
                commands.add(String.join(File.pathSeparator, entry.getValue()));
            }
        }
        if (modular) {
            for (Map.Entry<String, Path> entry : folders.entrySet()) {
                commands.add("--module-source-path");
                commands.add(modules.get(entry.getKey()) + "=" + entry.getValue());
            }
            commands.add("--module");
            commands.add(String.join(",", new TreeSet<>(modules.values())));
        } else {
            List<String> files = new ArrayList<>();
            for (Path folder : folders.values()) {
                try (Stream<Path> walk = Files.walk(folder)) {
                    walk.filter(file -> Files.isRegularFile(file)
                                    && file.getFileName().toString().endsWith(".java")
                                    && !file.getFileName().toString().equals("module-info.java"))
                            .map(Path::toString)
                            .forEach(files::add);
                }
            }
            if (files.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            files.sort(null);
            commands.addAll(files);
        }
        return CompletableFuture.completedStage(List.of("@" + argumentFile(context.supplement().resolve("javadoc.args"),
                commands)));
    }

    private static Path extract(Path jar, Path folder) throws IOException {
        Files.createDirectories(folder);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                if (entry.isDirectory() || entry.getName().startsWith("META-INF/")) {
                    continue;
                }
                Path target = folder.resolve(entry.getName()).normalize();
                if (!target.startsWith(folder)) {
                    throw new IllegalArgumentException("Sources jar " + jar + " holds an entry outside of its root: "
                            + entry.getName());
                }
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, target);
                }
            }
        }
        return folder;
    }

    private void archive(Path documentation, Path jar) throws IOException {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(documentation)) {
            files = walk.filter(file -> !file.equals(documentation))
                    .sorted(Comparator.comparing(file -> documentation.relativize(file).toString()
                            .replace(File.separatorChar, '/')))
                    .toList();
        }
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(jar))) {
            entry(out, "META-INF/", null);
            entry(out, "META-INF/MANIFEST.MF", null);
            out.write("Manifest-Version: 1.0\r\nCreated-By: Jenesis\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            for (Path file : files) {
                String name = documentation.relativize(file).toString().replace(File.separatorChar, '/');
                if (Files.isDirectory(file)) {
                    entry(out, name + "/", file);
                } else {
                    entry(out, name, file);
                    Files.copy(file, out);
                }
            }
        }
    }

    private void entry(ZipOutputStream out, String name, Path file) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        if (timestamp != null) {
            entry.setTimeLocal(timestamp.toLocalDateTime());
        } else if (file != null) {
            entry.setTime(Files.getLastModifiedTime(file).toMillis());
        }
        out.putNextEntry(entry);
    }
}
