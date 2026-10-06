package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.DependencyTreeReport;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.Palette;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class Tree implements BuildStep {

    private final transient Consumer<String> out;
    private final transient Palette palette;
    private final transient boolean compact, merged, tests;

    public Tree() {
        this(null, Palette.NONE, false, true, true);
    }

    public static Tree ofEnvironment(Environment environment) {
        Tree tree = new Tree(environment.out(), Palette.ofEnvironment(environment), false, true, true);
        String format = environment.getProperty("tree.format");
        if (format != null) {
            tree = tree.compact(switch (format) {
                case "full" -> false;
                case "compact" -> true;
                default -> throw new IllegalArgumentException(
                        "Unknown jenesis.tree.format '" + format + "', expected 'full' or 'compact'");
            });
        }
        String scopes = environment.getProperty("tree.scopes");
        if (scopes != null) {
            tree = tree.merged(switch (scopes) {
                case "merged" -> true;
                case "separate" -> false;
                default -> throw new IllegalArgumentException(
                        "Unknown jenesis.tree.scopes '" + scopes + "', expected 'merged' or 'separate'");
            });
        }
        Boolean tests = environment.flagOrNull("tree.tests");
        return tests == null ? tree : tree.tests(tests);
    }

    private Tree(Consumer<String> out, Palette palette, boolean compact, boolean merged, boolean tests) {
        this.out = out;
        this.palette = palette;
        this.compact = compact;
        this.merged = merged;
        this.tests = tests;
    }

    public Tree printing(Consumer<String> out, Palette palette) {
        return new Tree(out, palette, compact, merged, tests);
    }

    public Tree compact(boolean compact) {
        return new Tree(out, palette, compact, merged, tests);
    }

    public Tree merged(boolean merged) {
        return new Tree(out, palette, compact, merged, tests);
    }

    public Tree tests(boolean tests) {
        return new Tree(out, palette, compact, merged, tests);
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return true;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        if (out == null) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        Map<Path, SequencedProperties> inventories = new HashMap<>();
        SequencedMap<Path, String> prefixes = new LinkedHashMap<>();
        SequencedMap<String, String> locations = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            Path inventoryFile = argument.folder().resolve(Inventory.INVENTORY);
            if (argument.removed() || !Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String key : inventory.stringPropertyNames()) {
                if (key.endsWith(".graph.0")) {
                    String prefix = key.substring(0, key.length() - "graph.0".length());
                    for (int index = 0; inventory.value(prefix + "identity." + index) != null; index++) {
                        String identity = inventory.value(prefix + "identity." + index);
                        locations.put(identity.startsWith("maven/")
                                ? identity.substring(0, identity.lastIndexOf('/'))
                                : identity, "./" + inventory.value(prefix + "path", ""));
                    }
                    inventories.put(argument.folder(), inventory);
                    prefixes.put(argument.folder(), prefix);
                    break;
                }
            }
        }
        DependencyTreeReport report = new DependencyTreeReport(out, palette)
                .compact(compact)
                .merged(merged)
                .locations(locations);
        SequencedMap<String, Resolver.Vertex> aggregated = new LinkedHashMap<>();
        for (Map.Entry<Path, String> entry : prefixes.entrySet()) {
            SequencedProperties inventory = inventories.get(entry.getKey());
            String prefix = entry.getValue();
            if (!tests && inventory.getProperty(prefix + "test") != null) {
                continue;
            }
            String identity = inventory.value(prefix + "identity.0");
            for (int index = 0; inventory.value(prefix + "identity." + index) != null; index++) {
                if (inventory.value(prefix + "identity." + index).startsWith("maven/")) {
                    identity = inventory.value(prefix + "identity." + index);
                    break;
                }
            }
            boolean maven = identity != null && identity.startsWith("maven/");
            String key = maven ? identity.substring(0, identity.lastIndexOf('/')) : identity,
                    version = maven ? identity.substring(identity.lastIndexOf('/') + 1) : inventory.value(prefix + "version");
            List<License> licenses = new ArrayList<>();
            for (int index = 0; inventory.value(prefix + "license." + index) != null; index++) {
                licenses.add(new License(null, null, inventory.value(prefix + "license." + index), null));
            }
            Resolver.Vertex root = new Resolver.Vertex(version, inventory.value(prefix + "module"), false, true, licenses);
            SequencedMap<String, SequencedMap<String, Resolver.Resolution>> groups = new LinkedHashMap<>();
            Dependencies.graph(
                    Inventory.paths(inventory, entry.getKey(), prefix + "graph"),
                    Inventory.paths(inventory, entry.getKey(), prefix + "licenses")).forEach((groupScope, resolution) -> {
                groups.computeIfAbsent(groupScope.substring(0, groupScope.indexOf('/')), _ -> new TreeMap<>())
                        .put(groupScope.substring(groupScope.indexOf('/') + 1), resolution);
                aggregated.putAll(resolution.vertices());
            });
            groups.forEach((group, scopes) -> {
                if (key == null) {
                    report.render(group, scopes);
                } else {
                    report.render(scopes, key, root);
                }
            });
        }
        report.summary(aggregated);
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}
