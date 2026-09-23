package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Jar;
import build.jenesis.step.JarSigner;
import build.jenesis.step.ProcessHandler;

public class InferredJavaToolchainModule implements BuildExecutorModule {

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final InferredSourceGenerationModule generatorModule;
    private final InferredCompilerChainModule compilerModule;
    private final InferredByteCodeQualityModule validatorModule;
    private final JarSigner signerStep;
    private final Function<InferredSourceGenerationModule, BuildExecutorModule> generator;
    private final Function<InferredCompilerChainModule, BuildExecutorModule> compiler;
    private final Function<InferredByteCodeQualityModule, BuildExecutorModule> validator;
    private final BuildExecutorModule transformer;
    private final BuildExecutorModule archiver;
    private final Function<JarSigner, BuildStep> signer;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredJavaToolchainModule(SequencedSet<Path> configuration,
                                       Map<String, Repository> repositories,
                                       Map<String, Resolver> resolvers) {
        this(configuration,
                null,
                PathPlacement.INFERRED,
                new InferredSourceGenerationModule(configuration, repositories, resolvers),
                new InferredCompilerChainModule(configuration, repositories, resolvers),
                new InferredByteCodeQualityModule(configuration, repositories, resolvers),
                new JarSigner(),
                value -> value,
                value -> value,
                value -> value,
                null,
                new Jar(ProcessHandler.Factory.of(), Jar.Sort.CLASSES).asModule("jar"),
                step -> step.configured() ? step : null,
                Collections.emptyNavigableMap());
    }

    public static InferredJavaToolchainModule ofEnvironment(Environment environment,
                                                            SequencedSet<Path> configuration,
                                                            Map<String, Repository> repositories,
                                                            Map<String, Resolver> resolvers) {
        return new InferredJavaToolchainModule(configuration,
                null,
                PathPlacement.INFERRED,
                InferredSourceGenerationModule.ofEnvironment(environment, configuration, repositories, resolvers),
                InferredCompilerChainModule.ofEnvironment(environment, configuration, repositories, resolvers),
                InferredByteCodeQualityModule.ofEnvironment(environment, configuration, repositories, resolvers),
                JarSigner.ofEnvironment(environment),
                value -> value,
                value -> value,
                value -> value,
                null,
                Jar.ofEnvironment(environment, ProcessHandler.Factory.of(), Jar.Sort.CLASSES).asModule("jar"),
                step -> step.configured() ? step : null,
                Collections.emptyNavigableMap());
    }

    private InferredJavaToolchainModule(SequencedSet<Path> configuration,
                                        Pinning pinning,
                                        PathPlacement pathPlacement,
                                        InferredSourceGenerationModule generatorModule,
                                        InferredCompilerChainModule compilerModule,
                                        InferredByteCodeQualityModule validatorModule,
                                        JarSigner signerStep,
                                        Function<InferredSourceGenerationModule, BuildExecutorModule> generator,
                                        Function<InferredCompilerChainModule, BuildExecutorModule> compiler,
                                        Function<InferredByteCodeQualityModule, BuildExecutorModule> validator,
                                        BuildExecutorModule transformer,
                                        BuildExecutorModule archiver,
                                        Function<JarSigner, BuildStep> signer,
                                        SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.generatorModule = generatorModule;
        this.compilerModule = compilerModule;
        this.validatorModule = validatorModule;
        this.signerStep = signerStep;
        this.generator = generator;
        this.compiler = compiler;
        this.validator = validator;
        this.transformer = transformer;
        this.archiver = archiver;
        this.signer = signer;
        this.custom = custom;
    }

    public InferredJavaToolchainModule pinning(Pinning pinning) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                custom);
    }

    public InferredJavaToolchainModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                custom);
    }

    public InferredJavaToolchainModule generator(Function<InferredSourceGenerationModule, BuildExecutorModule> generator) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                custom);
    }

    public InferredJavaToolchainModule compiler(Function<InferredCompilerChainModule, BuildExecutorModule> compiler) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                custom);
    }

    public InferredJavaToolchainModule validator(Function<InferredByteCodeQualityModule, BuildExecutorModule> validator) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                custom);
    }

    public InferredJavaToolchainModule transformer(BuildExecutorModule transformer) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                custom);
    }

    public InferredJavaToolchainModule archiver(BuildExecutorModule archiver) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                custom);
    }

    public InferredJavaToolchainModule signer(Function<JarSigner, BuildStep> signer) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                custom);
    }

    public InferredJavaToolchainModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredJavaToolchainModule(configuration,
                pinning,
                pathPlacement,
                generatorModule,
                compilerModule,
                validatorModule,
                signerStep,
                generator,
                compiler,
                validator,
                transformer,
                archiver,
                signer,
                custom);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        toolchain(signer == null ? null : signer.apply(signerStep)).accept(buildExecutor, inherited);
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    @Override
    public Optional<String> resolve(String path) {
        return toolchain(null).resolve(path);
    }

    private JavaToolchainModule toolchain(BuildStep signing) {
        BuildExecutorModule compiled = compiler == null ? null : compiler.apply(compilerModule
                .pinning(pinning)
                .pathPlacement(pathPlacement));
        if (compiled == null) {
            throw new IllegalStateException("A Java toolchain requires a compiler but none is configured");
        }
        return new JavaToolchainModule(
                generator == null ? null : generator.apply(generatorModule.pinning(pinning)),
                compiled,
                transformer,
                validator == null ? null : validator.apply(validatorModule.pinning(pinning)),
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
