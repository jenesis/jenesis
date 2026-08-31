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
import build.jenesis.step.JdkProcessBuildStep;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class AvroModule implements BuildExecutorModule {

    public static final String FOLDER = "avro/";
    public static final String SCHEMA = "schema", PROTOCOL = "protocol";
    public static final String SCHEMA_FILE = ".avsc", PROTOCOL_FILE = ".avpr";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";

    private static final String MAVEN_GROUP = "org.apache.avro";
    private static final String MAVEN_ARTIFACT = "avro-tools";
    private static final String SHADED = "*/*";

    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final String tool;
    private final List<String> arguments;
    private final Boolean printing;

    public AvroModule(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(repositories, resolvers, null, "avro", List.of(), null);
    }

    private AvroModule(Map<String, Repository> repositories,
                       Map<String, Resolver> resolvers,
                       Pinning pinning,
                       String tool,
                       List<String> arguments,
                       Boolean printing) {
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.tool = tool;
        this.arguments = arguments;
        this.printing = printing;
    }

    public AvroModule pinning(Pinning pinning) {
        return new AvroModule(repositories, resolvers, pinning, tool, arguments, printing);
    }

    public AvroModule tool(String tool) {
        return new AvroModule(repositories, resolvers, pinning, tool, arguments, printing);
    }

    public AvroModule arguments(List<String> arguments) {
        return new AvroModule(repositories, resolvers, pinning, tool, arguments, printing);
    }

    public AvroModule printing(boolean printing) {
        return new AvroModule(repositories, resolvers, pinning, tool, arguments, printing);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED, new Requires(tool), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(DEPENDENCIES,
                new Dependencies(repositories, resolvers).pinning(pinning).group(tool),
                resolveInputs);
        SequencedSet<String> compileInputs = new LinkedHashSet<>();
        compileInputs.add(DEPENDENCIES);
        compileInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(SCHEMA,
                new Compile(tool, SCHEMA, SCHEMA_FILE, arguments, printing),
                compileInputs);
        buildExecutor.addStep(PROTOCOL,
                new Compile(tool, PROTOCOL, PROTOCOL_FILE, arguments, printing),
                compileInputs);
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
            String coordinate = tool + "/runtime/maven/" + MAVEN_GROUP + "/" + MAVEN_ARTIFACT + "/RELEASE";
            SequencedProperties requires = new SequencedProperties();
            requires.setProperty(coordinate, "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            SequencedProperties exclusions = new SequencedProperties();
            exclusions.setProperty(coordinate, SHADED);
            exclusions.store(context.next().resolve(BuildStep.EXCLUSIONS));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Compile extends JdkProcessBuildStep {

        private final String tool;
        private final String kind;
        private final String extension;
        private final List<String> arguments;

        private Compile(String tool,
                        String kind,
                        String extension,
                        List<String> arguments,
                        Boolean printing) {
            super("avro", ProcessHandler.OfProcess.ofJavaHome("bin/java"), printing == null ? ProcessBuildStep.printing("avro") : printing);
            this.tool = tool;
            this.kind = kind;
            this.extension = extension;
            this.arguments = arguments;
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> inputs,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> jars = new ArrayList<>();
            List<Path> folders = new ArrayList<>();
            for (BuildStepArgument input : inputs.values()) {
                if (input.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(input.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                Path folder = input.folder().resolve(FOLDER);
                if (Files.isDirectory(folder)) {
                    folders.add(folder);
                }
            }
            List<String> files = BuildStep.selectByExtension(folders, extension);
            if (files.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No avro-tools jars resolved upstream of the avro step");
            }
            Path target = Files.createDirectories(context.next().resolve(BuildStep.SOURCES));
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "org.apache.avro.tool.Main",
                    "compile"));
            commands.addAll(arguments);
            commands.add(kind);
            commands.addAll(files);
            commands.add(target.toString());
            return CompletableFuture.completedStage(commands);
        }
    }
}
