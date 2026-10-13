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
    private static final String BUNDLED = "bundled", SIGNATURES = "signatures", SUPPRESS = "suppress",
            INCLUDES = "includes", EXCLUDES = "excludes", WARN = "warn", ALLOW_MISSING_CLASSES = "allowmissingclasses",
            IGNORE_SIGNATURES_OF_MISSING_CLASSES = "ignoresignaturesofmissingclasses";
    private static final String ACCEPT = "accept", REMOVAL = "removal", RELEASE = "release";
    private static final Set<String> FORBIDDENAPIS_KEYS = Set.of(BUNDLED, SIGNATURES, SUPPRESS, INCLUDES, EXCLUDES, WARN,
            ALLOW_MISSING_CLASSES, IGNORE_SIGNATURES_OF_MISSING_CLASSES);

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
        forbidding(buildExecutor,
                inherited.sequencedKeySet(),
                forbiddenapis,
                forbiddenapisModule.pinning(pinning),
                ForbiddenApisModule.configurationFile(configuration));
        Path jdepsFile = JDepsModule.configurationFile(configuration);
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                JDEPS,
                jdeps,
                jdepsFile,
                properties -> accepting(jdepsModule, jdepsFile, properties));
        Path jdeprscanFile = JDeprScanModule.configurationFile(configuration);
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                JDEPRSCAN,
                jdeprscan,
                jdeprscanFile,
                properties -> scanning(jdeprscanModule, jdeprscanFile, properties));
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    private static void forbidding(BuildExecutor buildExecutor,
                                   SequencedSet<String> inputs,
                                   Function<ForbiddenApisModule, BuildExecutorModule> forbiddenapis,
                                   ForbiddenApisModule module,
                                   Path file) throws IOException {
        if (forbiddenapis == null || file == null) {
            return;
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String key : properties.stringPropertyNames()) {
            if (!FORBIDDENAPIS_KEYS.contains(key)) {
                throw new IllegalArgumentException(file + " names " + key + ", but takes "
                        + String.join(", ", new TreeSet<>(FORBIDDENAPIS_KEYS)));
            }
        }
        List<Path> signatures = new ArrayList<>();
        for (String name : Objects.requireNonNullElse(properties.entries(SIGNATURES), List.<String>of())) {
            Path signature = Path.of(name).normalize();
            if (signature.isAbsolute() || signature.startsWith("..")) {
                throw new IllegalArgumentException(file + " names the signatures " + name
                        + ", which is not beside or below it - place the file in the folder of " + file.getFileName());
            }
            if (!Files.isRegularFile(file.resolveSibling(signature))) {
                throw new IllegalArgumentException(file + " names the signatures " + name
                        + ", which does not exist beside it");
            }
            signatures.add(signature);
        }
        List<String> bundled = Objects.requireNonNullElse(properties.entries(BUNDLED), List.of());
        if (signatures.isEmpty() && bundled.isEmpty()) {
            throw new IllegalArgumentException(file + " names no signatures - set " + BUNDLED
                    + "=<bundled signatures, as jdk-unsafe,jdk-deprecated> or " + SIGNATURES
                    + "=<files of signatures beside it>");
        }
        BuildExecutorModule configured = forbiddenapis.apply(module.bundled(bundled)
                .signatures(signatures)
                .suppress(Objects.requireNonNullElse(properties.entries(SUPPRESS), List.of()))
                .includes(Objects.requireNonNullElse(properties.entries(INCLUDES), List.of()))
                .excludes(Objects.requireNonNullElse(properties.entries(EXCLUDES), List.of()))
                .warn(Objects.requireNonNullElse(properties.entries(WARN), List.of()))
                .allowMissingClasses(properties.flag(ALLOW_MISSING_CLASSES, false))
                .ignoreSignaturesOfMissingClasses(properties.flag(IGNORE_SIGNATURES_OF_MISSING_CLASSES, false)));
        if (configured == null) {
            return;
        }
        buildExecutor.addModule(FORBIDDENAPIS, (nested, inherited) -> {
            SequencedSet<String> toolInputs = new LinkedHashSet<>();
            if (!signatures.isEmpty()) {
                SequencedSet<String> sources = new LinkedHashSet<>();
                for (Path signature : signatures) {
                    String source = SIGNATURES + "-" + sources.size();
                    nested.addSource(source, new Bind(Map.of(Path.of(""), signature)), file.resolveSibling(signature));
                    sources.add(source);
                }
                nested.addStep(SIGNATURES, new Bind(Map.of(Path.of(""), Path.of(""))), sources);
                toolInputs.add(SIGNATURES);
            }
            toolInputs.addAll(inherited.sequencedKeySet());
            nested.addModule("tool", configured, toolInputs);
        }, inputs);
    }

    private static JDepsModule accepting(JDepsModule module, Path file, SequencedProperties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.equals(ACCEPT)) {
                throw new IllegalArgumentException(file + " names " + key + ", but takes " + ACCEPT
                        + "=<the JDK internal classes or packages the classes may use> alone");
            }
        }
        return module.accepted(Objects.requireNonNullElse(properties.entries(ACCEPT), List.of()));
    }

    private static JDeprScanModule scanning(JDeprScanModule module, Path file, SequencedProperties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.equals(REMOVAL) && !key.equals(RELEASE)) {
                throw new IllegalArgumentException(file + " names " + key + ", but takes " + REMOVAL
                        + "=false to report every deprecated API rather than those deprecated for removal, and "
                        + RELEASE + "=<the Java release whose deprecations to check>");
            }
        }
        String release = properties.value(RELEASE);
        if (release != null && !release.matches("[0-9]+")) {
            throw new IllegalArgumentException(file + " names the release " + release
                    + ", but takes a Java release as a number, as 21");
        }
        return module.forRemoval(properties.flag(REMOVAL, true))
                .release(release == null ? null : Integer.valueOf(release));
    }
}
