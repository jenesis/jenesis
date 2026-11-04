package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildModuleName;
import build.jenesis.BuildModuleFactory;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.DependencyScope;
import build.jenesis.Environment;
import build.jenesis.Language;
import build.jenesis.LanguageFactory;
import build.jenesis.ObservabilityEngine;
import build.jenesis.ObservabilityFactory;
import build.jenesis.Observation;
import build.jenesis.PathPlacement;
import build.jenesis.Permits;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.TestFramework;
import build.jenesis.TestFrameworkFactory;
import build.jenesis.step.ProcessHandler;
import java.lang.annotation.Annotation;
import java.lang.module.Configuration;
import java.lang.reflect.Proxy;

class JenesisClassLoaderBridge {

    private static final Map<String, Warmed> WARMED = new ConcurrentHashMap<>();
    private static final Object RECORDING = new Object();

    private final ModuleLayer layer;
    private final ModuleLayer.Controller controller;
    private final ClassLoader loader;

    private final Class<?> foreignBuildExecutorModule;

    private final MethodHandle foreignAccept, foreignResolve;
    private final MethodHandle foreignApply;
    private final MethodHandle foreignScope;
    private final AtomicBoolean scoped = new AtomicBoolean();
    private final MethodHandle foreignShouldRun;
    private final MethodHandle foreignShouldCacheRemotely;

    private final MethodHandle foreignContextCtor;
    private final MethodHandle foreignArgumentCtor;
    private final MethodHandle foreignResultNext;

    private final Class<?> foreignBuildExecutor;
    private final Class<?> foreignBuildModuleFactory;
    private final MethodHandle foreignCreate;
    private final Class<?> foreignObservabilityFactory;
    private final MethodHandle foreignObserve, foreignEngine, foreignReport;
    private final Class<?> foreignTestFrameworkFactory;
    private final MethodHandle foreignFramework;
    private final Class<?> foreignLanguageFactory;
    private final MethodHandle foreignLanguage;

    private final Class<? extends Annotation> foreignBuildModuleName;
    private final MethodHandle foreignBuildModuleNameValue;

    private final Map<String, Object> foreignChecksums;

    private final byte[] origin;

    private final Map<String, Set<String>> declared;

    static JenesisClassLoaderBridge of(String group, List<Path> artifacts, PathPlacement pathPlacement, Path recorded)
            throws ReflectiveOperationException, IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        digest.update(pathPlacement.name().getBytes(StandardCharsets.UTF_8));
        for (Path artifact : artifacts) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(artifact)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                digest.update(artifact.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
                    in.transferTo(OutputStream.nullOutputStream());
                }
            }
        }
        byte[] origin = digest.digest();
        Warmed warmed = WARMED.get(group);
        JenesisClassLoaderBridge kept = warmed == null || !Arrays.equals(warmed.origin(), origin) ? null : warmed.bridge().get();
        if (kept != null) {
            return kept;
        }
        JenesisClassLoaderBridge bridge = new JenesisClassLoaderBridge(group, artifacts, pathPlacement, origin, recorded);
        WARMED.put(group, new Warmed(origin, new SoftReference<>(bridge)));
        return bridge;
    }

    private record Warmed(byte[] origin, SoftReference<JenesisClassLoaderBridge> bridge) {
    }

    private JenesisClassLoaderBridge(String group,
                                     List<Path> artifacts,
                                     PathPlacement pathPlacement,
                                     byte[] origin,
                                     Path recorded) throws ReflectiveOperationException, IOException {
        List<Path> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
        Path copies = ProcessHandler.OfProcess.temporary("jenesis-plugin");
        for (Path artifact : artifacts) {
            Path copy = Files.createDirectory(copies.resolve(Integer.toString(modulePath.size() + classPath.size())))
                    .resolve(artifact.getFileName().toString());
            try (Stream<Path> walk = Files.walk(artifact)) {
                for (Path file : walk.toList()) {
                    Path target = copy.resolve(artifact.relativize(file).toString());
                    if (Files.isDirectory(file)) {
                        Files.createDirectories(target);
                    } else {
                        Files.copy(file, target);
                    }
                }
            }
            (pathPlacement.test(copy) ? modulePath : classPath).add(copy);
        }
        if (recorded != null) {
            synchronized (RECORDING) {
                if (!Files.exists(recorded)) {
                    Path staging = Files.createDirectories(recorded.resolveSibling(recorded.getFileName() + "~"));
                    try (Stream<Path> walk = Files.walk(copies)) {
                        for (Path file : walk.toList()) {
                            Path target = staging.resolve(copies.relativize(file).toString());
                            if (Files.isDirectory(file)) {
                                Files.createDirectories(target);
                            } else {
                                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                    Files.move(staging, recorded, StandardCopyOption.ATOMIC_MOVE);
                }
            }
        }
        declared = declared(modulePath, classPath);
        Module hostModule = JenesisClassLoaderBridge.class.getModule();
        ClassLoader parent = ClassLoader.getPlatformClassLoader();
        if (!classPath.isEmpty()) {
            List<URL> urls = new ArrayList<>(classPath.size());
            for (Path artifact : classPath) {
                urls.add(artifact.toUri().toURL());
            }
            parent = new URLClassLoader("jenesis-plugin." + group, urls.toArray(URL[]::new), parent);
            hostModule.addReads(parent.getUnnamedModule());
        }
        if (modulePath.isEmpty()) {
            controller = null;
            layer = null;
            loader = parent;
        } else {
            ModuleFinder finder = ModuleFinder.of(modulePath.toArray(Path[]::new));
            Set<String> roots = finder.findAll().stream()
                    .map(ref -> ref.descriptor().name())
                    .collect(Collectors.toUnmodifiableSet());
            Configuration config = ModuleLayer.boot().configuration()
                    .resolveAndBind(finder, ModuleFinder.of(), roots);
            controller = ModuleLayer.defineModulesWithOneLoader(config, List.of(ModuleLayer.boot()), parent);
            layer = controller.layer();
            loader = layer.findLoader(roots.iterator().next());
            for (Module foreignModule : layer.modules()) {
                hostModule.addReads(foreignModule);
            }
        }
        foreignBuildExecutor = Class.forName(BuildExecutor.class.getName(), false, loader);
        foreignBuildExecutorModule = Class.forName(BuildExecutorModule.class.getName(), false, loader);
        if (layer != null && foreignBuildExecutor.getModule().getLayer() == layer) {
            controller.addOpens(foreignBuildExecutor.getModule(), BuildExecutor.class.getPackageName(), hostModule);
        }
        Class<?> foreignBuildStep = Class.forName(BuildStep.class.getName(), false, loader);
        Class<?> foreignBuildStepContext = Class.forName(BuildStepContext.class.getName(), false, loader);
        Class<?> foreignBuildStepArgument = Class.forName(BuildStepArgument.class.getName(), false, loader);
        Class<?> foreignBuildStepResult = Class.forName(BuildStepResult.class.getName(), false, loader);
        Class<?> foreignChecksumStatus = Class.forName(ChecksumStatus.class.getName(), false, loader);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(foreignBuildExecutorModule, MethodHandles.lookup());
        foreignAccept = lookup.findVirtual(foreignBuildExecutorModule,
                "accept",
                MethodType.methodType(void.class, foreignBuildExecutor, SequencedMap.class));
        foreignResolve = lookup.findVirtual(foreignBuildExecutorModule,
                "resolve",
                MethodType.methodType(Optional.class, String.class));
        foreignApply = lookup.findVirtual(foreignBuildStep,
                "apply",
                MethodType.methodType(CompletionStage.class, Executor.class, foreignBuildStepContext, SequencedMap.class));
        Class<?> foreignProcessHandler = Class.forName(ProcessHandler.class.getName(), false, loader);
        MethodHandle scope;
        try {
            scope = foreignProcessHandler == ProcessHandler.class
                    ? null
                    : MethodHandles.publicLookup().findStatic(foreignProcessHandler,
                            "scope",
                            MethodType.methodType(Closeable.class));
        } catch (NoSuchMethodException _) {
            scope = null;
        }
        foreignScope = scope;
        foreignShouldRun = lookup.findVirtual(foreignBuildStep,
                "shouldRun",
                MethodType.methodType(boolean.class, SequencedMap.class));
        foreignShouldCacheRemotely = lookup.findVirtual(foreignBuildStep,
                "shouldCacheRemotely",
                MethodType.methodType(boolean.class));
        foreignContextCtor = lookup.findConstructor(foreignBuildStepContext,
                MethodType.methodType(void.class, Path.class, Path.class, Path.class, Path.class));
        foreignArgumentCtor = lookup.findConstructor(foreignBuildStepArgument,
                MethodType.methodType(void.class, Path.class, Map.class));
        foreignResultNext = lookup.findVirtual(foreignBuildStepResult,
                "next",
                MethodType.methodType(boolean.class));
        Class<?> factory;
        MethodHandle create;
        try {
            factory = Class.forName(BuildModuleFactory.class.getName(), false, loader);
            create = lookup.findVirtual(factory, "create", MethodType.methodType(foreignBuildExecutorModule,
                    Class.forName(Environment.class.getName(), false, loader),
                    SequencedMap.class,
                    Class.forName(Permits.class.getName(), false, loader),
                    String.class,
                    Map.class,
                    Map.class));
        } catch (ClassNotFoundException | NoSuchMethodException _) {
            factory = null;
            create = null;
        }
        foreignBuildModuleFactory = factory;
        foreignCreate = create;
        Class<?> observability;
        MethodHandle observe, engine, report;
        try {
            observability = Class.forName(ObservabilityFactory.class.getName(), false, loader);
            Class<?> observation = Class.forName(Observation.class.getName(), false, loader);
            observe = lookup.findVirtual(observability, "create", MethodType.methodType(observation,
                    Class.forName(Environment.class.getName(), false, loader),
                    SequencedMap.class,
                    Class.forName(Permits.class.getName(), false, loader),
                    String.class,
                    Map.class,
                    Map.class));
            engine = lookup.findVirtual(observation, "engine", MethodType.methodType(
                    Class.forName(ObservabilityEngine.class.getName(), false, loader)));
            report = lookup.findVirtual(observation, "report", MethodType.methodType(foreignBuildExecutorModule));
        } catch (ClassNotFoundException | NoSuchMethodException _) {
            observability = null;
            observe = null;
            engine = null;
            report = null;
        }
        foreignObservabilityFactory = observability;
        foreignObserve = observe;
        foreignEngine = engine;
        foreignReport = report;
        Class<?> frameworks;
        MethodHandle framework;
        try {
            frameworks = Class.forName(TestFrameworkFactory.class.getName(), false, loader);
            framework = lookup.findVirtual(frameworks, "create", MethodType.methodType(
                    Class.forName(TestFramework.class.getName(), false, loader),
                    Class.forName(Environment.class.getName(), false, loader),
                    SequencedMap.class,
                    Class.forName(Permits.class.getName(), false, loader),
                    String.class,
                    Map.class,
                    Map.class));
        } catch (ClassNotFoundException | NoSuchMethodException _) {
            frameworks = null;
            framework = null;
        }
        foreignTestFrameworkFactory = frameworks;
        foreignFramework = framework;
        Class<?> languages;
        MethodHandle language;
        try {
            languages = Class.forName(LanguageFactory.class.getName(), false, loader);
            language = lookup.findVirtual(languages, "create", MethodType.methodType(
                    Class.forName(Language.class.getName(), false, loader),
                    Class.forName(Environment.class.getName(), false, loader),
                    SequencedMap.class,
                    Class.forName(Permits.class.getName(), false, loader),
                    String.class,
                    Map.class,
                    Map.class));
        } catch (ClassNotFoundException | NoSuchMethodException _) {
            languages = null;
            language = null;
        }
        foreignLanguageFactory = languages;
        foreignLanguage = language;
        foreignBuildModuleName = Class.forName(BuildModuleName.class.getName(), false, loader)
                .asSubclass(Annotation.class);
        foreignBuildModuleNameValue = lookup.findVirtual(foreignBuildModuleName,
                "value",
                MethodType.methodType(String.class));
        Class<?> foreignChecksum = Class.forName(Checksum.class.getName(), false, loader);
        Method foreignChecksumOf = foreignChecksum.getMethod("of", foreignChecksumStatus);
        Map<String, Object> values = new HashMap<>();
        for (ChecksumStatus status : ChecksumStatus.values()) {
            Field field = foreignChecksumStatus.getField(status.name());
            values.put(status.name(), foreignChecksumOf.invoke(null, field.get(null)));
        }
        foreignChecksums = values;
        this.origin = origin;
    }

    Object create(String name,
                  Environment environment,
                  SequencedMap<String, String> properties,
                  Permits permits,
                  String group,
                  Map<String, Repository> repositories,
                  Map<String, Resolver> resolvers) throws IOException {
        if (foreignBuildModuleFactory == null) {
            throw new IllegalStateException("The build module is compiled against a version of Jenesis without"
                    + " BuildModuleFactory - compile it against one that provides it");
        }
        ServiceLoader.Provider<?> factory = provider(foreignBuildModuleFactory, name);
        ClassLoader host = JenesisClassLoaderBridge.class.getClassLoader();
        try {
            return foreignCreate.invoke(factory.get(),
                    convert(environment, host, loader),
                    Collections.unmodifiableSequencedMap(new LinkedHashMap<>(properties)),
                    convert(permits, host, loader),
                    group,
                    convert(repositories, host, loader),
                    convert(resolvers, host, loader));
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }


    Observation observe(String name,
                        Environment environment,
                        SequencedMap<String, String> properties,
                        Permits permits,
                        String group,
                        Map<String, Repository> repositories,
                        Map<String, Resolver> resolvers) throws IOException {
        if (foreignObservabilityFactory == null) {
            throw new IllegalStateException("The plugin is compiled against a version of Jenesis without"
                    + " ObservabilityFactory - compile it against one that provides it");
        }
        ServiceLoader.Provider<?> factory = provider(foreignObservabilityFactory, name);
        ClassLoader host = JenesisClassLoaderBridge.class.getClassLoader();
        try {
            Object observation = foreignObserve.invoke(factory.get(),
                    convert(environment, host, loader),
                    Collections.unmodifiableSequencedMap(new LinkedHashMap<>(properties)),
                    convert(permits, host, loader),
                    group,
                    convert(repositories, host, loader),
                    convert(resolvers, host, loader));
            Object report = foreignReport.invoke(observation);
            return new Observation((ObservabilityEngine) convert(foreignEngine.invoke(observation), loader, host),
                    report == null ? null : wrapModule(report, group));
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    TestFramework framework(String name,
                            Environment environment,
                            SequencedMap<String, String> properties,
                            Permits permits,
                            String group,
                            Map<String, Repository> repositories,
                            Map<String, Resolver> resolvers) throws IOException {
        if (foreignTestFrameworkFactory == null) {
            throw new IllegalStateException("The plugin is compiled against a version of Jenesis without"
                    + " TestFrameworkFactory - compile it against one that provides it");
        }
        ServiceLoader.Provider<?> factory = provider(foreignTestFrameworkFactory, name);
        ClassLoader host = JenesisClassLoaderBridge.class.getClassLoader();
        try {
            return (TestFramework) convert(foreignFramework.invoke(factory.get(),
                    convert(environment, host, loader),
                    Collections.unmodifiableSequencedMap(new LinkedHashMap<>(properties)),
                    convert(permits, host, loader),
                    group,
                    convert(repositories, host, loader),
                    convert(resolvers, host, loader)), loader, host);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    Language language(String name,
                      Environment environment,
                      SequencedMap<String, String> properties,
                      Permits permits,
                      String group,
                      Map<String, Repository> repositories,
                      Map<String, Resolver> resolvers) throws IOException {
        if (foreignLanguageFactory == null) {
            throw new IllegalStateException("The plugin is compiled against a version of Jenesis without"
                    + " LanguageFactory - compile it against one that provides it");
        }
        ServiceLoader.Provider<?> factory = provider(foreignLanguageFactory, name);
        ClassLoader host = JenesisClassLoaderBridge.class.getClassLoader();
        try {
            Object language = foreignLanguage.invoke(factory.get(),
                    convert(environment, host, loader),
                    Collections.unmodifiableSequencedMap(new LinkedHashMap<>(properties)),
                    convert(permits, host, loader),
                    group,
                    convert(repositories, host, loader),
                    convert(resolvers, host, loader));
            Class<?> type = language.getClass();
            @SuppressWarnings("unchecked")
            SequencedSet<String> extensions = (SequencedSet<String>) type.getMethod("extensions").invoke(language);
            Object compiler = type.getMethod("compiler").invoke(language),
                    documentation = type.getMethod("documentation").invoke(language);
            return new Language(extensions,
                    Language.Order.valueOf(((Enum<?>) type.getMethod("order").invoke(language)).name()),
                    wrapModule(compiler, group),
                    documentation == null ? null : wrapModule(documentation, group));
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private ServiceLoader.Provider<?> provider(Class<?> service, String name) {
        String kind = service.getSimpleName();
        ServiceLoader.Provider<?> factory = null;
        Set<String> named = declared.getOrDefault(service.getName(), Set.of());
        List<ServiceLoader.Provider<?>> providers = new ArrayList<>();
        Set<String> found = new HashSet<>();
        for (ServiceLoader.Provider<?> provider : ServiceLoader.load(service, loader).stream().toList()) {
            if (named.contains(provider.type().getName()) && found.add(provider.type().getName())) {
                providers.add(provider);
            }
        }
        for (String type : named) {
            if (!found.contains(type)) {
                try {
                    providers.add(new DeclaredProvider(Class.forName(type, false, loader)));
                } catch (ClassNotFoundException _) {
                }
            }
        }
        for (ServiceLoader.Provider<?> provider : providers) {
            Annotation annotation = provider.type().getAnnotation(foreignBuildModuleName);
            String providerName;
            if (annotation == null) {
                providerName = null;
            } else {
                try {
                    providerName = (String) foreignBuildModuleNameValue.invoke(annotation);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
            if (!Objects.equals(name, providerName)) {
                continue;
            }
            if (factory != null) {
                throw new IllegalStateException(name == null
                        ? "Multiple unnamed " + kind + " service providers found in the plugin"
                        : "Multiple " + kind + " service providers named " + name + " found in the plugin");
            }
            factory = provider;
        }
        if (factory == null) {
            throw new IllegalStateException(name == null
                    ? "No unnamed " + kind + " service provider found in the plugin"
                    : "No " + kind + " service provider named " + name + " found in the plugin");
        }
        return factory;
    }

    private static Map<String, Set<String>> declared(List<Path> modulePath, List<Path> classPath) throws IOException {
        Map<String, Set<String>> declared = new HashMap<>();
        for (ModuleReference reference : ModuleFinder.of(modulePath.toArray(Path[]::new)).findAll()) {
            for (ModuleDescriptor.Provides provides : reference.descriptor().provides()) {
                declared.computeIfAbsent(provides.service(), _ -> new LinkedHashSet<>()).addAll(provides.providers());
            }
        }
        for (Path artifact : classPath) {
            Map<String, List<String>> services = new LinkedHashMap<>();
            if (Files.isDirectory(artifact)) {
                Path folder = artifact.resolve("META-INF").resolve("services");
                if (Files.isDirectory(folder)) {
                    try (Stream<Path> files = Files.list(folder)) {
                        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                            services.put(file.getFileName().toString(), Files.readAllLines(file, StandardCharsets.UTF_8));
                        }
                    }
                }
            } else {
                try (JarFile jar = new JarFile(artifact.toFile())) {
                    for (JarEntry entry : Collections.list(jar.entries())) {
                        if (!entry.isDirectory() && entry.getName().startsWith("META-INF/services/")) {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                    jar.getInputStream(entry),
                                    StandardCharsets.UTF_8))) {
                                services.put(entry.getName().substring("META-INF/services/".length()),
                                        reader.lines().toList());
                            }
                        }
                    }
                }
            }
            services.forEach((service, lines) -> {
                for (String line : lines) {
                    String provider = line.split("#", 2)[0].strip();
                    if (!provider.isEmpty()) {
                        declared.computeIfAbsent(service, _ -> new LinkedHashSet<>()).add(provider);
                    }
                }
            });
        }
        return declared;
    }

    private record DeclaredProvider(Class<?> type) implements ServiceLoader.Provider<Object> {

        @Override
        public Object get() {
            try {
                Method provider;
                try {
                    provider = type.getMethod("provider");
                } catch (NoSuchMethodException _) {
                    provider = null;
                }
                return provider != null && Modifier.isStatic(provider.getModifiers())
                        ? provider.invoke(null)
                        : type.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new ServiceConfigurationError("Cannot create the provider " + type.getName() + " its plugin declares", e);
            }
        }
    }

    private static Object convert(Object value, ClassLoader source, ClassLoader target) {
        return switch (value) {
            case null -> null;
            case Proxy proxy when Proxy.getInvocationHandler(proxy) instanceof Adapter adapter
                    && adapter.origin == target -> adapter.delegate;
            case Optional<?> optional -> optional.map(element -> convert(element, source, target));
            case SequencedMap<?, ?> map -> {
                SequencedMap<Object, Object> converted = new LinkedHashMap<>();
                map.forEach((key, element) -> converted.put(convert(key, source, target), convert(element, source, target)));
                yield converted;
            }
            case Map<?, ?> map -> {
                Map<Object, Object> converted = new HashMap<>();
                map.forEach((key, element) -> converted.put(convert(key, source, target), convert(element, source, target)));
                yield converted;
            }
            case List<?> list -> list.stream()
                    .map(element -> convert(element, source, target))
                    .collect(Collectors.toCollection(ArrayList::new));
            case Set<?> set -> set.stream()
                    .map(element -> convert(element, source, target))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            default -> {
                Class<?> type = value.getClass();
                try {
                    if (type.isEnum() && bridged(type, source, target)) {
                        yield Enum.valueOf(Class.forName(type.getName(), false, target).asSubclass(Enum.class),
                                ((Enum<?>) value).name());
                    }
                    if (type.isRecord() && bridged(type, source, target)) {
                        Class<?> counterpart = Class.forName(type.getName(), false, target);
                        Map<String, RecordComponent> components = new HashMap<>();
                        for (RecordComponent component : type.getRecordComponents()) {
                            components.put(component.getName(), component);
                        }
                        RecordComponent[] fields = counterpart.getRecordComponents();
                        Object[] values = new Object[fields.length];
                        Class<?>[] types = new Class<?>[fields.length];
                        for (int index = 0; index < fields.length; index++) {
                            RecordComponent component = components.get(fields[index].getName());
                            types[index] = fields[index].getType();
                            if (component != null) {
                                values[index] = convert(component.getAccessor().invoke(value), source, target);
                            } else if (types[index].isPrimitive()) {
                                values[index] = Array.get(Array.newInstance(types[index], 1), 0);
                            } else if (types[index] == Optional.class) {
                                values[index] = Optional.empty();
                            } else {
                                values[index] = Stream.of(Collections.emptyNavigableMap(), List.of(), Collections.emptyNavigableSet())
                                        .filter(types[index]::isInstance)
                                        .findFirst()
                                        .orElse(null);
                            }
                        }
                        yield counterpart.getDeclaredConstructor(types).newInstance(values);
                    }
                    List<Class<?>> interfaces = new ArrayList<>();
                    for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                        for (Class<?> candidate : current.getInterfaces()) {
                            collect(candidate, interfaces);
                        }
                    }
                    List<Class<?>> counterparts = new ArrayList<>();
                    for (Class<?> candidate : interfaces) {
                        if (bridged(candidate, source, target)) {
                            counterparts.add(Class.forName(candidate.getName(), false, target));
                        }
                    }
                    if (counterparts.isEmpty()) {
                        yield value;
                    }
                    yield Proxy.newProxyInstance(target,
                            counterparts.toArray(Class<?>[]::new),
                            new Adapter(value, source, target));
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot hand " + type.getName() + " to a build module that runs"
                            + " a version of Jenesis of its own, whose copy of it differs", e);
                }
            }
        };
    }

    private static boolean bridged(Class<?> type, ClassLoader loader, ClassLoader target) {
        if (!type.getName().startsWith("build.jenesis.") || type.getClassLoader() != loader) {
            return false;
        }
        try {
            return Class.forName(type.getName(), false, target) != type;
        } catch (ClassNotFoundException _) {
            return false;
        }
    }

    private static void collect(Class<?> type, List<Class<?>> interfaces) {
        if (!interfaces.contains(type)) {
            interfaces.add(type);
            for (Class<?> parent : type.getInterfaces()) {
                collect(parent, interfaces);
            }
        }
    }

    private static final class Adapter implements InvocationHandler, Serializable {

        private final Object delegate;
        private final transient ClassLoader origin, loader;

        private Adapter(Object delegate, ClassLoader origin, ClassLoader loader) {
            this.delegate = delegate;
            this.origin = origin;
            this.loader = loader;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> delegate.hashCode();
                    default -> delegate.toString();
                };
            }
            Class<?>[] types = new Class<?>[method.getParameterCount()];
            Object[] converted = new Object[types.length];
            for (int index = 0; index < types.length; index++) {
                Class<?> type = method.getParameterTypes()[index];
                types[index] = type.isPrimitive() || !type.getName().startsWith("build.jenesis.")
                        ? type
                        : Class.forName(type.getName(), false, origin);
                converted[index] = convert(args[index], loader, origin);
            }
            Class<?> declaring = Class.forName(method.getDeclaringClass().getName(), false, origin);
            Method target;
            if (declaring == Resolver.class && method.getName().equals("dependencies") && types.length < 8) {
                target = Resolver.class.getMethod("dependencies",
                        Executor.class,
                        String.class,
                        Map.class,
                        SequencedMap.class,
                        SequencedMap.class,
                        SequencedMap.class,
                        Map.class,
                        DependencyScope.class);
                converted = new Object[]{
                        converted[0],
                        converted[1],
                        converted[2],
                        converted[3],
                        converted[4],
                        types.length == 7 ? converted[5] : Collections.emptyNavigableMap(),
                        Map.of(),
                        converted[types.length - 1]
                };
            } else {
                try {
                    target = declaring.getMethod(method.getName(), types);
                } catch (NoSuchMethodException e) {
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }
                    throw e;
                }
            }
            try {
                return convert(target.invoke(delegate, converted), origin, loader);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    void accept(Object foreignModule,
                BuildExecutor hostExecutor,
                SequencedMap<String, Path> inherited,
                String group) throws IOException {
        Object foreignProxy = foreignBuildExecutor == BuildExecutor.class
                ? new DelegatingBuildExecutor(new BuildExecutorBridge(hostExecutor, group))
                : Proxy.newProxyInstance(loader,
                        new Class<?>[]{foreignBuildExecutor},
                        new BuildExecutorBridge(hostExecutor, group));
        try {
            foreignAccept.invoke(foreignModule, foreignProxy, inherited);
        } catch (IOException | RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private Object toForeignContext(BuildStepContext context) {
        try {
            return foreignContextCtor.invoke(context.previous(), context.next(), context.supplement(), context.persistent());
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private SequencedMap<String, Object> toForeignArguments(SequencedMap<String, BuildStepArgument> arguments) {
        SequencedMap<String, Object> foreign = new LinkedHashMap<>();
        arguments.forEach((key, value) -> {
            Map<Path, Object> foreignFiles = new LinkedHashMap<>();
            value.files().forEach((path, checksum) ->
                    foreignFiles.put(path, foreignChecksums.get(checksum.status().name())));
            try {
                foreign.put(key, foreignArgumentCtor.invoke(value.folder(), foreignFiles));
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
        return foreign;
    }

    private BuildStepResult fromForeignResult(Object foreignResult) {
        try {
            return new BuildStepResult((boolean) foreignResultNext.invoke(foreignResult));
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private BuildStep wrapStep(Object foreignStep, String group) {
        return new ForeignBuildStep(this, foreignStep, origin, group);
    }

    private static final class ForeignBuildStep implements BuildStep {

        private final transient JenesisClassLoaderBridge bridge;
        private final Object foreignStep;
        private final byte[] origin;
        private final String group;

        ForeignBuildStep(JenesisClassLoaderBridge bridge, Object foreignStep, byte[] origin, String group) {
            this.bridge = bridge;
            this.foreignStep = foreignStep;
            this.origin = origin;
            this.group = group;
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            try {
                return (boolean) bridge.foreignShouldRun.invoke(foreignStep, bridge.toForeignArguments(arguments));
            } catch (IOException | RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        @Override
        public boolean shouldCacheRemotely() {
            try {
                return (boolean) bridge.foreignShouldCacheRemotely.invoke(foreignStep);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            try {
                if (bridge.foreignScope != null && bridge.scoped.compareAndSet(false, true)) {
                    Closeable foreignScope = (Closeable) bridge.foreignScope.invoke();
                    Closeable nested = () -> {
                        bridge.scoped.set(false);
                        foreignScope.close();
                    };
                    if (!ProcessHandler.nest(nested)) {
                        nested.close();
                    }
                }
                CompletionStage<Object> foreign = (CompletionStage<Object>) bridge.foreignApply.invoke(
                        foreignStep, executor, bridge.toForeignContext(context), bridge.toForeignArguments(arguments));
                return foreign.thenApply(result -> {
                    Path index = context.next().resolve(BuildStep.DEPENDENCIES);
                    if (Files.exists(index)) {
                        SequencedProperties dependencies;
                        try {
                            dependencies = SequencedProperties.ofFiles(index);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        for (String key : dependencies.stringPropertyNames()) {
                            String resolved = key.substring(0, Math.max(key.indexOf('/'), 0));
                            if (!resolved.startsWith(group + ":")) {
                                throw new IllegalStateException("The plugin " + group + " resolved " + key
                                        + " in the group " + resolved + ", but a plugin resolves what it runs in a"
                                        + " group named " + group + ":<purpose>, such as " + group + ":tool, so that"
                                        + " its pins are its own - resolve it in such a group");
                            }
                        }
                    }
                    return bridge.fromForeignResult(result);
                });
            } catch (IOException | RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    BuildExecutorModule wrapModule(Object foreignModule, String group) {
        return new BuildExecutorModule() {
            @Override
            public void accept(BuildExecutor hostExecutor, SequencedMap<String, Path> inherited) throws IOException {
                JenesisClassLoaderBridge.this.accept(foreignModule, hostExecutor, inherited, group);
            }

            @Override
            @SuppressWarnings("unchecked")
            public Optional<String> resolve(String path) {
                try {
                    return (Optional<String>) foreignResolve.invoke(foreignModule, path);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        };
    }

    private class BuildExecutorBridge implements InvocationHandler {

        private final BuildExecutor host;
        private final String group;

        BuildExecutorBridge(BuildExecutor host, String group) {
            this.host = host;
            this.group = group;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            return dispatch(method.getName(), method.getParameterCount(), args);
        }

        @SuppressWarnings("unchecked")
        Object dispatch(String name, int parameters, Object[] args) {
            return switch (name) {
                case "addSource" -> {
                    if (parameters == 2) {
                        host.addSource((String) args[0], (Path) args[1]);
                    } else {
                        host.addSource((String) args[0], wrapStep(args[1], group), (SequencedSet<Path>) args[2]);
                    }
                    yield null;
                }
                case "replaceSource" -> {
                    if (parameters == 2) {
                        host.replaceSource((String) args[0], (Path) args[1]);
                    } else {
                        host.replaceSource((String) args[0], wrapStep(args[1], group), (SequencedSet<Path>) args[2]);
                    }
                    yield null;
                }
                case "addStep" -> {
                    host.addStep((String) args[0], wrapStep(args[1], group), (SequencedMap<String, String>) args[2]);
                    yield null;
                }
                case "replaceStep" -> {
                    host.replaceStep((String) args[0], wrapStep(args[1], group));
                    yield null;
                }
                case "prependStep" -> {
                    host.prependStep((String) args[0], (String) args[1], wrapStep(args[2], group));
                    yield null;
                }
                case "appendStep" -> {
                    host.appendStep((String) args[0], (String) args[1], wrapStep(args[2], group));
                    yield null;
                }
                case "addModule" -> {
                    host.addModule((String) args[0],
                            wrapModule(args[1], group),
                            (Function<String, Optional<String>>) args[2],
                            (SequencedMap<String, String>) args[3]);
                    yield null;
                }
                case "replaceModule" -> {
                    host.replaceModule((String) args[0],
                            wrapModule(args[1], group),
                            (Function<String, Optional<String>>) args[2]);
                    yield null;
                }
                case "prependModule" -> {
                    host.prependModule((String) args[0],
                            (String) args[1],
                            wrapModule(args[2], group),
                            (Function<String, Optional<String>>) args[3]);
                    yield null;
                }
                case "appendModule" -> {
                    host.appendModule((String) args[0],
                            (String) args[1],
                            wrapModule(args[2], group),
                            (Function<String, Optional<String>>) args[3]);
                    yield null;
                }
                case "execute" -> host.execute((Executor) args[0], (List<String>) args[1], (List<String>) args[2]);
                default -> throw new UnsupportedOperationException(
                        "This build module calls BuildExecutor." + name + " with " + parameters
                                + " arguments, which the running version of Jenesis does not provide. The module was "
                                + "built against a newer Jenesis API; upgrade Jenesis to a version that "
                                + "supports it.");
            };
        }
    }

    private record DelegatingBuildExecutor(BuildExecutorBridge bridge) implements BuildExecutor {

        @Override
        public void addSource(String identity, Path path) {
            bridge.dispatch("addSource", 2, new Object[]{identity, path});
        }

        @Override
        public void addSource(String identity, BuildStep step, SequencedSet<Path> paths) {
            bridge.dispatch("addSource", 3, new Object[]{identity, step, paths});
        }

        @Override
        public void replaceSource(String identity, Path path) {
            bridge.dispatch("replaceSource", 2, new Object[]{identity, path});
        }

        @Override
        public void replaceSource(String identity, BuildStep step, SequencedSet<Path> paths) {
            bridge.dispatch("replaceSource", 3, new Object[]{identity, step, paths});
        }

        @Override
        public void addStep(String identity, BuildStep step, SequencedMap<String, String> dependencies) {
            bridge.dispatch("addStep", 3, new Object[]{identity, step, dependencies});
        }

        @Override
        public void replaceStep(String identity, BuildStep step) {
            bridge.dispatch("replaceStep", 2, new Object[]{identity, step});
        }

        @Override
        public void prependStep(String identity, String prepended, BuildStep step) {
            bridge.dispatch("prependStep", 3, new Object[]{identity, prepended, step});
        }

        @Override
        public void appendStep(String identity, String original, BuildStep step) {
            bridge.dispatch("appendStep", 3, new Object[]{identity, original, step});
        }

        @Override
        public void addModule(String identity,
                              BuildExecutorModule module,
                              Function<String, Optional<String>> resolver,
                              SequencedMap<String, String> dependencies) {
            bridge.dispatch("addModule", 4, new Object[]{identity, module, resolver, dependencies});
        }

        @Override
        public void replaceModule(String identity,
                                  BuildExecutorModule module,
                                  Function<String, Optional<String>> resolver) {
            bridge.dispatch("replaceModule", 3, new Object[]{identity, module, resolver});
        }

        @Override
        public void prependModule(String identity,
                                  String prepended,
                                  BuildExecutorModule module,
                                  Function<String, Optional<String>> resolver) {
            bridge.dispatch("prependModule", 4, new Object[]{identity, prepended, module, resolver});
        }

        @Override
        public void appendModule(String identity,
                                 String appended,
                                 BuildExecutorModule module,
                                 Function<String, Optional<String>> resolver) {
            bridge.dispatch("appendModule", 4, new Object[]{identity, appended, module, resolver});
        }

        @Override
        @SuppressWarnings("unchecked")
        public CompletionStage<SequencedMap<String, Path>> execute(Executor executor,
                                                                   List<String> selectors,
                                                                   List<String> optional) {
            return (CompletionStage<SequencedMap<String, Path>>) bridge.dispatch("execute",
                    3,
                    new Object[]{executor, selectors, optional});
        }
    }
}
