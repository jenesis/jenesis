package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class ProtocModule implements BuildExecutorModule {

    public static final String FOLDER = "protoc/";
    public static final String DEFINITION = ".proto";

    public static final String GENERATE = "generate";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";

    private static final String MAVEN_GROUP = "com.google.protobuf";
    private static final String MAVEN_ARTIFACT = "protoc";

    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final String tool;
    private final String classifier;
    private final SequencedMap<String, String> plugins;
    private final List<String> arguments;
    private final Boolean printing;

    public ProtocModule(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(repositories, resolvers, null, "protoc", classifier(), new LinkedHashMap<>(), List.of(), null);
    }

    private ProtocModule(Map<String, Repository> repositories,
                         Map<String, Resolver> resolvers,
                         Pinning pinning,
                         String tool,
                         String classifier,
                         SequencedMap<String, String> plugins,
                         List<String> arguments,
                         Boolean printing) {
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.tool = tool;
        this.classifier = classifier;
        this.plugins = plugins;
        this.arguments = arguments;
        this.printing = printing;
    }

    public static String classifier() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String system;
        if (name.startsWith("windows")) {
            system = "windows";
        } else if (name.startsWith("mac") || name.startsWith("darwin")) {
            system = "osx";
        } else if (name.startsWith("linux")) {
            system = "linux";
        } else {
            throw new IllegalArgumentException("No protoc executable is published for operating system: " + name
                    + " (name one with classifier=<protoc classifier> in protoc.properties)");
        }
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String chipset = switch (architecture) {
            case "amd64", "x86-64", "x86_64", "x64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch_64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86_32";
            default -> throw new IllegalArgumentException("No protoc executable is published for architecture: "
                    + architecture + " (name one with classifier=<protoc classifier> in protoc.properties)");
        };
        return system + "-" + chipset;
    }

    public ProtocModule pinning(Pinning pinning) {
        return new ProtocModule(repositories, resolvers, pinning, tool, classifier, plugins, arguments, printing);
    }

    public ProtocModule tool(String tool) {
        return new ProtocModule(repositories, resolvers, pinning, tool, classifier, plugins, arguments, printing);
    }

    public ProtocModule classifier(String classifier) {
        return new ProtocModule(repositories, resolvers, pinning, tool, classifier, plugins, arguments, printing);
    }

    public ProtocModule plugins(SequencedMap<String, String> plugins) {
        return new ProtocModule(repositories, resolvers, pinning, tool, classifier, plugins, arguments, printing);
    }

    public ProtocModule arguments(List<String> arguments) {
        return new ProtocModule(repositories, resolvers, pinning, tool, classifier, plugins, arguments, printing);
    }

    public ProtocModule printing(boolean printing) {
        return new ProtocModule(repositories, resolvers, pinning, tool, classifier, plugins, arguments, printing);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED, new Requires(tool, classifier, plugins), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(DEPENDENCIES,
                new Dependencies(repositories, resolvers).pinning(pinning).group(tool),
                resolveInputs);
        SequencedSet<String> generateInputs = new LinkedHashSet<>();
        generateInputs.add(DEPENDENCIES);
        for (String plugin : plugins.keySet()) {
            String step = DEPENDENCIES + "-" + plugin;
            buildExecutor.addStep(step,
                    new Dependencies(repositories, resolvers).pinning(pinning).group(tool + "-" + plugin),
                    resolveInputs);
            generateInputs.add(step);
        }
        generateInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(GENERATE,
                new Generate(tool, List.copyOf(plugins.sequencedKeySet()), arguments, printing),
                generateInputs);
    }

    private record Requires(String tool, String classifier, SequencedMap<String, String> plugins) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return false;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties requires = new SequencedProperties();
            requires.setProperty(tool
                    + "/runtime/maven/" + MAVEN_GROUP
                    + "/" + MAVEN_ARTIFACT
                    + "/exe/" + classifier
                    + "/RELEASE", "");
            plugins.forEach((plugin, coordinate) -> requires.setProperty(tool
                    + "-" + plugin
                    + "/runtime/maven/" + coordinate
                    + "/exe/" + classifier
                    + "/RELEASE", ""));
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Generate extends ProcessBuildStep {

        private final String tool;
        private final List<String> plugins;
        private final List<String> arguments;

        private Generate(String tool, List<String> plugins, List<String> arguments, Boolean printing) {
            super("protoc", ProcessHandler.OfProcess.of(List.of()), printing == null ? ProcessBuildStep.printing("protoc") : printing);
            this.tool = tool;
            this.plugins = plugins;
            this.arguments = arguments;
        }

        @Override
        protected List<String> commands() {
            return List.of();
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> inputs,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            Path executable = null;
            SequencedMap<String, Path> located = new LinkedHashMap<>();
            List<Path> roots = new ArrayList<>();
            for (BuildStepArgument input : inputs.values()) {
                if (input.removed()) {
                    continue;
                }
                for (Path resolved : Dependencies.select(input.folder(), tool, "runtime")) {
                    if (executable != null) {
                        throw new IllegalStateException("Resolved more than one protoc executable: "
                                + executable + " and " + resolved);
                    }
                    executable = resolved;
                }
                for (String plugin : plugins) {
                    for (Path resolved : Dependencies.select(input.folder(), tool + "-" + plugin, "runtime")) {
                        Path previous = located.putIfAbsent(plugin, resolved);
                        if (previous != null) {
                            throw new IllegalStateException("Resolved more than one executable for protoc plugin "
                                    + plugin + ": " + previous + " and " + resolved);
                        }
                    }
                }
                Path folder = input.folder().resolve(FOLDER);
                if (Files.isDirectory(folder)) {
                    roots.add(folder);
                }
            }
            List<String> files = BuildStep.selectByExtension(roots, DEFINITION);
            if (files.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            if (executable == null) {
                throw new IllegalStateException("No protoc executable resolved upstream of the protoc step");
            }
            Path program = stage(context, executable, "protoc");
            Path target = Files.createDirectories(context.next().resolve(BuildStep.SOURCES));
            List<String> commands = new ArrayList<>(List.of(program.toString(), "--java_out=" + target));
            for (String plugin : plugins) {
                Path resolved = located.get(plugin);
                if (resolved == null) {
                    throw new IllegalStateException("No executable resolved for protoc plugin " + plugin);
                }
                commands.add("--plugin=protoc-gen-" + plugin + "=" + stage(context, resolved, plugin));
                commands.add("--" + plugin + "_out=" + target);
            }
            for (Path root : roots) {
                commands.add("-I" + root);
            }
            commands.addAll(arguments);
            commands.addAll(files);
            return CompletableFuture.completedStage(commands);
        }

        private static Path stage(BuildStepContext context, Path executable, String name) throws IOException {
            Path program = context.supplement().resolve(name + ".exe");
            Files.copy(executable, program);
            program.toFile().setExecutable(true);
            if (!Files.isExecutable(program)) {
                throw new IllegalStateException("Could not make the resolved " + name + " executable: " + program);
            }
            return program;
        }
    }
}
