package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.DependencyTreeReport;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class Tree implements BuildStep {

    private final transient Consumer<String> out;
    private final transient boolean compact, tests;

    public Tree() {
        this(System.out::println, false, true);
    }

    public Tree(Environment environment) {
        this(environment.out(), false, true);
    }

    public static Tree ofEnvironment(Environment environment) {
        Tree tree = new Tree(environment);
        String format = environment.getProperty("tree.format");
        if (format != null) {
            tree = tree.compact(switch (format) {
                case "full" -> false;
                case "compact" -> true;
                default -> throw new IllegalArgumentException(
                        "Unknown jenesis.tree.format '" + format + "', expected 'full' or 'compact'");
            });
        }
        Boolean tests = environment.flagOrNull("tree.tests");
        return tests == null ? tree : tree.tests(tests);
    }

    private Tree(Consumer<String> out, boolean compact, boolean tests) {
        this.out = out;
        this.compact = compact;
        this.tests = tests;
    }

    public Tree compact(boolean compact) {
        return new Tree(out, compact, tests);
    }

    public Tree tests(boolean tests) {
        return new Tree(out, compact, tests);
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
        DependencyTreeReport report = new DependencyTreeReport(out).compact(compact).locations(locations);
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
            Dependencies.graph(
                    Inventory.paths(inventory, entry.getKey(), prefix + "graph"),
                    Inventory.paths(inventory, entry.getKey(), prefix + "licenses")).forEach((groupScope, resolution) -> {
                if (key == null) {
                    report.render(resolution, groupScope);
                } else {
                    report.render(resolution, key, groupScope.substring(groupScope.indexOf('/') + 1), root);
                }
                aggregated.putAll(resolution.vertices());
            });
        }
        report.summary(aggregated);
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}
