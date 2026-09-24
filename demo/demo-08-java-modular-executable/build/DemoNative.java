package build;

import module java.base;

/**
 * Sibling of {@link Demo} that builds a <em>fully bundled native installer</em>
 * rather than a run-in-place app-image. Where {@code Demo} packages {@code app-image}
 * (a directory you launch directly), {@code DemoNative} packages the platform's
 * native installer type - a {@code deb} or {@code rpm} on Linux, an {@code exe} or
 * {@code msi} on Windows, a {@code dmg} or {@code pkg} on macOS - the single
 * artifact you hand to a user to install.
 *
 * It follows the same shape as {@code Demo}: set the packaging type explicitly on
 * the assembler, build the fixed {@code stage} target, and read the result from the
 * fixed {@code stage/packages} key. Only the last step differs: a native installer
 * is a deliverable to be installed, not a program to launch in place, so this
 * reports the produced package instead of running it.
 *
 * Producing a native installer needs the platform's packaging tooling on the PATH
 * (Linux: {@code dpkg-deb}/{@code fakeroot} for {@code deb}, {@code rpmbuild} for
 * {@code rpm}; Windows: the WiX Toolset; macOS: the bundled {@code productbuild}/
 * {@code hdiutil}). Run it from this directory:
 *
 *     java build/DemoNative.java
 */
public class DemoNative {

    static void main(String[] args) throws Exception {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String type;
        if (os.contains("win")) {
            type = "exe";
        } else if (os.contains("mac")) {
            type = "dmg";
        } else {
            type = "deb";
        }
        Path configuration = Files.createTempDirectory("packaging-");
        Files.writeString(configuration.resolve("packaging.properties"), "jpackage=" + type + "\n");
        make("-Djenesis.project.configuration=" + configuration, "stage");

        Path output = Path.of("target", "stage", "packages", "output");

        System.out.println("Built a fully bundled " + type + " installer under " + output + ":");
        try (Stream<Path> files = Files.walk(output)) {
            files.filter(Files::isRegularFile).forEach(file -> System.out.println(
                    "  " + output.relativize(file) + " (" + size(file) + ")"));
        }
        System.out.println("Unlike the app-image, this is a deliverable to install with the platform's "
                + "package manager, not a directory to launch in place.");
    }

    private static String size(Path file) {
        try {
            long bytes = Files.size(file);
            return bytes < 1024 * 1024 ? bytes / 1024 + " KiB" : bytes / (1024 * 1024) + " MiB";
        } catch (IOException e) {
            return "unknown size";
        }
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
