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
    private final OffsetDateTime timestamp;

    public Launcher(String tool, PathPlacement pathPlacement) {
        this(tool, "main", pathPlacement, BuildStep.timestamp());
    }

    private Launcher(String tool, String group, PathPlacement pathPlacement, OffsetDateTime timestamp) {
        this.tool = tool;
        this.group = group;
        this.pathPlacement = pathPlacement;
        this.timestamp = timestamp;
    }

    public Launcher group(String group) {
        return new Launcher(tool, group, pathPlacement, timestamp);
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
        SequencedMap<String, Layers.Membership> layers = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            layers.putAll(Layers.membership(argument.folder()));
        }
        SequencedSet<String> named = new LinkedHashSet<>();
        layers.values().forEach(membership -> named.addAll(membership.all()));
        SequencedMap<String, Path> isolated = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path file : Dependencies.all(argument.folder())) {
                if (named.contains(file.getFileName().toString())) {
                    isolated.putIfAbsent(file.getFileName().toString(), file);
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
        application.setProperty("classpath", String.join(",", classpath.sequencedKeySet()));
        application.setProperty("modulepath", String.join(",", modulepath.sequencedKeySet()));
        layers.forEach((layer, membership) -> {
            application.setProperty("modulepath." + layer,
                    String.join(",", membership.modulepath()));
            if (!membership.classpath().isEmpty()) {
                application.setProperty("classpath." + layer,
                        String.join(",", membership.classpath()));
            }
        });
        Path descriptor = context.supplement().resolve("application.properties");
        application.store(descriptor);
        SequencedMap<String, Path> stored = new TreeMap<>(classpath);
        stored.putAll(modulepath);
        for (Map.Entry<String, Layers.Membership> layer : layers.entrySet()) {
            for (String member : layer.getValue().all()) {
                Path file = isolated.get(member);
                if (file == null) {
                    throw new IllegalStateException("Layer " + layer.getKey() + " names " + member
                            + ", which was not resolved for this application");
                }
                stored.putIfAbsent(member, file);
            }
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, MAIN_CLASS);
        Path jar = Files.createDirectory(context.next().resolve(LAUNCHER))
                .resolve((name == null ? "application" : name) + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            writeManifest(out, manifest);
            explode(out, shaded, "", entry -> entry.startsWith(LAUNCHER_PREFIX) && entry.endsWith(".class"));
            writeEntry(out, "application.properties", descriptor);
            for (Map.Entry<String, Path> entry : stored.entrySet()) {
                explode(out, entry.getValue(), "jars/" + entry.getKey() + "/", _ -> true);
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private void explode(JarOutputStream out, Path file, String prefix, Predicate<String> include)
            throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            for (JarEntry entry : (Iterable<JarEntry>) jar.stream()::iterator) {
                if (entry.isDirectory() || !include.test(entry.getName())) {
                    continue;
                }
                JarEntry copy = new JarEntry(prefix + entry.getName());
                copy.setTimeLocal(timestamp.toLocalDateTime());
                out.putNextEntry(copy);
                try (InputStream in = jar.getInputStream(entry)) {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
        }
    }

    private void writeManifest(JarOutputStream out, Manifest manifest) throws IOException {
        JarEntry entry = new JarEntry(JarFile.MANIFEST_NAME);
        entry.setTimeLocal(timestamp.toLocalDateTime());
        out.putNextEntry(entry);
        manifest.write(out);
        out.closeEntry();
    }

    private void writeEntry(JarOutputStream out, String name, Path file) throws IOException {
        JarEntry entry = new JarEntry(name);
        entry.setTimeLocal(timestamp.toLocalDateTime());
        out.putNextEntry(entry);
        Files.copy(file, out);
        out.closeEntry();
    }
}
