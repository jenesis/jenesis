package build.jenesis;

import module java.base;

public final class Jdk {

    private Jdk() {
    }

    public static boolean image() {
        return Make.image();
    }

    public static Set<String> modules() {
        return Modules.NAMES;
    }

    public static ModuleFinder system() {
        if (Make.image()) {
            throw new Unavailable("The build reads the module descriptors of the JDK at "
                    + System.getProperty("java.home") + ", which a native image of Jenesis does not hold");
        }
        return ModuleFinder.ofSystem();
    }

    public static ModuleLayer boot() {
        if (Make.image()) {
            throw new Unavailable("The build resolves modules against those of the JDK at "
                    + System.getProperty("java.home") + ", which a native image of Jenesis does not hold");
        }
        return ModuleLayer.boot();
    }

    public static Throwable unsupported(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            String name = cause.getClass().getName();
            if (cause instanceof Unavailable
                    || Make.image() && (cause instanceof LinkageError
                    || cause instanceof ClassNotFoundException
                    || name.startsWith("org.graalvm.nativeimage.Missing")
                    || name.equals("com.oracle.svm.core.jdk.UnsupportedFeatureError"))) {
                return cause;
            }
        }
        return null;
    }

    public static Set<String> released(Path home) {
        Properties release = new Properties();
        try (Reader reader = Files.newBufferedReader(home.resolve("release"), StandardCharsets.UTF_8)) {
            release.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("The JDK at " + home + " has no readable release file to name its modules", e);
        }
        String modules = release.getProperty("MODULES", "").replace("\"", "").strip();
        if (modules.isEmpty()) {
            throw new IllegalStateException("The release file of the JDK at " + home + " names no MODULES");
        }
        return Set.of(modules.split("\\s+"));
    }

    private static Path home() {
        String home = System.getProperty("java.home");
        if (home == null) {
            throw new IllegalStateException("A native image of Jenesis reads the modules of the JDK from its release"
                    + " file, but no java.home names one: run the image through Make, which names the JDK it runs on");
        }
        return Path.of(home);
    }

    private static final class Modules {

        private static final Set<String> NAMES = Make.image()
                ? released(home())
                : ModuleFinder.ofSystem().findAll().stream()
                        .map(reference -> reference.descriptor().name())
                        .collect(Collectors.toUnmodifiableSet());
    }

    public static final class Unavailable extends UnsupportedOperationException {

        public Unavailable(String message) {
            super(message);
        }
    }
}
