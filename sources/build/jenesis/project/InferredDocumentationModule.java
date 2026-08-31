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

    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final Function<InferredDocumentationChainModule, BuildExecutorModule> generate;
    private final BuildStep archiver;

    public InferredDocumentationModule(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(repositories, resolvers, null, module -> module, new Jar(ProcessHandler.Factory.of(), Jar.Sort.JAVADOC));
    }

    private InferredDocumentationModule(Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers,
                                        Pinning pinning,
                                        Function<InferredDocumentationChainModule, BuildExecutorModule> generate,
                                        BuildStep archiver) {
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.generate = generate;
        this.archiver = archiver;
    }

    public InferredDocumentationModule pinning(Pinning pinning) {
        return new InferredDocumentationModule(repositories, resolvers, pinning, generate, archiver);
    }

    public InferredDocumentationModule generate(Function<InferredDocumentationChainModule, BuildExecutorModule> generate) {
        return new InferredDocumentationModule(repositories, resolvers, pinning, generate, archiver);
    }

    public InferredDocumentationModule archiver(BuildStep archiver) {
        return new InferredDocumentationModule(repositories, resolvers, pinning, generate, archiver);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        if (generate == null) {
            return;
        }
        BuildExecutorModule chain = generate.apply(new InferredDocumentationChainModule(repositories, resolvers)
                .pinning(pinning));
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
