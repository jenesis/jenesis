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

public class VerifyModule implements BuildExecutorModule {

    public static final String PINS = "pins",
            TRANSFORM = "transform",
            ADDITIONS = "additions",
            SEALED = "sealed",
            INSPECT = "inspect",
            UNCHANGED = "unchanged",
            RESOLVED = "resolved";

    private final Path pins;
    private final SequencedMap<String, BuildExecutorModule> transforms, inspections, resolutions;

    public VerifyModule() {
        this(null, Collections.emptyNavigableMap(), Collections.emptyNavigableMap(), Collections.emptyNavigableMap());
    }

    public VerifyModule(Path pins,
                        SequencedMap<String, BuildExecutorModule> transforms,
                        SequencedMap<String, BuildExecutorModule> inspections,
                        SequencedMap<String, BuildExecutorModule> resolutions) {
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

    public VerifyModule pins(Path pins) {
        return new VerifyModule(pins, transforms, inspections, resolutions);
    }

    public VerifyModule transforms(SequencedMap<String, BuildExecutorModule> transforms) {
        return new VerifyModule(pins, transforms, inspections, resolutions);
    }

    public VerifyModule inspections(SequencedMap<String, BuildExecutorModule> inspections) {
        return new VerifyModule(pins, transforms, inspections, resolutions);
    }

    public VerifyModule resolutions(SequencedMap<String, BuildExecutorModule> resolutions) {
        return new VerifyModule(pins, transforms, inspections, resolutions);
    }

    public VerifyModule transform(String name, BuildExecutorModule module) {
        if (transforms.containsKey(name)) {
            throw new IllegalArgumentException("A transform named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(transforms);
        added.put(name, module);
        return transforms(added);
    }

    public VerifyModule transform(String name, BuildStep step) {
        return transform(name, step.asModule(name));
    }

    public VerifyModule inspect(String name, BuildExecutorModule module) {
        if (inspections.containsKey(name)) {
            throw new IllegalArgumentException("An inspection named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(inspections);
        added.put(name, module);
        return inspections(added);
    }

    public VerifyModule inspect(String name, BuildStep step) {
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

    @Override
    public Optional<String> resolve(String path) {
        return path.startsWith(ADDITIONS + "/") ? Optional.of(path) : Optional.empty();
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        if (transforms.isEmpty() && inspections.isEmpty()) {
            return;
        }
        SequencedSet<String> inputs = pinned(buildExecutor);
        inputs.addAll(inherited.sequencedKeySet());
        SequencedSet<String> inspected = new LinkedHashSet<>(inherited.sequencedKeySet());
        if (!transforms.isEmpty()) {
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
            buildExecutor.addModule(TRANSFORM, (transform, available) -> {
                SequencedSet<String> previous = new LinkedHashSet<>(available.sequencedKeySet());
                for (Map.Entry<String, BuildExecutorModule> entry : transforms.entrySet()) {
                    transform.addModule(entry.getKey(), entry.getValue(), previous);
                    previous.add(entry.getKey());
                }
            }, inputs);
            buildExecutor.addModule(ADDITIONS, (additions, produced) -> {
                for (String prefix : prefixes) {
                    additions.addStep(BuildExecutorModule.encodePath(prefix),
                            new Additions(prefix, prefixes),
                            produced.sequencedKeySet());
                }
            }, TRANSFORM);
            inspected.add(ADDITIONS);
        }
        if (!inspections.isEmpty()) {
            buildExecutor.addStep(SEALED, new Sealed(), inspected);
            SequencedSet<String> available = new LinkedHashSet<>(inputs);
            available.addAll(inspected);
            available.add(SEALED);
            buildExecutor.addModule(INSPECT, (inspect, given) -> {
                for (Map.Entry<String, BuildExecutorModule> entry : inspections.entrySet()) {
                    inspect.addModule(entry.getKey(), entry.getValue(), given.sequencedKeySet());
                }
            }, available);
            SequencedSet<String> compared = new LinkedHashSet<>(inspected);
            compared.add(SEALED);
            compared.add(INSPECT);
            buildExecutor.addStep(UNCHANGED, new Unchanged(), compared);
        }
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

    private record Unchanged() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, BuildStepArgument> inspected = new LinkedHashMap<>(arguments);
            BuildStepArgument seal = inspected.remove(SEALED);
            inspected.remove(INSPECT);
            inspected.keySet().removeIf(key -> key.startsWith(INSPECT + "/"));
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
