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

public class InferredByteCodeQualityModule implements BuildExecutorModule {

    public static final String SPOTBUGS = "spotbugs", FORBIDDENAPIS = "forbiddenapis", JDEPS = "jdeps",
            JDEPRSCAN = "jdeprscan";

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final SpotBugsModule spotbugsModule;
    private final Function<SpotBugsModule, BuildExecutorModule> spotbugs;
    private final ForbiddenApisModule forbiddenapisModule;
    private final Function<ForbiddenApisModule, BuildExecutorModule> forbiddenapis;
    private final JDepsModule jdepsModule;
    private final Function<JDepsModule, BuildExecutorModule> jdeps;
    private final JDeprScanModule jdeprscanModule;
    private final Function<JDeprScanModule, BuildExecutorModule> jdeprscan;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredByteCodeQualityModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration,
                null,
                new SpotBugsModule(repositories, resolvers),
                value -> value,
                new ForbiddenApisModule(repositories, resolvers),
                value -> value,
                new JDepsModule(),
                value -> value,
                new JDeprScanModule(),
                value -> value,
                Collections.emptyNavigableMap());
    }

    public static InferredByteCodeQualityModule ofEnvironment(Environment environment,
                                                              SequencedSet<Path> configuration,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
        InferredByteCodeQualityModule module = new InferredByteCodeQualityModule(configuration,
                null,
                SpotBugsModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                ForbiddenApisModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                JDepsModule.ofEnvironment(environment),
                value -> value,
                JDeprScanModule.ofEnvironment(environment),
                value -> value,
                Collections.emptyNavigableMap());
        if (Enforcement.ofEnvironment(environment, "validator.spotbugs") == Enforcement.IGNORE) {
            module = module.spotbugs(null);
        }
        if (Enforcement.ofEnvironment(environment, "validator.forbiddenapis") == Enforcement.IGNORE) {
            module = module.forbiddenapis(null);
        }
        if (Enforcement.ofEnvironment(environment, "validator.jdeps") == Enforcement.IGNORE) {
            module = module.jdeps(null);
        }
        if (Enforcement.ofEnvironment(environment, "validator.jdeprscan") == Enforcement.IGNORE) {
            module = module.jdeprscan(null);
        }
        return module;
    }

    private InferredByteCodeQualityModule(SequencedSet<Path> configuration,
                                          Pinning pinning,
                                          SpotBugsModule spotbugsModule,
                                          Function<SpotBugsModule, BuildExecutorModule> spotbugs,
                                          ForbiddenApisModule forbiddenapisModule,
                                          Function<ForbiddenApisModule, BuildExecutorModule> forbiddenapis,
                                          JDepsModule jdepsModule,
                                          Function<JDepsModule, BuildExecutorModule> jdeps,
                                          JDeprScanModule jdeprscanModule,
                                          Function<JDeprScanModule, BuildExecutorModule> jdeprscan,
                                          SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.spotbugsModule = spotbugsModule;
        this.spotbugs = spotbugs;
        this.forbiddenapisModule = forbiddenapisModule;
        this.forbiddenapis = forbiddenapis;
        this.jdepsModule = jdepsModule;
        this.jdeps = jdeps;
        this.jdeprscanModule = jdeprscanModule;
        this.jdeprscan = jdeprscan;
        this.custom = custom;
    }

    public InferredByteCodeQualityModule pinning(Pinning pinning) {
        return new InferredByteCodeQualityModule(configuration,
                pinning,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                custom);
    }

    public InferredByteCodeQualityModule spotbugs(Function<SpotBugsModule, BuildExecutorModule> spotbugs) {
        return new InferredByteCodeQualityModule(configuration,
                pinning,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                custom);
    }

    public InferredByteCodeQualityModule forbiddenapis(Function<ForbiddenApisModule, BuildExecutorModule> forbiddenapis) {
        return new InferredByteCodeQualityModule(configuration,
                pinning,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                custom);
    }

    public InferredByteCodeQualityModule jdeps(Function<JDepsModule, BuildExecutorModule> jdeps) {
        return new InferredByteCodeQualityModule(configuration,
                pinning,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                custom);
    }

    public InferredByteCodeQualityModule jdeprscan(Function<JDeprScanModule, BuildExecutorModule> jdeprscan) {
        return new InferredByteCodeQualityModule(configuration,
                pinning,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                custom);
    }

    public InferredByteCodeQualityModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredByteCodeQualityModule(configuration,
                pinning,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                custom);
    }

    public InferredByteCodeQualityModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredByteCodeQualityModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Bind.configured(buildExecutor,
                inherited.sequencedKeySet(),
                SPOTBUGS,
                spotbugs,
                SpotBugsModule.configurationFile(configuration),
                () -> spotbugsModule.pinning(pinning));
        Path forbiddenapisFile = ForbiddenApisModule.configurationFile(configuration);
        Bind.configured(buildExecutor,
                inherited.sequencedKeySet(),
                FORBIDDENAPIS,
                forbiddenapis,
                forbiddenapisFile,
                forbiddenapis == null ? Collections.emptyNavigableSet() : ForbiddenApisModule.siblings(forbiddenapisFile),
                () -> forbiddenapisModule.pinning(pinning));
        Bind.configured(buildExecutor,
                inherited.sequencedKeySet(),
                JDEPS,
                jdeps,
                JDepsModule.configurationFile(configuration),
                () -> jdepsModule);
        Bind.configured(buildExecutor,
                inherited.sequencedKeySet(),
                JDEPRSCAN,
                jdeprscan,
                JDeprScanModule.configurationFile(configuration),
                () -> jdeprscanModule);
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}
