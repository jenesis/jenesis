package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.ModuleGraph;

public class JPackage extends JdkProcessBuildStep {

    public static final String PACKAGES = "packages/";

    private final String type;
    private final String group;

    public JPackage(ProcessHandler.Factory factory) {
        this(factory, null);
    }

    public JPackage(ProcessHandler.Factory factory, String type) {
        this(factory.apply("jpackage", "bin/jpackage"), type, "main", printing("jpackage"));
    }

    private JPackage(Function<List<String>, ? extends ProcessHandler> factory, String type, String group, boolean verbose) {
        super("jpackage", factory, verbose);
        this.type = type;
        this.group = group;
    }

    public JPackage group(String group) {
        return new JPackage(factory, type, group, verbose);
    }

    public JPackage verbose(boolean verbose) {
        return new JPackage(factory, type, group, verbose);
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        boolean modular = properties.values().stream().anyMatch(folder -> folder.containsKey("--module"));
        if (!modular && properties.values().stream().noneMatch(folder -> folder.containsKey("--main-jar"))) {
            return CompletableFuture.completedStage(null);
        }
        Path runtime = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path candidate = argument.folder().resolve(JLink.RUNTIME);
            if (Files.isDirectory(candidate)) {
                runtime = candidate;
                break;
            }
        }
        if (runtime != null) {
            List<String> commands = new ArrayList<>();
            if (type != null) {
                commands.add("--type");
                commands.add(type);
            }
            commands.add("--runtime-image");
            commands.add(runtime.toString());
            commands.add("--dest");
            commands.add(Files.createDirectory(context.next().resolve(PACKAGES)).toString());
            return CompletableFuture.completedStage(commands);
        }
        Path input = Files.createDirectory(context.supplement().resolve("input"));
        SequencedMap<String, Path> staged = new LinkedHashMap<>();
        ModuleGraph graph = new ModuleGraph();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            List<Path> jars = new ArrayList<>();
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        jars.add(file);
                    }
                }
            }
            jars.addAll(Dependencies.select(argument.folder(), group, "runtime"));
            for (Path file : jars) {
                String name = file.getFileName().toString();
                Path previous = staged.putIfAbsent(name, file);
                if (previous != null) {
                    throw new IllegalStateException("Cannot stage two jars with the same file name '"
                            + name + "' into a single jpackage input: " + previous + " and " + file);
                }
                if (modular) {
                    graph.module(file);
                }
                BuildStep.linkOrCopy(input.resolve(name), file);
            }
        }
        if (staged.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        List<String> commands = new ArrayList<>();
        if (type != null) {
            commands.add("--type");
            commands.add(type);
        }
        commands.add(modular ? "--module-path" : "--input");
        commands.add(input.toString());
        for (String option : graph.options()) {
            commands.add("--java-options");
            commands.add(option);
        }
        commands.add("--dest");
        commands.add(Files.createDirectory(context.next().resolve(PACKAGES)).toString());
        return CompletableFuture.completedStage(commands);
    }
}
