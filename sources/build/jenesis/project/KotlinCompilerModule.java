package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
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
            DEPENDENCIES = "dependencies",
            INCREMENTAL = "incremental/",
            STATE = "state.properties",
            LISTING = "sources.properties";
    private static final List<String> PREFERRED_PREFIXES = List.of("maven", "module");
    private static final String MODULE_NAME = "kotlin.compiler.embeddable",
            MAVEN_GROUP = "org.jetbrains.kotlin",
            MAVEN_ARTIFACT = "kotlin-compiler-embeddable",
            INCREMENTAL_MODULE_NAME = "kotlin.build.tools.impl",
            INCREMENTAL_MAVEN_ARTIFACT = "kotlin-build-tools-impl",
            MAIN_CLASS = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            MODULE_OPTION = "-module-name";

    private final Map<String, Resolver> resolvers;
    private final Dependencies dependencies;
    private final Pinning pinning;
    private final boolean includeResources;
    private final boolean incremental;
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
             false,
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
                environment.flag("kotlin.incremental", false),
                "kotlinc",
                "main",
                null,
                ProcessBuildStep.Terms.ofEnvironment(environment, "kotlinc"));
    }

    private KotlinCompilerModule(Map<String, Resolver> resolvers,
                                 Dependencies dependencies,
                                 Pinning pinning,
                                 boolean includeResources,
                                 boolean incremental,
                                 String tool,
                                 String group,
                                 Function<List<String>, ? extends ProcessHandler> factory,
                                 ProcessBuildStep.Terms terms) {
        this.resolvers = resolvers;
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.includeResources = includeResources;
        this.incremental = incremental;
        this.tool = tool;
        this.group = group;
        this.factory = factory;
        this.terms = terms;
    }

    public KotlinCompilerModule factory(Function<List<String>, ? extends ProcessHandler> factory) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public KotlinCompilerModule pinning(Pinning pinning) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public KotlinCompilerModule includeResources(boolean includeResources) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public KotlinCompilerModule incremental(boolean incremental) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public KotlinCompilerModule tool(String tool) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public KotlinCompilerModule group(String group) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public KotlinCompilerModule printing(BiConsumer<Boolean, String> printing) {
        return new KotlinCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        SequencedSet<String> upstream = inherited.sequencedKeySet();
        buildExecutor.addStep(REQUIRED, new Requires(Set.copyOf(resolvers.keySet()), tool, incremental), upstream);
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
                factory == null
                        ? new Compile(terms, includeResources, incremental, tool, group)
                        : new Compile(terms, includeResources, incremental, tool, group, factory),
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

    private record Requires(Set<String> prefixes, String tool, boolean incremental) implements BuildStep {

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
                case "module" -> selectedPrefix + "/" + (incremental ? INCREMENTAL_MODULE_NAME : MODULE_NAME);
                case "maven" -> selectedPrefix + "/" + MAVEN_GROUP + "/"
                        + (incremental ? INCREMENTAL_MAVEN_ARTIFACT : MAVEN_ARTIFACT) + "/RELEASE";
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
        private final boolean incremental;
        private final String tool;
        private final String group;

        private Compile(ProcessBuildStep.Terms terms,
                        boolean includeResources,
                        boolean incremental,
                        String tool,
                        String group) {
            this(terms, includeResources, incremental, tool, group, ProcessHandler.OfProcess.ofJavaHome("bin/java"));
        }

        private Compile(ProcessBuildStep.Terms terms,
                        boolean includeResources,
                        boolean incremental,
                        String tool,
                        String group,
                        Function<List<String>, ? extends ProcessHandler> factory) {
            super("kotlinc", factory, terms);
            this.includeResources = includeResources;
            this.incremental = incremental;
            this.tool = tool;
            this.group = group;
        }

        @Override
        protected ProcessHandler handler(BuildStepContext context, List<String> commands) throws IOException {
            return incremental
                    ? ProcessHandler.OfTool.of(new IncrementalKotlinCompiler()).apply(commands)
                    : super.handler(context, commands);
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            return Javac.hasRelevantChange(arguments,
                    Set.of(".kt", ".java"),
                    Set.of("kotlinc", "javac"),
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
                    if (incremental) {
                        sources(arguments, context.next().resolve(CLASSES));
                    }
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
            Sources sources = sources(arguments, incremental ? null : target);
            if (sources.files().stream().noneMatch(name -> name.endsWith(".kt"))) {
                return CompletableFuture.completedStage(null);
            }
            if (!incremental) {
                return CompletableFuture.completedStage(command(properties,
                        target,
                        sources.release(),
                        sources,
                        sources.classpath(),
                        sources.friends(),
                        sources.files()));
            }
            Daemon daemon = daemon(properties, sources.release(), sources, sources.classpath(), sources.friends());
            List<String> configuration = new ArrayList<>(sources.jars());
            configuration.addAll(daemon.virtualMachine());
            for (int index = 0; index < daemon.compiler().size(); index++) {
                configuration.add(index > 0 && daemon.compiler().get(index - 1).equals(IncrementalKotlinCompiler.CLASSPATH)
                        ? ""
                        : daemon.compiler().get(index));
            }
            SequencedProperties listing = new SequencedProperties();
            for (String file : sources.files()) {
                listing.setProperty(file, IncrementalKotlinCompiler.digest(Path.of(file)));
            }
            SequencedProperties state = new SequencedProperties();
            state.setProperty("configuration", String.join("\n", configuration));
            state.setProperty("destination", target.toAbsolutePath().toString());
            Path working = Files.createDirectories(context.next().resolve(INCREMENTAL));
            Path previous = context.previous() == null ? null : context.previous().resolve(INCREMENTAL);
            boolean rebuild = previous == null
                    || !Files.isRegularFile(previous.resolve(STATE))
                    || !Files.isRegularFile(previous.resolve(LISTING))
                    || !SequencedProperties.ofFiles(previous.resolve(STATE)).equals(state);
            List<String> commands = new ArrayList<>(List.of(IncrementalKotlinCompiler.WORKING, working.resolve("caches").toString(),
                    IncrementalKotlinCompiler.SNAPSHOTS, working.resolve("snapshots").toString()));
            if (rebuild) {
                commands.add(IncrementalKotlinCompiler.REBUILD);
            } else {
                Path classes = context.previous().resolve(CLASSES);
                if (Files.isDirectory(classes)) {
                    Files.walkFileTree(classes, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            return BuildStep.underMetaInfVersions(classes.relativize(dir))
                                    ? FileVisitResult.SKIP_SUBTREE
                                    : FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String name = file.getFileName().toString();
                            if (name.endsWith(".class") || name.endsWith(".kotlin_module")) {
                                Path copy = target.resolve(classes.relativize(file));
                                Files.createDirectories(copy.getParent());
                                Files.copy(file, copy, StandardCopyOption.COPY_ATTRIBUTES);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                for (String folder : List.of("caches", "snapshots")) {
                    Path source = previous.resolve(folder);
                    if (Files.isDirectory(source)) {
                        Files.walkFileTree(source, new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                Files.createDirectories(working.resolve(folder).resolve(source.relativize(dir)));
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Files.copy(file, working.resolve(folder).resolve(source.relativize(file)));
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                }
                SequencedProperties recorded = SequencedProperties.ofFiles(previous.resolve(LISTING));
                for (String file : sources.files()) {
                    if (!listing.getProperty(file).equals(recorded.getProperty(file))) {
                        commands.add(IncrementalKotlinCompiler.MODIFIED);
                        commands.add(file);
                    }
                }
                for (String file : recorded.stringPropertyNames()) {
                    if (listing.getProperty(file) == null) {
                        commands.add(IncrementalKotlinCompiler.REMOVED);
                        commands.add(file);
                    }
                }
            }
            state.store(working.resolve(STATE));
            listing.store(working.resolve(LISTING));
            return CompletableFuture.completedStage(daemon.commands(sources.jars(), commands, target, sources.files()));
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
                List<String> friends = new ArrayList<>(classpath);
                friends.addAll(sources.friends());
                classpath.addAll(sources.classpath());
                List<String> commands = incremental
                        ? daemon(properties(arguments), Integer.toString(release), sources, classpath, friends)
                                .commands(sources.jars(), List.of(), target, files)
                        : command(properties(arguments), target, Integer.toString(release), sources, classpath, friends, files);
                chain = chain.thenComposeAsync(_ -> {
                    try {
                        ProcessHandler handler = incremental
                                ? ProcessHandler.OfTool.of(new IncrementalKotlinCompiler()).apply(commands)
                                : factory.apply(commands);
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
            SequencedMap<String, Path> internal = new LinkedHashMap<>();
            String release = null, tested = null, project = null, artifact = null;
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
                Dependencies.internal(argument.folder()).forEach(internal::putIfAbsent);
                Path module = argument.folder().resolve(BuildStep.MODULE);
                if (Files.isRegularFile(module)) {
                    tested = SequencedProperties.ofFiles(module).value("test");
                }
                Path metadata = argument.folder().resolve(BuildStep.METADATA);
                if (Files.isRegularFile(metadata)) {
                    SequencedProperties loaded = SequencedProperties.ofFiles(metadata);
                    project = loaded.value("project");
                    artifact = loaded.value("artifact");
                }
                Path javacProperties = ProcessBuildStep.locate(argument.folder(), ProcessBuildStep.PROCESS, "javac");
                if (javacProperties != null) {
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
            List<String> friends = new ArrayList<>();
            if (tested != null) {
                String coordinate = "maven/" + project + "/" + artifact + "/";
                for (Map.Entry<String, Path> entry : internal.entrySet()) {
                    ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(entry.getValue());
                    if ((project != null && artifact != null && entry.getKey().startsWith(coordinate))
                            || descriptor != null && descriptor.name().equals(tested)) {
                        String friend = entry.getValue().toString();
                        if (classpath.contains(friend)) {
                            friends.add(friend);
                        }
                    }
                }
            }
            return new Sources(files, jars, classpath, plugins, friends, versioned, release);
        }

        private List<String> command(SequencedMap<String, SequencedMap<String, String>> properties,
                                     Path target,
                                     String release,
                                     Sources sources,
                                     List<String> classpath,
                                     List<String> friends,
                                     List<String> files) {
            ForkedOptions forked = forked(properties);
            List<String> commands = new ArrayList<>(forked.jvm()), options = forked.program();
            List<String> arguments = arguments(release, sources, classpath, friends, options);
            commands.addAll(List.of(
                    "-cp", String.join(File.pathSeparator, sources.jars()),
                    MAIN_CLASS,
                    "-d", target.toString()));
            commands.addAll(arguments);
            commands.addAll(files);
            return commands;
        }

        private static Daemon daemon(SequencedMap<String, SequencedMap<String, String>> properties,
                                     String release,
                                     Sources sources,
                                     List<String> classpath,
                                     List<String> friends) {
            ForkedOptions forked = forked(properties);
            List<String> options = new ArrayList<>(forked.program()), virtualMachine = forked.jvm();
            if (!options.contains(MODULE_OPTION)) {
                options.addAll(List.of(MODULE_OPTION, "main"));
            }
            return new Daemon(virtualMachine, arguments(release, sources, classpath, friends, options));
        }

        private static List<String> arguments(String release,
                                              Sources sources,
                                              List<String> classpath,
                                              List<String> friends,
                                              List<String> options) {
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
            List<String> arguments = new ArrayList<>(List.of(
                    "-no-stdlib",
                    "-no-reflect",
                    IncrementalKotlinCompiler.CLASSPATH, String.join(File.pathSeparator, classpath)));
            if (release != null) {
                arguments.add("-jvm-target");
                arguments.add(Integer.parseInt(release) < 9 ? "1." + release : release);
            }
            for (String plugin : sources.plugins()) {
                arguments.add("-Xplugin=" + plugin);
            }
            if (!friends.isEmpty() && options.stream().noneMatch(option -> option.startsWith("-Xfriend-paths"))) {
                arguments.add("-Xfriend-paths=" + String.join(",", friends));
            }
            arguments.addAll(options);
            return arguments;
        }
    }

    private record Daemon(List<String> virtualMachine, List<String> compiler) {

        private List<String> commands(List<String> jars, List<String> state, Path target, List<String> files) {
            List<String> commands = new ArrayList<>();
            for (String jar : jars) {
                commands.add(IncrementalKotlinCompiler.TOOL);
                commands.add(jar);
            }
            for (String option : virtualMachine) {
                commands.add(IncrementalKotlinCompiler.JVM);
                commands.add(option);
            }
            commands.addAll(state);
            commands.add(IncrementalKotlinCompiler.DESTINATION);
            commands.add(target.toString());
            commands.addAll(compiler);
            commands.add(IncrementalKotlinCompiler.SOURCES);
            commands.addAll(files);
            return commands;
        }
    }

    private record Sources(List<String> files,
                           List<String> jars,
                           List<String> classpath,
                           List<String> plugins,
                           List<String> friends,
                           NavigableMap<Integer, List<String>> versioned,
                           String release) {
    }
}
