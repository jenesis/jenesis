package build;

import module java.base;

/**
 * Reusing a JVM ahead-of-time cache of the build engine instead of keeping a daemon alive.
 *
 * The first build that runs with {@code jenesis.aot.enabled} trains the cache and pays for
 * it; every later build starts from the cache and loads the engine already linked. The cache
 * is a file, so nothing stays resident between builds, and it survives a reboot.
 *
 * Run from this directory with:
 *
 *     java build/Demo.java
 */
public class Demo {

    static void main(String[] args) throws Exception {
        Path cache = Path.of(".jenesis", "engine.aot"), digest = Path.of(".jenesis", "engine.aot.digest");
        Files.deleteIfExists(cache);
        Files.deleteIfExists(digest);

        // One build to compile the engine this demo vendors, so that no measurement below
        // pays for a compilation the cache has nothing to do with.
        timed("build");

        // A build without the cache, for reference.
        long plain = timed("build");

        // The first build with the cache enabled trains it: the JVM records what the build
        // loads and links, and writes it out when the build is done.
        long trained = timed("-Djenesis.aot.enabled=true", "build");
        if (!Files.isRegularFile(cache)) {
            throw new IllegalStateException("No cache was trained at " + cache);
        }
        String identity = Files.readString(digest);

        // Every later build starts from that file. The digest names the engine and the JVM
        // it was trained for, so an engine or a JDK it does not match is trained again.
        long reused = timed("-Djenesis.aot.enabled=true", "build");
        if (!Files.readString(digest).equals(identity)) {
            throw new IllegalStateException("The cache was trained again instead of reused");
        }

        // Where the saving actually lands: an entry point whose own JVM is cheap. The
        // installed CLI runs the engine from a jar, so its launcher can name the cache on
        // the command line and skip the relaunch this demo's source mode needs.
        Path jar = Path.of(".jenesis", "engine.jar");
        long engine = engine(jar, null);
        long engineCached = engine(jar, cache);

        System.out.println();
        System.out.println("Without the cache:      " + plain + " ms");
        System.out.println("Training it:            " + trained + " ms");
        System.out.println("Reusing it:             " + reused + " ms");
        System.out.println("Cache:                  " + Files.size(cache) / (1 << 20) + " MB at " + cache);
        System.out.println();
        System.out.println("The same build off the compiled engine, as the installed CLI runs it:");
        System.out.println("  without the cache:    " + engine + " ms");
        System.out.println("  with the cache:       " + engineCached + " ms");

        // A selector that only prints trains nothing: it loads none of the machinery a build
        // loads, so a cache trained on it would serve no build.
        Path unused = Path.of(".jenesis", "help.aot");
        timed("-Djenesis.aot.enabled=true", "-Djenesis.aot.file=" + unused, "help");
        System.out.println("A cache for `help`:     "
                + (Files.exists(unused) ? "trained, which it should not be" : "never trained, as intended"));
    }

    private static long engine(Path jar, Path cache) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        if (cache != null) {
            command.add("-XX:AOTCache=" + cache);
            command.add("-Xlog:aot=off");
        }
        command.addAll(List.of("-cp", jar.toString(), "build.jenesis.Make", "build"));
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException(String.join(" ", command) + " failed:" + System.lineSeparator() + output);
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static long timed(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(List.of(arguments).subList(0, arguments.length - 1));
        command.add("build/jenesis/Make.java");
        command.add(arguments[arguments.length - 1]);
        long start = System.nanoTime();
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException(String.join(" ", command) + " failed:" + System.lineSeparator() + output);
        }
        return (System.nanoTime() - start) / 1_000_000;
    }
}
