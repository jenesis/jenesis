package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;
import build.jenesis.step.Dependencies;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class KspModule implements BuildExecutorModule {

    public static final String CONFIGURATION = "ksp.properties", GENERATE = "generate";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies", PROCESSORS = "processors";
    private static final String MAVEN_GROUP = "com.google.devtools.ksp", MAVEN_ARTIFACT = "symbol-processing-aa-embeddable";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String tool;
    private final String processors;
    private final String group;
    private final SequencedMap<String, String> options;
    private final ProcessBuildStep.Terms terms;

    public KspModule(Map<String, Repository> repositories,
                     Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "ksp-runtime",
             "ksp",
             "main",
             Collections.emptyNavigableMap(),
             new ProcessBuildStep.Terms());
    }

    public static KspModule ofEnvironment(Environment environment,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        return new KspModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "ksp-runtime",
                "ksp",
                "main",
                Collections.emptyNavigableMap(),
                ProcessBuildStep.Terms.ofEnvironment(environment, "ksp"));
    }

    private KspModule(Dependencies dependencies,
                      Pinning pinning,
                      String tool,
                      String processors,
                      String group,
                      SequencedMap<String, String> options,
                      ProcessBuildStep.Terms terms) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.tool = tool;
        this.processors = processors;
        this.group = group;
        this.options = options;
        this.terms = terms;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, CONFIGURATION);
    }

    public KspModule pinning(Pinning pinning) {
        return new KspModule(dependencies, pinning, tool, processors, group, options, terms);
    }

    public KspModule tool(String tool) {
        return new KspModule(dependencies, pinning, tool, processors, group, options, terms);
    }

    public KspModule processors(String processors) {
        return new KspModule(dependencies, pinning, tool, processors, group, options, terms);
    }

    public KspModule group(String group) {
        return new KspModule(dependencies, pinning, tool, processors, group, options, terms);
    }

    public KspModule options(SequencedMap<String, String> options) {
        return new KspModule(dependencies, pinning, tool, processors, group, options, terms);
    }

    public KspModule printing(BiConsumer<Boolean, String> printing) {
        return new KspModule(dependencies, pinning, tool, processors, group, options, terms.printing(printing));
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
        buildExecutor.addModule(PROCESSORS,
                dependencies.pinning(pinning).group(processors),
                inherited.sequencedKeySet());
        SequencedSet<String> generateInputs = new LinkedHashSet<>();
        generateInputs.add(DEPENDENCIES);
        generateInputs.add(PROCESSORS);
        generateInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(GENERATE, new Generate(terms, tool, processors, group, new TreeMap<>(options)), generateInputs);
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
        private final String processors;
        private final String group;
        private final TreeMap<String, String> options;

        private Generate(ProcessBuildStep.Terms terms,
                         String tool,
                         String processors,
                         String group,
                         TreeMap<String, String> options) {
            super("ksp", ProcessHandler.OfProcess.ofJavaHome("bin/java"), terms);
            this.tool = tool;
            this.processors = processors;
            this.group = group;
            this.options = options;
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            SequencedSet<String> jars = new LinkedHashSet<>(),
                    processorPath = new LinkedHashSet<>(),
                    libraries = new LinkedHashSet<>(),
                    roots = new LinkedHashSet<>();
            String release = null, module = null;
            boolean sources = false;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path folder = argument.folder();
                Dependencies.select(folder, tool, "runtime").forEach(jar -> jars.add(jar.toString()));
                Dependencies.select(folder, processors, "plugin").forEach(jar -> processorPath.add(jar.toString()));
                Dependencies.select(folder, group, "compile").forEach(jar -> libraries.add(jar.toString()));
                Path javac = ProcessBuildStep.locate(folder, ProcessBuildStep.PROCESS, "javac");
                if (javac != null) {
                    String value = SequencedProperties.ofFiles(javac).value("--release");
                    if (value != null) {
                        release = value;
                    }
                }
                Path described = folder.resolve(BuildStep.MODULE);
                if (Files.isRegularFile(described)) {
                    module = SequencedProperties.ofFiles(described).value("module", module);
                }
                Path metadata = folder.resolve(BuildStep.METADATA);
                if (module == null && Files.isRegularFile(metadata)) {
                    module = SequencedProperties.ofFiles(metadata).value("artifact");
                }
                Path root = folder.resolve(Bind.SOURCES);
                if (Files.isDirectory(root)) {
                    roots.add(root.toString());
                    try (Stream<Path> files = Files.walk(root)) {
                        sources |= files.anyMatch(file -> file.toString().endsWith(".kt")
                                || file.toString().endsWith(".java"));
                    }
                }
            }
            if (!sources) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException("No KSP jars resolved upstream of the KSP step");
            }
            if (processorPath.isEmpty()) {
                throw new IllegalStateException(CONFIGURATION + " switches KSP on, but no symbol processor is declared:"
                        + " name one with @jenesis.plugin " + processors + " <coordinate> in module-info.java, or"
                        + " <!--jenesis.plugin " + processors + " maven/<groupId>/<artifactId>--> in pom.xml");
            }
            ForkedOptions forked = forked(properties);
            List<String> commands = new ArrayList<>(forked.jvm()), declared = forked.program();
            Path supplement = context.supplement();
            String sourceRoots = String.join(File.pathSeparator, roots);
            commands.addAll(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "com.google.devtools.ksp.cmdline.KSPJvmMain",
                    "-module-name=" + (module == null ? "main" : module),
                    "-jvm-target=" + (release == null
                            ? Integer.toString(Runtime.version().feature())
                            : Integer.parseInt(release) < 9 ? "1." + release : release),
                    "-source-roots=" + sourceRoots,
                    "-java-source-roots=" + sourceRoots,
                    "-project-base-dir=" + supplement,
                    "-output-base-dir=" + supplement.resolve("output"),
                    "-caches-dir=" + supplement.resolve("caches"),
                    "-class-output-dir=" + supplement.resolve("classes"),
                    "-kotlin-output-dir=" + Files.createDirectories(context.next().resolve(BuildStep.SOURCES)),
                    "-java-output-dir=" + context.next().resolve(BuildStep.SOURCES),
                    "-resource-output-dir=" + Files.createDirectories(context.next().resolve(BuildStep.RESOURCES)),
                    "-incremental=false"));
            if (declared.stream().noneMatch(option -> option.startsWith("-language-version"))) {
                String version = kotlinVersion(jars);
                commands.add("-language-version=" + version);
                if (declared.stream().noneMatch(option -> option.startsWith("-api-version"))) {
                    commands.add("-api-version=" + version);
                }
            }
            if (!libraries.isEmpty()) {
                commands.add("-libraries=" + String.join(File.pathSeparator, libraries));
            }
            if (!options.isEmpty()) {
                commands.add("-processor-options=" + options.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .collect(Collectors.joining(File.pathSeparator)));
            }
            commands.addAll(declared);
            commands.add(String.join(File.pathSeparator, processorPath));
            return CompletableFuture.completedStage(commands);
        }

        private static String kotlinVersion(Collection<String> jars) throws IOException {
            for (String jar : jars) {
                try (JarFile file = new JarFile(jar)) {
                    Manifest manifest = file.getManifest();
                    if (manifest != null && "kotlin-stdlib".equals(manifest.getMainAttributes().getValue("Implementation-Title"))) {
                        String version = manifest.getMainAttributes().getValue("Kotlin-Version");
                        if (version != null) {
                            return version;
                        }
                    }
                }
            }
            throw new IllegalStateException("The KSP jars hold no kotlin-stdlib to take the Kotlin language version"
                    + " from: name it with -language-version=<version> in process-ksp.properties");
        }
    }
}
