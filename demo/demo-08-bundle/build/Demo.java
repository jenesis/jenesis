package build;

import module java.base;
import build.jenesis.Make;

/**
 * Builds this modular app with the {@code bundle} target enabled, unpacks the
 * produced {@code bundle.zip}, and launches the app out of it on this JDK's own
 * {@code java} - exactly the way a consumer would run the bundle on a stock JRE
 * base image. Run it from this directory, passing whatever arguments you want the
 * app to receive:
 *
 *     java build/Demo.java Ada Lovelace
 *
 * which builds the bundle, unpacks it, and prints (from the launched app):
 *
 *     Hello, Ada Lovelace, from a Jenesis bundle.zip on a stock JRE!
 */
public class Demo {

    static void main(String[] args) throws Exception {
        build();

        Path zip;
        try (Stream<Path> walk = Files.walk(Path.of("target"))) {
            zip = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("bundle.zip")
                            && path.getParent().getFileName().toString().equals("bundle")
                            && path.getParent().getParent().getFileName().toString().equals("output"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No bundle.zip was produced"));
        }

        Path unpacked = Files.createTempDirectory("bundle-");
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path target = unpacked.resolve(entry.getName()).normalize();
                if (!target.startsWith(unpacked)) {
                    throw new IOException("Bundle entry escapes the target directory: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    try (InputStream in = archive.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("@application." + (File.pathSeparatorChar == ';' ? "windows" : "unix") + ".args");
        command.addAll(List.of(args));
        System.exit(new ProcessBuilder(command).directory(unpacked.toFile()).inheritIO().start().waitFor());
    }

    private static void build(String... selectors) throws Exception {
        if (new Make("build.jenesis.Project").build(selectors).code() != 0) {
            throw new IllegalStateException("The build exited with a non-zero status");
        }
    }
}
