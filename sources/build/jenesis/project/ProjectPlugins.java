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
import build.jenesis.step.ProjectFiles;

public record ProjectPlugins(Path pins,
                             Path arguments,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> preprocessors,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> transforms,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> inspections,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> stageTransforms,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> stageInspections,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> exporters,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> releasers,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> goals,
                             SequencedMap<String, BuildExecutorModule> resolutions) {

    public static final String PREPROCESS = "preprocess",
            POSTPROCESS = "postprocess",
            CUSTOM = "custom",
            TRANSFORM = "transform",
            INSPECT = "inspect",
            PINS = "jenesis.plugins.pin.properties",
            ADDITIONS = "additions",
            STAGED = "staged",
            PROJECT = "project",
            SEALED = "sealed",
            UNCHANGED = "unchanged",
            RESOLVED = "resolved";

    public ProjectPlugins() {
        this(null,
                null,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap());
    }

    public ProjectPlugins pins(Path pins) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins arguments(Path arguments) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins preprocessors(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> preprocessors) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins transforms(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> transforms) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins inspections(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> inspections) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins stageTransforms(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> stageTransforms) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins stageInspections(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> stageInspections) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins exporters(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> exporters) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins releasers(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> releasers) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins goals(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> goals) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins resolutions(SequencedMap<String, BuildExecutorModule> resolutions) {
        return new ProjectPlugins(pins, arguments, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals, resolutions);
    }

    public ProjectPlugins preprocess(String name, BuildExecutorModule module) {
        if (preprocessors.containsKey(name)) {
            throw new IllegalArgumentException("A preprocessor named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(preprocessors);
        added.put(name, _ -> module);
        return preprocessors(added);
    }

    public ProjectPlugins preprocess(String name, BuildStep step) {
        return preprocess(name, step.asModule(name));
    }

    public ProjectPlugins transform(String name, BuildExecutorModule module) {
        if (transforms.containsKey(name)) {
            throw new IllegalArgumentException("A transform named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(transforms);
        added.put(name, _ -> module);
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
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(inspections);
        added.put(name, _ -> module);
        return inspections(added);
    }

    public ProjectPlugins inspect(String name, BuildStep step) {
        return inspect(name, step.asModule(name));
    }

    public ProjectPlugins stageTransform(String name, BuildExecutorModule module) {
        if (stageTransforms.containsKey(name)) {
            throw new IllegalArgumentException("A stage transform named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(stageTransforms);
        added.put(name, _ -> module);
        return stageTransforms(added);
    }

    public ProjectPlugins stageTransform(String name, BuildStep step) {
        return stageTransform(name, step.asModule(name));
    }

    public ProjectPlugins stageInspect(String name, BuildExecutorModule module) {
        if (stageInspections.containsKey(name)) {
            throw new IllegalArgumentException("A stage inspection named " + name + " is added already - give this"
                    + " one another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(stageInspections);
        added.put(name, _ -> module);
        return stageInspections(added);
    }

    public ProjectPlugins stageInspect(String name, BuildStep step) {
        return stageInspect(name, step.asModule(name));
    }

    public ProjectPlugins export(String name, BuildExecutorModule module) {
        if (exporters.containsKey(name)) {
            throw new IllegalArgumentException("An exporter named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(exporters);
        added.put(name, _ -> module);
        return exporters(added);
    }

    public ProjectPlugins export(String name, BuildStep step) {
        return export(name, step.asModule(name));
    }

    public ProjectPlugins release(String name, BuildExecutorModule module) {
        if (releasers.containsKey(name)) {
            throw new IllegalArgumentException("A releaser named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(releasers);
        added.put(name, _ -> module);
        return releasers(added);
    }

    public ProjectPlugins release(String name, BuildStep step) {
        return release(name, step.asModule(name));
    }

    public ProjectPlugins goal(String name, BuildExecutorModule module) {
        if (goals.containsKey(name)) {
            throw new IllegalArgumentException("A goal named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(goals);
        added.put(name, _ -> module);
        return goals(added);
    }

    public ProjectPlugins goal(String name, BuildStep step) {
        return goal(name, step.asModule(name));
    }

    public BuildExecutorModule resolution() {
        return (buildExecutor, _) -> buildExecutor.addModule(CUSTOM, (custom, given) -> {
            for (Map.Entry<String, BuildExecutorModule> resolution : resolutions.entrySet()) {
                custom.addModule(resolution.getKey(), resolution.getValue(), given.sequencedKeySet());
            }
        }, pinned(buildExecutor));
    }

    public BuildExecutorModule preprocess(SequencedSet<Path> profiles) {
        return new BuildExecutorModule() {
            @Override
            public Optional<String> resolve(String path) {
                return Optional.empty();
            }

            @Override
            public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
                hook(buildExecutor, inherited, preprocessors, profiles);
            }
        };
    }

    public BuildExecutorModule stage(SequencedSet<Path> profiles, SequencedMap<String, BuildStep> trees) {
        return (buildExecutor, inherited) -> {
            if (stageTransforms.isEmpty() && stageInspections.isEmpty()) {
                for (Map.Entry<String, BuildStep> tree : trees.entrySet()) {
                    buildExecutor.addStep(tree.getKey(), tree.getValue(), inherited.sequencedKeySet());
                }
                return;
            }
            SequencedMap<String, SequencedMap<String, String>> values = arguments(profiles);
            SequencedSet<String> inputs = pinned(buildExecutor);
            buildExecutor.addModule(STAGED, (staged, given) -> {
                for (Map.Entry<String, BuildStep> tree : trees.entrySet()) {
                    staged.addStep(tree.getKey(), tree.getValue(), given.sequencedKeySet());
                }
            }, inherited.sequencedKeySet());
            SequencedSet<String> handed = new LinkedHashSet<>(inputs);
            handed.add(STAGED);
            if (!stageTransforms.isEmpty()) {
                buildExecutor.addModule(TRANSFORM, (transform, given) -> {
                    SequencedSet<String> previous = new LinkedHashSet<>(given.sequencedKeySet());
                    for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : stageTransforms.entrySet()) {
                        transform.addModule(entry.getKey(),
                                entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                                previous);
                        previous.add(entry.getKey());
                    }
                }, handed);
            }
            for (String tree : trees.keySet()) {
                buildExecutor.addStep(tree,
                        new Merged(tree, STAGED + "/" + tree),
                        stageTransforms.isEmpty() ? Stream.of(STAGED + "/" + tree) : Stream.of(STAGED + "/" + tree, TRANSFORM));
            }
            if (!stageInspections.isEmpty()) {
                SequencedSet<String> inspected = new LinkedHashSet<>(trees.sequencedKeySet());
                buildExecutor.addStep(SEALED, new Sealed(), inspected);
                SequencedSet<String> available = new LinkedHashSet<>(inputs);
                available.addAll(inspected);
                available.add(SEALED);
                buildExecutor.addModule(INSPECT, (inspect, given) -> {
                    for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : stageInspections.entrySet()) {
                        inspect.addModule(entry.getKey(),
                                entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                                given.sequencedKeySet());
                    }
                }, available);
                SequencedSet<String> compared = new LinkedHashSet<>(inspected);
                compared.add(SEALED);
                compared.add(INSPECT);
                buildExecutor.addStep(UNCHANGED, new Unchanged(), compared);
            }
        };
    }

    public BuildExecutorModule export(SequencedSet<Path> profiles) {
        return (buildExecutor, inherited) -> hook(buildExecutor, inherited, exporters, profiles);
    }

    public BuildExecutorModule release(SequencedSet<Path> profiles) {
        return (buildExecutor, inherited) -> hook(buildExecutor, inherited, releasers, profiles);
    }

    public BuildExecutorModule goal(SequencedSet<Path> profiles) {
        return (buildExecutor, inherited) -> {
            if (goals.isEmpty()) {
                return;
            }
            SequencedMap<String, SequencedMap<String, String>> values = arguments(profiles);
            SequencedSet<String> inputs = pinned(buildExecutor);
            inputs.addAll(inherited.sequencedKeySet());
            for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : goals.entrySet()) {
                buildExecutor.addModule(entry.getKey(),
                        entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                        inputs);
            }
        };
    }

    public BuildExecutorModule postprocess(SequencedSet<Path> profiles) {
        return new BuildExecutorModule() {
            @Override
            public Optional<String> resolve(String path) {
                return path.startsWith(ADDITIONS + "/") || path.equals(PROJECT) ? Optional.of(path) : Optional.empty();
            }

            @Override
            public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
                if (transforms.isEmpty() && inspections.isEmpty()) {
                    return;
                }
                SequencedMap<String, SequencedMap<String, String>> values = arguments(profiles);
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
                    buildExecutor.addModule(TRANSFORM, (transform, given) -> {
                        SequencedSet<String> previous = new LinkedHashSet<>(given.sequencedKeySet());
                        for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : transforms.entrySet()) {
                            transform.addModule(entry.getKey(),
                                    entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                                    previous);
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
                    buildExecutor.addStep(PROJECT, new ProjectFiles().folder(ProjectFiles.PROJECT), TRANSFORM);
                    inspected.add(ADDITIONS);
                    inspected.add(PROJECT);
                }
                if (!inspections.isEmpty()) {
                    buildExecutor.addStep(SEALED, new Sealed(), inspected);
                    SequencedSet<String> available = new LinkedHashSet<>(inputs);
                    available.addAll(inspected);
                    available.add(SEALED);
                    buildExecutor.addModule(INSPECT, (inspect, given) -> {
                        for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : inspections.entrySet()) {
                            inspect.addModule(entry.getKey(),
                                    entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                                    given.sequencedKeySet());
                        }
                    }, available);
                    SequencedSet<String> compared = new LinkedHashSet<>(inspected);
                    compared.add(SEALED);
                    compared.add(INSPECT);
                    buildExecutor.addStep(UNCHANGED, new Unchanged(), compared);
                }
            }
        };
    }

    private void hook(BuildExecutor buildExecutor,
                      SequencedMap<String, Path> inherited,
                      SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> plugins,
                      SequencedSet<Path> profiles) throws IOException {
        if (plugins.isEmpty()) {
            return;
        }
        SequencedMap<String, SequencedMap<String, String>> values = arguments(profiles);
        SequencedSet<String> inputs = pinned(buildExecutor);
        inputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(CUSTOM, (custom, given) -> {
            for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : plugins.entrySet()) {
                custom.addModule(entry.getKey(),
                        entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                        given.sequencedKeySet());
            }
        }, inputs);
    }

    private SequencedMap<String, SequencedMap<String, String>> arguments(SequencedSet<Path> profiles) throws IOException {
        SequencedMap<String, SequencedMap<String, String>> values = new TreeMap<>();
        if (arguments == null) {
            return values;
        }
        List<Path> files = new ArrayList<>();
        for (Path profile : profiles) {
            String name = arguments.getFileName().toString();
            files.add(arguments.resolveSibling(name.substring(0, name.length() - ".properties".length())
                    + "-" + profile + ".properties"));
        }
        files.add(arguments);
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties declared = SequencedProperties.ofFiles(file);
            for (String key : declared.stringPropertyNames()) {
                int dot = key.indexOf('.');
                String plugin = dot == -1 ? key : key.substring(0, dot);
                if (dot <= 0 || dot == key.length() - 1 || Stream.of(resolutions, preprocessors, transforms, inspections, stageTransforms, stageInspections, exporters, releasers, goals)
                        .noneMatch(plugins -> plugins.containsKey(plugin))) {
                    throw new IllegalArgumentException("The argument " + key + " in " + file + " names no plugin of "
                            + "the whole project - write <plugin>.<key>=<value> for one of "
                            + Stream.of(resolutions.keySet(), preprocessors.keySet(), transforms.keySet(),
                                            inspections.keySet(), stageTransforms.keySet(), stageInspections.keySet(),
                                            exporters.keySet(), releasers.keySet(), goals.keySet())
                                    .flatMap(Set::stream)
                                    .collect(Collectors.toCollection(TreeSet::new)));
                }
                values.computeIfAbsent(plugin, _ -> new TreeMap<>()).putIfAbsent(key.substring(dot + 1), declared.getProperty(key));
            }
        }
        return values;
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
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(Inventory.INVENTORY);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                SequencedProperties inventory = SequencedProperties.ofFiles(file);
                for (String key : inventory.stringPropertyNames()) {
                    int dot = key.indexOf('.');
                    String owner = dot > 0 ? key.substring(0, dot) : key, entry = dot > 0 ? key.substring(dot + 1) : "";
                    if (!prefixes.contains(owner)) {
                        throw new IllegalArgumentException("A transform adds " + key + " in " + file
                                + ", which names no module of this build - prefix it with one of " + prefixes);
                    }
                    if (entry.isEmpty() || entry.contains("/") || Arrays.asList(entry.split("\\.", -1)).contains("")) {
                        throw new IllegalArgumentException("A transform adds " + key + " in " + file + " - name what it"
                                + " adds as <module>.<key>, a key of dot-separated names, for a file below its own"
                                + " output");
                    }
                    if (!owner.equals(prefix)) {
                        continue;
                    }
                    Path source = BuildStep.resolveContained(argument.folder(), inventory.getProperty(key));
                    if (!Files.exists(source)) {
                        throw new IllegalArgumentException("A transform adds " + key + " in " + file + ", but "
                                + source + " does not exist");
                    }
                    Path target = context.next().resolve(entry.replace('.', '/')).resolve(source.getFileName().toString());
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

    private record Merged(String tree, String staged) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            BuildStepArgument base = arguments.get(staged);
            if (base != null && !base.removed()) {
                try (Stream<Path> files = Files.walk(base.folder())) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Path placed = context.next().resolve(base.folder().relativize(file).toString());
                        Files.createDirectories(placed.getParent());
                        BuildStep.linkOrCopy(placed, file);
                    }
                }
            }
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (argument.getKey().equals(staged) || argument.getValue().removed()) {
                    continue;
                }
                Path folder = argument.getValue().folder().resolve(tree);
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        String relative = folder.relativize(file).toString().replace(File.separatorChar, '/');
                        Path placed = context.next().resolve(relative);
                        if (Files.exists(placed)) {
                            throw new IllegalStateException(argument.getKey() + " adds " + tree + "/" + relative
                                    + ", which is staged already - a transform of stage adds and never replaces");
                        }
                        Files.createDirectories(placed.getParent());
                        BuildStep.linkOrCopy(placed, file);
                    }
                }
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
            inspected.keySet().removeIf(key -> key.equals(INSPECT) || key.startsWith(INSPECT + "/"));
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
