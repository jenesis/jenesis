package build;

import module java.base;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;

/**
 * Builds this project, unpacks the produced {@code bundle.zip}, and launches the app out of it on
 * this JDK's own {@code java}. Run it from this directory:
 *
 *     java build/Demo.java
 *
 * which prints a legacy tree running privately inside a layer - one jar named, the rest read off
 * the layer's own class path:
 *
 *     the application requires one module, and knows no commons-anything
 *     the library's private tree: commons-beanutils converted "42" to Integer 42, logging through
 *         commons-logging/commons-logging/jar/1.2.jar, loaded by ...
 */
public class Demo {

    static void main(String[] args) throws Exception {
        Project project = new Project(Path.of("."))
                .assembler(new InferredMultiProjectAssembler());
        project.build();

        Path zip;
        try (Stream<Path> walk = Files.walk(Path.of("target"))) {
            zip = walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("bundle.zip")
                            && path.getParent().getFileName().toString().equals("bundle")
                            && path.getParent().getParent().getFileName().toString().equals("output"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No bundle.zip was produced"));
        }

        Path unpacked = Files.createTempDirectory("layers-");
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

        // The argument file is the launch itself: the module path, the layer properties and the entry
        // point are all in it, so a deployment runs `java @application.<platform>.args` from the folder
        // it unpacked into. Each layer travels as -Djlayer.<path>.<name>, the property the
        // library's own code reads; inside an executable jar no such option is needed, because the
        // launcher reads the layer out of the jar it is already holding open.
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("@application." + (File.pathSeparatorChar == ';' ? "windows" : "unix") + ".args");
        command.addAll(List.of(args));
        System.exit(new ProcessBuilder(command).directory(unpacked.toFile()).inheritIO().start().waitFor());
    }
}
