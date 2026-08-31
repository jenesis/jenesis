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

public class WsImportModule implements BuildExecutorModule {

    public static final String FOLDER = "wsimport/";
    public static final String DESCRIPTION = ".wsdl", BINDING = ".xjb", CATALOG = "catalog";

    public static final String GENERATE = "generate";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";

    private static final String MAVEN_GROUP = "com.sun.xml.ws";
    private static final String MAVEN_ARTIFACT = "jaxws-tools";

    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final String tool;
    private final String packageName;
    private final String location;
    private final List<String> arguments;
    private final Boolean printing;

    public WsImportModule(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(repositories, resolvers, null, "wsimport", null, null, List.of(), null);
    }

    private WsImportModule(Map<String, Repository> repositories,
                           Map<String, Resolver> resolvers,
                           Pinning pinning,
                           String tool,
                           String packageName,
                           String location,
                           List<String> arguments,
                           Boolean printing) {
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.tool = tool;
        this.packageName = packageName;
        this.location = location;
        this.arguments = arguments;
        this.printing = printing;
    }

    public WsImportModule pinning(Pinning pinning) {
        return new WsImportModule(repositories, resolvers, pinning, tool, packageName, location, arguments, printing);
    }

    public WsImportModule tool(String tool) {
        return new WsImportModule(repositories, resolvers, pinning, tool, packageName, location, arguments, printing);
    }

    public WsImportModule packageName(String packageName) {
        return new WsImportModule(repositories, resolvers, pinning, tool, packageName, location, arguments, printing);
    }

    public WsImportModule location(String location) {
        return new WsImportModule(repositories, resolvers, pinning, tool, packageName, location, arguments, printing);
    }

    public WsImportModule arguments(List<String> arguments) {
        return new WsImportModule(repositories, resolvers, pinning, tool, packageName, location, arguments, printing);
    }

    public WsImportModule printing(boolean printing) {
        return new WsImportModule(repositories, resolvers, pinning, tool, packageName, location, arguments, printing);
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
        SequencedSet<String> generateInputs = new LinkedHashSet<>();
        generateInputs.add(DEPENDENCIES);
        generateInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(GENERATE,
                new Generate(tool, packageName, location, arguments, printing),
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

    private static class Generate extends JdkProcessBuildStep {

        private final String tool;
        private final String packageName;
        private final String location;
        private final List<String> arguments;

        private Generate(String tool,
                         String packageName,
                         String location,
                         List<String> arguments,
                         Boolean printing) {
            super("wsimport", ProcessHandler.OfProcess.ofJavaHome("bin/java"), printing == null ? ProcessBuildStep.printing("wsimport") : printing);
            this.tool = tool;
            this.packageName = packageName;
            this.location = location;
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
            List<String> descriptions = BuildStep.selectByExtension(folders, DESCRIPTION);
            if (descriptions.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No jaxws-tools jars resolved upstream of the wsimport step");
            }
            if (location == null) {
                throw new IllegalStateException("The wsimport step generates a client that reads its service"
                        + " description at run time, but nothing states where it is served; add location=<url>"
                        + " to wsimport.properties, naming the path the description ships under");
            }
            Path target = Files.createDirectories(context.next().resolve(BuildStep.SOURCES));
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "com.sun.tools.ws.WsImport",
                    "-Xnocompile",
                    "-s", target.toString(),
                    "-wsdllocation", location));
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
            commands.addAll(descriptions);
            return CompletableFuture.completedStage(commands);
        }
    }
}
