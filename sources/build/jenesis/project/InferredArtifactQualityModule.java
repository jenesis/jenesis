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

    public static final String JAPICMP = "japicmp", REVAPI = "revapi";
    private static final String BASELINE = "baseline", SEVERITY = "severity", ANALYSIS = "configuration";
    private static final Set<String> JAPICMP_KEYS = Set.of(BASELINE,
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
    private final RevapiModule revapiModule;
    private final Function<RevapiModule, BuildExecutorModule> revapi;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredArtifactQualityModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration,
                null,
                new JApiCmpModule(repositories, resolvers),
                value -> value,
                new RevapiModule(repositories, resolvers),
                value -> value,
                Collections.emptyNavigableMap());
    }

    public static InferredArtifactQualityModule ofEnvironment(Environment environment,
                                                              SequencedSet<Path> configuration,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
        InferredArtifactQualityModule module = new InferredArtifactQualityModule(configuration,
                null,
                JApiCmpModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                RevapiModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                Collections.emptyNavigableMap());
        Boolean japicmp = environment.flagOrNull("artifact.japicmp");
        if (japicmp != null) {
            module = module.japicmp(japicmp ? value -> value : null);
        }
        if (Enforcement.ofEnvironment(environment, "artifact.revapi") == Enforcement.IGNORE) {
            module = module.revapi(null);
        }
        return module;
    }

    private InferredArtifactQualityModule(SequencedSet<Path> configuration,
                                          Pinning pinning,
                                          JApiCmpModule japicmpModule,
                                          Function<JApiCmpModule, BuildExecutorModule> japicmp,
                                          RevapiModule revapiModule,
                                          Function<RevapiModule, BuildExecutorModule> revapi,
                                          SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.japicmpModule = japicmpModule;
        this.japicmp = japicmp;
        this.revapiModule = revapiModule;
        this.revapi = revapi;
        this.custom = custom;
    }

    public InferredArtifactQualityModule pinning(Pinning pinning) {
        return new InferredArtifactQualityModule(configuration,
                pinning,
                japicmpModule,
                japicmp,
                revapiModule,
                revapi,
                custom);
    }

    public InferredArtifactQualityModule japicmp(Function<JApiCmpModule, BuildExecutorModule> japicmp) {
        return new InferredArtifactQualityModule(configuration,
                pinning,
                japicmpModule,
                japicmp,
                revapiModule,
                revapi,
                custom);
    }

    public InferredArtifactQualityModule revapi(Function<RevapiModule, BuildExecutorModule> revapi) {
        return new InferredArtifactQualityModule(configuration,
                pinning,
                japicmpModule,
                japicmp,
                revapiModule,
                revapi,
                custom);
    }

    public InferredArtifactQualityModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredArtifactQualityModule(configuration,
                pinning,
                japicmpModule,
                japicmp,
                revapiModule,
                revapi,
                custom);
    }

    public InferredArtifactQualityModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredArtifactQualityModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path described = BuildStep.locate(new LinkedHashSet<>(inherited.values()), BuildStep.MODULE);
        boolean test = described != null && SequencedProperties.ofFiles(described).getProperty("test") != null;
        Path japicmpFile = BuildStep.locate(configuration, "japicmp.properties");
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                JAPICMP,
                test ? null : japicmp,
                japicmpFile,
                properties -> compared(japicmpModule.pinning(pinning), japicmpFile, properties));
        Path revapiFile = RevapiModule.configurationFile(configuration);
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                REVAPI,
                test ? null : revapi,
                revapiFile,
                properties -> analysed(revapiModule.pinning(pinning), revapiFile, properties));
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    private static JApiCmpModule compared(JApiCmpModule module, Path file, SequencedProperties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!JAPICMP_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown japicmp property: "
                        + key
                        + " in "
                        + file
                        + " (expected one of "
                        + new TreeSet<>(JAPICMP_KEYS)
                        + ")");
            }
        }
        String access = properties.value("access");
        List<String> include = properties.entries("include"), exclude = properties.entries("exclude"),
                formats = properties.entries("format");
        if (access != null) {
            module = module.access(named(JApiCmpModule.Access.class, file, "access", access));
        }
        if (include != null) {
            module = module.include(include);
        }
        if (exclude != null) {
            module = module.exclude(exclude);
        }
        if (formats != null) {
            Set<JApiCmpModule.Format> selected = EnumSet.noneOf(JApiCmpModule.Format.class);
            for (String format : formats) {
                selected.add(named(JApiCmpModule.Format.class, file, "format", format));
            }
            module = module.formats(selected);
        }
        Set<JApiCmpModule.Failure> errorOn = EnumSet.noneOf(JApiCmpModule.Failure.class);
        for (JApiCmpModule.Failure failure : JApiCmpModule.Failure.values()) {
            if (properties.flag("error-on-" + spelt(failure))) {
                errorOn.add(failure);
            }
        }
        return module.baseline(properties.value(BASELINE))
                .ignoreMissingClasses(properties.flag("ignore-missing-classes", true))
                .onlyIncompatible(properties.flag("only-incompatible"))
                .onlyModified(properties.flag("only-modified"))
                .semanticVersioning(properties.flag("semantic-versioning"))
                .errorOn(errorOn);
    }

    private static RevapiModule analysed(RevapiModule module, Path file, SequencedProperties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.equals(BASELINE) && !key.equals(SEVERITY) && !key.equals(ANALYSIS)) {
                throw new IllegalArgumentException(file + " names " + key + ", but takes " + BASELINE
                        + "=<groupId>/<artifactId>[/<version>], " + SEVERITY + "=<"
                        + String.join("|", Arrays.stream(RevapiModule.Severity.values())
                                .map(InferredArtifactQualityModule::spelt)
                                .toList())
                        + "> and " + ANALYSIS + "=<a JSON file of revapi's own configuration beside it>");
            }
        }
        String severity = properties.value(SEVERITY), name = properties.value(ANALYSIS);
        if (severity != null) {
            module = module.severity(named(RevapiModule.Severity.class, file, SEVERITY, severity));
        }
        if (name != null) {
            Path sibling = Path.of(name).normalize();
            if (sibling.isAbsolute() || sibling.startsWith("..")) {
                throw new IllegalArgumentException(file + " names the configuration " + name
                        + ", which is not beside or below it - place the file in the folder of "
                        + RevapiModule.CONFIGURATION);
            }
            Path resolved = file.resolveSibling(sibling);
            if (!Files.isRegularFile(resolved)) {
                throw new IllegalArgumentException(file + " names the configuration " + name
                        + ", which does not exist beside it");
            }
            try {
                module = module.analysis(Files.readString(resolved));
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read the revapi configuration " + resolved, e);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(resolved + ": " + e.getMessage());
            }
        }
        return module.baseline(properties.value(BASELINE));
    }

    private static <E extends Enum<E>> E named(Class<E> type, Path file, String key, String value) {
        for (E constant : type.getEnumConstants()) {
            if (spelt(constant).equals(value)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(file + " names the " + key + " " + value + ", but takes one of "
                + Arrays.stream(type.getEnumConstants()).map(InferredArtifactQualityModule::spelt).toList());
    }

    private static String spelt(Enum<?> constant) {
        return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
