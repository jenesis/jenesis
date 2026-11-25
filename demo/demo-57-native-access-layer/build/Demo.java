package build;

import module java.base;
import build.jenesis.Make;

/**
 * Builds this project, unpacks the produced {@code bundle.zip}, and launches the app out of it on
 * this JDK's own {@code java}, refusing native access to every module the launch does not grant.
 * Run it from this directory:
 *
 *     java build/Demo.java
 *
 * which prints
 *
 *     strlen("layered") = 7, measured in the library's layer with native access true
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

        Path unpacked = Files.createTempDirectory("strings-");
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
        command.add("--illegal-native-access=deny");
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
