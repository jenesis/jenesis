package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Bind;

public class InferredTestObservationModule implements BuildExecutorModule {

    public static final String TEST = "test", MUTATE = "mutate";

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final String moduleName;
    private final Function<TestModule, BuildExecutorModule> test;
    private final Function<JaCoCoModule, BuildExecutorModule> jacoco;
    private final Function<NativeImageAgentModule, BuildExecutorModule> nativeImage;
    private final Function<PiTestModule, BuildExecutorModule> pitest;

    public InferredTestObservationModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration,
                repositories,
                resolvers,
                null,
                PathPlacement.CLASS_PATH,
                null,
                module -> module,
                enabledBy("jenesis.observe.jacoco"),
                enabledBy("jenesis.observe.native"),
                enabledBy("jenesis.mutate.pitest"));
    }

    private InferredTestObservationModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers,
                                          Pinning pinning,
                                          PathPlacement pathPlacement,
                                          String moduleName,
                                          Function<TestModule, BuildExecutorModule> test,
                                          Function<JaCoCoModule, BuildExecutorModule> jacoco,
                                          Function<NativeImageAgentModule, BuildExecutorModule> nativeImage,
                                          Function<PiTestModule, BuildExecutorModule> pitest) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.moduleName = moduleName;
        this.test = test;
        this.jacoco = jacoco;
        this.nativeImage = nativeImage;
        this.pitest = pitest;
    }

    private static <M extends BuildExecutorModule> Function<M, BuildExecutorModule> enabledBy(String property) {
        return Boolean.parseBoolean(System.getProperty(property, "true")) ? module -> module : null;
    }

    public InferredTestObservationModule pinning(Pinning pinning) {
        return new InferredTestObservationModule(configuration, repositories, resolvers, pinning,
                pathPlacement, moduleName, test, jacoco, nativeImage, pitest);
    }

    public InferredTestObservationModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredTestObservationModule(configuration, repositories, resolvers, pinning,
                pathPlacement, moduleName, test, jacoco, nativeImage, pitest);
    }

    public InferredTestObservationModule moduleName(String moduleName) {
        return new InferredTestObservationModule(configuration, repositories, resolvers, pinning,
                pathPlacement, moduleName, test, jacoco, nativeImage, pitest);
    }

    public InferredTestObservationModule test(Function<TestModule, BuildExecutorModule> test) {
        return new InferredTestObservationModule(configuration, repositories, resolvers, pinning,
                pathPlacement, moduleName, test, jacoco, nativeImage, pitest);
    }

    public InferredTestObservationModule jacoco(Function<JaCoCoModule, BuildExecutorModule> jacoco) {
        return new InferredTestObservationModule(configuration, repositories, resolvers, pinning,
                pathPlacement, moduleName, test, jacoco, nativeImage, pitest);
    }

    public InferredTestObservationModule nativeImage(Function<NativeImageAgentModule, BuildExecutorModule> nativeImage) {
        return new InferredTestObservationModule(configuration, repositories, resolvers, pinning,
                pathPlacement, moduleName, test, jacoco, nativeImage, pitest);
    }

    public InferredTestObservationModule pitest(Function<PiTestModule, BuildExecutorModule> pitest) {
        return new InferredTestObservationModule(configuration, repositories, resolvers, pinning,
                pathPlacement, moduleName, test, jacoco, nativeImage, pitest);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        SequencedMap<String, BuildExecutorModule> reports = new LinkedHashMap<>();
        List<ObservabilityEngine> engines = new ArrayList<>();
        if (jacoco != null && BuildStep.locate(configuration, "jacoco.properties") != null) {
            BuildExecutorModule report = jacoco.apply(new JaCoCoModule(repositories, resolvers).pinning(pinning));
            if (report != null) {
                JaCoCo engine = new JaCoCo();
                engines.add(engine);
                reports.put(engine.name(), report);
            }
        }
        if (nativeImage != null && BuildStep.locate(configuration, "graal.properties") != null) {
            BuildExecutorModule report = nativeImage.apply(new NativeImageAgentModule());
            if (report != null) {
                NativeImageAgent engine = new NativeImageAgent();
                engines.add(engine);
                reports.put(engine.name(), report);
            }
        }
        if (test != null) {
            BuildExecutorModule executed = test.apply(new TestModule(repositories, resolvers)
                    .observe(engines)
                    .pinning(pinning)
                    .pathPlacement(pathPlacement)
                    .moduleName(moduleName));
            if (executed != null) {
                buildExecutor.addModule(TEST, executed, inherited.sequencedKeySet());
                SequencedSet<String> reportInputs = new LinkedHashSet<>();
                reportInputs.add(TEST);
                reportInputs.addAll(inherited.sequencedKeySet());
                reports.forEach((name, report) -> buildExecutor.addModule(name, report, reportInputs));
            }
        }
        Bind.configuredByProperties(buildExecutor, inherited.sequencedKeySet(), MUTATE, pitest,
                BuildStep.locate(configuration, "pitest.properties"),
                properties -> new PiTestModule(repositories, resolvers).pinning(pinning).config(properties));
    }
}
