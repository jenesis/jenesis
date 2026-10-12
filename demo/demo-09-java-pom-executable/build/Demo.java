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
 *     Hello, Ada Lovelace, from a packaged Maven project built by Jenesis!
 */
public class Demo {

    static void main(String[] args) throws Exception {
        Make.Result staged = new Make("build.jenesis.Project").build("stage");
        if (staged.code() != 0) {
            throw new IllegalStateException("The build exited with a non-zero status");
        }
        SequencedMap<String, Path> outputs = staged.outputs();
        Path output = outputs.get("stage/packages");

        String name = "java-pom-executable";
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
