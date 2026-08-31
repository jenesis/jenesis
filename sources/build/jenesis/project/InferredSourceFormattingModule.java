package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Bind;

public class InferredSourceFormattingModule implements BuildExecutorModule {

    private static final String JAVA = "java",
            KTLINT = "ktlint",
            SCALAFMT = "scalafmt";

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final boolean verify;
    private final Function<BuildExecutorModule, BuildExecutorModule> java;
    private final Function<KtlintFormatModule, BuildExecutorModule> ktlint;
    private final Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt;

    public InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration, repositories, resolvers, null,
                !Boolean.getBoolean("jenesis.format.rewrite"),
                enabledBy("jenesis.format.java"),
                enabledBy("jenesis.format.ktlint"),
                enabledBy("jenesis.format.scalafmt"));
    }

    private InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers,
                                           Pinning pinning,
                                           boolean verify,
                                           Function<BuildExecutorModule, BuildExecutorModule> java,
                                           Function<KtlintFormatModule, BuildExecutorModule> ktlint,
                                           Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.verify = verify;
        this.java = java;
        this.ktlint = ktlint;
        this.scalafmt = scalafmt;
    }

    private static <M extends BuildExecutorModule> Function<M, BuildExecutorModule> enabledBy(String property) {
        return Boolean.parseBoolean(System.getProperty(property, "true")) ? module -> module : null;
    }

    public InferredSourceFormattingModule pinning(Pinning pinning) {
        return new InferredSourceFormattingModule(configuration, repositories, resolvers, pinning, verify, java, ktlint, scalafmt);
    }

    public InferredSourceFormattingModule verify(boolean verify) {
        return new InferredSourceFormattingModule(configuration, repositories, resolvers, pinning, verify, java, ktlint, scalafmt);
    }

    public InferredSourceFormattingModule java(Function<BuildExecutorModule, BuildExecutorModule> java) {
        return new InferredSourceFormattingModule(configuration, repositories, resolvers, pinning, verify, java, ktlint, scalafmt);
    }

    public InferredSourceFormattingModule ktlint(Function<KtlintFormatModule, BuildExecutorModule> ktlint) {
        return new InferredSourceFormattingModule(configuration, repositories, resolvers, pinning, verify, java, ktlint, scalafmt);
    }

    public InferredSourceFormattingModule scalafmt(Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt) {
        return new InferredSourceFormattingModule(configuration, repositories, resolvers, pinning, verify, java, ktlint, scalafmt);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Bind.configuredByProperties(buildExecutor, inherited.sequencedKeySet(), JAVA, java,
                BuildStep.locate(configuration, "javaformat.properties"),
                properties -> switch (properties.value("formatter")) {
                    case "google" -> new GoogleJavaFormatModule(repositories, resolvers).pinning(pinning).verify(verify);
                    case "palantir" -> new PalantirJavaFormatModule(repositories, resolvers).pinning(pinning).verify(verify);
                    case null -> null;
                    default -> throw new IllegalArgumentException("Unknown Java format: " + properties.value("formatter"));
                });
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), KTLINT, ktlint,
                KtlintFormatModule.configurationFile(configuration),
                () -> new KtlintFormatModule(repositories, resolvers).pinning(pinning).verify(verify));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), SCALAFMT, scalafmt,
                ScalafmtFormatModule.configurationFile(configuration),
                () -> new ScalafmtFormatModule(repositories, resolvers).pinning(pinning).verify(verify));
    }
}
