package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Jar;
import build.jenesis.step.ProcessHandler;

public class InferredDocumentationModule implements BuildExecutorModule {

    public static final String GENERATE = "generate", ARCHIVE = "archive";

    private final Pinning pinning;
    private final InferredDocumentationChainModule generateModule;
    private final Function<InferredDocumentationChainModule, BuildExecutorModule> generate;
    private final BuildStep archiver;

    public InferredDocumentationModule(Map<String, Repository> repositories,
                                       Map<String, Resolver> resolvers) {
        this(null, new InferredDocumentationChainModule(repositories, resolvers),
             value -> value,
             new Jar(ProcessHandler.Factory.of(), Jar.Sort.JAVADOC));
    }

    public static InferredDocumentationModule ofKeys(Function<String, String> keys,
                                                     Map<String, Repository> repositories,
                                                     Map<String, Resolver> resolvers) {
        return new InferredDocumentationModule(null, InferredDocumentationChainModule.ofKeys(keys, repositories, resolvers),
                value -> value,
                Jar.ofKeys(keys, ProcessHandler.Factory.of(), Jar.Sort.JAVADOC));
    }

    private InferredDocumentationModule(Pinning pinning,
                                        InferredDocumentationChainModule generateModule,
                                        Function<InferredDocumentationChainModule, BuildExecutorModule> generate,
                                        BuildStep archiver) {
        this.pinning = pinning;
        this.generateModule = generateModule;
        this.generate = generate;
        this.archiver = archiver;
    }

    public InferredDocumentationModule pinning(Pinning pinning) {
        return new InferredDocumentationModule(pinning, generateModule, generate, archiver);
    }

    public InferredDocumentationModule generate(Function<InferredDocumentationChainModule, BuildExecutorModule> generate) {
        return new InferredDocumentationModule(pinning, generateModule, generate, archiver);
    }

    public InferredDocumentationModule archiver(BuildStep archiver) {
        return new InferredDocumentationModule(pinning, generateModule, generate, archiver);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        if (generate == null) {
            return;
        }
        BuildExecutorModule chain = generate.apply(generateModule.pinning(pinning));
        if (chain == null) {
            return;
        }
        buildExecutor.addModule(GENERATE, chain, inherited.sequencedKeySet());
        if (archiver != null) {
            buildExecutor.addStep(ARCHIVE,
                    archiver,
                    GENERATE
                            + "/"
                            + InferredDocumentationChainModule.DOCUMENT
                            + "/"
                            + InferredDocumentationChainModule.AGGREGATE);
        }
    }
}
