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

public class InferredSourceGenerationModule implements BuildExecutorModule {

    public static final String XJC = "xjc";

    public static final String PREPARE = "prepare", TOOL = "tool";

    private static final String FOLDERS = "META-INF/build.jenesis";

    private static final Set<String> XJC_KEYS = Set.of("folders", "package", "catalog", "arguments");

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final Function<XjcModule, BuildExecutorModule> xjc;

    public InferredSourceGenerationModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration, repositories, resolvers, null, enabledBy("jenesis.generate.xjc"));
    }

    private InferredSourceGenerationModule(SequencedSet<Path> configuration,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers,
                                           Pinning pinning,
                                           Function<XjcModule, BuildExecutorModule> xjc) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.xjc = xjc;
    }

    private static <M extends BuildExecutorModule> Function<M, BuildExecutorModule> enabledBy(String property) {
        return Boolean.parseBoolean(System.getProperty(property, "true")) ? module -> module : null;
    }

    public InferredSourceGenerationModule pinning(Pinning pinning) {
        return new InferredSourceGenerationModule(configuration, repositories, resolvers, pinning, xjc);
    }

    public InferredSourceGenerationModule xjc(Function<XjcModule, BuildExecutorModule> xjc) {
        return new InferredSourceGenerationModule(configuration, repositories, resolvers, pinning, xjc);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        SequencedProperties properties = read(XJC, XJC_KEYS, xjc);
        if (properties != null) {
            generate(buildExecutor, inherited.sequencedKeySet(), XJC,
                    prepare(properties, XjcModule.FOLDER,
                            Set.of(XjcModule.SCHEMA, XjcModule.BINDING),
                            named(properties, "catalog", XjcModule.CATALOG)),
                    xjc.apply(new XjcModule(repositories, resolvers)
                            .pinning(pinning)
                            .packageName(properties.value("package"))
                            .arguments(words(properties, "arguments"))));
        }
    }

    private SequencedProperties read(String tool, Set<String> keys, Function<?, BuildExecutorModule> configurator)
            throws IOException {
        Path file = BuildStep.locate(configuration, tool + ".properties");
        if (configurator == null || file == null) {
            return null;
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String key : properties.stringPropertyNames()) {
            if (!keys.contains(key)) {
                throw new IllegalArgumentException("Unknown " + tool + " property: " + key
                        + " (expected one of " + new TreeSet<>(keys) + ")");
            }
        }
        return properties;
    }

    private static void generate(BuildExecutor buildExecutor,
                                 SequencedSet<String> inputs,
                                 String name,
                                 Bind prepare,
                                 BuildExecutorModule tool) {
        if (tool == null) {
            return;
        }
        buildExecutor.addModule(name, (nested, inherited) -> {
            nested.addStep(PREPARE, prepare, inherited.sequencedKeySet());
            SequencedSet<String> toolInputs = new LinkedHashSet<>();
            toolInputs.add(PREPARE);
            toolInputs.addAll(inherited.sequencedKeySet());
            nested.addModule(TOOL, tool, toolInputs);
        }, inputs);
    }

    private static Bind prepare(SequencedProperties properties,
                                String folder,
                                Set<String> extensions,
                                SequencedMap<String, String> named) {
        List<String> folders = properties.entries("folders");
        if (folders != null && folders.isEmpty()) {
            throw new IllegalArgumentException("No folder listed for folders in the generator configuration");
        }
        SequencedMap<Path, Path> paths = new LinkedHashMap<>();
        for (String source : folders == null ? List.of(FOLDERS) : folders) {
            for (String root : List.of(BuildStep.SOURCES, BuildStep.RESOURCES)) {
                paths.put(Path.of(root + source), Path.of(folder));
                named.forEach((file, customary) -> paths.put(
                        Path.of(root + source + "/" + file),
                        Path.of(folder + customary + extension(file))));
            }
        }
        return new Bind(paths).extensions(extensions);
    }

    private static SequencedMap<String, String> named(SequencedProperties properties, String key, String customary) {
        SequencedMap<String, String> named = new LinkedHashMap<>();
        String value = properties.value(key);
        if (value != null) {
            named.put(value, customary);
        }
        return named;
    }

    private static String extension(String file) {
        int dot = file.lastIndexOf('.');
        return dot < 0 ? "" : file.substring(dot);
    }

    private static List<String> words(SequencedProperties properties, String key) {
        String value = properties.value(key);
        return value == null ? List.of() : List.of(value.split("\\s+"));
    }
}
