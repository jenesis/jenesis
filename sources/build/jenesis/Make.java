package build.jenesis;

import module java.base;

public final class Make {

    private static final String PROVIDED = "jenesis.make.provided";
    private static final List<String> CREDENTIALS = List.of("jenesis.maven.token",
            "jenesis.module.token",
            "jenesis.cache.key");
    private static final List<String> PLAINTEXT = List.of("jenesis.repository.insecure", "jenesis.cache.insecure");

    private final String mainClass;
    private final Path root;
    private final Path classes;
    private final boolean daemon;
    private final boolean compile;
    private final Settings settings;

    public record Settings(Function<String, String> keys, SequencedSet<Path> profiles) {
    }

    private record Layer(Properties properties, boolean trusted) {
    }

    public Make(String mainClass) {
        this.mainClass = mainClass;
        root = Path.of(System.getProperty("jenesis.make.root", "")).toAbsolutePath().normalize();
        try {
            settings = settings(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the properties that configure this build", e);
        }
        String location = settings.keys().apply("make.classes");
        classes = location == null || location.isBlank()
                ? root.resolve(".jenesis").resolve("classes")
                : root.resolve(location).normalize();
        daemon = flag(settings.keys(), "make.daemon", false);
        compile = flag(settings.keys(), "make.compile", true);
    }

    private static boolean flag(Function<String, String> keys, String key, boolean defaultValue) {
        String value = keys.apply(key);
        if (value == null) {
            return defaultValue;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "", "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Malformed value for jenesis."
                    + key
                    + ": '"
                    + value
                    + "' (expected true, false, or the property named with no value at all)");
        };
    }

    private Make(String mainClass, Path root, Path classes, boolean daemon, boolean compile, Settings settings) {
        this.mainClass = mainClass;
        this.root = root;
        this.classes = classes;
        this.daemon = daemon;
        this.compile = compile;
        this.settings = settings;
    }

    public Make root(Path root) {
        try {
            return new Make(mainClass, root, classes, daemon, compile, settings(root));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the properties that configure this build", e);
        }
    }

    public Make classes(Path classes) {
        return new Make(mainClass, root, classes, daemon, compile, settings);
    }

    public Make daemon(boolean daemon) {
        return new Make(mainClass, root, classes, daemon, compile, settings);
    }

    public Make compile(boolean compile) {
        return new Make(mainClass, root, classes, daemon, compile, settings);
    }

    public record Result(int code, SequencedMap<String, Path> outputs) {
    }

    public static void main(String... selectors) throws Exception {
        List<String> options = options();
        Make make = new Make("build.jenesis.Project");
        Integer code = relaunched(Make.class, options, selectors);
        System.exit(code == null ? make.run(selectors) : code);
    }

    static List<String> options() {
        List<String> options = new ArrayList<>();
        for (String name : new TreeSet<>(System.getProperties().stringPropertyNames())) {
            if (name.startsWith("jenesis.") && !name.startsWith("jenesis.toolchain.")) {
                options.add("-D" + name + "=" + System.getProperty(name));
            }
        }
        return options;
    }

    static Integer relaunched(Class<?> main, List<String> options, String... arguments) throws Exception {
        String version = System.getProperty("jenesis.toolchain.version");
        if (version == null || version.isBlank()) {
            return null;
        }
        Class<?> type = Class.forName("build.jenesis.Toolchain", true, Make.class.getClassLoader());
        try {
            Object toolchain = type.getMethod("ofKeys", Function.class)
                    .invoke(null, (Function<String, String>) key -> System.getProperty("jenesis." + key));
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
        boolean stop = selectors.length == 1 && selectors[0].equals("--stop");
        if (location == null || !location.getFileName().toString().endsWith(".java")) {
            if (!daemon) {
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
        if (!daemon && !compile && !stop) {
            return invoke(Make.class.getClassLoader(), collected, selectors);
        }
        Path build = location.getParent();
        String seed = fingerprint(build, files(build, ".java"), classes.toString());
        Path folder = precompiled(build, seed);
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { folder.toUri().toURL() },
                ClassLoader.getPlatformClassLoader())) {
            if (daemon || stop) {
                return dispatched(loader, List.of("-cp", folder.toString()), seed, collected, selectors);
            }
            return invoke(loader, collected, selectors);
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
                .getMethod("doDispatch", Path.class, List.class, String.class, String.class,
                        SequencedMap.class, String[].class)
                .invoke(null, root, path, mainClass, seed, collected, selectors);
    }

    private int invoke(ClassLoader loader, SequencedMap<String, String> collected, String... selectors)
            throws Exception {
        Class<?> project = Class.forName("build.jenesis.Project", true, loader);
        Class<?> console = Class.forName("build.jenesis.Output", true, loader);
        Object defaults = console.getConstructor().newInstance();
        if (collected == null || !mainClass.equals("build.jenesis.Project")) {
            return (int) project
                    .getMethod("run", Function.class, console, String.class, Path.class, SequencedSet.class, String[].class)
                    .invoke(null, settings.keys(), defaults, mainClass, root, settings.profiles(), selectors);
        }
        Object produced = project
                .getMethod("perform", Function.class, console, Path.class, SequencedSet.class, String[].class)
                .invoke(null, settings.keys(), defaults, root, settings.profiles(), selectors);
        if (produced == null) {
            return 1;
        }
        ((Map<?, ?>) produced).forEach((identity, output) -> collected.put(identity.toString(),
                root.relativize(Path.of(output.toString()).toAbsolutePath().normalize()).toString()));
        return 0;
    }

    public static Settings settings(Path path) throws IOException {
        return settings(path, key -> System.getProperty("jenesis." + key));
    }

    public static Settings settings(Path path, Function<String, String> ambient) throws IOException {
        Path base = path.resolve("jenesis.properties");
        Properties project = read(base);
        if (project != null) {
            requireApplicable(base, project, false);
        }
        String configured = ambient.apply("make.global");
        String location = configured == null ? System.getProperty("user.home") : configured;
        Properties user = null;
        Path home = null;
        if (!location.isEmpty()) {
            home = Path.of(location).resolve(".jenesis");
            Path file = home.resolve("jenesis.properties");
            user = read(file);
            if (user != null) {
                requireApplicable(file, user, true);
            }
        }
        Set<Path> loaded = new LinkedHashSet<>();
        Deque<Path> pending = new ArrayDeque<>();
        List<Layer> layers = new ArrayList<>();
        addProfiles(pending, path, ambient.apply("make.profiles"));
        if (project != null) {
            addProfiles(pending, path, project.getProperty("jenesis.make.profiles"));
        }
        loadProfiles(loaded, layers, pending, path, false);
        if (user != null) {
            addProfiles(pending, home, user.getProperty("jenesis.make.profiles"));
            loadProfiles(loaded, layers, pending, home, true);
        }
        if (project != null) {
            layers.add(new Layer(project, false));
        }
        if (user != null) {
            layers.add(new Layer(user, true));
        }
        SequencedSet<Path> profiles = new LinkedHashSet<>();
        for (Path file : loaded) {
            String name = file.getFileName().toString();
            profiles.add(Path.of(name.substring("jenesis-".length(), name.length() - ".properties".length())));
        }
        return new Settings(layered(ambient, layers), profiles);
    }

    private static Function<String, String> layered(Function<String, String> ambient, List<Layer> layers) {
        List<Layer> ordered = List.copyOf(layers);
        SequencedSet<String> provided = new LinkedHashSet<>();
        Set<String> declared = new HashSet<>();
        for (Layer layer : ordered) {
            for (String name : layer.properties().stringPropertyNames()) {
                if (!name.startsWith("jenesis.")) {
                    continue;
                }
                String key = name.substring("jenesis.".length());
                if (declared.add(key) && ambient.apply(key) == null && !layer.trusted()) {
                    provided.add(key);
                }
            }
        }
        String supplied = String.join(",", provided);
        return key -> {
            if (key.equals("make.provided")) {
                return supplied.isEmpty() ? null : supplied;
            }
            String value = ambient.apply(key);
            if (value != null) {
                return value;
            }
            String qualified = "jenesis." + key;
            for (Layer layer : ordered) {
                value = layer.properties().getProperty(qualified);
                if (value != null) {
                    return value;
                }
            }
            return null;
        };
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
            requireApplicable(file, properties, trusted);
            addProfiles(pending, base, properties.getProperty("jenesis.make.profiles"));
            layers.add(new Layer(properties, trusted));
        }
    }

    private static void requireApplicable(Path file, Properties properties, boolean trusted) {
        if (properties.getProperty(PROVIDED) != null) {
            throw new IllegalStateException(PROVIDED + " cannot be set in " + file
                    + ": it records which settings the files a project provides supplied, and Make derives it");
        }
        if (!trusted) {
            for (String key : CREDENTIALS) {
                if (properties.getProperty(key) != null) {
                    throw new IllegalStateException(key + " cannot be set in " + file
                            + ": a credential is yours to hand out, so only the command line, your own"
                            + " ~/.jenesis/jenesis.properties or the environment may name one"
                            + " (pass -D" + key + " instead)");
                }
            }
            for (String key : PLAINTEXT) {
                if (properties.getProperty(key) != null) {
                    throw new IllegalStateException(key + " cannot be set in " + file
                            + ": whether this build may talk plaintext http is yours to decide, so only the"
                            + " command line or your own ~/.jenesis/jenesis.properties may allow it"
                            + " (pass -D" + key + " instead)");
                }
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
        if (!trusted && properties.getProperty("jenesis.toolchain.searchpath") != null) {
            throw new IllegalStateException("jenesis.toolchain.searchpath cannot be set in " + file
                    + ": the folders searched for a JDK decide what the build executes, so only the command line"
                    + " or your own ~/.jenesis/jenesis.properties may name them, never a file the project provides"
                    + " (pass -Djenesis.toolchain.searchpath instead)");
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

    private Path precompiled(Path build, String seed) throws IOException {
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
        files(build, ".java").forEach(source -> arguments.add(source.toString()));
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
