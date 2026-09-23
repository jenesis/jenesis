package build;

import module java.base;
import build.jenesis.ExecuteTool;
import build.jenesis.JpxTool;
import build.jenesis.MakeTool;

/**
 * Runs Jenesis through java.util.spi.ToolProvider, in this one JVM. Each run is
 * configured by the -D arguments it is handed rather than by the properties of this JVM,
 * and everything each build prints arrives on the writers this program supplies.
 *
 * When build.jenesis is a resolved module the service loader finds a tool by the name the
 * command line uses. In source mode there is no module to load a service from, so the demo
 * builds the same tools directly and says which of the two it used. Run it from this
 * directory:
 *
 *     java build/Demo.java
 *
 * which prints the version each build stamped onto its jar, the line the built program
 * writes when jenesis-exec runs it, the exit code jpx answers --help with, and then the
 * value this JVM holds for the setting the builds were given, which is none:
 *
 *     no service loader here, so the tools are built directly - the contract below is the same
 *     jenesis-make -Djenesis.project.version=1.0.0 @target/build.args -> 0, produced demo.tools@1.0.0
 *     jenesis-make -Djenesis.project.version=2.0.0 @target/build.args -> 0, produced demo.tools@2.0.0
 *     jenesis-exec builds and runs the program, which prints on its own:
 *     hello
 *     jenesis-exec -> 0
 *     jpx --help -> 0
 *     jenesis.project.version in this JVM: null
 */
public class Demo {

    public static void main(String... args) throws Exception {
        boolean discovered = ToolProvider.findFirst("jenesis-make").isPresent();
        System.out.println(discovered
                ? "found the Jenesis tools through the service loader"
                : "no service loader here, so the tools are built directly - the contract below is the same");
        ToolProvider make = tool("jenesis-make", MakeTool::new);
        Path arguments = Files.writeString(
                Files.createDirectories(Path.of("target")).resolve("build.args"), """
                # the settings and selectors this run stands for
                -Djenesis.print.progress=false
                build
                """);
        for (String version : List.of("1.0.0", "2.0.0")) {
            StringWriter out = new StringWriter(), err = new StringWriter();
            int code = make.run(new PrintWriter(out),
                    new PrintWriter(err),
                    "-Djenesis.project.version=" + version,
                    "@" + arguments);
            if (code != 0) {
                throw new IllegalStateException("Build for " + version + " failed with " + code + "\n" + out + err);
            }
            System.out.printf("jenesis-make -Djenesis.project.version=%s @%s -> %d, produced %s%n",
                    version, arguments, code, stamped(Path.of("target")));
        }
        StringWriter out = new StringWriter(), err = new StringWriter();
        System.out.println("jenesis-exec builds and runs the program, which prints on its own:");
        int executed = tool("jenesis-exec", ExecuteTool::new).run(new PrintWriter(out),
                new PrintWriter(err),
                "-Djenesis.project.version=2.0.0",
                "-Djenesis.print.progress=false");
        if (executed != 0) {
            throw new IllegalStateException("Running the program failed with " + executed + "\n" + out + err);
        }
        System.out.printf("jenesis-exec -> %d%n", executed);
        out = new StringWriter();
        err = new StringWriter();
        int helped = tool("jpx", JpxTool::new).run(new PrintWriter(out), new PrintWriter(err), "--help");
        if (helped != 0 || !out.toString().startsWith("Usage:")) {
            throw new IllegalStateException("jpx did not answer --help: " + helped + "\n" + out + err);
        }
        System.out.printf("jpx --help -> %d%n", helped);
        System.out.println("jenesis.project.version in this JVM: " + System.getProperty("jenesis.project.version"));
    }

    private static ToolProvider tool(String name, Supplier<ToolProvider> fallback) {
        return ToolProvider.findFirst(name).orElseGet(fallback);
    }

    private static String stamped(Path folder) throws IOException {
        try (Stream<Path> walk = Files.walk(folder)) {
            Path jar = walk.filter(path -> path.getFileName().toString().equals("classes.jar")).findFirst()
                    .orElseThrow(() -> new IllegalStateException("The build produced no jar below " + folder));
            return ModuleFinder.of(jar).findAll().stream()
                    .map(reference -> reference.descriptor().toNameAndVersion())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No module descriptor in " + jar));
        }
    }
}
