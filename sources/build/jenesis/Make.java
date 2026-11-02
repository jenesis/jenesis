package build.jenesis;

import module java.base;

public final class Make {

    private static final String PROVIDED = "make.provided",
            PERSONAL = "make.personal",
            HOME = "make.home",
            AMBIENT = "make.ambient",
            DIRECTORY = "make.directory";
    private static final List<String> RECORDED = List.of(PROVIDED, PERSONAL, HOME, AMBIENT, DIRECTORY);
    private static final Set<String> PRINTING = Set.of("help", "skill", "prompt", "configuration", "properties", "--stop");
    static final int JVM = 3;
    static final String PLUGINS = "META-INF/jenesis/plugins.properties";
    static final List<String> FACTORIES = List.of("build.jenesis.BuildModuleFactory",
            "build.jenesis.ObservabilityFactory",
            "build.jenesis.TestFrameworkFactory",
            "build.jenesis.LanguageFactory");
    private static final List<String> SERIALIZED = List.of("byte[]",
            "java.lang.Boolean",
            "java.lang.Character",
            "java.lang.Double",
            "java.lang.Enum",
            "java.lang.Integer",
            "java.lang.Long",
            "java.lang.Number",
            "java.lang.Object[]",
            "java.lang.String",
            "java.lang.String[]",
            "java.lang.invoke.SerializedLambda",
            "java.lang.reflect.Proxy",
            "java.net.URI",
            "java.time.Duration",
            "java.time.Instant",
            "java.time.OffsetDateTime",
            "java.time.Ser",
            "java.util.ArrayDeque",
            "java.util.ArrayList",
            "java.util.CollSer",
            "java.util.HashMap",
            "java.util.HashSet",
            "java.util.Hashtable",
            "java.util.LinkedHashMap",
            "java.util.LinkedHashSet",
            "java.util.LinkedList",
            "java.util.Properties",
            "java.util.TreeMap",
            "java.util.TreeSet",
            "java.util.regex.Pattern");
    private static final String REFLECTED = """
            ,
                {"type": "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl", "methods": [{"name": "<init>", "parameterTypes": []}]},
                {"type": "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl", "methods": [{"name": "<init>", "parameterTypes": []}]},
                {"type": "com.sun.tools.javac.api.JavacTrees", "methods": [{"name": "instance", "parameterTypes": ["javax.tools.JavaCompiler$CompilationTask"]}]},
                {"type": "com.sun.tools.javac.jvm.ClassWriter$StackMapTableFrame[]"},
                {"type": "com.sun.tools.javac.jvm.PoolConstant$LoadableConstant[]"},
                {"type": "java.lang.ClassLoader", "fields": [{"name": "classLoaderValueMap"}]},
                {"type": "javax.tools.JavaCompiler$CompilationTask"},
                {"type": "jdk.internal.jrtfs.JrtFileSystemProvider"},
                {"type": "jdk.internal.misc.Unsafe"},
                {"type": "sun.security.provider.MD5", "methods": [{"name": "<init>", "parameterTypes": []}]},
                {"type": "sun.security.provider.NativePRNG", "methods": [{"name": "<init>", "parameterTypes": ["java.security.SecureRandomParameters"]}]},
                {"type": "sun.security.provider.SHA", "methods": [{"name": "<init>", "parameterTypes": []}]},
                {"type": "sun.security.provider.SHA2$SHA256", "methods": [{"name": "<init>", "parameterTypes": []}]},
                {"type": "sun.security.provider.SHA5$SHA512", "methods": [{"name": "<init>", "parameterTypes": []}]},
                {"type": "sun.text.resources.cldr.FormatData"},
                {"type": "sun.text.resources.cldr.FormatData_en"},
                {"type": "sun.text.resources.cldr.FormatData_en_US"},
                {"type": "sun.util.resources.cldr.TimeZoneNames"},
                {"type": "sun.util.resources.cldr.TimeZoneNames_en"},
                {"type": "sun.util.resources.cldr.TimeZoneNames_en_US"},
                {"type": {"proxy": ["build.jenesis.BuildExecutor"]}, "methods": [{"name": "proxyClassLookup", "parameterTypes": ["java.lang.invoke.MethodHandles$Lookup"]}]},
                {"type": {"proxy": ["build.jenesis.Repository"]}, "methods": [{"name": "proxyClassLookup", "parameterTypes": ["java.lang.invoke.MethodHandles$Lookup"]}]},
                {"type": {"proxy": ["build.jenesis.Repository$Observer"]}, "methods": [{"name": "proxyClassLookup", "parameterTypes": ["java.lang.invoke.MethodHandles$Lookup"]}]},
                {"type": {"proxy": ["build.jenesis.RepositoryItem"]}, "methods": [{"name": "proxyClassLookup", "parameterTypes": ["java.lang.invoke.MethodHandles$Lookup"]}]},
                {"type": {"proxy": ["build.jenesis.Resolver"]}, "methods": [{"name": "proxyClassLookup", "parameterTypes": ["java.lang.invoke.MethodHandles$Lookup"]}]},
                {"type": {"proxy": ["build.jenesis.maven.MavenRepository", "build.jenesis.Repository"]}, "methods": [{"name": "proxyClassLookup", "parameterTypes": ["java.lang.invoke.MethodHandles$Lookup"]}]},
                {"type": {"proxy": ["build.jenesis.maven.MavenResolver", "build.jenesis.Resolver"]}, "methods": [{"name": "proxyClassLookup", "parameterTypes": ["java.lang.invoke.MethodHandles$Lookup"]}]},
                {"type": {"proxy": ["build.jenesis.module.JenesisRepository", "build.jenesis.Repository"]}, "methods": [{"name": "proxyClassLookup", "parameterTypes": ["java.lang.invoke.MethodHandles$Lookup"]}]}""";
    private static final String RESOURCES = """
            ,
                {"glob": "META-INF/services/com.sun.source.util.Plugin"},
                {"glob": "META-INF/services/java.nio.charset.spi.CharsetProvider"},
                {"glob": "META-INF/services/java.nio.file.spi.FileSystemProvider"},
                {"glob": "META-INF/services/java.time.zone.ZoneRulesProvider"},
                {"glob": "META-INF/services/javax.annotation.processing.Processor"},
                {"glob": "META-INF/services/javax.xml.parsers.DocumentBuilderFactory"},
                {"glob": "META-INF/services/javax.xml.transform.TransformerFactory"},
                {"module": "java.base", "glob": "java/lang/Object.class"},
                {"module": "java.xml", "glob": "com/sun/org/apache/xml/internal/serializer/*.properties"},
                {"module": "jdk.compiler", "glob": "com/sun/tools/javac/resources/*.properties"},
                {"module": "jdk.jartool", "glob": "sun/tools/jar/resources/*.properties"}""";
    static final List<String> CONTAINED = List.of("jenesis.project.docker",
            "jenesis.project.image",
            "jenesis.project.readable",
            "jenesis.project.writable",
            "jenesis.project.env");

    private final String mainClass;
    private final Map<String, String> ambient;
    private final Path root;
    private final Path classes;
    private final Mode mode;
    private final Path aotFolder;
    private final Duration aotLifetime;
    private final Path graalvm;
    private final Settings settings;

    public record Settings(Map<String, String> keys,
                           SequencedSet<Path> profiles,
                           SequencedSet<String> declared) {
    }

    private record Layer(Properties properties, boolean trusted) {
    }

    public Make(String mainClass) {
        this(mainClass, ambient(Map.of()));
    }

    public Make(String mainClass, Map<String, String> ambient) {
        this.mainClass = mainClass;
        this.ambient = ambient;
        String location = ambient.get("make.root");
        root = Path.of(location == null ? "" : location).toAbsolutePath().normalize();
        try {
            settings = settings(root, ambient);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the properties that configure this build", e);
        }
        classes = path(settings.keys(), root, "make.classes", ".jenesis/classes", true, false);
        mode = Mode.of(settings.keys());
        aotFolder = path(settings.keys(), root, "aot.folder", ".jenesis/aot", true, false);
        graalvm = path(settings.keys(), root, "make.graalvm", null, false, true);
        String lifetime = settings.keys().get("aot.lifetime");
        if (lifetime == null || lifetime.isBlank()) {
            aotLifetime = Duration.ZERO;
        } else {
            try {
                aotLifetime = Duration.parse(lifetime.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Malformed value for jenesis.aot.lifetime: '"
                        + lifetime
                        + "' (expected an ISO-8601 duration, as PT12H or P7D, or no value to keep the cache)", e);
            }
        }
    }

    public enum Mode {

        SOURCE, COMPILED, AOT, NATIVE, DAEMON;

        static Mode of(Map<String, String> keys) {
            String value = keys.get("make.mode"), process = keys.get("process.mode");
            if (value != null && !value.isBlank()) {
                return of(value);
            }
            return process != null && !process.isBlank() && !process.trim().equalsIgnoreCase("daemon")
                    || Objects.equals(parsed("jenesis.project.docker",
                            machine(keys, "project.docker")), true)
                    || Objects.equals(parsed("jenesis.release.publish",
                            command(keys, "release.publish")), true)
                    ? COMPILED
                    : image() ? COMPILED : DAEMON;
        }

        static Mode of(String value) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Malformed value for jenesis.make.mode: '"
                    + value
                    + "' (expected source, compiled, aot, native or daemon)");
        }
    }

    static String machine(Map<String, String> keys, String key) {
        String value = keys.get(key);
        if (value != null && listed(keys, PROVIDED, key)) {
            throw new IllegalStateException("jenesis." + key + " cannot be set in a file the project provides: it"
                    + " configures what the machine running the build trusts, runs or shares between projects, so"
                    + " only the command line or your own ~/.jenesis/jenesis.properties may set it"
                    + " (pass -Djenesis." + key + " instead)");
        }
        return value;
    }

    static String command(Map<String, String> keys, String key) {
        String value = keys.get(key);
        if (value != null && (listed(keys, PROVIDED, key) || listed(keys, PERSONAL, key))) {
            throw new IllegalStateException("jenesis." + key + " cannot be set in a file: it is what one run is"
                    + " asked to do, so only the command line may name it (pass -Djenesis." + key + " instead)");
        }
        return value;
    }

    static Path path(Map<String, String> keys,
                     Path root,
                     String key,
                     String defaultValue,
                     boolean confined,
                     boolean machine) {
        String value = keys.get(key);
        String location = value == null || value.isBlank() ? defaultValue : value;
        return location == null ? null : located(keys, root, key, location, confined, machine);
    }

    static Path located(Map<String, String> keys,
                        Path root,
                        String key,
                        String location,
                        boolean confined,
                        boolean machine) {
        if (location.contains("://")) {
            throw new IllegalArgumentException("Malformed value for jenesis." + key + ": '" + location
                    + "' (expected a path, not a URI)");
        }
        Path path;
        try {
            path = Path.of(location.strip());
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Malformed value for jenesis." + key + ": '" + location
                    + "' (expected a path)", e);
        }
        if (listed(keys, PROVIDED, key)) {
            if (root == null && confined) {
                throw new IllegalStateException("jenesis." + key + " is set in a file the project provides, but the"
                        + " environment reading it names no project root to confine it to - hand the environment"
                        + " the root with root(...)");
            }
            if (root != null) {
                Path resolved = root.resolve(path).normalize();
                if (confined
                        && !resolved.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
                    throw new IllegalStateException("jenesis." + key + " cannot name '" + location.strip() + "' in a"
                            + " file the project provides: such a file names only a location inside the project, which"
                            + " the build writes to and may delete (pass -Djenesis." + key + " on the command line for"
                            + " one outside it)");
                }
                return resolved;
            }
        } else {
            String base = listed(keys, PERSONAL, key)
                    ? machine ? keys.get(HOME) : null
                    : listed(keys, AMBIENT, key) ? keys.get(DIRECTORY) : null;
            if (base != null) {
                return Path.of(base).resolve(path).normalize();
            }
        }
        if (root == null) {
            if (path.isAbsolute()) {
                return path;
            }
            throw new IllegalStateException("jenesis." + key + " is the relative path '" + location.strip()
                    + "', but the environment reading it names no project root to resolve it against - name an"
                    + " absolute path, or hand the environment the root with root(...)");
        }
        return root.resolve(path).normalize();
    }

    static boolean listed(Map<String, String> keys, String list, String key) {
        String value = keys.get(list);
        if (value == null) {
            return false;
        }
        for (String entry : value.split(",")) {
            if (entry.strip().equals(key)) {
                return true;
            }
        }
        return false;
    }

    static Boolean parsed(String name, String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "", "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Malformed value for "
                    + name
                    + ": '"
                    + value
                    + "' (expected true, false, or the setting named with no value at all)");
        };
    }

    private Make(String mainClass,
                 Map<String, String> ambient,
                 Path root,
                 Path classes,
                 Mode mode,
                 Path aotFolder,
                 Duration aotLifetime,
                 Path graalvm,
                 Settings settings) {
        this.mainClass = mainClass;
        this.ambient = ambient;
        this.root = root;
        this.classes = classes;
        this.mode = mode;
        this.aotFolder = aotFolder;
        this.aotLifetime = aotLifetime;
        this.graalvm = graalvm;
        this.settings = settings;
    }

    public Make root(Path root) {
        try {
            return new Make(mainClass,
                    ambient,
                    root,
                    classes,
                    mode,
                    aotFolder,
                    aotLifetime,
                    graalvm,
                    settings(root, ambient));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the properties that configure this build", e);
        }
    }

    public Make classes(Path classes) {
        return new Make(mainClass, ambient, root, classes, mode, aotFolder, aotLifetime, graalvm, settings);
    }

    public Mode mode() {
        return mode;
    }

    public Make mode(Mode mode) {
        return new Make(mainClass, ambient, root, classes, mode, aotFolder, aotLifetime, graalvm, settings);
    }

    public Make aotFolder(Path aotFolder) {
        return new Make(mainClass, ambient, root, classes, mode, aotFolder, aotLifetime, graalvm, settings);
    }

    public Make aotLifetime(Duration aotLifetime) {
        return new Make(mainClass, ambient, root, classes, mode, aotFolder, aotLifetime, graalvm, settings);
    }

    public Make graalvm(Path graalvm) {
        return new Make(mainClass, ambient, root, classes, mode, aotFolder, aotLifetime, graalvm, settings);
    }

    public record Result(int code, SequencedMap<String, Path> outputs) {
    }

    public static void main(String... arguments) throws Exception {
        SequencedMap<String, String> named = new LinkedHashMap<>();
        String[] selectors = partitioned(arguments, named);
        List<String> options = options(named);
        Map<String, String> ambient = ambient(named);
        if (image() && System.getProperty("java.home") == null) {
            System.setProperty("java.home", home().toString());
        }
        Make make = new Make("build.jenesis.Project", ambient);
        if (image() && (make.mode != Mode.COMPILED && make.mode != Mode.NATIVE
                || ambient.get("toolchain.version") != null)) {
            System.exit(make.jvm(selectors));
        }
        Integer code = relaunched(Make.class, ambient, options, selectors);
        int result = code == null ? make.run(selectors) : code;
        System.exit(image() && result == JVM && make.mode != Mode.NATIVE ? make.jvm(selectors) : result);
    }

    static boolean image() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    static Path home() {
        String home = System.getProperty("java.home");
        if (home == null) {
            home = System.getenv("JAVA_HOME");
        }
        if (home != null && !home.isBlank()) {
            return Path.of(home);
        }
        String path = System.getenv("PATH");
        for (String entry : path == null ? new String[0] : path.split(File.pathSeparator)) {
            Path java = Path.of(entry, File.separatorChar == '\\' ? "java.exe" : "java");
            if (!entry.isBlank() && Files.isExecutable(java)) {
                try {
                    return java.toRealPath().getParent().getParent();
                } catch (IOException _) {
                }
            }
        }
        throw new IllegalStateException("No JDK is found to compile and test with: set JAVA_HOME to a JDK 25 or newer,"
                + " or put its bin/java on the PATH");
    }

    int jvm(String... selectors) throws IOException, InterruptedException {
        Path script = root.resolve("build").resolve("jenesis").resolve("Make.java");
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("This build needs a JVM, which a native image of Jenesis hands it to by"
                    + " running build/jenesis/Make.java, but " + root + " holds no such file: run the build with the"
                    + " engine the project carries, as java build/jenesis/Make.java");
        }
        List<String> command = new ArrayList<>();
        command.add(home().resolve("bin").resolve(File.separatorChar == '\\' ? "java.exe" : "java").toString());
        new TreeMap<>(ambient).forEach((key, value) -> command.add("-Djenesis." + key + "=" + value));
        command.add("-Djenesis.make.root=" + root);
        command.add(script.toString());
        for (String selector : selectors) {
            command.add(selector.startsWith("@") ? "@" + selector : selector);
        }
        return awaited(command);
    }

    static Map<String, String> ambient(Map<String, String> named) {
        Map<String, String> properties = new HashMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("jenesis.")) {
                properties.put(name, System.getProperty(name));
            }
        }
        properties.putAll(named);
        return directed(keys(properties), Path.of("").toAbsolutePath());
    }

    static Map<String, String> directed(Map<String, String> keys, Path directory) {
        if (keys.containsKey(DIRECTORY)) {
            return keys;
        }
        Map<String, String> directed = new HashMap<>(keys);
        directed.put(DIRECTORY, directory.toString());
        return Map.copyOf(directed);
    }

    public static Map<String, String> keys(Map<String, String> properties) {
        Map<String, String> keys = new HashMap<>();
        properties.forEach((name, value) -> {
            if (name.startsWith("jenesis.")) {
                keys.put(name.substring("jenesis.".length()), value);
            }
        });
        return Map.copyOf(keys);
    }

    static String[] partitioned(String[] arguments, SequencedMap<String, String> named) throws IOException {
        List<String> expanded = expanded(arguments), selectors = new ArrayList<>();
        for (String argument : expanded) {
            if (!selectors.isEmpty() || !argument.startsWith("-D")) {
                selectors.add(argument);
                continue;
            }
            String assignment = argument.substring(2);
            int equals = assignment.indexOf('=');
            String name = equals < 0 ? assignment : assignment.substring(0, equals);
            if (!name.startsWith("jenesis.")) {
                throw new IllegalArgumentException("Not a Jenesis setting: -D"
                        + assignment
                        + " - a run is configured by jenesis.* settings alone, and a JVM property is named"
                        + " before the main class");
            }
            named.put(name, equals < 0 ? "" : assignment.substring(equals + 1));
        }
        return selectors.toArray(String[]::new);
    }

    static List<String> expanded(String[] arguments) throws IOException {
        List<String> expanded = new ArrayList<>();
        for (String argument : arguments) {
            if (argument.startsWith("@@")) {
                expanded.add(argument.substring(1));
            } else if (argument.length() > 1 && argument.charAt(0) == '@') {
                expanded.addAll(words(Path.of(argument.substring(1))));
            } else {
                expanded.add(argument);
            }
        }
        return expanded;
    }

    private static List<String> words(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("No argument file at "
                    + file
                    + " - @<file> names a file of arguments, @@<text> an argument starting with @");
        }
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean started = false, escaped = false, comment = false;
        char quote = 0;
        for (char current : Files.readString(file).toCharArray()) {
            if (escaped) {
                word.append(switch (current) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'f' -> '\f';
                    default -> current;
                });
                escaped = false;
            } else if (comment) {
                comment = current != '\n';
            } else if (quote != 0) {
                if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                } else {
                    word.append(current);
                }
            } else if (current == '\'' || current == '"') {
                quote = current;
                started = true;
            } else if (current == '#') {
                comment = true;
            } else if (Character.isWhitespace(current)) {
                if (started) {
                    words.add(word.toString());
                    word.setLength(0);
                    started = false;
                }
            } else {
                word.append(current);
                started = true;
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Unterminated " + quote + " in the argument file " + file);
        }
        if (started) {
            words.add(word.toString());
        }
        return words;
    }

    static List<String> options(SequencedMap<String, String> named) {
        SortedMap<String, String> options = new TreeMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("jenesis.")) {
                options.put(name, System.getProperty(name));
            }
        }
        options.putAll(named);
        List<String> arguments = new ArrayList<>();
        options.forEach((name, value) -> {
            if (!name.startsWith("jenesis.toolchain.")) {
                arguments.add("-D" + name + "=" + value);
            }
        });
        return arguments;
    }

    static Integer relaunched(Class<?> main,
                              Map<String, String> keys,
                              List<String> options,
                              String... arguments) throws Exception {
        String version = keys.get("toolchain.version");
        if (version == null || version.isBlank()) {
            return null;
        }
        Class<?> type = Class.forName("build.jenesis.Toolchain", true, Make.class.getClassLoader());
        try {
            Object toolchain = type.getMethod("ofKeys", Map.class)
                    .invoke(null, keys);
            if (type.getMethod("home").invoke(toolchain).equals(Path.of(System.getProperty("java.home")))) {
                return null;
            }
            return (Integer) type.getMethod("launch", Class.class, List.class, List.class)
                    .invoke(toolchain, main, options, List.of(arguments));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    public Result build(String... selectors) throws Exception {
        SequencedMap<String, String> collected = new LinkedHashMap<>();
        int code = run(collected, selectors);
        SequencedMap<String, Path> outputs = new LinkedHashMap<>();
        collected.forEach((identity, output) -> outputs.put(identity, root.resolve(output)));
        return new Result(code, outputs);
    }

    public int run(String... selectors) throws Exception {
        return run(null, selectors);
    }

    private int run(SequencedMap<String, String> collected, String... selectors) throws Exception {
        CodeSource code = Make.class.getProtectionDomain().getCodeSource();
        Path location;
        try {
            location = code == null ? null : Path.of(code.getLocation().toURI());
        } catch (URISyntaxException _) {
            location = null;
        }
        boolean stop = selectors.length == 1 && selectors[0].equals("--stop"),
                building = mainClass.equals("build.jenesis.Project") && (selectors.length == 0 || !Stream.of(selectors)
                        .allMatch(selector -> PRINTING.contains(selector.split("/", 2)[0])));
        boolean natively = mode == Mode.NATIVE && collected == null && building && !image();
        if (location == null || !location.getFileName().toString().endsWith(".java")) {
            if (natively) {
                if (location == null) {
                    throw new IllegalStateException("jenesis.make.mode=native compiles the engine from its classes,"
                            + " but this JVM loaded them from no location it can name - run the engine from a folder"
                            + " or a jar, or as java build/jenesis/Make.java");
                }
                return natively(location, selectors);
            }
            if (mode != Mode.DAEMON) {
                if (mode == Mode.AOT && collected == null && location != null && building) {
                    return cached(location, selectors);
                }
                if (location != null
                        && building
                        && !(Files.isDirectory(aotFolder) && location.toRealPath().startsWith(aotFolder.toRealPath()))
                        && tools()) {
                    sweep(location);
                }
                return invoke(Make.class.getClassLoader(), collected, selectors);
            }
            String modules = System.getProperty("jdk.module.path");
            String classPath = location == null ? System.getProperty("java.class.path") : location.toString();
            Path engine = location == null || Files.isRegularFile(location)
                    ? location
                    : location.resolve("build").resolve("jenesis");
            return dispatched(Make.class.getClassLoader(),
                    modules != null ? List.of("-p", modules) : List.of("-cp", classPath),
                    engine == null ? "" : fingerprint(engine, files(engine, ""), ""),
                    collected,
                    selectors);
        }
        if (mode == Mode.SOURCE && !stop) {
            return invoke(Make.class.getClassLoader(), collected, selectors);
        }
        Path build = location.getParent();
        List<Path> sources = files(build, ".java");
        String seed = fingerprint(build, sources, classes.toString());
        Path folder = precompiled(build, sources, seed);
        if (natively) {
            return natively(folder, selectors);
        }
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { folder.toUri().toURL() },
                ClassLoader.getPlatformClassLoader())) {
            if (mode == Mode.DAEMON || stop) {
                return dispatched(loader,
                        List.of("-cp", folder.toString()),
                        fingerprint(build, sources, ""),
                        collected,
                        selectors);
            }
            if (building && tools()) {
                sweep(folder);
            }
            return invoke(loader, collected, selectors);
        }
    }

    private int cached(Path engine, String... selectors) throws IOException, InterruptedException {
        sweep(engine);
        String modules = System.getProperty("jdk.module.path");
        String module = modules == null ? null : Make.class.getModule().getName();
        Path jar = module != null || Files.isRegularFile(engine) ? engine : aotFolder.resolve("engine.jar");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        Path cache = aotFolder.resolve("engine-" + HexFormat.of()
                .formatHex(digest.digest((module == null ? jar.toString() : modules).getBytes(StandardCharsets.UTF_8)))
                .substring(0, 12) + ".aot");
        boolean reusable = Files.isRegularFile(cache) && Files.exists(jar);
        if (!reusable && !jar.equals(engine)) {
            Path temporary = Files.createTempFile(jar.getParent(), "engine", ".jar");
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(temporary));
                 Stream<Path> files = Files.walk(engine)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    out.putNextEntry(new JarEntry(engine.relativize(file).toString()
                            .replace(File.separatorChar, '/')));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }
            Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", File.separatorChar == '\\' ? "java.exe" : "java")
                .toString());
        command.add((reusable ? "-XX:AOTCache=" : "-XX:AOTCacheOutput=") + cache);
        if (!reusable) {
            command.add("-XX:+UnlockDiagnosticVMOptions");
            command.add("-XX:-DisplayVMOutput");
        }
        command.add("-Xlog:aot=off");
        new TreeMap<>(ambient).forEach((key, value) -> {
            if (!key.startsWith("toolchain.")) {
                command.add("-Djenesis." + key + "=" + value);
            }
        });
        command.add("-Djenesis.make.root=" + root);
        command.add("-Djenesis.make.classes=" + classes);
        command.add("-Djenesis.make.mode=compiled");
        if (tools()) {
            command.add("-Djenesis.process.mode=aot");
            command.add("-Djenesis.aot.folder=" + aotFolder);
        }
        if (module == null) {
            command.addAll(List.of("-cp", jar.toString(), Make.class.getName()));
        } else {
            command.addAll(List.of("-p", modules, "-m", module + "/" + Make.class.getName()));
        }
        for (String selector : selectors) {
            command.add(selector.startsWith("@") ? "@" + selector : selector);
        }
        return awaited(command);
    }

    private int natively(Path engine, String... selectors) throws IOException, InterruptedException {
        Path home = graalvm == null ? Path.of(System.getProperty("java.home")) : graalvm;
        Path compiler = home.resolve("bin").resolve(File.separatorChar == '\\' ? "native-image.cmd" : "native-image");
        if (!Files.isExecutable(compiler)) {
            throw new IllegalStateException("jenesis.make.mode=native compiles the engine with the native-image of a"
                    + " GraalVM, which " + home + " does not hold: name a GraalVM 25 or newer as jenesis.make.graalvm"
                    + " on the command line or in your own ~/.jenesis/jenesis.properties, or run the build on one");
        }
        sweep(engine);
        Path folder = Files.createDirectories(aotFolder.resolve("native"));
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        Path record = folder.resolve("project-" + HexFormat.of()
                .formatHex(digest.digest(root.toString().getBytes(StandardCharsets.UTF_8)))
                .substring(0, 12) + ".properties");
        Properties recorded = Objects.requireNonNullElseGet(read(record), Properties::new);
        String image = recorded.getProperty("image"), executable = File.separatorChar == '\\'
                ? "build.jenesis.exe"
                : "build.jenesis";
        boolean stuck = Boolean.parseBoolean(recorded.getProperty("stuck")), handed = false;
        if (image != null && !stuck && Files.isExecutable(folder.resolve(image).resolve(executable))) {
            List<String> command = new ArrayList<>();
            command.add(folder.resolve(image).resolve(executable).toString());
            for (String name : new TreeSet<>(System.getProperties().stringPropertyNames())) {
                if (name.startsWith("javax.net.ssl.")
                        || name.startsWith("http.")
                        || name.startsWith("https.")
                        || name.startsWith("socksProxy")) {
                    command.add("-D" + name + "=" + System.getProperty(name));
                }
            }
            command.add("-Djava.home=" + home);
            new TreeMap<>(ambient).forEach((key, value) -> {
                if (!key.startsWith("toolchain.")) {
                    command.add("-Djenesis." + key + "=" + value);
                }
            });
            command.add("-Djenesis.make.root=" + root);
            command.add("-Djenesis.make.mode=native");
            for (String selector : selectors) {
                command.add(selector.startsWith("@") ? "@" + selector : selector);
            }
            int code = awaited(command);
            if (code != JVM) {
                return code;
            }
            handed = true;
        }
        Path metadata = Files.createDirectories(folder.resolve("metadata"));
        Path loaded = Files.createTempFile(folder, "plugins", ".properties");
        try {
            List<String> command = new ArrayList<>();
            command.add(home.resolve("bin").resolve(File.separatorChar == '\\' ? "java.exe" : "java").toString());
            if (!stuck) {
                command.add("-agentlib:native-image-agent=config-merge-dir=" + metadata);
            }
            new TreeMap<>(ambient).forEach((key, value) -> {
                if (!key.startsWith("toolchain.") && !key.equals("make.mode")) {
                    command.add("-Djenesis." + key + "=" + value);
                }
            });
            command.add("-Djenesis.make.root=" + root);
            command.add("-Djenesis.make.classes=" + classes);
            command.add("-Djenesis.make.mode=compiled");
            command.add("-Djenesis.make.plugins=" + loaded);
            command.addAll(List.of("-cp", engine.toString(), Make.class.getName()));
            for (String selector : selectors) {
                command.add(selector.startsWith("@") ? "@" + selector : selector);
            }
            int code = awaited(command);
            Properties plugins = Objects.requireNonNullElseGet(read(loaded), Properties::new);
            StringBuilder setup = new StringBuilder(Files.readString(home.resolve("release")));
            for (String group : new TreeSet<>(plugins.stringPropertyNames())) {
                if (!group.endsWith(".artifacts")) {
                    setup.append('\n').append(group).append('=').append(plugins.getProperty(group));
                }
            }
            setup.append('\n').append(fingerprint(metadata, files(metadata, ""), ""));
            String key = fingerprint(engine, files(engine, ""), setup.toString()).substring(0, 16);
            if (!key.equals(image)) {
                Path target = folder.resolve(key);
                recorded.setProperty("image", key);
                recorded.setProperty("stuck", Boolean.toString(!Files.isExecutable(target.resolve(executable))
                        && !compiled(compiler, engine, plugins, metadata, target)));
            } else if (handed) {
                recorded.setProperty("stuck", "true");
            }
            Path temporary = Files.createTempFile(folder, "project", ".properties");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                recorded.store(writer, null);
            }
            Files.move(temporary, record, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return code;
        } finally {
            Files.deleteIfExists(loaded);
        }
    }

    private static boolean compiled(Path compiler,
                                    Path engine,
                                    Properties plugins,
                                    Path metadata,
                                    Path target) throws IOException, InterruptedException {
        Path staging = target.resolveSibling(target.getFileName() + "~");
        if (Files.exists(staging)) {
            try (Stream<Path> files = Files.walk(staging)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
        Path configuration = Files.createDirectories(staging.resolve("configuration")),
                resources = Files.createDirectories(staging.resolve("resources"));
        SequencedSet<Path> path = new LinkedHashSet<>(List.of(engine));
        Map<String, Path> modules = new HashMap<>();
        Properties compiled = new Properties();
        for (String group : new TreeSet<>(plugins.stringPropertyNames())) {
            if (group.endsWith(".artifacts")) {
                continue;
            }
            SequencedMap<String, Path> named = new LinkedHashMap<>();
            String conflict = null;
            for (String artifact : plugins.getProperty(group + ".artifacts", "").split(File.pathSeparator)) {
                if (artifact.isEmpty()) {
                    continue;
                }
                Path jar = Path.of(artifact);
                String name = ModuleFinder.of(jar).findAll().stream()
                        .map(reference -> reference.descriptor().name())
                        .findFirst()
                        .orElse(jar.getFileName().toString());
                if (name.equals("build.jenesis")) {
                    continue;
                }
                Path known = modules.get(name);
                if (known != null && Files.mismatch(known, jar) != -1) {
                    conflict = name;
                }
                named.put(name, jar);
            }
            if (conflict != null) {
                System.err.println("The plugin " + group + " needs another " + conflict + " than a plugin the native"
                        + " image of Jenesis holds already, so it is left out and its builds run on a JVM.");
                continue;
            }
            SequencedSet<String> providers = new TreeSet<>();
            for (Path jar : named.values()) {
                modules.putIfAbsent(jar.getFileName().toString(), jar);
                path.add(jar);
                if (Files.isRegularFile(jar)) {
                    try (JarFile file = new JarFile(jar.toFile())) {
                        for (String factory : FACTORIES) {
                            JarEntry entry = file.getJarEntry("META-INF/services/" + factory);
                            if (entry != null) {
                                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                        file.getInputStream(entry), StandardCharsets.UTF_8))) {
                                    reader.lines()
                                            .map(line -> line.replaceFirst("#.*", "").strip())
                                            .filter(line -> !line.isEmpty())
                                            .forEach(providers::add);
                                }
                            }
                        }
                    }
                }
            }
            named.forEach(modules::putIfAbsent);
            compiled.setProperty(group, plugins.getProperty(group));
            compiled.setProperty(group + ".providers", String.join(",", providers));
        }
        Path registry = Files.createDirectories(resources.resolve(PLUGINS).getParent()).resolve(Path.of(PLUGINS)
                .getFileName().toString());
        try (Writer writer = Files.newBufferedWriter(registry, StandardCharsets.UTF_8)) {
            compiled.store(writer, null);
        }
        described(configuration, engine, path);
        List<String> command = new ArrayList<>(List.of(compiler.toString(),
                "--no-fallback",
                "--enable-url-protocols=http,https",
                "--add-modules=jdk.compiler,jdk.jartool",
                "--exact-reachability-metadata-path=" + path.stream()
                        .map(Path::toString)
                        .collect(Collectors.joining(File.pathSeparator)),
                "-H:+UnlockExperimentalVMOptions",
                "-H:IncludeResourceBundles=com.sun.tools.javac.resources.compiler,com.sun.tools.javac.resources.javac,"
                        + "com.sun.tools.javac.resources.ct,sun.tools.jar.resources.jar,"
                        + "com.sun.org.apache.xml.internal.serializer.XMLEntities",
                "-H:ConfigurationFileDirectories=" + configuration
                        + (files(metadata, "").isEmpty() ? "" : "," + metadata),
                "-o", staging.resolve("build.jenesis").toString(),
                "-cp", Stream.concat(Stream.of(resources), path.stream())
                        .map(Path::toString)
                        .collect(Collectors.joining(File.pathSeparator)),
                Make.class.getName()));
        System.err.println("Compiling a native image of Jenesis for this build with " + compiler
                + ", once for this engine, these plugins and what earlier builds recorded; the build ran on a JVM.");
        Path log = staging.resolve("native-image.log");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        Thread hook = new Thread(process::destroy);
        Runtime.getRuntime().addShutdownHook(hook);
        int code;
        try {
            code = process.waitFor();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException _) {
                process.destroy();
            }
        }
        if (code != 0) {
            System.err.println("native-image failed with exit code " + code + ", so builds of this setup keep running"
                    + " on a JVM - its output is in " + log);
            return false;
        }
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        return true;
    }

    static void described(Path configuration, Path engine, SequencedSet<Path> path) throws IOException {
        record Shape(String parent, List<String> interfaces, boolean type, boolean lambdas) {
        }
        SequencedMap<String, Shape> shapes = new TreeMap<>();
        Map<String, Path> origins = new HashMap<>();
        SequencedSet<String> resources = new TreeSet<>(Set.of(PLUGINS));
        for (Path entry : path) {
            Map<String, byte[]> files = new TreeMap<>();
            if (Files.isDirectory(entry)) {
                try (Stream<Path> walk = Files.walk(entry)) {
                    for (Path file : walk.filter(Files::isRegularFile).toList()) {
                        files.put(entry.relativize(file).toString().replace(File.separatorChar, '/'),
                                Files.readAllBytes(file));
                    }
                }
            } else {
                try (JarFile jar = new JarFile(entry.toFile())) {
                    for (JarEntry file : Collections.list(jar.entries())) {
                        if (!file.isDirectory()) {
                            try (InputStream input = jar.getInputStream(file)) {
                                files.put(file.getName(), input.readAllBytes());
                            }
                        }
                    }
                }
            }
            files.forEach((name, bytes) -> {
                if (!name.startsWith("META-INF/") || name.startsWith("META-INF/services/")) {
                    resources.add(name);
                }
                if (!name.endsWith(".class") || name.endsWith("module-info.class") || name.startsWith("META-INF/")) {
                    return;
                }
                ClassModel model;
                try {
                    model = ClassFile.of().parse(bytes);
                } catch (IllegalArgumentException _) {
                    return;
                }
                String internal = model.thisClass().asInternalName();
                shapes.putIfAbsent(internal, new Shape(model.superclass().map(ClassEntry::asInternalName).orElse(null),
                        model.interfaces().stream().map(ClassEntry::asInternalName).toList(),
                        !model.flags().has(AccessFlag.INTERFACE),
                        model.methods().stream()
                                .anyMatch(method -> method.methodName().equalsString("$deserializeLambda$"))));
                origins.putIfAbsent(internal, entry);
            });
        }
        Map<String, Boolean> serializable = new HashMap<>();
        Function<String, Boolean> serializing = new Function<>() {
            @Override
            public Boolean apply(String name) {
                Boolean known = serializable.get(name);
                if (known != null) {
                    return known;
                }
                Shape shape = shapes.get(name);
                boolean result;
                if (name.equals("java/io/Serializable")) {
                    result = true;
                } else if (shape == null) {
                    boolean found;
                    try {
                        found = Serializable.class.isAssignableFrom(Class.forName(name.replace('/', '.'),
                                false,
                                ClassLoader.getPlatformClassLoader()));
                    } catch (ClassNotFoundException | LinkageError _) {
                        found = false;
                    }
                    result = found;
                } else {
                    result = shape.parent() != null && apply(shape.parent())
                            || shape.interfaces().stream().anyMatch(this::apply);
                }
                serializable.put(name, result);
                return result;
            }
        };
        StringBuilder reflection = new StringBuilder(), lambdas = new StringBuilder();
        shapes.forEach((internal, shape) -> {
            String name = internal.replace('/', '.');
            boolean owned = origins.get(internal).equals(engine);
            boolean serialized = shape.type() && serializing.apply(internal);
            if (owned || serialized) {
                reflection.append(reflection.isEmpty() ? "" : ",\n")
                        .append("    {\"type\": \"")
                        .append(name)
                        .append('"');
                if (owned) {
                    reflection.append(", \"allDeclaredConstructors\": true, \"allDeclaredMethods\": true,"
                            + " \"allDeclaredFields\": true");
                }
                if (serialized) {
                    reflection.append(", \"serializable\": true");
                }
                reflection.append('}');
            }
            if (shape.lambdas()) {
                lambdas.append(lambdas.isEmpty() ? "" : ",\n").append("    {\"name\": \"").append(name).append("\"}");
            }
        });
        for (String name : SERIALIZED) {
            reflection.append(",\n    {\"type\": \"").append(name).append("\", \"serializable\": true}");
        }
        for (String type : List.of("java.util.Collections", "java.util.ImmutableCollections", "java.util.Arrays")) {
            Deque<Class<?>> pending;
            try {
                pending = new ArrayDeque<>(List.of(Class.forName(type)));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException(e);
            }
            while (!pending.isEmpty()) {
                for (Class<?> nested : pending.pop().getDeclaredClasses()) {
                    if (Serializable.class.isAssignableFrom(nested) && !nested.isInterface()) {
                        reflection.append(",\n    {\"type\": \"")
                                .append(nested.getName())
                                .append("\", \"serializable\": true}");
                    }
                    pending.add(nested);
                }
            }
        }
        reflection.append(REFLECTED);
        StringBuilder globs = new StringBuilder();
        for (String resource : resources) {
            globs.append(globs.isEmpty() ? "" : ",\n").append("    {\"glob\": \"")
                    .append(resource.replace("\\", "\\\\").replace("\"", "\\\"").replace("*", "\\*")
                            .replace("?", "\\?").replace("{", "\\{").replace("[", "\\["))
                    .append("\"}");
        }
        globs.append(RESOURCES);
        Files.writeString(configuration.resolve("reachability-metadata.json"), "{\n  \"reflection\": [\n" + reflection
                + "\n  ],\n  \"resources\": [\n" + globs + "\n  ]\n}\n");
        Files.writeString(configuration.resolve("serialization-config.json"),
                "{\n  \"types\": [],\n  \"lambdaCapturingTypes\": [\n" + lambdas + "\n  ],\n  \"proxies\": []\n}\n");
    }

    private static int awaited(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).inheritIO().start();
        Thread hook = new Thread(process::destroy);
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            return process.waitFor();
        } finally {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException _) {
                process.destroy();
            }
        }
    }

    private static String fingerprint(Path folder, List<Path> files, String salt) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        digest.update((Runtime.version().feature() + "\n" + salt).getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(1 << 16);
        for (Path file : files) {
            digest.update(folder.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
            try (FileChannel channel = FileChannel.open(file)) {
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    digest.update(buffer);
                    buffer.clear();
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static List<Path> files(Path folder, String suffix) throws IOException {
        if (Files.isRegularFile(folder)) {
            return List.of(folder);
        }
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(folder, Set.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (directory.equals(folder)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = directory.getFileName().toString();
                return Character.isJavaIdentifierStart(name.codePointAt(0))
                        && name.codePoints().skip(1).allMatch(Character::isJavaIdentifierPart)
                        ? FileVisitResult.CONTINUE
                        : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (file.getFileName().toString().endsWith(suffix)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files.stream().sorted().toList();
    }

    private int dispatched(ClassLoader loader,
                           List<String> path,
                           String seed,
                           SequencedMap<String, String> collected,
                           String... selectors) throws Exception {
        return (int) Class.forName("build.jenesis.daemon.DaemonClient", true, loader)
                .getMethod("doDispatch",
                        Path.class,
                        List.class,
                        String.class,
                        String.class,
                        SequencedMap.class,
                        SequencedMap.class,
                        String[].class)
                .invoke(null, root, path, mainClass, seed, supplied(), collected, selectors);
    }

    private SequencedMap<String, String> supplied() {
        SequencedMap<String, String> supplied = new LinkedHashMap<>();
        SequencedSet<String> keys = new LinkedHashSet<>(new TreeSet<>(ambient.keySet()));
        keys.addAll(settings.declared());
        keys.addAll(List.of(PROVIDED, PERSONAL, HOME));
        for (String key : keys) {
            String value = settings.keys().get(key);
            if (value != null) {
                supplied.put("jenesis." + key, value);
            }
        }
        return supplied;
    }

    private int invoke(ClassLoader loader, SequencedMap<String, String> collected, String... selectors)
            throws Exception {
        Class<?> project = Class.forName("build.jenesis.Project", true, loader);
        if (collected == null || !mainClass.equals("build.jenesis.Project")) {
            int code = (int) project
                    .getMethod("run", Map.class, String.class, Path.class, SequencedSet.class, String[].class)
                    .invoke(null, settled(), mainClass, root, settings.profiles(), selectors);
            if (command(settings.keys(), "make.plugins") != null) {
                Properties loaded = new Properties();
                ((Map<?, ?>) Class.forName("build.jenesis.project.ExternalModule", true, loader)
                        .getMethod("loaded")
                        .invoke(null)).forEach((group, artifacts) -> {
                    List<?> values = (List<?>) artifacts;
                    loaded.setProperty(group.toString(), values.getFirst().toString());
                    loaded.setProperty(group + ".artifacts", values.subList(1, values.size()).stream()
                            .map(Object::toString)
                            .collect(Collectors.joining(File.pathSeparator)));
                });
                try (Writer writer = Files.newBufferedWriter(path(settings.keys(),
                        root,
                        "make.plugins",
                        null,
                        false,
                        false), StandardCharsets.UTF_8)) {
                    loaded.store(writer, null);
                }
            }
            return code;
        }
        Object produced = project
                .getMethod("perform", Map.class, Path.class, SequencedSet.class, String[].class)
                .invoke(null, settled(), root, settings.profiles(), selectors);
        if (produced == null) {
            return 1;
        }
        ((Map<?, ?>) produced).forEach((identity, output) -> collected.put(identity.toString(),
                root.relativize(Path.of(output.toString()).toAbsolutePath().normalize()).toString()));
        return 0;
    }

    private boolean tools() {
        String process = settings.keys().get("process.mode");
        return process == null || process.isBlank() ? mode == Mode.AOT : process.trim().equalsIgnoreCase("aot");
    }

    private Map<String, String> settled() {
        Map<String, String> keys = new HashMap<>(settings.keys());
        keys.put("make.mode", mode == Mode.COMPILED && ProcessHandle.current().info().arguments().stream()
                .flatMap(Stream::of)
                .anyMatch(argument -> argument.startsWith("-XX:AOTCache"))
                ? "aot"
                : mode.name().toLowerCase(Locale.ROOT));
        if (tools()) {
            keys.put("process.mode", "aot");
            keys.put("aot.folder", aotFolder.toString());
        }
        return Collections.unmodifiableMap(keys);
    }

    private void sweep(Path engine) throws IOException {
        StringBuilder identity = new StringBuilder(Runtime.version().toString());
        try (Stream<Path> files = Files.walk(engine)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                identity.append('|').append(engine.relativize(file))
                        .append(':').append(attributes.size())
                        .append(':').append(attributes.lastModifiedTime().toMillis());
            }
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        String fingerprint = HexFormat.of().formatHex(digest.digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
        Path stamp = aotFolder.resolve("aot.properties");
        Properties recorded = Files.isRegularFile(stamp) ? read(stamp) : new Properties();
        Instant created;
        try {
            created = Instant.parse(recorded.getProperty("created", ""));
        } catch (DateTimeParseException _) {
            created = null;
        }
        if (fingerprint.equals(recorded.getProperty("engine"))
                && created != null
                && (aotLifetime.isZero() || !created.isBefore(Instant.now().minus(aotLifetime)))) {
            return;
        }
        if (Files.isDirectory(aotFolder)) {
            try (Stream<Path> files = Files.walk(aotFolder)) {
                for (Path file : files.filter(file -> !file.equals(aotFolder)).sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
        Properties written = new Properties();
        written.setProperty("engine", fingerprint);
        written.setProperty("created", Instant.now().toString());
        Path temporary = Files.createTempFile(Files.createDirectories(aotFolder), "aot", ".properties");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            written.store(writer, null);
        }
        Files.move(temporary, stamp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static Settings settings(Path path) throws IOException {
        return settings(path, ambient(Map.of()));
    }

    public static Settings settings(Path path, Map<String, String> ambient) throws IOException {
        Path base = path.resolve("jenesis.properties");
        Properties project = read(base);
        if (project != null) {
            requireApplicable(base, project);
        }
        String configured = ambient.get("make.global"), directory = ambient.get(DIRECTORY);
        String location = configured == null ? System.getProperty("user.home") : configured;
        Properties user = null;
        Path home = null;
        if (!location.isEmpty()) {
            home = (directory == null ? path : Path.of(directory)).toAbsolutePath()
                    .resolve(location)
                    .resolve(".jenesis")
                    .normalize();
            Path file = home.resolve("jenesis.properties");
            user = read(file);
            if (user != null) {
                requireApplicable(file, user);
            }
        }
        Set<Path> loaded = new LinkedHashSet<>();
        Deque<Path> pending = new ArrayDeque<>();
        List<Layer> local = new ArrayList<>(), layers = new ArrayList<>();
        addProfiles(pending, path, ambient.get("make.profiles"));
        if (project != null) {
            addProfiles(pending, path, project.getProperty("jenesis.make.profiles"));
        }
        loadProfiles(loaded, local, pending, path, false);
        if (project != null) {
            local.add(new Layer(project, false));
        }
        if (user != null) {
            addProfiles(pending, home, user.getProperty("jenesis.make.profiles"));
            loadProfiles(loaded, layers, pending, home, true);
            layers.add(new Layer(user, true));
        }
        layers.addAll(local);
        SequencedSet<Path> profiles = new LinkedHashSet<>();
        for (Path file : loaded) {
            String name = file.getFileName().toString();
            profiles.add(Path.of(name.substring("jenesis-".length(), name.length() - ".properties".length())));
        }
        SequencedSet<String> declared = new LinkedHashSet<>();
        for (Layer layer : layers) {
            for (String name : layer.properties().stringPropertyNames()) {
                if (name.startsWith("jenesis.")) {
                    declared.add(name.substring("jenesis.".length()));
                }
            }
        }
        return new Settings(layered(ambient, layers, home), profiles, declared);
    }

    private static Map<String, String> layered(Map<String, String> ambient, List<Layer> layers, Path home) {
        SequencedSet<String> provided = new TreeSet<>(), personal = new TreeSet<>(), named = new TreeSet<>();
        Set<String> declared = new HashSet<>();
        for (Layer layer : layers) {
            for (String name : layer.properties().stringPropertyNames()) {
                String key = name.substring("jenesis.".length());
                if (declared.add(key) && !ambient.containsKey(key)) {
                    (layer.trusted() ? personal : provided).add(key);
                }
            }
        }
        for (String key : ambient.keySet()) {
            if (RECORDED.contains(key)) {
                continue;
            }
            if (listed(ambient, PROVIDED, key)) {
                provided.add(key);
            } else if (listed(ambient, PERSONAL, key)) {
                personal.add(key);
            } else {
                named.add(key);
            }
        }
        Map<String, String> keys = new HashMap<>();
        for (Layer layer : layers.reversed()) {
            for (String name : layer.properties().stringPropertyNames()) {
                keys.put(name.substring("jenesis.".length()), layer.properties().getProperty(name));
            }
        }
        keys.putAll(ambient);
        keys.keySet().removeAll(List.of(PROVIDED, PERSONAL, HOME, AMBIENT));
        String recorded = ambient.get(HOME);
        if (recorded != null || home != null) {
            keys.put(HOME, recorded == null ? home.toString() : recorded);
        }
        for (Map.Entry<String, SequencedSet<String>> entry : Map.of(PROVIDED, provided,
                PERSONAL, personal,
                AMBIENT, named).entrySet()) {
            if (!entry.getValue().isEmpty()) {
                keys.put(entry.getKey(), String.join(",", entry.getValue()));
            }
        }
        return Map.copyOf(keys);
    }

    private static void addProfiles(Deque<Path> pending, Path base, String list) {
        if (list == null) {
            return;
        }
        for (String name : list.split(",")) {
            String trimmed = name.trim();
            if (trimmed.endsWith(".properties")) {
                trimmed = trimmed.substring(0, trimmed.length() - ".properties".length());
            }
            if (!trimmed.isEmpty()) {
                pending.add(base.resolve("jenesis-" + trimmed + ".properties"));
            }
        }
    }

    private static void loadProfiles(Set<Path> loaded,
                                     List<Layer> layers,
                                     Deque<Path> pending,
                                     Path base,
                                     boolean trusted) throws IOException {
        while (!pending.isEmpty()) {
            Path file = pending.removeFirst().normalize();
            if (!loaded.add(file) || !Files.isRegularFile(file)) {
                continue;
            }
            Properties properties = read(file);
            requireApplicable(file, properties);
            addProfiles(pending, base, properties.getProperty("jenesis.make.profiles"));
            layers.add(new Layer(properties, trusted));
        }
    }

    private static void requireApplicable(Path file, Properties properties) {
        for (String name : new TreeSet<>(properties.stringPropertyNames())) {
            if (!name.startsWith("jenesis.")) {
                throw new IllegalArgumentException(name + " in " + file + " is no setting: every key of this file"
                        + " is written in full, as `configuration` prints it, so write jenesis." + name);
            }
            if (RECORDED.contains(name.substring("jenesis.".length()))) {
                throw new IllegalStateException(name + " cannot be set in " + file
                        + ": it records where the settings in force were written, and Make derives it");
            }
        }
        if (properties.getProperty("jenesis.make.root") != null) {
            throw new IllegalStateException("jenesis.make.root cannot be set in " + file
                    + ": the project root locates this file, so it is resolved before the file is read"
                    + " (pass -Djenesis.make.root on the command line instead)");
        }
        if (properties.getProperty("jenesis.make.global") != null) {
            throw new IllegalStateException("jenesis.make.global cannot be set in " + file
                    + ": it locates your own user-global settings, which no file may move, least of all one a"
                    + " project provides (pass -Djenesis.make.global on the command line instead)");
        }
    }

    private static Properties read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        return properties;
    }

    private Path precompiled(Path build, List<Path> sources, String seed) throws IOException {
        Path stamp = root.resolve(".jenesis").resolve("make.digest");
        Path compiled = classes.resolve(mainClass.replace('.', '/') + ".class");
        if (Files.isRegularFile(stamp) && Files.readString(stamp).equals(seed) && Files.isRegularFile(compiled)) {
            return classes;
        }
        ToolProvider javac = ToolProvider.findFirst("javac").orElseThrow(() -> new IllegalStateException(
                "No javac is available to compile the build sources - run on a JDK, not a JRE"));
        List<String> arguments = new ArrayList<>(List.of("-nowarn"));
        if (!classes.equals(root)) {
            if (Files.exists(classes)) {
                Files.walkFileTree(classes, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                            throws IOException {
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            Files.createDirectories(classes);
            arguments.addAll(List.of("-d", classes.toString()));
        }
        sources.forEach(source -> arguments.add(source.toString()));
        StringWriter out = new StringWriter(), error = new StringWriter();
        if (javac.run(new PrintWriter(out), new PrintWriter(error), arguments.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("Failed to compile the build sources under "
                    + build + ":\n" + error + out);
        }
        Files.createDirectories(stamp.getParent());
        Files.writeString(stamp, seed);
        return classes;
    }
}
