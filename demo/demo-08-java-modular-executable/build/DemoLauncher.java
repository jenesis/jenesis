package build;

import module java.base;

/**
 * Builds this project with the launcher target enabled, then runs the produced
 * executable jar with {@code java -jar}, forwarding this program's own arguments.
 *
 * The launcher target (selected by a {@code launcher=true} packaging.properties)
 * resolves {@code build.jenesis:build.jenesis.launcher} from Maven Central and shades
 * it into an executable jar: the launcher's classes sit in the jar root as its
 * {@code Main-Class}, every dependency is exploded into its own {@code classpath/<name>/}
 * or {@code modulepath/<name>/} subfolder, and {@code application.properties} names the
 * entry point - so {@code java -jar foo.jar} reconstructs the module graph and runs the
 * application while keeping full modularity.
 *
 *     java build/DemoLauncher.java Ada Lovelace
 */
public class DemoLauncher {

    static void main(String[] args) throws Exception {
        make("-Djenesis.make.profiles=launcher");

        Path jar;
        try (Stream<Path> walk = Files.walk(Path.of("target"))) {
            jar = walk.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".jar")
                            && path.getParent().getFileName().toString().equals("launcher")
                            && path.getParent().getParent().getFileName().toString().equals("output"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No launcher jar was produced"));
        }

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-jar");
        command.add(jar.toString());
        command.addAll(List.of(args));
        System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
    }

    private static void make(String flag, String... selectors) throws IOException, InterruptedException {
        String java = ProcessHandle.current().info().command().orElseGet(() -> Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                        ? "java.exe"
                        : "java").toString());
        List<String> command = new ArrayList<>(List.of(java, flag, "build/jenesis/Make.java"));
        command.addAll(List.of(selectors));
        if (new ProcessBuilder(command).inheritIO().start().waitFor() != 0) {
            throw new IllegalStateException("The build exited with a non-zero status");
        }
    }
}
