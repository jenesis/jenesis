package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.ErrorProne;
import build.jenesis.step.Javac;
import build.jenesis.step.ProcessHandler;

public class InferredCompilerChainModule implements BuildExecutorModule {

    public static final String JAVAC = "javac", KOTLINC = "kotlinc", SCALAC = "scalac", GROOVYC = "groovyc", RESOURCE = "resource";
    public static final String ERRORPRONE = "errorprone";
    public static final String COMPILE = "compile";
    private static final String SCAN = "scan", SCAN_FILE = "scan.properties";
    private static final Set<String> ERRORPRONE_KEYS = Set.of("arguments");

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final Javac javacStep;
    private final KotlinCompilerModule kotlincModule;
    private final ScalaCompilerModule scalacModule;
    private final GroovyCompilerModule groovycModule;
    private final Function<Javac, BuildStep> javac;
    private final Function<KotlinCompilerModule, BuildExecutorModule> kotlinc;
    private final Function<ScalaCompilerModule, BuildExecutorModule> scalac;
    private final Function<GroovyCompilerModule, BuildExecutorModule> groovyc;
    private final Function<ErrorProne, BuildStep> errorprone;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredCompilerChainModule(SequencedSet<Path> configuration,
                                       Map<String, Repository> repositories,
                                       Map<String, Resolver> resolvers) {
        this(configuration,
                repositories,
                resolvers,
                null,
                PathPlacement.INFERRED,
                new Javac(ProcessHandler.Factory.of()),
                new KotlinCompilerModule(repositories, resolvers),
                new ScalaCompilerModule(repositories, resolvers),
                new GroovyCompilerModule(repositories, resolvers),
                step -> step,
                value -> value,
                value -> value,
                value -> value,
                step -> step,
                Collections.emptyNavigableMap());
    }

    public static InferredCompilerChainModule ofEnvironment(Environment environment,
                                                            SequencedSet<Path> configuration,
                                                            Map<String, Repository> repositories,
                                                            Map<String, Resolver> resolvers) {
        InferredCompilerChainModule module = new InferredCompilerChainModule(configuration,
                repositories,
                resolvers,
                null,
                PathPlacement.INFERRED,
                Javac.ofEnvironment(environment, ProcessHandler.Factory.of()),
                KotlinCompilerModule.ofEnvironment(environment, repositories, resolvers),
                ScalaCompilerModule.ofEnvironment(environment, repositories, resolvers),
                GroovyCompilerModule.ofEnvironment(environment, repositories, resolvers),
                step -> step,
                value -> value,
                value -> value,
                value -> value,
                step -> step,
                Collections.emptyNavigableMap());
        Boolean errorprone = environment.flagOrNull("compile.errorprone");
        return errorprone == null ? module : module.errorprone(errorprone ? step -> step : null);
    }

    private InferredCompilerChainModule(SequencedSet<Path> configuration,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers,
                                        Pinning pinning,
                                        PathPlacement pathPlacement,
                                        Javac javacStep,
                                        KotlinCompilerModule kotlincModule,
                                        ScalaCompilerModule scalacModule,
                                        GroovyCompilerModule groovycModule,
                                        Function<Javac, BuildStep> javac,
                                        Function<KotlinCompilerModule, BuildExecutorModule> kotlinc,
                                        Function<ScalaCompilerModule, BuildExecutorModule> scalac,
                                        Function<GroovyCompilerModule, BuildExecutorModule> groovyc,
                                        Function<ErrorProne, BuildStep> errorprone,
                                        SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.javacStep = javacStep;
        this.kotlincModule = kotlincModule;
        this.scalacModule = scalacModule;
        this.groovycModule = groovycModule;
        this.javac = javac;
        this.kotlinc = kotlinc;
        this.scalac = scalac;
        this.groovyc = groovyc;
        this.errorprone = errorprone;
        this.custom = custom;
    }

    public InferredCompilerChainModule pinning(Pinning pinning) {
        return new InferredCompilerChainModule(configuration,
                repositories,
                resolvers,
                pinning,
                pathPlacement,
                javacStep,
                kotlincModule,
                scalacModule,
                groovycModule,
                javac,
                kotlinc,
                scalac,
                groovyc,
                errorprone,
                custom);
    }

    public InferredCompilerChainModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredCompilerChainModule(configuration,
                repositories,
                resolvers,
                pinning,
                pathPlacement,
                javacStep,
                kotlincModule,
                scalacModule,
                groovycModule,
                javac,
                kotlinc,
                scalac,
                groovyc,
                errorprone,
                custom);
    }

    public InferredCompilerChainModule javac(Function<Javac, BuildStep> javac) {
        return new InferredCompilerChainModule(configuration,
                repositories,
                resolvers,
                pinning,
                pathPlacement,
                javacStep,
                kotlincModule,
                scalacModule,
                groovycModule,
                javac,
                kotlinc,
                scalac,
                groovyc,
                errorprone,
                custom);
    }

    public InferredCompilerChainModule kotlinc(Function<KotlinCompilerModule, BuildExecutorModule> kotlinc) {
        return new InferredCompilerChainModule(configuration,
                repositories,
                resolvers,
                pinning,
                pathPlacement,
                javacStep,
                kotlincModule,
                scalacModule,
                groovycModule,
                javac,
                kotlinc,
                scalac,
                groovyc,
                errorprone,
                custom);
    }

    public InferredCompilerChainModule scalac(Function<ScalaCompilerModule, BuildExecutorModule> scalac) {
        return new InferredCompilerChainModule(configuration,
                repositories,
                resolvers,
                pinning,
                pathPlacement,
                javacStep,
                kotlincModule,
                scalacModule,
                groovycModule,
                javac,
                kotlinc,
                scalac,
                groovyc,
                errorprone,
                custom);
    }

    public InferredCompilerChainModule groovyc(Function<GroovyCompilerModule, BuildExecutorModule> groovyc) {
        return new InferredCompilerChainModule(configuration,
                repositories,
                resolvers,
                pinning,
                pathPlacement,
                javacStep,
                kotlincModule,
                scalacModule,
                groovycModule,
                javac,
                kotlinc,
                scalac,
                groovyc,
                errorprone,
                custom);
    }

    public InferredCompilerChainModule errorprone(Function<ErrorProne, BuildStep> errorprone) {
        return new InferredCompilerChainModule(configuration,
                repositories,
                resolvers,
                pinning,
                pathPlacement,
                javacStep,
                kotlincModule,
                scalacModule,
                groovycModule,
                javac,
                kotlinc,
                scalac,
                groovyc,
                errorprone,
                custom);
    }

    public InferredCompilerChainModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredCompilerChainModule(configuration,
                repositories,
                resolvers,
                pinning,
                pathPlacement,
                javacStep,
                kotlincModule,
                scalacModule,
                groovycModule,
                javac,
                kotlinc,
                scalac,
                groovyc,
                errorprone,
                custom);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(SCAN, new Scan(), inherited.sequencedKeySet());
        SequencedSet<String> compileInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
        compileInputs.add(SCAN);
        buildExecutor.addModule(COMPILE,
                new Compile(configuration, repositories, resolvers, pinning, pathPlacement,
                        javacStep, kotlincModule, scalacModule, groovycModule,
                        javac, kotlinc, scalac, groovyc, errorprone),
                compileInputs);
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(SCAN) ? Optional.empty() : Optional.of(path);
    }

    private static class Scan implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            boolean[] flags = new boolean[5];
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path sources = argument.folder().resolve(BuildStep.SOURCES);
                if (!Files.exists(sources)) {
                    continue;
                }
                Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName().toString();
                        if (name.endsWith(".java")) {
                            flags[0] = true;
                        } else if (name.endsWith(".kt")) {
                            flags[1] = true;
                        } else if (name.endsWith(".scala")) {
                            flags[2] = true;
                        } else if (name.endsWith(".groovy")) {
                            flags[3] = true;
                        } else {
                            flags[4] = true;
                        }
                        return flags[0] && flags[1] && flags[2] && flags[3] && flags[4]
                                ? FileVisitResult.TERMINATE
                                : FileVisitResult.CONTINUE;
                    }
                });
                if (flags[0] && flags[1] && flags[2] && flags[3] && flags[4]) {
                    break;
                }
            }
            SequencedProperties properties = new SequencedProperties();
            properties.setProperty(JAVAC, Boolean.toString(flags[0]));
            properties.setProperty(KOTLINC, Boolean.toString(flags[1]));
            properties.setProperty(SCALAC, Boolean.toString(flags[2]));
            properties.setProperty(GROOVYC, Boolean.toString(flags[3]));
            properties.setProperty(RESOURCE, Boolean.toString(flags[4]));
            properties.store(context.next().resolve(SCAN_FILE));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Compile(SequencedSet<Path> configuration,
                           Map<String, Repository> repositories,
                           Map<String, Resolver> resolvers,
                           Pinning pinning,
                           PathPlacement pathPlacement,
                           Javac javacStep,
                           KotlinCompilerModule kotlincModule,
                           ScalaCompilerModule scalacModule,
                           GroovyCompilerModule groovycModule,
                           Function<Javac, BuildStep> javac,
                           Function<KotlinCompilerModule, BuildExecutorModule> kotlinc,
                           Function<ScalaCompilerModule, BuildExecutorModule> scalac,
                           Function<GroovyCompilerModule, BuildExecutorModule> groovyc,
                           Function<ErrorProne, BuildStep> errorprone) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
            Path scanFolder = inherited.get(PREVIOUS + SCAN);
            if (scanFolder == null) {
                throw new IllegalStateException("Compile sub-module is missing its upstream scan input");
            }
            SequencedProperties scan = SequencedProperties.ofFiles(scanFolder.resolve(SCAN_FILE));
            boolean hasJava = scan.flag(JAVAC) && javac != null;
            boolean hasKotlin = scan.flag(KOTLINC) && kotlinc != null;
            boolean hasScala = scan.flag(SCALAC) && scalac != null;
            boolean hasGroovy = scan.flag(GROOVYC) && groovyc != null;
            boolean hasResource = scan.flag(RESOURCE);

            SequencedSet<String> sourceInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
            sourceInputs.remove(PREVIOUS + SCAN);

            SequencedSet<String> dependencies = new LinkedHashSet<>(sourceInputs);
            if (hasKotlin) {
                BuildExecutorModule compiler = kotlinc.apply(kotlincModule.pinning(pinning)
                        .includeResources(!hasJava && !hasScala && !hasGroovy));
                if (compiler != null) {
                    buildExecutor.addModule(KOTLINC, compiler, dependencies);
                    SequencedSet<String> updated = new LinkedHashSet<>(dependencies);
                    updated.add(KOTLINC + "/" + KotlinCompilerModule.CLASSES);
                    dependencies = updated;
                }
            }
            if (hasScala) {
                BuildExecutorModule compiler = scalac.apply(scalacModule.pinning(pinning)
                        .includeResources(!hasJava && !hasKotlin && !hasGroovy));
                if (compiler != null) {
                    buildExecutor.addModule(SCALAC, compiler, dependencies);
                    SequencedSet<String> updated = new LinkedHashSet<>(dependencies);
                    updated.add(SCALAC + "/" + ScalaCompilerModule.CLASSES);
                    dependencies = updated;
                }
            }
            if (hasJava) {
                BuildStep plugin = configured();
                BuildStep compiler = javac.apply((plugin == null
                        ? javacStep
                        : javacStep.factory(ProcessHandler.Factory.FORK))
                        .includeResources(!hasKotlin && !hasScala && !hasGroovy)
                        .pathPlacement(pathPlacement));
                if (compiler != null) {
                    SequencedSet<String> javacInputs = new LinkedHashSet<>(dependencies);
                    if (plugin != null) {
                        buildExecutor.addStep(ERRORPRONE, plugin, sourceInputs);
                        javacInputs.add(ERRORPRONE);
                    }
                    buildExecutor.addStep(JAVAC, compiler, javacInputs);
                    SequencedSet<String> updated = new LinkedHashSet<>(javacInputs);
                    updated.add(JAVAC);
                    dependencies = updated;
                }
            }
            if (hasGroovy) {
                BuildExecutorModule compiler = groovyc.apply(groovycModule.pinning(pinning)
                        .includeResources(!hasJava && !hasKotlin && !hasScala));
                if (compiler != null) {
                    buildExecutor.addModule(GROOVYC, compiler, dependencies);
                }
            }
            int compilers = (hasJava ? 1 : 0) + (hasKotlin ? 1 : 0) + (hasScala ? 1 : 0) + (hasGroovy ? 1 : 0);
            if (hasResource && compilers != 1) {
                buildExecutor.addStep(RESOURCE, new Resources(), sourceInputs);
            }
        }

        private BuildStep configured() throws IOException {
            Path file = errorprone == null ? null : BuildStep.locate(configuration, "errorprone.properties");
            if (file == null) {
                return null;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(file);
            for (String key : properties.stringPropertyNames()) {
                if (!ERRORPRONE_KEYS.contains(key)) {
                    throw new IllegalArgumentException("Unknown Error Prone property: "
                            + key
                            + " (expected one of "
                            + new TreeSet<>(ERRORPRONE_KEYS)
                            + ")");
                }
            }
            return errorprone.apply(new ErrorProne().arguments(properties.words("arguments")));
        }
    }

    private static class Resources implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path target = Files.createDirectory(context.next().resolve(BuildStep.CLASSES));
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path sources = argument.folder().resolve(BuildStep.SOURCES);
                if (!Files.exists(sources)) {
                    continue;
                }
                Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Files.createDirectories(target.resolve(sources.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String name = file.getFileName().toString();
                        Path relative = sources.relativize(file);
                        if (!name.endsWith(".java")
                                && !name.endsWith(".kt")
                                && !name.endsWith(".scala")
                                && !name.endsWith(".groovy")
                                && !BuildStep.underMetaInfVersions(relative)) {
                            BuildStep.linkOrCopy(target.resolve(relative), file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
