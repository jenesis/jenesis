package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;

public class InferredArtifactQualityModule implements BuildExecutorModule {

    public static final String JAPICMP = "japicmp";
    private static final Set<String> JAPICMP_KEYS = Set.of("baseline",
            "access",
            "include",
            "exclude",
            "format",
            "ignore-missing-classes",
            "only-incompatible",
            "only-modified",
            "semantic-versioning",
            "error-on-binary-incompatibility",
            "error-on-source-incompatibility",
            "error-on-modifications",
            "error-on-semantic-incompatibility");

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final Function<JApiCmpModule, BuildExecutorModule> japicmp;

    public InferredArtifactQualityModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration, repositories, resolvers, null,
                SequencedProperties.systemFlag("jenesis.artifact.japicmp", true) ? module -> module : null);
    }

    private InferredArtifactQualityModule(SequencedSet<Path> configuration,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers,
                                           Pinning pinning,
                                           Function<JApiCmpModule, BuildExecutorModule> japicmp) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.japicmp = japicmp;
    }

    public InferredArtifactQualityModule pinning(Pinning pinning) {
        return new InferredArtifactQualityModule(configuration, repositories, resolvers, pinning, japicmp);
    }

    public InferredArtifactQualityModule japicmp(Function<JApiCmpModule, BuildExecutorModule> japicmp) {
        return new InferredArtifactQualityModule(configuration, repositories, resolvers, pinning, japicmp);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Bind.configuredByProperties(buildExecutor, inherited.sequencedKeySet(), JAPICMP, japicmp,
                BuildStep.locate(configuration, "japicmp.properties"),
                properties -> {
                    for (String key : properties.stringPropertyNames()) {
                        if (!JAPICMP_KEYS.contains(key)) {
                            throw new IllegalArgumentException("Unknown japicmp property: "
                                    + key
                                    + " (expected one of "
                                    + new TreeSet<>(JAPICMP_KEYS)
                                    + ")");
                        }
                    }
                    return new JApiCmpModule(repositories, resolvers).pinning(pinning).config(properties);
                });
    }
}
