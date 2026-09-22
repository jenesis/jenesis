package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Output;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class AntlrModule implements BuildExecutorModule {

    public static final String FOLDER = "antlr/", GRAMMAR = ".g4";
    public static final String GENERATE = "generate";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String MAVEN_GROUP = "org.antlr", MAVEN_ARTIFACT = "antlr4";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String packageName;
    private final List<String> arguments;
    private final ProcessBuildStep.Terms terms;

    public AntlrModule(Map<String, Repository> repositories,
                       Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "antlr",
             null,
             List.of(),
             ProcessBuildStep.Terms.of("antlr"));
    }

    public static AntlrModule ofKeys(Function<String, String> keys,
                                     Output output,
                                     Map<String, Repository> repositories,
                                     Map<String, Resolver> resolvers) {
        return new AntlrModule(Dependencies.ofKeys(keys, output, repositories, resolvers),
                null,
                "antlr",
                null,
                List.of(),
                ProcessBuildStep.Terms.ofKeys(keys, output, "antlr"));
    }

    private AntlrModule(Dependencies dependencies,
                        Pinning pinning,
                        String tool,
                        String packageName,
                        List<String> arguments,
                        ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.packageName = packageName;
        this.arguments = arguments;
        this.terms = terms;
    }

    public AntlrModule pinning(Pinning pinning) {
        return new AntlrModule(dependencies, pinning, tool, packageName, arguments, terms);
    }

    public AntlrModule tool(String tool) {
        return new AntlrModule(dependencies, pinning, tool, packageName, arguments, terms);
    }

    public AntlrModule packageName(String packageName) {
        return new AntlrModule(dependencies, pinning, tool, packageName, arguments, terms);
    }

    public AntlrModule arguments(List<String> arguments) {
        return new AntlrModule(dependencies, pinning, tool, packageName, arguments, terms);
    }

    public AntlrModule printing(BiConsumer<Boolean, String> printing) {
        return new AntlrModule(dependencies, pinning, tool, packageName, arguments, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED, new Requires(tool), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(tool),
                resolveInputs);
        SequencedSet<String> generateInputs = new LinkedHashSet<>();
        generateInputs.add(DEPENDENCIES);
        generateInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(GENERATE, new Generate(terms, tool, packageName, arguments), generateInputs);
    }

    private record Requires(String tool) implements BuildStep {

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
            requires.setProperty(tool + "/runtime/maven/" + MAVEN_GROUP + "/" + MAVEN_ARTIFACT + "/RELEASE", "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Generate extends ProcessBuildStep {

        private final String tool;
        private final String packageName;
        private final List<String> arguments;

        private Generate(ProcessBuildStep.Terms terms,
                         String tool,
                         String packageName,
                         List<String> arguments) {
            super("antlr", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.packageName = packageName;
            this.arguments = arguments;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            return super.apply(executor, context, arguments).thenApply(result -> {
                try {
                    Path sources = context.next().resolve(BuildStep.SOURCES);
                    if (Files.isDirectory(sources)) {
                        List<Path> auxiliary = new ArrayList<>();
                        try (Stream<Path> files = Files.walk(sources)) {
                            files.filter(Files::isRegularFile)
                                    .filter(file -> !file.getFileName().toString().endsWith(".java"))
                                    .forEach(auxiliary::add);
                        }
                        for (Path file : auxiliary) {
                            Files.delete(file);
                        }
                    }
                    return result;
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> inputs,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> jars = new ArrayList<>();
            List<Path> roots = new ArrayList<>();
            for (BuildStepArgument input : inputs.values()) {
                if (input.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(input.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                Path folder = input.folder().resolve(FOLDER);
                if (Files.isDirectory(folder)) {
                    roots.add(folder);
                }
            }
            List<String> files = BuildStep.selectByExtension(roots, GRAMMAR);
            if (files.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No ANTLR jars resolved upstream of the ANTLR step");
            }
            Path target = context.next().resolve(BuildStep.SOURCES);
            if (packageName != null) {
                target = target.resolve(packageName.replace('.', File.separatorChar));
            }
            Files.createDirectories(target);
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "org.antlr.v4.Tool",
                    "-o", target.toString(),
                    "-Xexact-output-dir"));
            if (packageName != null) {
                commands.add("-package");
                commands.add(packageName);
            }
            commands.addAll(arguments);
            commands.addAll(files);
            return CompletableFuture.completedStage(commands);
        }
    }
}
