package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;

public class InferredTestObservationModule implements BuildExecutorModule {

    public static final String TEST = "test", MUTATE = "mutate";
    private static final String FRAMEWORK = "framework", ENGINES = "engines", SCAN = "scan";
    private static final String MINIMUM = "minimum.", MAXIMUM_MISSED = "maximum.missed.";
    private static final Set<String> TEST_KEYS = Set.of(FRAMEWORK, ENGINES, SCAN);

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
    private final UnaryOperator<Jfr> jfr;
    private final Function<PiTestModule, BuildExecutorModule> pitest;
    private final SequencedMap<String, BuildExecutorModule> custom;
    private final SequencedSet<String> untested;

    public InferredTestObservationModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration,
                null,
                PathPlacement.CLASS_PATH,
                null,
                new TestModule(repositories, resolvers),
                new JaCoCoModule(repositories, resolvers),
                new PiTestModule(repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet());
    }

    public static InferredTestObservationModule ofEnvironment(Environment environment,
                                                              SequencedSet<Path> configuration,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
        InferredTestObservationModule module = new InferredTestObservationModule(configuration,
                null,
                PathPlacement.CLASS_PATH,
                null,
                TestModule.ofEnvironment(environment, repositories, resolvers),
                JaCoCoModule.ofEnvironment(environment, repositories, resolvers),
                PiTestModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet());
        Boolean jacoco = environment.flagOrNull("observe.jacoco");
        if (jacoco != null) {
            module = module.jacoco(jacoco ? value -> value : null);
        }
        Boolean nativeImage = environment.flagOrNull("observe.native");
        if (nativeImage != null) {
            module = module.nativeImage(nativeImage ? value -> value : null);
        }
        Boolean jfr = environment.flagOrNull("observe.jfr");
        if (jfr != null) {
            module = module.jfr(jfr ? value -> value : null);
        }
        Boolean pitest = environment.flagOrNull("mutate.pitest");
        if (pitest != null) {
            module = module.pitest(pitest ? value -> value : null);
        }
        return module;
    }

    private static JaCoCoModule limited(JaCoCoModule module, Path file) throws IOException {
        SequencedMap<JaCoCoModule.Counter, Double> minimum = new LinkedHashMap<>();
        SequencedMap<JaCoCoModule.Counter, Long> maximumMissed = new LinkedHashMap<>();
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String key : properties.stringPropertyNames()) {
            boolean ratio = key.startsWith(MINIMUM);
            String name = ratio ? key.substring(MINIMUM.length())
                    : key.startsWith(MAXIMUM_MISSED) ? key.substring(MAXIMUM_MISSED.length())
                    : null;
            JaCoCoModule.Counter counter = name == null ? null : Arrays.stream(JaCoCoModule.Counter.values())
                    .filter(candidate -> candidate.name().toLowerCase(Locale.ROOT).equals(name))
                    .findFirst()
                    .orElse(null);
            if (counter == null) {
                throw new IllegalArgumentException("Unknown key " + key + " in " + file + ", expected " + MINIMUM
                        + "<counter> or " + MAXIMUM_MISSED + "<counter> for a counter of "
                        + Arrays.stream(JaCoCoModule.Counter.values())
                                .map(candidate -> candidate.name().toLowerCase(Locale.ROOT))
                                .toList());
            }
            String value = properties.value(key, "");
            try {
                if (ratio) {
                    minimum.put(counter, Double.parseDouble(value));
                } else {
                    maximumMissed.put(counter, Long.parseLong(value));
                }
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException("The value " + value + " of " + key + " in " + file + " is "
                        + (ratio
                                ? "no covered ratio, expected a number from 0 to 1, such as 0.8"
                                : "no count of missed items, expected a whole number of at least 0"));
            }
        }
        return module.minimum(minimum).maximumMissed(maximumMissed);
    }

    private static TestModule declared(TestModule module, Path file) throws IOException {
        if (file == null) {
            return module;
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String key : properties.stringPropertyNames()) {
            if (!TEST_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown test property: " + key);
            }
        }
        String framework = properties.value(FRAMEWORK), engines = properties.value(ENGINES);
        List<String> scan = properties.entries(SCAN);
        TestModule declared = framework == null ? module : module.framework(TestFramework.named(framework));
        declared = scan == null ? declared : declared.scan(scan);
        return engines == null ? declared : declared.engines(engines);
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
                                          UnaryOperator<Jfr> jfr,
                                          Function<PiTestModule, BuildExecutorModule> pitest,
                                          SequencedMap<String, BuildExecutorModule> custom,
                                          SequencedSet<String> untested) {
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
        this.jfr = jfr;
        this.pitest = pitest;
        this.custom = custom;
        this.untested = untested;
    }

    public InferredTestObservationModule pinning(Pinning pinning) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule moduleName(String moduleName) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule test(Function<TestModule, BuildExecutorModule> test) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule jacoco(Function<JaCoCoModule, BuildExecutorModule> jacoco) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule nativeImage(Function<NativeImageAgentModule, BuildExecutorModule> nativeImage) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule jfr(UnaryOperator<Jfr> jfr) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule pitest(Function<PiTestModule, BuildExecutorModule> pitest) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule untested(SequencedSet<String> untested) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                jacocoModule,
                pitestModule,
                test,
                jacoco,
                nativeImage,
                jfr,
                pitest,
                custom,
                untested);
    }

    public InferredTestObservationModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredTestObservationModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path described = BuildStep.locate(new LinkedHashSet<>(inherited.values()), BuildStep.MODULE);
        SequencedProperties description = described == null ? null : SequencedProperties.ofFiles(described);
        if (description != null && (description.getProperty("test") == null || description.flag("abstract"))) {
            return;
        }
        SequencedMap<String, BuildExecutorModule> reports = new LinkedHashMap<>();
        List<ObservabilityEngine> engines = new ArrayList<>();
        Path coverage = BuildStep.locate(configuration, "jacoco.properties");
        if (jacoco != null && coverage != null) {
            BuildExecutorModule report = jacoco.apply(limited(jacocoModule.pinning(pinning), coverage));
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
        Path recording = BuildStep.locate(configuration, "jfr.properties");
        if (jfr != null && recording != null) {
            Jfr engine = jfr.apply(Jfr.ofFile(recording));
            if (engine != null) {
                engines.add(engine);
            }
        }
        if (test != null) {
            TestModule module = testModule.observe(engines)
                    .pinning(pinning)
                    .pathPlacement(pathPlacement)
                    .moduleName(moduleName == null && description != null ? description.getProperty("module") : moduleName);
            List<String> scanned = description == null ? null : description.entries(SCAN);
            if (scanned != null) {
                module = module.scan(scanned);
            }
            BuildExecutorModule executed = test.apply(declared(module, BuildStep.locate(configuration, "test.properties")));
            if (executed != null) {
                buildExecutor.addModule(TEST, executed, inherited.sequencedKeySet().stream().filter(identity ->
                        untested.stream().noneMatch(input -> identity.equals(PREVIOUS + input)
                                || identity.startsWith(PREVIOUS + input + "/"))));
                SequencedSet<String> reportInputs = new LinkedHashSet<>();
                reportInputs.add(TEST);
                reportInputs.addAll(inherited.sequencedKeySet());
                reports.forEach((name, report) -> buildExecutor.addModule(name, report, reportInputs));
            }
        }
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                MUTATE,
                pitest,
                BuildStep.locate(configuration, "pitest.properties"),
                properties -> pitestModule.pinning(pinning).config(properties));
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}
