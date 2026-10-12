package build.jenesis.project;

import module java.base;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
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

public class ScalaCompilerModule implements BuildExecutorModule {

    public static final String ARTIFACTS = "artifacts", CLASSES = "classes";
    private static final String REQUIRED = "required",
            COMPILED = "compiled",
            DEPENDENCIES = "dependencies",
            ZINC = "zinc",
            INCREMENTAL = "incremental/",
            STATE = "state.properties";
    private static final List<String> PREFERRED_PREFIXES = List.of("maven", "module");
    private static final String MODULE_NAME = "org.scala.lang.scala3.compiler",
            MAVEN_GROUP = "org.scala-lang",
            MAVEN_ARTIFACT = "scala3-compiler_3",
            BRIDGE_ARTIFACT = "scala3-sbt-bridge",
            ZINC_GROUP = "org.scala-sbt",
            ZINC_ARTIFACT = "zinc_2.13";

    private final Map<String, Resolver> resolvers;
    private final Dependencies dependencies;
    private final Pinning pinning;
    private final boolean includeResources;
    private final boolean incremental;
    private final String tool;
    private final String group;
    private final Function<List<String>, ? extends ProcessHandler> factory;
    private final ProcessBuildStep.Terms terms;

    public ScalaCompilerModule(Map<String, Repository> repositories,
                               Map<String, Resolver> resolvers) {
        this(resolvers,
             new Dependencies(repositories, resolvers),
             null,
             true,
             false,
             "scalac",
             "main",
             null,
             new ProcessBuildStep.Terms());
    }

    public static ScalaCompilerModule ofEnvironment(Environment environment,
                                                    Map<String, Repository> repositories,
                                                    Map<String, Resolver> resolvers) {
        return new ScalaCompilerModule(resolvers,
                Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                true,
                environment.flag("scala.incremental", false),
                "scalac",
                "main",
                null,
                ProcessBuildStep.Terms.ofEnvironment(environment, "scalac"));
    }

    private ScalaCompilerModule(Map<String, Resolver> resolvers,
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

    public ScalaCompilerModule factory(Function<List<String>, ? extends ProcessHandler> factory) {
        return new ScalaCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public ScalaCompilerModule pinning(Pinning pinning) {
        return new ScalaCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public ScalaCompilerModule includeResources(boolean includeResources) {
        return new ScalaCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public ScalaCompilerModule incremental(boolean incremental) {
        return new ScalaCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public ScalaCompilerModule tool(String tool) {
        return new ScalaCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public ScalaCompilerModule group(String group) {
        return new ScalaCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms);
    }

    public ScalaCompilerModule printing(BiConsumer<Boolean, String> printing) {
        return new ScalaCompilerModule(resolvers, dependencies, pinning, includeResources, incremental, tool, group, factory, terms.printing(printing));
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
        if (incremental) {
            buildExecutor.addModule(ZINC,
                    dependencies.pinning(pinning).group(ZINC),
                    resolveInputs);
            compileInputs.add(ZINC);
        }
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
        if (path.equals(ZINC)) {
            return Optional.of(ZINC);
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
                        "No suitable resolver for Scala compiler. Available prefixes: " + prefixes
                                + ". Expected one of: " + PREFERRED_PREFIXES);
            }
            String coordinate = switch (selectedPrefix) {
                case "module" -> selectedPrefix + "/" + MODULE_NAME;
                case "maven" -> selectedPrefix + "/" + MAVEN_GROUP + "/" + MAVEN_ARTIFACT + "/RELEASE";
                default -> throw new IllegalStateException("Unreachable");
            };
            SequencedProperties requires = new SequencedProperties();
            requires.setProperty(tool + "/runtime/" + coordinate, "");
            if (incremental) {
                if (!prefixes.contains("maven")) {
                    throw new IllegalStateException("jenesis.scala.incremental resolves Zinc and the Scala"
                            + " compiler's bridge to it from a Maven repository, but only " + prefixes
                            + " resolve here: name a Maven repository, or set jenesis.scala.incremental=false");
                }
                requires.setProperty(tool + "/runtime/maven/" + MAVEN_GROUP + "/" + BRIDGE_ARTIFACT + "/RELEASE", "");
                requires.setProperty(ZINC + "/runtime/maven/" + ZINC_GROUP + "/" + ZINC_ARTIFACT + "/RELEASE", "");
            }
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
            super("scalac", factory, terms);
            this.includeResources = includeResources;
            this.incremental = incremental;
            this.tool = tool;
            this.group = group;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            if (!incremental) {
                return super.apply(executor, context, arguments);
            }
            return super.apply(executor, context, arguments).thenApplyAsync(result -> {
                Path compiled = context.next().resolve(INCREMENTAL + CLASSES), target = context.next().resolve(CLASSES);
                if (!result.next() || !Files.isDirectory(compiled)) {
                    return result;
                }
                try {
                    Files.walkFileTree(compiled, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (file.getFileName().toString().endsWith(".class") && ClassFile.of()
                                    .parse(file)
                                    .findAttribute(Attributes.sourceFile())
                                    .map(attribute -> attribute.sourceFile().stringValue().endsWith(".java"))
                                    .orElse(false)) {
                                return FileVisitResult.CONTINUE;
                            }
                            Path published = target.resolve(compiled.relativize(file));
                            Files.createDirectories(published.getParent());
                            BuildStep.linkOrCopy(published, file);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return result;
            }, executor);
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return Javac.hasRelevantChange(arguments,
                    Set.of(".scala", ".java"),
                    Set.of("scalac.properties", "javac.properties"),
                    includeResources);
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            Path target = Files.createDirectory(context.next().resolve(CLASSES));
            List<String> files = new ArrayList<>(),
                    jars = new ArrayList<>(),
                    classpath = new ArrayList<>(),
                    plugins = new ArrayList<>();
            String release = null;
            Path sourceroot = null;
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
                    if (sourceroot == null) {
                        sourceroot = sources;
                    }
                    Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            Path relative = sources.relativize(dir);
                            if (BuildStep.underBuildJenesis(relative)) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            Files.createDirectories(target.resolve(relative));
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String name = file.toString();
                            if (name.endsWith(".scala")
                                    || (name.endsWith(".java")
                                    && !file.getFileName().toString().equals("module-info.java"))) {
                                files.add(name);
                            } else if (includeResources
                                    && !name.endsWith(".java")
                                    && !BuildStep.underMetaInfVersions(sources.relativize(file))
                                    && !BuildStep.underBuildJenesis(sources.relativize(file))) {
                                BuildStep.linkOrCopy(target.resolve(sources.relativize(file)), file);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
            files.sort(null);
            if (files.stream().noneMatch(name -> name.endsWith(".scala"))) {
                return CompletableFuture.completedStage(null);
            }
            if (jars.isEmpty()) {
                throw new IllegalStateException(
                        "No compiler jars resolved upstream of the Scala compile step");
            }
            for (List<String> entries : List.of(jars, classpath, plugins)) {
                for (String entry : entries) {
                    if (entry.indexOf(File.pathSeparatorChar) != -1) {
                        throw new IllegalArgumentException(
                                "Path entry contains separator '" + File.pathSeparator + "': " + entry);
                    }
                }
            }
            List<String> userClasspath = new ArrayList<>(jars);
            userClasspath.addAll(classpath);
            if (incremental) {
                List<String> zinc = new ArrayList<>();
                for (BuildStepArgument argument : arguments.values()) {
                    if (!argument.removed()) {
                        for (Path jar : Dependencies.select(argument.folder(), ZINC, "runtime")) {
                            zinc.add(jar.toString());
                        }
                    }
                }
                if (zinc.isEmpty()) {
                    throw new IllegalStateException("No Zinc jars resolved upstream of the Scala compile step");
                }
                CodeSource source = IncrementalScalaCompiler.class.getProtectionDomain().getCodeSource();
                if (source == null) {
                    throw new IllegalStateException("jenesis.scala.incremental forks Zinc with the engine's own"
                            + " classes, but the engine was loaded from no location it can hand a JVM: set"
                            + " jenesis.scala.incremental=false");
                }
                String engine;
                try {
                    engine = Path.of(source.getLocation().toURI()).toString();
                } catch (URISyntaxException e) {
                    throw new IllegalStateException(e);
                }
                List<String> commands = new ArrayList<>(List.of(
                        "-cp", engine + File.pathSeparator + String.join(File.pathSeparator, zinc),
                        IncrementalScalaCompiler.class.getName()));
                for (String jar : jars) {
                    commands.add(IncrementalScalaCompiler.COMPILER);
                    commands.add(jar);
                }
                for (String entry : userClasspath) {
                    commands.add(IncrementalScalaCompiler.CLASSPATH);
                    commands.add(entry);
                }
                List<String> scalac = new ArrayList<>(List.of("-sourceroot", sourceroot.toString())),
                        javac = new ArrayList<>(List.of("-proc:none", "-nowarn"));
                if (release != null) {
                    scalac.addAll(List.of("-release", release));
                    javac.addAll(List.of("--release", release));
                }
                for (String plugin : plugins) {
                    scalac.add("-Xplugin:" + plugin);
                }
                scalac.forEach(option -> commands.addAll(List.of(IncrementalScalaCompiler.SCALAC, option)));
                javac.forEach(option -> commands.addAll(List.of(IncrementalScalaCompiler.JAVAC, option)));
                Path state = Files.createDirectories(context.next().resolve(INCREMENTAL));
                SequencedProperties configuration = new SequencedProperties();
                configuration.setProperty("configuration", String.join("\n", jars) + "\n" + String.join("\n", zinc)
                        + "\n" + String.join("\n", scalac) + "\n" + String.join("\n", javac));
                configuration.setProperty("state", state.toAbsolutePath().toString());
                Path previous = context.previous() == null ? null : context.previous().resolve(INCREMENTAL);
                if (previous != null
                        && Files.isRegularFile(previous.resolve(STATE))
                        && SequencedProperties.ofFiles(previous.resolve(STATE)).equals(configuration)) {
                    Files.walkFileTree(previous, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                            Files.createDirectories(state.resolve(previous.relativize(dir)));
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Files.copy(file, state.resolve(previous.relativize(file)), StandardCopyOption.COPY_ATTRIBUTES);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                configuration.store(state.resolve(STATE));
                commands.addAll(List.of(
                        IncrementalScalaCompiler.ANALYSIS, state.resolve("analysis.bin").toString(),
                        IncrementalScalaCompiler.CLASSES, state.resolve(CLASSES).toString(),
                        IncrementalScalaCompiler.SOURCES));
                commands.addAll(files);
                return CompletableFuture.completedStage(commands);
            }
            List<String> commands = new ArrayList<>(List.of(
                    "-cp", String.join(File.pathSeparator, jars),
                    "dotty.tools.dotc.Main",
                    "-d", target.toString(),
                    "-classpath", String.join(File.pathSeparator, userClasspath),
                    "-sourceroot", sourceroot.toString()));
            if (release != null) {
                commands.add("-release");
                commands.add(release);
            }
            for (String plugin : plugins) {
                commands.add("-Xplugin:" + plugin);
            }
            commands.addAll(files);
            return CompletableFuture.completedStage(commands);
        }
    }
}
