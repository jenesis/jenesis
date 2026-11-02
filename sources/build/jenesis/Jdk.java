package build.jenesis;

import module java.base;

public final class Jdk {

    private Jdk() {
    }

    public static boolean image() {
        return Make.image();
    }

    public static Path home() {
        return Make.home();
    }

    public static Set<String> modules() {
        return Make.image() ? Installed.MODULES : Running.MODULES;
    }

    public static ModuleFinder system() {
        if (Make.image()) {
            throw new Unavailable("The modules of the JDK at " + home() + " are read in the build, which a native"
                    + " image of Jenesis cannot do");
        }
        return ModuleFinder.ofSystem();
    }

    public static ModuleLayer boot() {
        if (Make.image()) {
            throw new Unavailable("Modules are resolved against those of the JDK at " + home() + " in the build,"
                    + " which a native image of Jenesis cannot do");
        }
        return ModuleLayer.boot();
    }

    public static boolean unregistered(Throwable throwable) {
        String name = throwable.getClass().getName();
        return Make.image() && (throwable instanceof LinkageError
                || throwable instanceof ClassNotFoundException
                || name.startsWith("org.graalvm.nativeimage.Missing")
                || name.equals("com.oracle.svm.core.jdk.UnsupportedFeatureError"));
    }

    public static Properties compiled() {
        return Compiled.PLUGINS;
    }

    public static Map<String, String> release(Path home) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(home.resolve("release"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("The JDK at " + home + " has no readable release file that names its"
                    + " modules: point JAVA_HOME at a JDK 25 or newer", e);
        }
        Map<String, String> release = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key).strip();
            release.put(key, value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")
                    ? value.substring(1, value.length() - 1)
                    : value);
        }
        return release;
    }

    private static final class Installed {

        private static final Set<String> MODULES;

        static {
            String modules = release(home()).get("MODULES");
            if (modules == null || modules.isBlank()) {
                throw new IllegalStateException("The release file of the JDK at " + home() + " names no MODULES:"
                        + " point JAVA_HOME at a JDK 25 or newer");
            }
            MODULES = Set.of(modules.split("\\s+"));
        }
    }

    private static final class Compiled {

        private static final Properties PLUGINS = new Properties();

        static {
            try (InputStream input = Jdk.class.getClassLoader().getResourceAsStream(Make.PLUGINS)) {
                if (input != null) {
                    PLUGINS.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final class Running {

        private static final Set<String> MODULES = ModuleFinder.ofSystem().findAll().stream()
                .map(reference -> reference.descriptor().name())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static final class Unavailable extends UnsupportedOperationException {

        public Unavailable(String message) {
            super(message);
        }
    }
}
