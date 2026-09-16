package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Jar;
import build.jenesis.step.JarSigner;
import build.jenesis.step.ProcessHandler;

public class InferredJavaToolchainModule implements BuildExecutorModule {

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final Function<InferredSourceGenerationModule, BuildExecutorModule> generator;
    private final Function<InferredCompilerChainModule, BuildExecutorModule> compiler;
    private final Function<InferredByteCodeQualityModule, BuildExecutorModule> validator;
    private final BuildExecutorModule transformer;
    private final BuildExecutorModule archiver;
    private final Function<JarSigner, BuildStep> signer;

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
                module -> module,
                null,
                new Jar(ProcessHandler.Factory.of(), Jar.Sort.CLASSES).asModule("jar"),
                step -> step.configured() ? step : null);
    }

    private InferredJavaToolchainModule(SequencedSet<Path> configuration,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers,
                                        Pinning pinning,
                                        PathPlacement pathPlacement,
                                        Function<InferredSourceGenerationModule, BuildExecutorModule> generator,
                                        Function<InferredCompilerChainModule, BuildExecutorModule> compiler,
                                        Function<InferredByteCodeQualityModule, BuildExecutorModule> validator,
                                        BuildExecutorModule transformer,
                                        BuildExecutorModule archiver,
                                        Function<JarSigner, BuildStep> signer) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.generator = generator;
        this.compiler = compiler;
        this.validator = validator;
        this.transformer = transformer;
        this.archiver = archiver;
        this.signer = signer;
    }

    public InferredJavaToolchainModule pinning(Pinning pinning) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                generator, compiler, validator, transformer, archiver, signer);
    }

    public InferredJavaToolchainModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                generator, compiler, validator, transformer, archiver, signer);
    }

    public InferredJavaToolchainModule generator(Function<InferredSourceGenerationModule, BuildExecutorModule> generator) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                generator, compiler, validator, transformer, archiver, signer);
    }

    public InferredJavaToolchainModule compiler(Function<InferredCompilerChainModule, BuildExecutorModule> compiler) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                generator, compiler, validator, transformer, archiver, signer);
    }

    public InferredJavaToolchainModule validator(Function<InferredByteCodeQualityModule, BuildExecutorModule> validator) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                generator, compiler, validator, transformer, archiver, signer);
    }

    public InferredJavaToolchainModule transformer(BuildExecutorModule transformer) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                generator, compiler, validator, transformer, archiver, signer);
    }

    public InferredJavaToolchainModule archiver(BuildExecutorModule archiver) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                generator, compiler, validator, transformer, archiver, signer);
    }

    public InferredJavaToolchainModule signer(Function<JarSigner, BuildStep> signer) {
        return new InferredJavaToolchainModule(configuration, repositories, resolvers, pinning, pathPlacement,
                generator, compiler, validator, transformer, archiver, signer);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        toolchain(signing()).accept(buildExecutor, inherited);
    }

    @Override
    public Optional<String> resolve(String path) {
        return toolchain(null).resolve(path);
    }

    private BuildStep signing() {
        return signer == null ? null : signer.apply(new JarSigner());
    }

    private JavaToolchainModule toolchain(BuildStep signing) {
        BuildExecutorModule compiled = compiler == null ? null : compiler.apply(
                new InferredCompilerChainModule(configuration, repositories, resolvers)
                        .pinning(pinning)
                        .pathPlacement(pathPlacement));
        if (compiled == null) {
            throw new IllegalStateException("A Java toolchain requires a compiler but none is configured");
        }
        return new JavaToolchainModule(
                generator == null ? null : generator.apply(
                        new InferredSourceGenerationModule(configuration, repositories, resolvers).pinning(pinning)),
                compiled,
                transformer,
                validator == null ? null : validator.apply(
                        new InferredByteCodeQualityModule(configuration, repositories, resolvers).pinning(pinning)),
                signing == null || archiver == null ? archiver : new SignedArchive(archiver, signing));
    }

    private record SignedArchive(BuildExecutorModule archiver, BuildStep signer) implements BuildExecutorModule {

        private static final String ARCHIVE = "archive", SIGN = "sign";

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            buildExecutor.addModule(ARCHIVE, archiver, inherited.sequencedKeySet());
            buildExecutor.addStep(SIGN, signer, Stream.concat(
                    Stream.of(ARCHIVE),
                    inherited.sequencedKeySet().stream()));
        }

        @Override
        public Optional<String> resolve(String path) {
            return path.equals(SIGN) ? Optional.of("") : Optional.empty();
        }
    }
}
