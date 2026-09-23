package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Bind;
import build.jenesis.SequencedProperties;

public class InferredTestObservationModule implements BuildExecutorModule {

    public static final String TEST = "test", MUTATE = "mutate";
    private static final String FRAMEWORK = "framework";
    private static final Set<String> TEST_KEYS = Set.of(FRAMEWORK);

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final String moduleName;
    private final TestModule testModule;
    private final JaCoCoModule jacocoModule;
    private final PiTestModule pitestModule;
    private final Function<TestModule, BuildExecutorModule> test;
    private final Function<JaCoCoModule, BuildExecutorModule> jacoco;
    private final Function<NativeImageAgentModule, BuildExecutorModule> nativeImage;
    private final Function<PiTestModule, BuildExecutorModule> pitest;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredTestObservationModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration, null, PathPlacement.CLASS_PATH,
             null,
             new TestModule(repositories, resolvers),
             new JaCoCoModule(repositories, resolvers),
             new PiTestModule(repositories, resolvers),
             value -> value,
             value -> value,
             value -> value,
             value -> value,
             Collections.emptyNavigableMap());
    }

    public static InferredTestObservationModule ofEnvironment(Environment environment,
                                                              SequencedSet<Path> configuration,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
        InferredTestObservationModule module = new InferredTestObservationModule(configuration, null, PathPlacement.CLASS_PATH,
                null,
                TestModule.ofEnvironment(environment, repositories, resolvers),
                JaCoCoModule.ofEnvironment(environment, repositories, resolvers),
                PiTestModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap());
        Boolean jacoco = environment.flagOrNull("observe.jacoco");
        if (jacoco != null) {
            module = module.jacoco(jacoco ? value -> value : null);
        }
        Boolean nativeImage = environment.flagOrNull("observe.native");
        if (nativeImage != null) {
            module = module.nativeImage(nativeImage ? value -> value : null);
        }
        Boolean pitest = environment.flagOrNull("mutate.pitest");
        if (pitest != null) {
            module = module.pitest(pitest ? value -> value : null);
        }
        return module;
    }

    private static TestFramework declaredFramework(Path file) throws IOException {
        if (file == null) {
            return null;
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String key : properties.stringPropertyNames()) {
            if (!TEST_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown test property: " + key);
            }
        }
        String framework = properties.value(FRAMEWORK);
        return framework == null ? null : TestFramework.named(framework);
    }

    private InferredTestObservationModule(SequencedSet<Path> configuration,
                                          Pinning pinning,
                                          PathPlacement pathPlacement,
                                          String moduleName,
                                          TestModule testModule,
                                          JaCoCoModule jacocoModule,
                                          PiTestModule pitestModule,
                                          Function<TestModule, BuildExecutorModule> test,
                                          Function<JaCoCoModule, BuildExecutorModule> jacoco,
                                          Function<NativeImageAgentModule, BuildExecutorModule> nativeImage,
                                          Function<PiTestModule, BuildExecutorModule> pitest,
                                          SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.moduleName = moduleName;
        this.testModule = testModule;
        this.jacocoModule = jacocoModule;
        this.pitestModule = pitestModule;
        this.test = test;
        this.jacoco = jacoco;
        this.nativeImage = nativeImage;
        this.pitest = pitest;
        this.custom = custom;
    }

    public InferredTestObservationModule pinning(Pinning pinning) {
        return new InferredTestObservationModule(configuration, pinning, pathPlacement, moduleName, testModule,
                jacocoModule, pitestModule, test, jacoco, nativeImage, pitest, custom);
    }

    public InferredTestObservationModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredTestObservationModule(configuration, pinning, pathPlacement, moduleName, testModule,
                jacocoModule, pitestModule, test, jacoco, nativeImage, pitest, custom);
    }

    public InferredTestObservationModule moduleName(String moduleName) {
        return new InferredTestObservationModule(configuration, pinning, pathPlacement, moduleName, testModule,
                jacocoModule, pitestModule, test, jacoco, nativeImage, pitest, custom);
    }

    public InferredTestObservationModule test(Function<TestModule, BuildExecutorModule> test) {
        return new InferredTestObservationModule(configuration, pinning, pathPlacement, moduleName, testModule,
                jacocoModule, pitestModule, test, jacoco, nativeImage, pitest, custom);
    }

    public InferredTestObservationModule jacoco(Function<JaCoCoModule, BuildExecutorModule> jacoco) {
        return new InferredTestObservationModule(configuration, pinning, pathPlacement, moduleName, testModule,
                jacocoModule, pitestModule, test, jacoco, nativeImage, pitest, custom);
    }

    public InferredTestObservationModule nativeImage(Function<NativeImageAgentModule, BuildExecutorModule> nativeImage) {
        return new InferredTestObservationModule(configuration, pinning, pathPlacement, moduleName, testModule,
                jacocoModule, pitestModule, test, jacoco, nativeImage, pitest, custom);
    }

    public InferredTestObservationModule pitest(Function<PiTestModule, BuildExecutorModule> pitest) {
        return new InferredTestObservationModule(configuration, pinning, pathPlacement, moduleName, testModule,
                jacocoModule, pitestModule, test, jacoco, nativeImage, pitest, custom);
    }

    public InferredTestObservationModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredTestObservationModule(configuration, pinning, pathPlacement, moduleName, testModule,
                jacocoModule, pitestModule, test, jacoco, nativeImage, pitest, custom);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        SequencedMap<String, BuildExecutorModule> reports = new LinkedHashMap<>();
        List<ObservabilityEngine> engines = new ArrayList<>();
        if (jacoco != null && BuildStep.locate(configuration, "jacoco.properties") != null) {
            BuildExecutorModule report = jacoco.apply(jacocoModule.pinning(pinning));
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
            TestModule module = testModule.observe(engines)
                    .pinning(pinning)
                    .pathPlacement(pathPlacement)
                    .moduleName(moduleName);
            TestFramework declared = declaredFramework(BuildStep.locate(configuration, "test.properties"));
            BuildExecutorModule executed = test.apply(declared == null ? module : module.framework(declared));
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
                properties -> pitestModule.pinning(pinning).config(properties));
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}
