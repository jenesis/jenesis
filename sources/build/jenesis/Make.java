package build.jenesis;

import module java.base;

public final class Make {

    private final String mainClass;
    private final Path root;
    private final Path classes;
    private final boolean daemon;
    private final boolean compile;

    public Make(String mainClass) {
        this(mainClass,
                root(),
                classes(configured("jenesis.make.classes")),
                configured("jenesis.make.daemon", false),
                configured("jenesis.make.compile", true));
    }

    private Make(String mainClass, Path root, Path classes, boolean daemon, boolean compile) {
        if (mainClass == null || mainClass.isBlank()) {
            throw new IllegalArgumentException("A build needs the name of the class whose main it should run,"
                    + " such as build.jenesis.Project or a project's own build/Demo.java entry point");
        }
        this.mainClass = mainClass;
        this.root = root;
        this.classes = classes;
        this.daemon = daemon;
        this.compile = compile;
    }

    public Make root(Path root) {
        return new Make(mainClass, root, classes, daemon, compile);
    }

    public Make classes(Path classes) {
        return new Make(mainClass, root, classes, daemon, compile);
    }

    public Make daemon(boolean daemon) {
        return new Make(mainClass, root, classes, daemon, compile);
    }

    public Make compile(boolean compile) {
        return new Make(mainClass, root, classes, daemon, compile);
    }

    public record Result(int code, SequencedMap<String, Path> outputs) {
    }

    public static void main(String... selectors) throws Exception {
        System.exit(new Make("build.jenesis.Project").run(selectors));
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
        Path entry = entry();
        if (entry == null) {
            return daemon
                    ? dispatched(Make.class.getClassLoader(), installed(), engine(), collected, selectors)
                    : invoke(Make.class.getClassLoader(), collected, selectors);
        }
        boolean stop = selectors.length == 1 && selectors[0].equals("--stop");
        if (!daemon && !compile && !stop) {
            return invoke(Make.class.getClassLoader(), collected, selectors);
        }
        Path build = entry.getParent();
        String seed = seedOf(build);
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

    private static String engine() throws IOException {
        CodeSource code = Make.class.getProtectionDomain().getCodeSource();
        Path location;
        try {
            location = Path.of(code.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Cannot resolve where the running engine was loaded from", e);
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable to fingerprint " + location, e);
        }
        if (Files.isRegularFile(location)) {
            digest.update(Files.readAllBytes(location));
            return HexFormat.of().formatHex(digest.digest());
        }
        Path folder = location.resolve("build").resolve("jenesis");
        try (Stream<Path> walk = Files.walk(folder, FileVisitOption.FOLLOW_LINKS)) {
            for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                digest.update(folder.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(file));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private List<String> installed() {
        String modules = System.getProperty("jdk.module.path");
        if (modules != null) {
            return List.of("-p", modules);
        }
        CodeSource code = Make.class.getProtectionDomain().getCodeSource();
        try {
            return code == null
                    ? List.of("-cp", System.getProperty("java.class.path"))
                    : List.of("-cp", Path.of(code.getLocation().toURI()).toString());
        } catch (URISyntaxException _) {
            return List.of("-cp", System.getProperty("java.class.path"));
        }
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
        if (collected == null || !mainClass.equals("build.jenesis.Project")) {
            return (int) project.getMethod("run", String.class, String[].class)
                    .invoke(null, mainClass, selectors);
        }
        Object produced = project.getMethod("perform", String[].class).invoke(null, (Object) selectors);
        if (produced == null) {
            return 1;
        }
        ((Map<?, ?>) produced).forEach((identity, output) -> collected.put(identity.toString(),
                root.relativize(Path.of(output.toString()).toAbsolutePath().normalize()).toString()));
        return 0;
    }

    private static Path root() {
        return Path.of("").toAbsolutePath().normalize();
    }

    private static Path classes(String location) {
        Path root = root();
        return location == null || location.isBlank() ? root : root.resolve(location).normalize();
    }

    private static Path entry() {
        CodeSource code = Make.class.getProtectionDomain().getCodeSource();
        if (code == null) {
            return null;
        }
        Path entry;
        try {
            entry = Path.of(code.getLocation().toURI());
        } catch (URISyntaxException _) {
            return null;
        }
        return entry.getFileName().toString().endsWith(".java") ? entry : null;
    }

    private static boolean configured(String name, boolean fallback) {
        String property = configured(name);
        return property == null ? fallback : Boolean.parseBoolean(property);
    }

    private static String configured(String name) {
        String property = System.getProperty(name);
        if (property != null) {
            return property;
        }
        Path file = root().resolve("jenesis.properties");
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file + " to decide how to run the build", e);
        }
        return properties.getProperty(name);
    }

    private String seedOf(Path build) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable to fingerprint " + build, e);
        }
        digest.update(classes.toString().getBytes(StandardCharsets.UTF_8));
        for (Path source : sourcesOf(build)) {
            digest.update(build.relativize(source).toString().getBytes(StandardCharsets.UTF_8));
            digest.update(Files.readAllBytes(source));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static List<Path> sourcesOf(Path build) throws IOException {
        try (Stream<Path> walk = Files.walk(build, FileVisitOption.FOLLOW_LINKS)) {
            return walk.filter(file -> file.getFileName().toString().endsWith(".java")).sorted().toList();
        }
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
        sourcesOf(build).forEach(source -> arguments.add(source.toString()));
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
