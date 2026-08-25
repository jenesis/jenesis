package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.DependencyTreeReport;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class Tree implements BuildStep {

    private final transient PrintStream out;
    private final transient boolean compact, tests;

    public Tree() {
        this(System.out);
    }

    public Tree(PrintStream out) {
        String format = System.getProperty("jenesis.tree.format", "full");
        this(out, switch (format) {
            case "full" -> false;
            case "compact" -> true;
            default -> throw new IllegalArgumentException(
                    "Unknown jenesis.tree.format '" + format + "', expected 'full' or 'compact'");
        }, Boolean.parseBoolean(System.getProperty("jenesis.tree.tests", "true")));
    }

    private Tree(PrintStream out, boolean compact, boolean tests) {
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
        DependencyTreeReport report = new DependencyTreeReport(out, compact);
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
            String candidate = null;
            for (String key : inventory.stringPropertyNames()) {
                int dot = key.indexOf('.');
                if (dot > 0) {
                    candidate = key.substring(0, dot);
                    break;
                }
            }
            if (candidate == null) {
                continue;
            }
            String prefix = candidate;
            if (!tests && inventory.getProperty(prefix + ".test") != null) {
                continue;
            }
            List<Path> graphs = Inventory.paths(inventory, argument.folder(), prefix + ".graph");
            List<Path> licenses = Inventory.paths(inventory, argument.folder(), prefix + ".licenses");
            SequencedMap<String, Resolver.Resolution> resolutions = Dependencies.graph(graphs, licenses);
            resolutions.forEach((groupScope, resolution) -> {
                report.render(resolution, groupScope + " (" + prefix + ")");
                aggregated.putAll(resolution.vertices());
            });
        }
        report.summary(aggregated);
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}
