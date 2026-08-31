package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Jar;
import build.jenesis.step.ProcessHandler;

public class InferredJavaToolchainModule implements BuildExecutorModule {

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final Function<InferredCompilerChainModule, BuildExecutorModule> compiler;
    private final Function<InferredByteCodeQualityModule, BuildExecutorModule> validator;
    private final BuildExecutorModule transformer;
    private final BuildExecutorModule archiver;

    public InferredJavaToolchainModule(SequencedSet<Path> configuration,
                                       Map<String, Repository> repositories,
                                       Map<String, Resolver> resolvers) {
        this(configuration,
                repositories,
                resolvers,
                null,
                PathPlacement.INFERRED,
                module -> module,
                module -> module,
                null,
                new Jar(ProcessHandler.Factory.of(), Jar.Sort.CLASSES).asModule("jar"));
    }

    private InferredJavaToolchainModule(SequencedSet<Path> configuration,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers,
                                        Pinning pinning,
                                        PathPlacement pathPlacement,
                                        Function<InferredCompilerChainModule, BuildExecutorModule> compiler,
                                        Function<InferredByteCodeQualityModule, BuildExecutorModule> validator,
                                        BuildExecutorModule transformer,
                                        BuildExecutorModule archiver) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.compiler = compiler;
        this.validator = validator;
        this.transformer = transformer;
        this.archiver = archiver;
    }

    public InferredJavaToolchainModule pinning(Pinning pinning) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                compiler, validator, transformer, archiver);
    }

    public InferredJavaToolchainModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                compiler, validator, transformer, archiver);
    }

    public InferredJavaToolchainModule compiler(Function<InferredCompilerChainModule, BuildExecutorModule> compiler) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                compiler, validator, transformer, archiver);
    }

    public InferredJavaToolchainModule validator(Function<InferredByteCodeQualityModule, BuildExecutorModule> validator) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                compiler, validator, transformer, archiver);
    }

    public InferredJavaToolchainModule transformer(BuildExecutorModule transformer) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                compiler, validator, transformer, archiver);
    }

    public InferredJavaToolchainModule archiver(BuildExecutorModule archiver) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                compiler, validator, transformer, archiver);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        toolchain().accept(buildExecutor, inherited);
    }

    @Override
    public Optional<String> resolve(String path) {
        return toolchain().resolve(path);
    }

    private JavaToolchainModule toolchain() {
        BuildExecutorModule compiled = compiler == null ? null : compiler.apply(
                new InferredCompilerChainModule(repositories, resolvers)
                        .pinning(pinning)
                        .pathPlacement(pathPlacement));
        if (compiled == null) {
            throw new IllegalStateException("A Java toolchain requires a compiler but none is configured");
        }
        return new JavaToolchainModule(
                null,
                compiled,
                transformer,
                validator == null ? null : validator.apply(
                        new InferredByteCodeQualityModule(configuration, repositories, resolvers).pinning(pinning)),
                archiver);
    }
}
