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

    public static final String XJC = "xjc",
            PROTOC = "protoc",
            AVRO = "avro",
            WSIMPORT = "wsimport",
            OPENAPI = "openapi";

    public static final String PREPARE = "prepare", TOOL = "tool";

    private static final String FOLDERS = "META-INF/build.jenesis";

    private static final Set<String> XJC_KEYS = Set.of("folders", "package", "catalog", "arguments");
    private static final Set<String> PROTOC_KEYS = Set.of("folders", "classifier", "plugins", "arguments");
    private static final Set<String> AVRO_KEYS = Set.of("folders", "arguments");
    private static final Set<String> WSIMPORT_KEYS = Set.of("folders", "package", "location", "catalog", "arguments");
    private static final Set<String> OPENAPI_KEYS = Set.of("folders", "specification", "generator", "package", "sources", "arguments");

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final Function<XjcModule, BuildExecutorModule> xjc;
    private final Function<ProtocModule, BuildExecutorModule> protoc;
    private final Function<AvroModule, BuildExecutorModule> avro;
    private final Function<WsImportModule, BuildExecutorModule> wsimport;
    private final Function<OpenApiModule, BuildExecutorModule> openapi;

    public InferredSourceGenerationModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration, repositories, resolvers, null,
                enabledBy("jenesis.generate.xjc"),
                enabledBy("jenesis.generate.protoc"),
                enabledBy("jenesis.generate.avro"),
                enabledBy("jenesis.generate.wsimport"),
                enabledBy("jenesis.generate.openapi"));
    }

    private InferredSourceGenerationModule(SequencedSet<Path> configuration,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers,
                                           Pinning pinning,
                                           Function<XjcModule, BuildExecutorModule> xjc,
                                           Function<ProtocModule, BuildExecutorModule> protoc,
                                           Function<AvroModule, BuildExecutorModule> avro,
                                           Function<WsImportModule, BuildExecutorModule> wsimport,
                                           Function<OpenApiModule, BuildExecutorModule> openapi) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.xjc = xjc;
        this.protoc = protoc;
        this.avro = avro;
        this.wsimport = wsimport;
        this.openapi = openapi;
    }

    private static <M extends BuildExecutorModule> Function<M, BuildExecutorModule> enabledBy(String property) {
        return Boolean.parseBoolean(System.getProperty(property, "true")) ? module -> module : null;
    }

    public InferredSourceGenerationModule pinning(Pinning pinning) {
        return new InferredSourceGenerationModule(configuration, repositories, resolvers, pinning, xjc, protoc, avro, wsimport, openapi);
    }

    public InferredSourceGenerationModule xjc(Function<XjcModule, BuildExecutorModule> xjc) {
        return new InferredSourceGenerationModule(configuration, repositories, resolvers, pinning, xjc, protoc, avro, wsimport, openapi);
    }

    public InferredSourceGenerationModule protoc(Function<ProtocModule, BuildExecutorModule> protoc) {
        return new InferredSourceGenerationModule(configuration, repositories, resolvers, pinning, xjc, protoc, avro, wsimport, openapi);
    }

    public InferredSourceGenerationModule avro(Function<AvroModule, BuildExecutorModule> avro) {
        return new InferredSourceGenerationModule(configuration, repositories, resolvers, pinning, xjc, protoc, avro, wsimport, openapi);
    }

    public InferredSourceGenerationModule wsimport(Function<WsImportModule, BuildExecutorModule> wsimport) {
        return new InferredSourceGenerationModule(configuration, repositories, resolvers, pinning, xjc, protoc, avro, wsimport, openapi);
    }

    public InferredSourceGenerationModule openapi(Function<OpenApiModule, BuildExecutorModule> openapi) {
        return new InferredSourceGenerationModule(configuration, repositories, resolvers, pinning, xjc, protoc, avro, wsimport, openapi);
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
        properties = read(PROTOC, PROTOC_KEYS, protoc);
        if (properties != null) {
            ProtocModule module = new ProtocModule(repositories, resolvers)
                    .pinning(pinning)
                    .plugins(plugins(properties))
                    .arguments(words(properties, "arguments"));
            String classifier = properties.value("classifier");
            generate(buildExecutor, inherited.sequencedKeySet(), PROTOC,
                    prepare(properties, ProtocModule.FOLDER,
                            Set.of(ProtocModule.DEFINITION),
                            new LinkedHashMap<>()),
                    protoc.apply(classifier == null ? module : module.classifier(classifier)));
        }
        properties = read(AVRO, AVRO_KEYS, avro);
        if (properties != null) {
            generate(buildExecutor, inherited.sequencedKeySet(), AVRO,
                    prepare(properties, AvroModule.FOLDER,
                            Set.of(AvroModule.SCHEMA_FILE, AvroModule.PROTOCOL_FILE),
                            new LinkedHashMap<>()),
                    avro.apply(new AvroModule(repositories, resolvers)
                            .pinning(pinning)
                            .arguments(words(properties, "arguments"))));
        }
        properties = read(WSIMPORT, WSIMPORT_KEYS, wsimport);
        if (properties != null) {
            generate(buildExecutor, inherited.sequencedKeySet(), WSIMPORT,
                    prepare(properties, WsImportModule.FOLDER,
                            Set.of(WsImportModule.DESCRIPTION, WsImportModule.BINDING),
                            named(properties, "catalog", WsImportModule.CATALOG)),
                    wsimport.apply(new WsImportModule(repositories, resolvers)
                            .pinning(pinning)
                            .packageName(properties.value("package"))
                            .location(properties.value("location"))
                            .arguments(words(properties, "arguments"))));
        }
        properties = read(OPENAPI, OPENAPI_KEYS, openapi);
        if (properties != null) {
            OpenApiModule module = new OpenApiModule(repositories, resolvers)
                    .pinning(pinning)
                    .packageName(properties.value("package"))
                    .arguments(words(properties, "arguments"));
            String generator = properties.value("generator");
            if (generator != null) {
                module = module.generator(generator);
            }
            String sources = properties.value("sources");
            generate(buildExecutor, inherited.sequencedKeySet(), OPENAPI,
                    prepare(properties, OpenApiModule.FOLDER,
                            OpenApiModule.DOCUMENTS,
                            named(properties, "specification", OpenApiModule.SPECIFICATION)),
                    openapi.apply(sources == null ? module : module.sourceFolder(sources)));
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

    private static SequencedMap<String, String> plugins(SequencedProperties properties) {
        SequencedMap<String, String> plugins = new LinkedHashMap<>();
        List<String> entries = properties.entries("plugins");
        if (entries == null) {
            return plugins;
        }
        for (String entry : entries) {
            int assign = entry.indexOf('=');
            if (assign < 1 || assign == entry.length() - 1) {
                throw new IllegalArgumentException("Malformed protoc plugin: " + entry
                        + " (expected <name>=<groupId>/<artifactId>)");
            }
            plugins.put(entry.substring(0, assign).trim(), entry.substring(assign + 1).trim());
        }
        return plugins;
    }

    private static List<String> words(SequencedProperties properties, String key) {
        String value = properties.value(key);
        return value == null ? List.of() : List.of(value.split("\\s+"));
    }
}
