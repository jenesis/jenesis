package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
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
    private final Pinning pinning;
    private final JApiCmpModule japicmpModule;
    private final Function<JApiCmpModule, BuildExecutorModule> japicmp;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredArtifactQualityModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration, null, new JApiCmpModule(repositories, resolvers),
             value -> value,
             Collections.emptyNavigableMap());
    }

    public static InferredArtifactQualityModule ofEnvironment(Environment environment,
                                                              SequencedSet<Path> configuration,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
        InferredArtifactQualityModule module = new InferredArtifactQualityModule(configuration, null, JApiCmpModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                Collections.emptyNavigableMap());
        Boolean japicmp = environment.flagOrNull("artifact.japicmp");
        if (japicmp != null) {
            module = module.japicmp(japicmp ? value -> value : null);
        }
        return module;
    }

    private InferredArtifactQualityModule(SequencedSet<Path> configuration,
                                          Pinning pinning,
                                          JApiCmpModule japicmpModule,
                                          Function<JApiCmpModule, BuildExecutorModule> japicmp,
                                          SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.japicmpModule = japicmpModule;
        this.japicmp = japicmp;
        this.custom = custom;
    }

    public InferredArtifactQualityModule pinning(Pinning pinning) {
        return new InferredArtifactQualityModule(configuration, pinning, japicmpModule, japicmp, custom);
    }

    public InferredArtifactQualityModule japicmp(Function<JApiCmpModule, BuildExecutorModule> japicmp) {
        return new InferredArtifactQualityModule(configuration, pinning, japicmpModule, japicmp, custom);
    }

    public InferredArtifactQualityModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredArtifactQualityModule(configuration, pinning, japicmpModule, japicmp, custom);
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
                    return japicmpModule.pinning(pinning).config(properties);
                });
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}
