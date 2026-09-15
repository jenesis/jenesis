package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public class Bundle implements BuildStep {

    public static final String BUNDLE = "bundle/";

    private final String group;

    public Bundle() {
        this("main");
    }

    private Bundle(String group) {
        this.group = group;
    }

    public Bundle group(String group) {
        return new Bundle(group);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String mainClass = null, mainModule = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path properties = argument.folder().resolve("launcher.properties");
            if (!Files.isRegularFile(properties)) {
                continue;
            }
            SequencedProperties launcher = SequencedProperties.ofFiles(properties);
            if (mainClass == null) {
                mainClass = launcher.getProperty("mainClass");
            }
            if (mainModule == null) {
                mainModule = launcher.getProperty("mainModule");
            }
        }
        if (mainClass == null) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, Path> jars = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
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
        if (jars.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, Path> classpath = new LinkedHashMap<>(), modulepath = new LinkedHashMap<>();
        ModuleGraph graph = new ModuleGraph();
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            if (mainModule != null) {
                (graph.place(PathPlacement.INFERRED, entry.getValue()) ? modulepath : classpath)
                        .put(entry.getKey(), entry.getValue());
            } else {
                classpath.put(entry.getKey(), entry.getValue());
            }
        }
        SequencedMap<String, SequencedSet<String>> layers = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            layers.putAll(Layers.membership(argument.folder()));
        }
        // A layer's jars are resolved in a group of its own, so they are not in the application's
        // selection; take them by the names the layer records, and nothing else that happens to be
        // resolved - a tool's own closure is not part of the application.
        SequencedSet<String> named = new LinkedHashSet<>();
        layers.values().forEach(named::addAll);
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.all(argument.folder())) {
                if (named.contains(jar.getFileName().toString())) {
                    jars.putIfAbsent(jar.getFileName().toString(), jar);
                }
            }
        }
        SequencedProperties application = new SequencedProperties();
        application.setProperty("mainClass", mainClass);
        if (mainModule != null) {
            application.setProperty("mainModule", mainModule);
        }
        // Every path is spelled out rather than handed over as a folder, which is what lets one store hold
        // the application's jars and every layer's alike: a jar more than one path names is stored once.
        application.setProperty("classpath", String.join(",", classpath.sequencedKeySet()));
        application.setProperty("modulepath", String.join(",", modulepath.sequencedKeySet()));
        layers.forEach((name, names) -> application.setProperty("layer." + name, String.join(",", names)));
        graph.store(application);
        Path descriptor = context.supplement().resolve("application.properties");
        application.store(descriptor);
        SequencedMap<String, Path> stored = new TreeMap<>(classpath);
        stored.putAll(modulepath);
        for (Map.Entry<String, SequencedSet<String>> layer : layers.entrySet()) {
            for (String name : layer.getValue()) {
                Path jar = jars.get(name);
                if (jar == null) {
                    throw new IllegalStateException("Layer " + layer.getKey() + " names " + name
                            + ", which was not resolved for this application");
                }
                stored.putIfAbsent(name, jar);
            }
        }
        Path zip = Files.createDirectory(context.next().resolve(BUNDLE)).resolve("bundle.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            writeEntry(out, "application.properties", descriptor);
            for (Map.Entry<String, Path> entry : stored.entrySet()) {
                writeEntry(out, "jars/" + entry.getKey(), entry.getValue());
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static void writeEntry(ZipOutputStream out, String name, Path file) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0L);
        out.putNextEntry(entry);
        Files.copy(file, out);
        out.closeEntry();
    }
}
