package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;
import build.jenesis.step.Inventory;

public class ProjectPlugins {

    public static final String TRANSFORM = "transform",
            INSPECT = "inspect",
            PINS = "pins",
            ADDITIONS = "additions",
            SEALED = "sealed",
            UNCHANGED = "unchanged",
            RESOLVED = "resolved";

    private final Path pins;
    private final SequencedMap<String, BuildExecutorModule> transforms, inspections, resolutions;

    public ProjectPlugins() {
        this(null, Collections.emptyNavigableMap(), Collections.emptyNavigableMap(), Collections.emptyNavigableMap());
    }

    public ProjectPlugins(Path pins,
                          SequencedMap<String, BuildExecutorModule> transforms,
                          SequencedMap<String, BuildExecutorModule> inspections,
                          SequencedMap<String, BuildExecutorModule> resolutions) {
        for (String name : transforms.keySet()) {
            if (List.of(PINS, ADDITIONS).contains(name)) {
                throw new IllegalArgumentException("Cannot add a transform named " + name + " - the build names a"
                        + " step of transform so, give the transform another name");
            }
        }
        for (String name : inspections.keySet()) {
            if (List.of(PINS, SEALED, UNCHANGED).contains(name)) {
                throw new IllegalArgumentException("Cannot add an inspection named " + name + " - the build names a"
                        + " step of inspect so, give the inspection another name");
            }
        }
        this.pins = pins;
        this.transforms = transforms;
        this.inspections = inspections;
        this.resolutions = resolutions;
    }

    public Path pins() {
        return pins;
    }

    public SequencedMap<String, BuildExecutorModule> transforms() {
        return transforms;
    }

    public SequencedMap<String, BuildExecutorModule> inspections() {
        return inspections;
    }

    public SequencedMap<String, BuildExecutorModule> resolutions() {
        return resolutions;
    }

    public ProjectPlugins pins(Path pins) {
        return new ProjectPlugins(pins, transforms, inspections, resolutions);
    }

    public ProjectPlugins transforms(SequencedMap<String, BuildExecutorModule> transforms) {
        return new ProjectPlugins(pins, transforms, inspections, resolutions);
    }

    public ProjectPlugins inspections(SequencedMap<String, BuildExecutorModule> inspections) {
        return new ProjectPlugins(pins, transforms, inspections, resolutions);
    }

    public ProjectPlugins resolutions(SequencedMap<String, BuildExecutorModule> resolutions) {
        return new ProjectPlugins(pins, transforms, inspections, resolutions);
    }

    public ProjectPlugins transform(String name, BuildExecutorModule module) {
        if (transforms.containsKey(name)) {
            throw new IllegalArgumentException("A transform named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(transforms);
        added.put(name, module);
        return transforms(added);
    }

    public ProjectPlugins transform(String name, BuildStep step) {
        return transform(name, step.asModule(name));
    }

    public ProjectPlugins inspect(String name, BuildExecutorModule module) {
        if (inspections.containsKey(name)) {
            throw new IllegalArgumentException("An inspection named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(inspections);
        added.put(name, module);
        return inspections(added);
    }

    public ProjectPlugins inspect(String name, BuildStep step) {
        return inspect(name, step.asModule(name));
    }

    public BuildExecutorModule resolution() {
        return (buildExecutor, _) -> {
            SequencedSet<String> inputs = pinned(buildExecutor);
            for (Map.Entry<String, BuildExecutorModule> resolution : resolutions.entrySet()) {
                buildExecutor.addModule(resolution.getKey(), resolution.getValue(), inputs);
            }
        };
    }

    public BuildExecutorModule transformModule() {
        return new BuildExecutorModule() {
            @Override
            public Optional<String> resolve(String path) {
                return path.startsWith(ADDITIONS + "/") ? Optional.of(path) : Optional.empty();
            }

            @Override
            public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
                if (transforms.isEmpty()) {
                    return;
                }
                SequencedSet<String> prefixes = new LinkedHashSet<>();
                for (Path folder : inherited.values()) {
                    Path file = folder.resolve(Inventory.INVENTORY);
                    if (Files.isRegularFile(file)) {
                        for (String key : SequencedProperties.ofFiles(file).stringPropertyNames()) {
                            if (key.indexOf('.') > 0 && key.endsWith(".path")) {
                                prefixes.add(key.substring(0, key.indexOf('.')));
                            }
                        }
                    }
                }
                SequencedSet<String> previous = pinned(buildExecutor);
                previous.addAll(inherited.sequencedKeySet());
                for (Map.Entry<String, BuildExecutorModule> entry : transforms.entrySet()) {
                    buildExecutor.addModule(entry.getKey(), entry.getValue(), previous);
                    previous.add(entry.getKey());
                }
                buildExecutor.addModule(ADDITIONS, (additions, produced) -> {
                    for (String prefix : prefixes) {
                        additions.addStep(BuildExecutorModule.encodePath(prefix),
                                new Additions(prefix, prefixes),
                                produced.sequencedKeySet());
                    }
                }, transforms.sequencedKeySet());
            }
        };
    }

    public BuildExecutorModule inspectModule() {
        return new BuildExecutorModule() {
            @Override
            public Optional<String> resolve(String path) {
                return Optional.empty();
            }

            @Override
            public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
                if (inspections.isEmpty()) {
                    return;
                }
                buildExecutor.addStep(SEALED, new Sealed(), inherited.sequencedKeySet());
                SequencedSet<String> available = pinned(buildExecutor);
                available.addAll(inherited.sequencedKeySet());
                available.add(SEALED);
                for (Map.Entry<String, BuildExecutorModule> entry : inspections.entrySet()) {
                    buildExecutor.addModule(entry.getKey(), entry.getValue(), available);
                }
                SequencedSet<String> compared = new LinkedHashSet<>(inherited.sequencedKeySet());
                compared.add(SEALED);
                compared.addAll(inspections.sequencedKeySet());
                buildExecutor.addStep(UNCHANGED, new Unchanged(new LinkedHashSet<>(inspections.sequencedKeySet())), compared);
            }
        };
    }

    private SequencedSet<String> pinned(BuildExecutor buildExecutor) {
        SequencedSet<String> inputs = new LinkedHashSet<>();
        if (pins != null && Files.isRegularFile(pins)) {
            buildExecutor.addSource(PINS, new Bind(Map.of(Path.of(""), Path.of(BuildStep.VERSIONS))), pins);
            inputs.add(PINS);
        }
        return inputs;
    }

    private static SequencedMap<String, String> listing(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        SequencedMap<String, String> listing = new TreeMap<>();
        for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
            Path folder = argument.getValue().folder();
            if (argument.getValue().removed() || !Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    listing.put(argument.getKey() + "/" + folder.relativize(file).toString().replace(File.separatorChar, '/'),
                            Files.size(file) + " " + Files.getLastModifiedTime(file).toMillis());
                }
            }
        }
        return listing;
    }

    private record Additions(String prefix, SequencedSet<String> prefixes) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties additions = new SequencedProperties();
            for (BuildStepArgument argument : arguments.values()) {
                Path file = argument.folder().resolve(Inventory.INVENTORY);
                if (argument.removed() || !Files.isRegularFile(file)) {
                    continue;
                }
                SequencedProperties inventory = SequencedProperties.ofFiles(file);
                for (String key : inventory.stringPropertyNames()) {
                    int dot = key.indexOf('.');
                    String owner = dot > 0 ? key.substring(0, dot) : key, entry = dot > 0 ? key.substring(dot + 1) : "";
                    int separator = entry.indexOf('.');
                    String kind = separator > 0 ? entry.substring(0, separator) : entry;
                    String name = separator > 0 ? entry.substring(separator + 1) : "";
                    if (!prefixes.contains(owner)) {
                        throw new IllegalArgumentException("A transform adds " + key + " in " + file
                                + ", which names no module of this build - prefix it with one of " + prefixes);
                    }
                    if (!List.of("attachment", "report").contains(kind) || name.isEmpty() || name.contains("/")) {
                        throw new IllegalArgumentException("A transform adds " + key + " in " + file + " - it can only"
                                + " add <module>.attachment.<classifier> or <module>.report.<name>, naming a file"
                                + " below its own output");
                    }
                    if (!owner.equals(prefix)) {
                        continue;
                    }
                    Path source = BuildStep.resolveContained(argument.folder(), inventory.getProperty(key));
                    if (!Files.exists(source)) {
                        throw new IllegalArgumentException("A transform adds " + key + " in " + file + ", but "
                                + source + " does not exist");
                    }
                    Path target = context.next().resolve(kind).resolve(name).resolve(source.getFileName().toString());
                    if (additions.getProperty(key) != null) {
                        throw new IllegalStateException("More than one transform adds " + key + " - give each"
                                + " addition a name of its own");
                    }
                    Files.createDirectories(target.getParent());
                    if (Files.isDirectory(source)) {
                        try (Stream<Path> files = Files.walk(source)) {
                            for (Path nested : files.filter(Files::isRegularFile).toList()) {
                                Path linked = target.resolve(source.relativize(nested).toString());
                                Files.createDirectories(linked.getParent());
                                BuildStep.linkOrCopy(linked, nested);
                            }
                        }
                    } else {
                        BuildStep.linkOrCopy(target, source);
                    }
                    additions.setProperty(key, context.next().relativize(target).toString().replace(File.separatorChar, '/'));
                }
            }
            if (!additions.isEmpty()) {
                additions.store(context.next().resolve(Inventory.INVENTORY));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Sealed() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties sealed = new SequencedProperties();
            listing(arguments).forEach(sealed::setProperty);
            sealed.store(context.next().resolve("sealed.properties"));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Unchanged(SequencedSet<String> inspections) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, BuildStepArgument> inspected = new LinkedHashMap<>(arguments);
            BuildStepArgument seal = inspected.remove(SEALED);
            inspected.keySet().removeIf(key -> inspections.contains(key)
                    || inspections.stream().anyMatch(name -> key.startsWith(name + "/")));
            SequencedProperties sealed = SequencedProperties.ofFiles(seal.folder().resolve("sealed.properties"));
            SequencedMap<String, String> expected = new TreeMap<>();
            sealed.forEachProperty(expected::put);
            SequencedMap<String, String> actual = listing(inspected);
            if (!expected.equals(actual)) {
                SequencedSet<String> changed = new TreeSet<>(expected.keySet());
                changed.addAll(actual.keySet());
                changed.removeIf(key -> Objects.equals(expected.get(key), actual.get(key)));
                throw new IllegalStateException("An inspection changed what it inspects: " + changed
                        + " - an inspection writes only into its own output");
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
