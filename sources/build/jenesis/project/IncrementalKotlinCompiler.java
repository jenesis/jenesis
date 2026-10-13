package build.jenesis.project;

import module java.base;
import java.lang.reflect.Proxy;

final class IncrementalKotlinCompiler implements ToolProvider {

    static final String TOOL = "--tool",
            WORKING = "--working",
            SNAPSHOTS = "--snapshots",
            MODIFIED = "--modified",
            REMOVED = "--removed",
            REBUILD = "--rebuild",
            JVM = "--jvm",
            DESTINATION = "-d",
            CLASSPATH = "-classpath",
            FRIENDS = "-Xfriend-paths=",
            SOURCES = "--";
    private static final String API = "org.jetbrains.kotlin.buildtools.api.",
            SNAPSHOT = ".snapshot",
            SHRUNK = "shrunk-classpath-snapshot.bin";
    private static final ConcurrentMap<List<Path>, Object> TOOLCHAINS = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "kotlinc";
    }

    @Override
    public int run(PrintWriter out, PrintWriter err, String... args) {
        List<Path> jars = new ArrayList<>();
        List<File> modified = new ArrayList<>(), removed = new ArrayList<>();
        List<String> options = new ArrayList<>(), classpath = new ArrayList<>(), virtualMachine = new ArrayList<>();
        List<Path> sources = new ArrayList<>();
        Path working = null, snapshots = null, destination = null;
        boolean rebuild = false;
        for (int index = 0; index < args.length; index++) {
            switch (args[index]) {
                case TOOL -> jars.add(absolute(args[++index]));
                case WORKING -> working = absolute(args[++index]);
                case SNAPSHOTS -> snapshots = absolute(args[++index]);
                case MODIFIED -> modified.add(absolute(args[++index]).toFile());
                case REMOVED -> removed.add(absolute(args[++index]).toFile());
                case REBUILD -> rebuild = true;
                case JVM -> virtualMachine.add(args[++index].substring(1));
                case DESTINATION -> destination = absolute(args[++index]);
                case SOURCES -> {
                    while (++index < args.length) {
                        sources.add(absolute(args[index]));
                    }
                }
                case CLASSPATH -> {
                    options.add(CLASSPATH);
                    if (index + 1 < args.length) {
                        List<String> entries = Stream.of(args[++index].split(File.pathSeparator))
                                .filter(entry -> !entry.isEmpty())
                                .map(entry -> absolute(entry).toString())
                                .toList();
                        classpath.addAll(entries);
                        options.add(String.join(File.pathSeparator, entries));
                    }
                }
                default -> options.add(args[index].startsWith(FRIENDS)
                        ? FRIENDS + Stream.of(args[index].substring(FRIENDS.length()).split(","))
                                .map(entry -> absolute(entry).toString())
                                .collect(Collectors.joining(","))
                        : args[index]);
            }
        }
        if (jars.isEmpty() || (working == null) != (snapshots == null) || destination == null) {
            err.println("Expected " + TOOL + " <jar> and " + DESTINATION + " <folder>, and " + WORKING + " <folder> with "
                    + SNAPSHOTS + " <folder> to compile incrementally, ahead of the compiler's arguments, and "
                    + SOURCES + " before the sources");
            return 2;
        }
        try {
            Object toolchains = toolchains(jars);
            ClassLoader loader = toolchains.getClass().getClassLoader();
            Object logger = Proxy.newProxyInstance(loader, new Class<?>[]{type(loader, "KotlinLogger")}, (proxy, method, arguments) -> switch (method.getName()) {
                case "isDebugEnabled" -> false;
                case "error", "warn" -> {
                    err.println(arguments[0]);
                    if (arguments.length > 1 && arguments[1] instanceof Throwable throwable) {
                        throwable.printStackTrace(err);
                    }
                    yield null;
                }
                case "info", "lifecycle" -> {
                    out.println(arguments[0]);
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "logger";
                default -> null;
            });
            Object jvm = invoke(type(loader, "jvm.JvmPlatformToolchain"), null, "from", toolchains);
            Object daemon = invoke(type(loader, "KotlinToolchains"), toolchains, "daemonExecutionPolicyBuilder");
            if (!virtualMachine.isEmpty()) {
                invoke(type(loader, "ExecutionPolicy$WithDaemon$Builder"), daemon, "set",
                        type(loader, "ExecutionPolicy$WithDaemon").getField("JVM_ARGUMENTS").get(null), List.copyOf(virtualMachine));
            }
            Object policy = invoke(type(loader, "ExecutionPolicy$WithDaemon$Builder"), daemon, "build");
            Class<?> sessionType = type(loader, "KotlinToolchains$BuildSession");
            try (AutoCloseable session = (AutoCloseable) invoke(type(loader, "KotlinToolchains"), toolchains, "createBuildSession")) {
                Object builder = invoke(type(loader, "jvm.JvmPlatformToolchain"), jvm, "jvmCompilationOperationBuilder", sources, destination);
                Object arguments = invoke(type(loader, "jvm.operations.JvmCompilationOperation$Builder"), builder, "getCompilerArguments");
                invoke(type(loader, "arguments.CommonToolArguments$Builder"), arguments, "applyArgumentStrings", options);
                if (working != null) {
                    Files.createDirectories(snapshots);
                    Set<Path> used = new HashSet<>();
                    List<Path> dependencies = new ArrayList<>();
                    for (String entry : classpath) {
                        Path path = Path.of(entry);
                        if (!Files.exists(path)) {
                            continue;
                        }
                        Path snapshot = snapshots.resolve(digest(path) + SNAPSHOT);
                        if (!Files.exists(snapshot)) {
                            Object snapshotting = invoke(type(loader, "jvm.JvmPlatformToolchain"), jvm, "classpathSnapshottingOperationBuilder", path);
                            Object operation = invoke(type(loader, "jvm.operations.JvmClasspathSnapshottingOperation$Builder"), snapshotting, "build");
                            Object taken = invoke(sessionType, session, "executeOperation", operation, policy, logger);
                            invoke(type(loader, "jvm.ClasspathEntrySnapshot"), taken, "saveSnapshot", snapshot);
                        }
                        used.add(snapshot);
                        dependencies.add(snapshot);
                    }
                    try (Stream<Path> files = Files.list(snapshots)) {
                        for (Path stale : files.filter(file -> !used.contains(file)).toList()) {
                            Files.delete(stale);
                        }
                    }
                    Object changes = rebuild
                            ? type(loader, "SourcesChanges$Unknown").getField("INSTANCE").get(null)
                            : type(loader, "SourcesChanges$Known").getConstructor(List.class, List.class).newInstance(modified, removed);
                    Object configuration = invoke(type(loader, "jvm.operations.JvmCompilationOperation$Builder"), builder,
                            "snapshotBasedIcConfigurationBuilder", working, changes, dependencies, working.resolve(SHRUNK));
                    Object incremental = invoke(type(loader, "jvm.JvmSnapshotBasedIncrementalCompilationConfiguration$Builder"), configuration, "build");
                    Class<?> operationType = type(loader, "jvm.operations.JvmCompilationOperation");
                    invoke(type(loader, "jvm.operations.JvmCompilationOperation$Builder"), builder, "set",
                            operationType.getField("INCREMENTAL_COMPILATION").get(null), incremental);
                }
                Object operation = invoke(type(loader, "jvm.operations.JvmCompilationOperation$Builder"), builder, "build");
                Object result = invoke(sessionType, session, "executeOperation", operation, policy, logger);
                if (!((Enum<?>) result).name().equals("COMPILATION_SUCCESS")) {
                    err.println("Kotlin compilation ended with " + result);
                    return 1;
                }
                return 0;
            }
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException target && target.getCause() != null ? target.getCause() : e;
            cause.printStackTrace(err);
            return 1;
        }
    }

    private static Object toolchains(List<Path> jars) {
        return TOOLCHAINS.computeIfAbsent(List.copyOf(jars), key -> {
            URL[] urls = new URL[key.size()];
            try {
                Path copies = Path.of(System.getProperty("java.io.tmpdir"), "jenesis-kotlin");
                for (int index = 0; index < urls.length; index++) {
                    Path jar = key.get(index), copy = copies.resolve(digest(jar)).resolve(jar.getFileName().toString());
                    if (!Files.exists(copy)) {
                        Files.createDirectories(copy.getParent());
                        Path temporary = Files.createTempFile(copy.getParent(), "jar", ".tmp");
                        Files.copy(jar, temporary, StandardCopyOption.REPLACE_EXISTING);
                        try {
                            Files.move(temporary, copy, StandardCopyOption.ATOMIC_MOVE);
                        } catch (FileAlreadyExistsException _) {
                            Files.delete(temporary);
                        }
                    }
                    urls[index] = copy.toUri().toURL();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());
            Class<?> type;
            try {
                type = Class.forName(API + "KotlinToolchains", true, loader);
            } catch (ClassNotFoundException _) {
                throw new IllegalStateException("Incremental Kotlin compilation drives the Kotlin Build Tools API of"
                        + " Kotlin 2.3 or newer, which " + key + " does not hold: pin"
                        + " org.jetbrains.kotlin:kotlin-build-tools-impl to 2.3 or newer, or set"
                        + " jenesis.kotlin.incremental=false");
            }
            try {
                Object toolchains = type.getMethod("loadImplementation", ClassLoader.class).invoke(null, loader);
                String compiler = (String) type.getMethod("getCompilerVersion").invoke(toolchains), implementation = null;
                CodeSource source = toolchains.getClass().getProtectionDomain().getCodeSource();
                if (source != null) {
                    try (JarFile jar = new JarFile(Path.of(source.getLocation().toURI()).toFile())) {
                        Manifest manifest = jar.getManifest();
                        implementation = manifest == null ? null : manifest.getMainAttributes().getValue("Implementation-Version");
                    }
                }
                if (implementation != null && !implementation.replaceFirst("-release-\\d+$", "").equals(compiler)) {
                    throw new IllegalStateException("The Kotlin Build Tools API implementation " + implementation
                            + " drives the Kotlin compiler " + compiler + ", but each needs the other's version: pin"
                            + " org.jetbrains.kotlin:kotlin-build-tools-impl and kotlin-compiler-embeddable to one version");
                }
                return toolchains;
            } catch (ReflectiveOperationException | IOException | URISyntaxException e) {
                throw new IllegalStateException("Could not load the Kotlin Build Tools API from " + key, e);
            }
        });
    }

    private static Path absolute(String path) {
        return Path.of(path).toAbsolutePath().normalize();
    }

    private static Class<?> type(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(API + name, false, loader);
    }

    private static Object invoke(Class<?> type, Object target, String name, Object... arguments) throws ReflectiveOperationException {
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            boolean matches = true;
            for (int index = 0; index < parameters.length && matches; index++) {
                matches = arguments[index] == null || parameters[index].isInstance(arguments[index]);
            }
            if (matches) {
                return method.invoke(target, arguments);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name + " taking " + arguments.length + " arguments");
    }

    static String digest(Path path) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (Files.isDirectory(path)) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(path)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                digest.update(path.relativize(file).toString().replace(File.separatorChar, '/').getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(Files.readAllBytes(file));
            }
        } else {
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
