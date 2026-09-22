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

public class XjcModule implements BuildExecutorModule {

    public static final String FOLDER = "xjc/", SCHEMA = ".xsd", BINDING = ".xjb", CATALOG = "catalog";
    public static final String GENERATE = "generate";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";
    private static final String MAVEN_GROUP = "org.glassfish.jaxb", MAVEN_ARTIFACT = "jaxb-xjc";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String packageName;
    private final List<String> arguments;
    private final ProcessBuildStep.Terms terms;

    public XjcModule(Map<String, Repository> repositories,
                     Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "xjc",
             null,
             List.of(),
             ProcessBuildStep.Terms.of("xjc"));
    }

    public static XjcModule ofKeys(Function<String, String> keys,
                                   Output output,
                                   Map<String, Repository> repositories,
                                   Map<String, Resolver> resolvers) {
        return new XjcModule(Dependencies.ofKeys(keys, output, repositories, resolvers),
                null,
                "xjc",
                null,
                List.of(),
                ProcessBuildStep.Terms.ofKeys(keys, output, "xjc"));
    }

    private XjcModule(Dependencies dependencies,
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

    public XjcModule pinning(Pinning pinning) {
        return new XjcModule(dependencies, pinning, tool, packageName, arguments, terms);
    }

    public XjcModule tool(String tool) {
        return new XjcModule(dependencies, pinning, tool, packageName, arguments, terms);
    }

    public XjcModule packageName(String packageName) {
        return new XjcModule(dependencies, pinning, tool, packageName, arguments, terms);
    }

    public XjcModule arguments(List<String> arguments) {
        return new XjcModule(dependencies, pinning, tool, packageName, arguments, terms);
    }

    public XjcModule printing(BiConsumer<Boolean, String> printing) {
        return new XjcModule(dependencies, pinning, tool, packageName, arguments, terms.printing(printing));
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
        buildExecutor.addStep(GENERATE,
                new Generate(terms, tool, packageName, arguments),
                generateInputs);
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
            super("xjc", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.packageName = packageName;
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
            List<String> schemas = BuildStep.selectByExtension(folders, SCHEMA);
            if (schemas.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No xjc jars resolved upstream of the xjc step");
            }
            Path target = Files.createDirectories(context.next().resolve(BuildStep.SOURCES));
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "com.sun.tools.xjc.XJCFacade",
                    "-no-header",
                    "-d", target.toString()));
            if (packageName != null) {
                commands.add("-p");
                commands.add(packageName);
            }
            for (String binding : BuildStep.selectByExtension(folders, BINDING)) {
                commands.add("-b");
                commands.add(binding);
            }
            Path catalog = BuildStep.selectByName(folders, CATALOG);
            if (catalog != null) {
                commands.add("-catalog");
                commands.add(catalog.toString());
            }
            commands.addAll(arguments);
            commands.addAll(schemas);
            return CompletableFuture.completedStage(commands);
        }
    }
}
