package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Bind;

public class InferredSourceCodeQualityModule implements BuildExecutorModule {

    public static final String CHECKSTYLE = "checkstyle",
            PMD = "pmd",
            DETEKT = "detekt",
            KTLINT = "ktlint",
            SCALASTYLE = "scalastyle",
            SCALAFMT = "scalafmt",
            CODENARC = "codenarc";

    private final SequencedSet<Path> configuration;
    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final Function<CheckstyleModule, BuildExecutorModule> checkstyle;
    private final Function<PmdModule, BuildExecutorModule> pmd;
    private final Function<DetektModule, BuildExecutorModule> detekt;
    private final Function<KtlintModule, BuildExecutorModule> ktlint;
    private final Function<ScalastyleModule, BuildExecutorModule> scalastyle;
    private final Function<ScalafmtModule, BuildExecutorModule> scalafmt;
    private final Function<CodeNarcModule, BuildExecutorModule> codenarc;

    public InferredSourceCodeQualityModule(SequencedSet<Path> configuration,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers) {
        this(configuration, repositories, resolvers, null,
                enabledBy("jenesis.source.checkstyle"),
                enabledBy("jenesis.source.pmd"),
                enabledBy("jenesis.source.detekt"),
                enabledBy("jenesis.source.ktlint"),
                enabledBy("jenesis.source.scalastyle"),
                enabledBy("jenesis.source.scalafmt"),
                enabledBy("jenesis.source.codenarc"));
    }

    private InferredSourceCodeQualityModule(SequencedSet<Path> configuration,
                                            Map<String, Repository> repositories,
                                            Map<String, Resolver> resolvers,
                                            Pinning pinning,
                                            Function<CheckstyleModule, BuildExecutorModule> checkstyle,
                                            Function<PmdModule, BuildExecutorModule> pmd,
                                            Function<DetektModule, BuildExecutorModule> detekt,
                                            Function<KtlintModule, BuildExecutorModule> ktlint,
                                            Function<ScalastyleModule, BuildExecutorModule> scalastyle,
                                            Function<ScalafmtModule, BuildExecutorModule> scalafmt,
                                            Function<CodeNarcModule, BuildExecutorModule> codenarc) {
        this.configuration = configuration;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.pinning = pinning;
        this.checkstyle = checkstyle;
        this.pmd = pmd;
        this.detekt = detekt;
        this.ktlint = ktlint;
        this.scalastyle = scalastyle;
        this.scalafmt = scalafmt;
        this.codenarc = codenarc;
    }

    private static <M extends BuildExecutorModule> Function<M, BuildExecutorModule> enabledBy(String property) {
        return Boolean.parseBoolean(System.getProperty(property, "true")) ? module -> module : null;
    }

    public InferredSourceCodeQualityModule pinning(Pinning pinning) {
        return new InferredSourceCodeQualityModule(configuration, repositories, resolvers, pinning,
                checkstyle, pmd, detekt, ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule checkstyle(Function<CheckstyleModule, BuildExecutorModule> checkstyle) {
        return new InferredSourceCodeQualityModule(configuration, repositories, resolvers, pinning,
                checkstyle, pmd, detekt, ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule pmd(Function<PmdModule, BuildExecutorModule> pmd) {
        return new InferredSourceCodeQualityModule(configuration, repositories, resolvers, pinning,
                checkstyle, pmd, detekt, ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule detekt(Function<DetektModule, BuildExecutorModule> detekt) {
        return new InferredSourceCodeQualityModule(configuration, repositories, resolvers, pinning,
                checkstyle, pmd, detekt, ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule ktlint(Function<KtlintModule, BuildExecutorModule> ktlint) {
        return new InferredSourceCodeQualityModule(configuration, repositories, resolvers, pinning,
                checkstyle, pmd, detekt, ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule scalastyle(Function<ScalastyleModule, BuildExecutorModule> scalastyle) {
        return new InferredSourceCodeQualityModule(configuration, repositories, resolvers, pinning,
                checkstyle, pmd, detekt, ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule scalafmt(Function<ScalafmtModule, BuildExecutorModule> scalafmt) {
        return new InferredSourceCodeQualityModule(configuration, repositories, resolvers, pinning,
                checkstyle, pmd, detekt, ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule codenarc(Function<CodeNarcModule, BuildExecutorModule> codenarc) {
        return new InferredSourceCodeQualityModule(configuration, repositories, resolvers, pinning,
                checkstyle, pmd, detekt, ktlint, scalastyle, scalafmt, codenarc);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), CHECKSTYLE, checkstyle,
                CheckstyleModule.configurationFile(configuration),
                () -> new CheckstyleModule(repositories, resolvers).pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), PMD, pmd,
                PmdModule.configurationFile(configuration),
                () -> new PmdModule(repositories, resolvers).pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), DETEKT, detekt,
                DetektModule.configurationFile(configuration),
                () -> new DetektModule(repositories, resolvers).pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), KTLINT, ktlint,
                KtlintModule.configurationFile(configuration),
                () -> new KtlintModule(repositories, resolvers).pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), SCALASTYLE, scalastyle,
                ScalastyleModule.configurationFile(configuration),
                () -> new ScalastyleModule(repositories, resolvers).pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), SCALAFMT, scalafmt,
                ScalafmtModule.configurationFile(configuration),
                () -> new ScalafmtModule(repositories, resolvers).pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), CODENARC, codenarc,
                CodeNarcModule.configurationFile(configuration),
                () -> new CodeNarcModule(repositories, resolvers).pinning(pinning));
    }
}
