package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.DependencyTreeReport;
import build.jenesis.Environment;
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
        DependencyTreeReport report = new DependencyTreeReport(out).compact(compact);
        SequencedMap<String, Resolver.Vertex> aggregated = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            String prefix = null;
            for (String key : inventory.stringPropertyNames()) {
                if (key.endsWith(".graph.0")) {
                    prefix = key.substring(0, key.length() - "graph.0".length());
                    break;
                }
            }
            if (prefix == null) {
                continue;
            }
            String tested = inventory.getProperty(prefix + "test");
            if (!tests && tested != null) {
                continue;
            }
            StringBuilder title = new StringBuilder("./" + inventory.value(prefix + "path", ""));
            String version = inventory.value(prefix + "version"), module = inventory.value(prefix + "module");
            if (version != null) {
                title.append(' ').append(version);
            }
            StringBuilder meta = new StringBuilder();
            if (module != null) {
                meta.append("module ").append(module);
            }
            if (tested != null) {
                meta.append(meta.isEmpty() ? "test" : ", test");
            }
            if (!meta.isEmpty()) {
                title.append(" (").append(meta).append(')');
            }
            List<String> licenses = new ArrayList<>();
            for (int index = 0; inventory.value(prefix + "license." + index) != null; index++) {
                licenses.add(inventory.value(prefix + "license." + index));
            }
            if (!licenses.isEmpty()) {
                title.append(" {").append(String.join(", ", licenses)).append('}');
            }
            SequencedMap<List<String>, Resolver.Edge> edges = new LinkedHashMap<>();
            SequencedMap<String, Resolver.Vertex> vertices = new LinkedHashMap<>();
            for (Resolver.Resolution resolution : Dependencies.graph(
                    Inventory.paths(inventory, argument.folder(), prefix + "graph"),
                    Inventory.paths(inventory, argument.folder(), prefix + "licenses")).values()) {
                for (Resolver.Edge edge : resolution.edges()) {
                    edges.merge(Arrays.asList(edge.parent(), edge.coordinate()), edge,
                            (current, candidate) -> current.followed() || !candidate.followed() ? current : candidate);
                }
                resolution.vertices().forEach(vertices::putIfAbsent);
            }
            report.render(new Resolver.Resolution(new LinkedHashMap<>(), List.copyOf(edges.values()), vertices),
                    title.toString());
            aggregated.putAll(vertices);
        }
        report.summary(aggregated);
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}
