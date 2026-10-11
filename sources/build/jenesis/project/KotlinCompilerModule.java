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
import build.jenesis.step.Javac;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Versions;

public class KotlinCompilerModule implements BuildExecutorModule {

    public static final String ARTIFACTS = "artifacts", CLASSES = "classes";
    private static final String REQUIRED = "required",
            COMPILED = "compiled",
            DEPENDENCIES = "dependencies";
    private static final List<String> PREFERRED_PREFIXES = List.of("maven", "module");
    private static final String MODULE_NAME = "kotlin.compiler.embeddable",
            MAVEN_GROUP = "org.jetbrains.kotlin",
            MAVEN_ARTIFACT = "kotlin-compiler-embeddable";

    private final Map<String, Resolver> resolvers;
    private final Dependencies dependencies;
    private final Pinning pinning;
    private final boolean includeResources;
    private final String tool;
    private final String group;
    private final Function<List<String>, ? extends ProcessHandler> factory;
    private final ProcessBuildStep.Terms terms;

    public KotlinCompilerModule(Map<String, Repository> repositories,
                                Map<String, Resolver> resolvers) {
        this(resolvers,
             new Dependencies(repositories, resolvers),
             null,
             true,
             "kotlinc",
             "main",
             null,
             new ProcessBuildStep.Terms());
    }

    public static KotlinCompilerModule ofEnvironment(Environment environment,
                                                     Map<String, Repository> repositories,
                                                     Map<String, Resolver> resolvers) {
        return new KotlinCompilerModule(resolvers,
                Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                true,
                "kotlinc",
                "main",
                null,
                ProcessBuildStep.Terms.ofEnvironment(environment, "kotlinc"));
    }

    private KotlinCompilerModule(Map<String, Resolver> resolvers,
                                 Dependencies dependencies,
                                 Pinning pinning,
                                 boolean includeResources,
                                 String tool,
                                 String group,
                                 Function<List<String>, ? extends ProcessHandler> factory,
                                 ProcessBuildStep.Terms terms) {
        this.resolvers = resolvers;
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.includeResources = includeResources;
        this.tool = tool;
        this.group = group;
        this.factory = factory;
        this.terms = terms;
    }

    public KotlinCompilerModule factory(Function<List<String>, ? extends ProcessHandler> factory) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, tool, group, factory, terms);
    }

    public KotlinCompilerModule pinning(Pinning pinning) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, tool, group, factory, terms);
    }

    public KotlinCompilerModule includeResources(boolean includeResources) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, tool, group, factory, terms);
    }

    public KotlinCompilerModule tool(String tool) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, tool, group, factory, terms);
    }

    public KotlinCompilerModule group(String group) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, tool, group, factory, terms);
    }

    public KotlinCompilerModule printing(BiConsumer<Boolean, String> printing) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, tool, group, factory, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        SequencedSet<String> upstream = inherited.sequencedKeySet();
        buildExecutor.addStep(REQUIRED, new Requires(Set.copyOf(resolvers.keySet()), tool), upstream);
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(upstream);
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(tool),
                resolveInputs);
        SequencedSet<String> compileInputs = new LinkedHashSet<>();
        compileInputs.add(DEPENDENCIES);
        compileInputs.addAll(upstream);
        buildExecutor.addStep(COMPILED,
                factory == null ? new Compile(terms, includeResources, tool, group) : new Compile(terms, includeResources, tool, group, factory),
                compileInputs);
        buildExecutor.addStep(CLASSES, new Versions(), COMPILED);
    }

    @Override
    public Optional<String> resolve(String path) {
        if (path.equals(CLASSES)) {
            return Optional.of(CLASSES);
        }
        if (path.equals(DEPENDENCIES)) {
            return Optional.of(ARTIFACTS);
        }
        return Optional.empty();
    }

    private record Requires(Set<String> prefixes, String tool) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return false;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            String selectedPrefix = null;
            for (String prefix : PREFERRED_PREFIXES) {
                if (prefixes.contains(prefix)) {
                    selectedPrefix = prefix;
                    break;
                }
            }
            if (selectedPrefix == null) {
                throw new IllegalStateException(
                        "No suitable resolver for Kotlin compiler. Available prefixes: " + prefixes
                                + ". Expected one of: " + PREFERRED_PREFIXES);
            }
            String coordinate = switch (selectedPrefix) {
                case "module" -> selectedPrefix + "/" + MODULE_NAME;
                case "maven" -> selectedPrefix + "/" + MAVEN_GROUP + "/" + MAVEN_ARTIFACT + "/RELEASE";
                default -> throw new IllegalStateException("Unreachable");
            };
            SequencedProperties requires = new SequencedProperties();
            requires.setProperty(tool + "/runtime/" + coordinate, "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static class Compile extends ProcessBuildStep {

        private final boolean includeResources;
        private final String tool;
        private final String group;

        private Compile(ProcessBuildStep.Terms terms,
                        boolean includeResources,
                        String tool,
                        String group) {
            this(terms, includeResources, tool, group, ProcessHandler.OfProcess.ofJavaHome("bin/java"));
        }

        private Compile(ProcessBuildStep.Terms terms,
                        boolean includeResources,
                        String tool,
                        String group,
                        Function<List<String>, ? extends ProcessHandler> factory) {
            super("kotlinc", factory, terms);
            this.includeResources = includeResources;
            this.tool = tool;
            this.group = group;
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return Javac.hasRelevantChange(arguments,
                    Set.of(".kt", ".java"),
                    Set.of("kotlinc.properties", "javac.properties"),
                    includeResources);
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            return super.apply(executor, context, arguments).thenComposeAsync(result -> {
                if (!result.next()) {
                    return CompletableFuture.completedStage(result);
                }
                try {
                    return compileVersioned(executor, context, arguments).thenApply(_ -> result);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, executor);
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            Path target = Files.createDirectory(context.next().resolve(CLASSES));
            Sources sources = sources(arguments, target);
            if (sources.files().stream().noneMatch(name -> name.endsWith(".kt"))) {
                return CompletableFuture.completedStage(null);
            }
            return CompletableFuture.completedStage(command(properties,
                    target,
                    sources.release(),
                    sources,
                    sources.classpath(),
                    sources.files()));
        }

        private CompletionStage<Void> compileVersioned(Executor executor,
                                                       BuildStepContext context,
                                                       SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Sources sources = sources(arguments, null);
            Path base = context.next().resolve(CLASSES);
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            boolean compiled = false;
            for (Map.Entry<Integer, List<String>> entry : sources.versioned().entrySet()) {
                int release = entry.getKey();
                List<String> files = entry.getValue();
                if (files.stream().noneMatch(name -> name.endsWith(".kt"))) {
                    continue;
                }
                compiled = true;
                Path target = Files.createDirectories(base.resolve("META-INF/versions/" + release));
                List<String> classpath = new ArrayList<>();
                for (int lower : sources.versioned().headMap(release, false).descendingKeySet()) {
                    Path overlay = base.resolve("META-INF/versions/" + lower);
                    if (Files.isDirectory(overlay)) {
                        classpath.add(overlay.toString());
                    }
                }
                classpath.add(base.toString());
                classpath.addAll(sources.classpath());
                List<String> commands = command(properties(arguments),
                        target,
                        Integer.toString(release),
                        sources,
                        classpath,
                        files);
                chain = chain.thenComposeAsync(_ -> {
                    try {
                        ProcessHandler handler = handler(context, commands);
                        Path output = context.supplement().resolve("output-" + release),
                                error = context.supplement().resolve("error-" + release);
                        Files.writeString(context.supplement().resolve("command-" + release), shell(handler.commands()));
                        int exitCode = execute(handler, output, error, tee(executor, handler));
                        if (exitCode != 0) {
                            throw new IllegalStateException("Unexpected exit code: " + exitCode
                                    + " (multi-release " + release + ")\n"
                                    + "To reproduce, execute:\n "
                                    + shell(handler.commands())
                                    + tail("Output", output)
                                    + tail("Error", error));
                        }
                        return CompletableFuture.<Void>completedFuture(null);
                    } catch (IOException | InterruptedException e) {
                        return CompletableFuture.<Void>failedFuture(e);
                    }
                }, executor);
            }
            if (compiled && !Javac.hasMultiReleaseManifest(arguments)) {
                Files.writeString(context.next().resolve(Versions.MANIFEST), "Manifest-Version: 1.0\r\nMulti-Release: true\r\n");
            }
            return chain;
        }

        private Sources sources(SequencedMap<String, BuildStepArgument> arguments, Path target) throws IOException {
            List<String> files = new ArrayList<>(),
                    jars = new ArrayList<>(),
                    classpath = new ArrayList<>(),
                    plugins = new ArrayList<>();
            NavigableMap<Integer, List<String>> versioned = new TreeMap<>();
            String release = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "runtime")) {
                    jars.add(jar.toString());
                }
                for (Path jar : Dependencies.select(argument.folder(), tool, "plugin")) {
                    plugins.add(jar.toString());
                }
                for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                    classpath.add(jar.toString());
                }
                Path javacProperties = argument.folder().resolve(ProcessBuildStep.PROCESS + "javac.properties");
                if (Files.exists(javacProperties)) {
                    SequencedProperties loaded = SequencedProperties.ofFiles(javacProperties);
                    String value = loaded.value("--release");
                    if (value != null) {
                        release = value;
                    }
                }
                Path classes = argument.folder().resolve(CLASSES);
                if (Files.exists(classes)) {
                    classpath.add(classes.toString());
                }
                Path sources = argument.folder().resolve(Bind.SOURCES);
                if (Files.exists(sources)) {
                    Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            Path relative = sources.relativize(dir);
                            if (BuildStep.underBuildJenesis(relative)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            if (target != null && !BuildStep.underMetaInfVersions(relative)) {
                                Files.createDirectories(target.resolve(relative));
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String name = file.toString();
                            Path relative = sources.relativize(file);
                            if (name.endsWith(".kt")
                                    || (name.endsWith(".java")
                                    && !file.getFileName().toString().equals("module-info.java"))) {
                                Integer version = Javac.versionOf(relative);
                                if (version == null) {
                                    files.add(name);
                                } else {
                                    versioned.computeIfAbsent(version, _ -> new ArrayList<>()).add(name);
                                }
                            } else if (target != null
                                    && includeResources
                                    && !name.endsWith(".java")
                                    && !BuildStep.underMetaInfVersions(relative)
                                    && !BuildStep.underBuildJenesis(relative)) {
                                BuildStep.linkOrCopy(target.resolve(relative), file);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
            files.sort(null);
            versioned.values().forEach(names -> names.sort(null));
            return new Sources(files, jars, classpath, plugins, versioned, release);
        }

        private List<String> command(SequencedMap<String, SequencedMap<String, String>> properties,
                                     Path target,
                                     String release,
                                     Sources sources,
                                     List<String> classpath,
                                     List<String> files) {
            if (sources.jars().isEmpty()) {
                throw new IllegalStateException(
                        "No compiler jars resolved upstream of the Kotlin compile step");
            }
            for (List<String> entries : List.of(sources.jars(), classpath, sources.plugins())) {
                for (String entry : entries) {
                    if (entry.indexOf(File.pathSeparatorChar) != -1) {
                        throw new IllegalArgumentException(
                                "Path entry contains separator '" + File.pathSeparator + "': " + entry);
                    }
                }
            }
            List<String> userClasspath = new ArrayList<>(sources.jars());
            userClasspath.addAll(classpath);
            List<String> commands = new ArrayList<>(), options = new ArrayList<>();
            for (String option : prepended(properties)) {
                if (option.startsWith("-J")) {
                    commands.add(option.substring(2));
                } else if (option.startsWith("-D")) {
                    commands.add(option);
                } else {
                    options.add(option);
                }
            }
            properties.values().forEach(Map::clear);
            commands.addAll(List.of(
                    "-cp", String.join(File.pathSeparator, sources.jars()),
                    "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-d", target.toString(),
                    "-no-stdlib",
                    "-no-reflect",
                    "-classpath", String.join(File.pathSeparator, userClasspath)));
            if (release != null) {
                commands.add("-jvm-target");
                commands.add(Integer.parseInt(release) < 9 ? "1." + release : release);
            }
            for (String plugin : sources.plugins()) {
                commands.add("-Xplugin=" + plugin);
            }
            commands.addAll(options);
            commands.addAll(files);
            return commands;
        }
    }

    private record Sources(List<String> files,
                           List<String> jars,
                           List<String> classpath,
                           List<String> plugins,
                           NavigableMap<Integer, List<String>> versioned,
                           String release) {
    }
}
