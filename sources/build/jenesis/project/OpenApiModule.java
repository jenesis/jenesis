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

public class OpenApiModule implements BuildExecutorModule {

    public static final String FOLDER = "openapi/";
    public static final String SPECIFICATION = "openapi";
    public static final Set<String> DOCUMENTS = Set.of(".yaml", ".yml", ".json");

    public static final String COLLECT = "collect";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies", GENERATE = "generate";

    private static final String MAVEN_GROUP = "org.openapitools";
    private static final String MAVEN_ARTIFACT = "openapi-generator-cli";

    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final String tool;
    private final String generator;
    private final String packageName;
    private final String sourceFolder;
    private final List<String> arguments;
    private final Boolean printing;

    public OpenApiModule(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(repositories, resolvers, null, "openapi", "java", null, "src/main/java", List.of(), null);
    }

    private OpenApiModule(Map<String, Repository> repositories,
                          Map<String, Resolver> resolvers,
                          Pinning pinning,
                          String tool,
                          String generator,
                          String packageName,
                          String sourceFolder,
                          List<String> arguments,
                          Boolean printing) {
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.tool = tool;
        this.generator = generator;
        this.packageName = packageName;
        this.sourceFolder = sourceFolder;
        this.arguments = arguments;
        this.printing = printing;
    }

    public OpenApiModule pinning(Pinning pinning) {
        return new OpenApiModule(repositories, resolvers, pinning, tool, generator, packageName, sourceFolder, arguments, printing);
    }

    public OpenApiModule tool(String tool) {
        return new OpenApiModule(repositories, resolvers, pinning, tool, generator, packageName, sourceFolder, arguments, printing);
    }

    public OpenApiModule generator(String generator) {
        return new OpenApiModule(repositories, resolvers, pinning, tool, generator, packageName, sourceFolder, arguments, printing);
    }

    public OpenApiModule packageName(String packageName) {
        return new OpenApiModule(repositories, resolvers, pinning, tool, generator, packageName, sourceFolder, arguments, printing);
    }

    public OpenApiModule sourceFolder(String sourceFolder) {
        return new OpenApiModule(repositories, resolvers, pinning, tool, generator, packageName, sourceFolder, arguments, printing);
    }

    public OpenApiModule arguments(List<String> arguments) {
        return new OpenApiModule(repositories, resolvers, pinning, tool, generator, packageName, sourceFolder, arguments, printing);
    }

    public OpenApiModule printing(boolean printing) {
        return new OpenApiModule(repositories, resolvers, pinning, tool, generator, packageName, sourceFolder, arguments, printing);
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
                new Generate(tool, generator, packageName, arguments, printing),
                generateInputs);
        buildExecutor.addStep(COLLECT, new Collect(sourceFolder), GENERATE);
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(GENERATE) ? Optional.empty() : Optional.of(path);
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
        private final String generator;
        private final String packageName;
        private final List<String> arguments;

        private Generate(String tool,
                         String generator,
                         String packageName,
                         List<String> arguments,
                         Boolean printing) {
            super("openapi", ProcessHandler.OfProcess.ofJavaHome("bin/java"), printing == null ? ProcessBuildStep.printing("openapi") : printing);
            this.tool = tool;
            this.generator = generator;
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
            Path located = BuildStep.selectByName(roots, SPECIFICATION);
            if (located == null) {
                List<String> documents = new ArrayList<>();
                for (String extension : new TreeSet<>(DOCUMENTS)) {
                    documents.addAll(BuildStep.selectByExtension(roots, extension));
                }
                if (documents.isEmpty()) {
                    return CompletableFuture.completedStage(null);
                }
                if (documents.size() > 1) {
                    throw new IllegalStateException("The openapi step reads " + documents.size()
                            + " documents but none of them is the specification to generate from;"
                            + " name it with specification=<file> in openapi.properties");
                }
                located = Path.of(documents.getFirst());
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No openapi-generator jars resolved upstream of the openapi step");
            }
            Path target = Files.createDirectories(context.next().resolve("generated"));
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "org.openapitools.codegen.OpenAPIGenerator",
                    "generate",
                    "-i", located.toString(),
                    "-g", generator,
                    "-o", target.toString(),
                    "--additional-properties", "hideGenerationTimestamp=true"));
            if (packageName != null) {
                commands.add("--additional-properties");
                commands.add("apiPackage=" + packageName
                        + ",modelPackage=" + packageName + ".model"
                        + ",invokerPackage=" + packageName);
            }
            commands.addAll(arguments);
            return CompletableFuture.completedStage(commands);
        }
    }

    private record Collect(String sourceFolder) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path generated = argument.folder().resolve("generated");
                if (!Files.isDirectory(generated)) {
                    continue;
                }
                Path source = BuildStep.resolveContained(generated, sourceFolder);
                if (!Files.isDirectory(source)) {
                    throw new IllegalStateException("The openapi generator wrote no sources to " + sourceFolder
                            + "; name the folder it writes with sources=<path> in openapi.properties");
                }
                Path target = Files.createDirectories(context.next().resolve(BuildStep.SOURCES));
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Files.createDirectories(target.resolve(source.relativize(dir).toString()));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        BuildStep.linkOrCopy(target.resolve(source.relativize(file).toString()), file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
