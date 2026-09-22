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

public class JaCoCoModule implements BuildExecutorModule {

    public static final String REPORT = "report";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final ProcessBuildStep.Terms terms;

    public JaCoCoModule(Map<String, Repository> repositories,
                        Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "jacoco",
             ProcessBuildStep.Terms.of("jacoco"));
    }

    public static JaCoCoModule ofKeys(Function<String, String> keys,
                                      Output output,
                                      Map<String, Repository> repositories,
                                      Map<String, Resolver> resolvers) {
        return new JaCoCoModule(Dependencies.ofKeys(keys, output, repositories, resolvers),
                null,
                "jacoco",
                ProcessBuildStep.Terms.ofKeys(keys, output, "jacoco"));
    }

    private JaCoCoModule(Dependencies dependencies,
                         Pinning pinning,
                         String tool,
                         ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.terms = terms;
    }

    public JaCoCoModule pinning(Pinning pinning) {
        return new JaCoCoModule(dependencies, pinning, tool, terms);
    }

    public JaCoCoModule tool(String tool) {
        return new JaCoCoModule(dependencies, pinning, tool, terms);
    }

    public JaCoCoModule printing(BiConsumer<Boolean, String> printing) {
        return new JaCoCoModule(dependencies, pinning, tool, terms.printing(printing));
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
        SequencedSet<String> reportInputs = new LinkedHashSet<>();
        reportInputs.add(DEPENDENCIES);
        reportInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(REPORT, new Report(terms, tool), reportInputs);
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
            requires.setProperty(tool + "/runtime/maven/org.jacoco/org.jacoco.cli/RELEASE", "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Report extends ProcessBuildStep {

        private final String tool;

        private Report(ProcessBuildStep.Terms terms, String tool) {
            super("jacoco", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> jars = new ArrayList<>(), sources = new ArrayList<>();
            SequencedSet<String> classes = new LinkedHashSet<>();
            SequencedMap<String, Path> covered = new LinkedHashMap<>();
            Path data = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                Path exec = argument.folder().resolve("jacoco.exec");
                if (Files.isRegularFile(exec)) {
                    data = exec;
                }
                Path compiled = argument.folder().resolve(BuildStep.CLASSES);
                if (Files.isDirectory(compiled)) {
                    classes.add(compiled.toString());
                }
                Path source = argument.folder().resolve(BuildStep.SOURCES);
                if (Files.isDirectory(source)) {
                    sources.add(source.toString());
                }
                Dependencies.internal(argument.folder()).forEach(covered::putIfAbsent);
            }
            for (Path jar : covered.values()) {
                classes.add(jar.toString());
            }
            if (data == null || classes.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No JaCoCo CLI jars resolved upstream of the JaCoCo report step");
            }
            Path report = Files.createDirectories(context.next().resolve(BuildStep.REPORTS + "jacoco"));
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "org.jacoco.cli.internal.Main", "report", data.toString()));
            for (String directory : classes) {
                commands.add("--classfiles");
                commands.add(directory);
            }
            for (String directory : sources) {
                commands.add("--sourcefiles");
                commands.add(directory);
            }
            commands.add("--html");
            commands.add(report.toString());
            commands.add("--xml");
            commands.add(report.resolve("jacoco.xml").toString());
            return CompletableFuture.completedStage(commands);
        }
    }
}
