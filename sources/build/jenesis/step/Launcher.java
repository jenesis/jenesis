package build.jenesis.step;

import module java.base;
import java.util.jar.Attributes;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public class Launcher implements BuildStep {

    public static final String LAUNCHER = "launcher/";

    private static final String MAIN_CLASS = "build.jenesis.launcher.Launcher";
    private static final String LAUNCHER_PREFIX = "build/jenesis/launcher/";

    private final String tool;
    private final String group;
    private final PathPlacement pathPlacement;

    public Launcher(String tool, PathPlacement pathPlacement) {
        this(tool, "main", pathPlacement);
    }

    private Launcher(String tool, String group, PathPlacement pathPlacement) {
        this.tool = tool;
        this.group = group;
        this.pathPlacement = pathPlacement;
    }

    public Launcher group(String group) {
        return new Launcher(tool, group, pathPlacement);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String mainClass = null, mainModule = null, name = null;
        Path shaded = null;
        SequencedMap<String, Path> jars = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path properties = argument.folder().resolve("launcher.properties");
            if (Files.isRegularFile(properties)) {
                SequencedProperties application = SequencedProperties.ofFiles(properties);
                if (mainClass == null) {
                    mainClass = application.getProperty("mainClass");
                }
                if (mainModule == null) {
                    mainModule = application.getProperty("mainModule");
                }
                if (name == null) {
                    name = application.getProperty("name");
                }
            }
            for (Path file : Dependencies.select(argument.folder(), tool, "runtime")) {
                shaded = file;
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        jars.putIfAbsent(file.getFileName().toString(), file);
                    }
                }
            }
            for (Path file : Dependencies.select(argument.folder(), group, "runtime")) {
                jars.putIfAbsent(file.getFileName().toString(), file);
            }
        }
        if (mainClass == null || shaded == null || jars.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, SequencedMap<String, Path>> layers = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Map.Entry<String, Path> layer : Layers.folders(argument.folder()).entrySet()) {
                SequencedMap<String, Path> modules = layers.computeIfAbsent(
                        layer.getKey(), _ -> new TreeMap<>());
                try (DirectoryStream<Path> files = Files.newDirectoryStream(layer.getValue())) {
                    for (Path file : files) {
                        modules.putIfAbsent(file.getFileName().toString(), file);
                    }
                }
            }
        }
        SequencedMap<String, Path> classpath = new LinkedHashMap<>(), modulepath = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            boolean onModulePath = mainModule != null && pathPlacement.test(entry.getValue());
            (onModulePath ? modulepath : classpath).put(entry.getKey(), entry.getValue());
        }
        SequencedProperties application = new SequencedProperties();
        application.setProperty("mainClass", mainClass);
        if (mainModule != null) {
            application.setProperty("mainModule", mainModule);
        }
        if (!classpath.isEmpty()) {
            application.setProperty("classpath", String.join(",", classpath.sequencedKeySet()));
        }
        Path descriptor = context.supplement().resolve("application.properties");
        application.store(descriptor);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, MAIN_CLASS);
        Path jar = Files.createDirectory(context.next().resolve(LAUNCHER))
                .resolve((name == null ? "application" : name) + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            explode(out, shaded, "", entry -> entry.startsWith(LAUNCHER_PREFIX) && entry.endsWith(".class"));
            writeEntry(out, "application.properties", descriptor);
            for (Map.Entry<String, Path> entry : classpath.entrySet()) {
                explode(out, entry.getValue(), "classpath/" + entry.getKey() + "/", _ -> true);
            }
            for (Map.Entry<String, Path> entry : modulepath.entrySet()) {
                explode(out, entry.getValue(), "modulepath/" + entry.getKey() + "/", _ -> true);
            }
            // A layer is exploded under a prefix of its own, exactly as a dependency is under modulepath/.
            // The prefix is what keeps its modules off the application's module path, and the launcher finds
            // them there by scanning - the same way it finds the class path and the module path.
            for (Map.Entry<String, SequencedMap<String, Path>> layer : layers.entrySet()) {
                for (Map.Entry<String, Path> entry : layer.getValue().entrySet()) {
                    explode(out, entry.getValue(),
                            "layers/" + layer.getKey() + "/" + entry.getKey() + "/", _ -> true);
                }
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static void explode(JarOutputStream out, Path file, String prefix, Predicate<String> include)
            throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            for (JarEntry entry : (Iterable<JarEntry>) jar.stream()::iterator) {
                if (entry.isDirectory() || !include.test(entry.getName())) {
                    continue;
                }
                JarEntry copy = new JarEntry(prefix + entry.getName());
                copy.setTime(0L);
                out.putNextEntry(copy);
                try (InputStream in = jar.getInputStream(entry)) {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
        }
    }

    private static void writeEntry(JarOutputStream out, String name, Path file) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTime(0L);
        out.putNextEntry(entry);
        Files.copy(file, out);
        out.closeEntry();
    }
}
