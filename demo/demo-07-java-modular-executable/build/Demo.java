package build;

import module java.base;
import build.jenesis.Make;

/**
 * Builds this project's {@code stage} goal with jpackage packaging enabled, then
 * launches the produced application image, forwarding this program's own
 * arguments to the packaged application's {@code main} method.
 *
 * Run it from this directory, passing whatever arguments you want the packaged
 * app to receive:
 *
 *     java build/Demo.java Ada Lovelace
 *
 * which builds the image and then prints (from the launched app):
 *
 *     Hello, Ada Lovelace, from a packaged Java module built by Jenesis!
 */
public class Demo {

    static void main(String[] args) throws Exception {
        // Packaging is selected by the committed packaging.properties in this directory
        // (jpackage=app-image), which Jenesis reads from the configuration location for every
        // module, so this runs the tool exactly as the command line does and needs no wiring.
        // `stage/packages` is a fixed build target: building `stage` returns a map keyed by the
        // steps that ran, so the image folder is read straight from that map rather than
        // reconstructed by hand.
        Make.Result staged = new Make("build.jenesis.Project").build("stage");
        if (staged.code() != 0) {
            throw new IllegalStateException("The build exited with a non-zero status");
        }
        SequencedMap<String, Path> outputs = staged.outputs();
        Path output = outputs.get("stage/packages");

        // The image folder is fixed too: jpackage names it after --name, which the build
        // derives from this project's coordinate (the module name). So the launcher path
        // is fully determined - no directory scanning needed.
        // jpackage names the image after --name (here the module name) and lays the
        // launcher out per platform - Windows <name>/<name>.exe, macOS a <name>.app
        // bundle, every other OS <name>/bin/<name> - so the path is fixed once the OS
        // is known; no scanning is needed.
        String name = "demo.modular.executable";
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path launcher;
        if (os.contains("win")) {
            launcher = output.resolve(name).resolve(name + ".exe");
        } else if (os.contains("mac")) {
            launcher = output.resolve(name + ".app").resolve("Contents").resolve("MacOS").resolve(name);
        } else {
            launcher = output.resolve(name).resolve("bin").resolve(name);
        }

        List<String> command = new ArrayList<>();
        command.add(launcher.toString());
        command.addAll(List.of(args));
        System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
    }
}
