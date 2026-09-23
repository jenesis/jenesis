package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Bind;
import build.jenesis.SequencedProperties;

public class InferredByteCodeQualityModule implements BuildExecutorModule {

    public static final String SPOTBUGS = "spotbugs";

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final SpotBugsModule spotbugsModule;
    private final Function<SpotBugsModule, BuildExecutorModule> spotbugs;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredByteCodeQualityModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration, null, new SpotBugsModule(repositories, resolvers),
             value -> value,
             Collections.emptyNavigableMap());
    }

    public static InferredByteCodeQualityModule ofEnvironment(Environment environment,
                                                              SequencedSet<Path> configuration,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
        InferredByteCodeQualityModule module = new InferredByteCodeQualityModule(configuration, null, SpotBugsModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                Collections.emptyNavigableMap());
        Boolean spotbugs = environment.flagOrNull("validator.spotbugs");
        if (spotbugs != null) {
            module = module.spotbugs(spotbugs ? value -> value : null);
        }
        return module;
    }

    private InferredByteCodeQualityModule(SequencedSet<Path> configuration,
                                          Pinning pinning,
                                          SpotBugsModule spotbugsModule,
                                          Function<SpotBugsModule, BuildExecutorModule> spotbugs,
                                          SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.spotbugsModule = spotbugsModule;
        this.spotbugs = spotbugs;
        this.custom = custom;
    }

    public InferredByteCodeQualityModule pinning(Pinning pinning) {
        return new InferredByteCodeQualityModule(configuration, pinning, spotbugsModule, spotbugs, custom);
    }

    public InferredByteCodeQualityModule spotbugs(Function<SpotBugsModule, BuildExecutorModule> spotbugs) {
        return new InferredByteCodeQualityModule(configuration, pinning, spotbugsModule, spotbugs, custom);
    }

    public InferredByteCodeQualityModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredByteCodeQualityModule(configuration, pinning, spotbugsModule, spotbugs, custom);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        Bind.configured(buildExecutor,
                inherited.sequencedKeySet(),
                SPOTBUGS,
                spotbugs,
                SpotBugsModule.configurationFile(configuration),
                () -> spotbugsModule.pinning(pinning));
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}
