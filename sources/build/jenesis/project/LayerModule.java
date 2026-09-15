package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Layers;

/**
 * Reconstructs the layers a module's dependencies declare. A module that keeps a dependency private says so
 * in its jar's {@code Jenesis-Layer} header, naming the module it shares with the layer and the coordinates
 * the layer holds; a consumer reads that, resolves those coordinates in the layer's own group, and
 * materialises them into a folder the module can be handed at run time. The consumer declares nothing and
 * need not know the layer exists.
 */
public class LayerModule implements BuildExecutorModule {

    public static final String BUNDLE = "bundle";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";

    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;

    public LayerModule(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(repositories, resolvers, null);
    }

    private LayerModule(Map<String, Repository> repositories, Map<String, Resolver> resolvers, Pinning pinning) {
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
    }

    public LayerModule pinning(Pinning pinning) {
        return new LayerModule(repositories, resolvers, pinning);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED, new Required(), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES,
                new Dependencies(repositories, resolvers).pinning(pinning),
                resolveInputs);
        SequencedSet<String> bundleInputs = new LinkedHashSet<>();
        bundleInputs.add(REQUIRED);
        bundleInputs.add(DEPENDENCIES);
        bundleInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(BUNDLE, new Layers(), bundleInputs);
    }

    /**
     * Turns every {@code Jenesis-Layer} header among the resolved dependencies into declarations of its own:
     * the coordinates into the layer's group, and the layer's API module into the declaration the
     * materialising step reads. A module's own layers are already declared in its sources and resolved with
     * the rest of its dependencies, so only what its dependencies declare is added here.
     */
    private record Required() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties requires = new SequencedProperties(), layers = new SequencedProperties();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.all(argument.folder())) {
                    PathPlacement.layers(jar).forEach((layer, tokens) -> {
                        Iterator<String> iterator = tokens.iterator();
                        layers.setProperty(layer, iterator.next());
                        while (iterator.hasNext()) {
                            requires.setProperty("layer:" + layer + "/runtime/" + iterator.next(), "");
                        }
                    });
                }
            }
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            if (!layers.isEmpty()) {
                layers.store(context.next().resolve(BuildStep.LAYERS));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
