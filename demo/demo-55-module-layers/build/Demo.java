package build;

import module java.base;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;

/**
 * Builds this project with the {@code bundle} target enabled, unpacks the produced
 * {@code bundle.zip}, and launches the app out of it on this JDK's own {@code java}.
 * The bundle carries a {@code layers/render/} folder beside {@code modulepath/}, and
 * {@code application.properties} names it, so the launch command passes it as
 * {@code -Djenesis.layer.render}. Run it from this directory:
 *
 *     java build/Demo.java
 *
 * which prints two different versions of one module, in one JVM, unrelocated:
 *
 *     the application sees jackson-core 2.18.2, loaded by jdk.internal.loader.ClassLoaders$AppClassLoader@...
 *     the layer sees jackson-core 2.15.4, loaded by jdk.internal.loader.Loader@...
 *     the seam module is the parent's: true
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

        Properties application = new Properties();
        try (InputStream in = Files.newInputStream(unpacked.resolve("application.properties"))) {
            application.load(in);
        }
        String mainClass = application.getProperty("mainClass");
        String mainModule = application.getProperty("mainModule");
        String javaOptions = application.getProperty("javaOptions", "");

        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        Path modulepath = unpacked.resolve("modulepath");
        Path classpath = unpacked.resolve("classpath");
        if (Files.isDirectory(modulepath)) {
            command.add("--module-path");
            command.add(modulepath.toString());
        }
        if (Files.isDirectory(classpath)) {
            command.add("-classpath");
            command.add(classpath.resolve("*").toString());
        }
        for (String option : javaOptions.split(" ")) {
            if (!option.isEmpty()) {
                command.add(option);
            }
        }

        // Every layer the build isolated is announced as layer.<name>=<folder in the
        // bundle>. A deployment resolves each against wherever it unpacked the bundle
        // and hands the absolute folder to the application, which is all the
        // application needs to define the layer for itself.
        for (String key : application.stringPropertyNames()) {
            if (key.startsWith("layer.")) {
                command.add("-Djenesis." + key + "=" + unpacked.resolve(application.getProperty(key)));
            }
        }

        if (mainModule != null) {
            command.add("-m");
            command.add(mainModule + "/" + mainClass);
        } else {
            command.add(mainClass);
        }
        command.addAll(List.of(args));
        System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
    }
}
