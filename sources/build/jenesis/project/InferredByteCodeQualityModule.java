package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Bind;

public class InferredByteCodeQualityModule implements BuildExecutorModule {

    public static final String SPOTBUGS = "spotbugs";

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final Function<SpotBugsModule, BuildExecutorModule> spotbugs;

    public InferredByteCodeQualityModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration, repositories, resolvers, null,
                enabledBy("jenesis.validator.spotbugs"));
    }

    private InferredByteCodeQualityModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers,
                                          Pinning pinning,
                                          Function<SpotBugsModule, BuildExecutorModule> spotbugs) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.spotbugs = spotbugs;
    }

    private static <M extends BuildExecutorModule> Function<M, BuildExecutorModule> enabledBy(String property) {
        return Boolean.parseBoolean(System.getProperty(property, "true")) ? module -> module : null;
    }

    public InferredByteCodeQualityModule pinning(Pinning pinning) {
        return new InferredByteCodeQualityModule(configuration, repositories, resolvers, pinning, spotbugs);
    }

    public InferredByteCodeQualityModule spotbugs(Function<SpotBugsModule, BuildExecutorModule> spotbugs) {
        return new InferredByteCodeQualityModule(configuration, repositories, resolvers, pinning, spotbugs);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        Bind.configured(buildExecutor,
                inherited.sequencedKeySet(),
                SPOTBUGS,
                spotbugs,
                SpotBugsModule.configurationFile(configuration),
                () -> new SpotBugsModule(repositories, resolvers).pinning(pinning));
    }
}
