package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
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
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredDocumentationModule(Map<String, Repository> repositories,
                                       Map<String, Resolver> resolvers) {
        this(null,
                new InferredDocumentationChainModule(repositories, resolvers),
                value -> value,
                new Jar(ProcessHandler.Factory.of(), Jar.Sort.JAVADOC),
                Collections.emptyNavigableMap());
    }

    public static InferredDocumentationModule ofEnvironment(Environment environment,
                                                            Map<String, Repository> repositories,
                                                            Map<String, Resolver> resolvers) {
        return new InferredDocumentationModule(null,
                InferredDocumentationChainModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                Jar.ofEnvironment(environment, ProcessHandler.Factory.of(), Jar.Sort.JAVADOC),
                Collections.emptyNavigableMap());
    }

    private InferredDocumentationModule(Pinning pinning,
                                        InferredDocumentationChainModule generateModule,
                                        Function<InferredDocumentationChainModule, BuildExecutorModule> generate,
                                        BuildStep archiver,
                                        SequencedMap<String, BuildExecutorModule> custom) {
        this.pinning = pinning;
        this.generateModule = generateModule;
        this.generate = generate;
        this.archiver = archiver;
        this.custom = custom;
    }

    public InferredDocumentationModule pinning(Pinning pinning) {
        return new InferredDocumentationModule(pinning,
                generateModule,
                generate,
                archiver,
                custom);
    }

    public InferredDocumentationModule generate(Function<InferredDocumentationChainModule, BuildExecutorModule> generate) {
        return new InferredDocumentationModule(pinning,
                generateModule,
                generate,
                archiver,
                custom);
    }

    public InferredDocumentationModule archiver(BuildStep archiver) {
        return new InferredDocumentationModule(pinning,
                generateModule,
                generate,
                archiver,
                custom);
    }

    public InferredDocumentationModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredDocumentationModule(pinning,
                generateModule,
                generate,
                archiver,
                custom);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
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
