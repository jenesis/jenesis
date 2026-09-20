package build;

import module java.base;

/**
 * Reusing a JVM ahead-of-time cache of the build engine instead of keeping a daemon alive.
 *
 * The first build that runs with {@code jenesis.aot.enabled} trains the cache and pays for
 * it; every later build starts from the cache with the engine already loaded and linked.
 * The cache is a file, so nothing stays resident between builds, and it survives a reboot.
 *
 * Run from this directory with:
 *
 *     java build/Demo.java
 */
public class Demo {

    private static final Path CLASSES = Path.of(".jenesis", "classes");

    private static final Path CACHE = Path.of(".jenesis", "engine.aot");

    private static final Path DIGEST = Path.of(".jenesis", "engine.aot.digest");

    static void main(String[] args) throws Exception {
        Files.deleteIfExists(CACHE);
        Files.deleteIfExists(DIGEST);

        // One build in source mode compiles the engine into .jenesis/classes, which is the
        // engine every measurement below runs. A cache serves classes an application loader
        // reads, so source mode itself is out of its reach: the JVM that compiles Make.java
        // is already running by the time any of this tool's code does.
        source();

        // The build as it runs off that engine today, for reference.
        long plain = engine();

        // The first build with the cache enabled trains it: Make relaunches into a JVM that
        // records what the build loads and links, and writes it out when the build is done.
        long trained = engine("-Djenesis.aot.enabled=true");
        if (!Files.isRegularFile(CACHE)) {
            throw new IllegalStateException("No cache was trained at " + CACHE);
        }
        String identity = Files.readString(DIGEST);

        // Every later build starts from that file. The digest names the engine and the JVM
        // it was trained for, the way a daemon is keyed, so an engine or a JVM it does not
        // match is trained again.
        long reused = engine("-Djenesis.aot.enabled=true");
        if (!Files.readString(DIGEST).equals(identity)) {
            throw new IllegalStateException("The cache was trained again instead of reused");
        }

        // What the relaunch costs: a launcher that names the cache on the command line, as
        // an installed CLI can, starts the build from it without a JVM in between.
        long launched = launcher();

        System.out.println();
        System.out.println("Without the cache:      " + plain + " ms");
        System.out.println("Training it:            " + trained + " ms");
        System.out.println("Reusing it:             " + reused + " ms");
        System.out.println("Named by a launcher:    " + launched + " ms");
        System.out.println("Cache:                  " + Files.size(CACHE) / (1 << 20) + " MB at " + CACHE);

        // The two settings a cache contradicts are refused where they meet it.
        System.out.println("Beside the daemon:      "
                + refused("jenesis.make.daemon", "-Djenesis.aot.enabled=true", "-Djenesis.make.daemon=true"));
        System.out.println("Without compiling:      "
                + refused("jenesis.make.compile", "-Djenesis.aot.enabled=true", "-Djenesis.make.compile=false"));

        // A selector that only prints trains nothing: it loads none of the machinery a build
        // loads, so a cache trained on it would serve no build.
        Path unused = Path.of(".jenesis", "help.aot");
        run(List.of("-Djenesis.aot.enabled=true", "-Djenesis.aot.file=" + unused), "help");
        System.out.println("A cache for `help`:     "
                + (Files.exists(unused) ? "trained, which it should not be" : "never trained, as intended"));
    }

    private static void source() throws Exception {
        timed(List.of(java(), "build/jenesis/Make.java", "build"));
    }

    private static long engine(String... options) throws Exception {
        return run(List.of(options), "build");
    }

    private static long run(List<String> options, String selector) throws Exception {
        List<String> command = new ArrayList<>(List.of(java()));
        command.addAll(options);
        command.addAll(List.of("-cp", CLASSES.toString(), "build.jenesis.Make", selector));
        return timed(command);
    }

    private static long launcher() throws Exception {
        return timed(List.of(java(),
                "-XX:AOTCache=" + CACHE,
                "-Xlog:aot=off",
                "-cp", Path.of(".jenesis", "engine.jar").toString(),
                "build.jenesis.Make",
                "build"));
    }

    private static String refused(String setting, String... options) throws Exception {
        List<String> command = new ArrayList<>(List.of(java()));
        command.addAll(List.of(options));
        command.addAll(List.of("-cp", CLASSES.toString(), "build.jenesis.Make", "build"));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() == 0) {
            throw new IllegalStateException(String.join(" ", command)
                    + " was accepted:"
                    + System.lineSeparator()
                    + output);
        }
        if (!output.contains(setting) || !output.contains("jenesis.aot.enabled")) {
            throw new IllegalStateException("The refusal named neither setting:" + System.lineSeparator() + output);
        }
        return "refused, naming " + setting;
    }

    private static long timed(List<String> command) throws Exception {
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException(String.join(" ", command) + " failed:" + System.lineSeparator() + output);
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }
}
