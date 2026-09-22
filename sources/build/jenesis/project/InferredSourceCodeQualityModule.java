package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Bind;
import build.jenesis.SequencedProperties;

public class InferredSourceCodeQualityModule implements BuildExecutorModule {

    public static final String CHECKSTYLE = "checkstyle",
            PMD = "pmd",
            DETEKT = "detekt",
            KTLINT = "ktlint",
            SCALASTYLE = "scalastyle",
            SCALAFMT = "scalafmt",
            CODENARC = "codenarc";

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final CheckstyleModule checkstyleModule;
    private final PmdModule pmdModule;
    private final DetektModule detektModule;
    private final KtlintModule ktlintModule;
    private final ScalastyleModule scalastyleModule;
    private final ScalafmtModule scalafmtModule;
    private final CodeNarcModule codenarcModule;
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
        this(configuration, null, new CheckstyleModule(repositories, resolvers),
             new PmdModule(repositories, resolvers),
             new DetektModule(repositories, resolvers),
             new KtlintModule(repositories, resolvers),
             new ScalastyleModule(repositories, resolvers),
             new ScalafmtModule(repositories, resolvers),
             new CodeNarcModule(repositories, resolvers),
             value -> value,
             value -> value,
             value -> value,
             value -> value,
             value -> value,
             value -> value,
             value -> value);
    }

    public static InferredSourceCodeQualityModule ofKeys(Function<String, String> keys,
                                                         SequencedSet<Path> configuration,
                                                         Map<String, Repository> repositories,
                                                         Map<String, Resolver> resolvers) {
        InferredSourceCodeQualityModule module = new InferredSourceCodeQualityModule(configuration, null, CheckstyleModule.ofKeys(keys, repositories, resolvers),
                PmdModule.ofKeys(keys, repositories, resolvers),
                DetektModule.ofKeys(keys, repositories, resolvers),
                KtlintModule.ofKeys(keys, repositories, resolvers),
                ScalastyleModule.ofKeys(keys, repositories, resolvers),
                ScalafmtModule.ofKeys(keys, repositories, resolvers),
                CodeNarcModule.ofKeys(keys, repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                value -> value);
        Boolean checkstyle = SequencedProperties.flagOrNull(keys, "source.checkstyle");
        if (checkstyle != null) {
            module = module.checkstyle(checkstyle ? value -> value : null);
        }
        Boolean pmd = SequencedProperties.flagOrNull(keys, "source.pmd");
        if (pmd != null) {
            module = module.pmd(pmd ? value -> value : null);
        }
        Boolean detekt = SequencedProperties.flagOrNull(keys, "source.detekt");
        if (detekt != null) {
            module = module.detekt(detekt ? value -> value : null);
        }
        Boolean ktlint = SequencedProperties.flagOrNull(keys, "source.ktlint");
        if (ktlint != null) {
            module = module.ktlint(ktlint ? value -> value : null);
        }
        Boolean scalastyle = SequencedProperties.flagOrNull(keys, "source.scalastyle");
        if (scalastyle != null) {
            module = module.scalastyle(scalastyle ? value -> value : null);
        }
        Boolean scalafmt = SequencedProperties.flagOrNull(keys, "source.scalafmt");
        if (scalafmt != null) {
            module = module.scalafmt(scalafmt ? value -> value : null);
        }
        Boolean codenarc = SequencedProperties.flagOrNull(keys, "source.codenarc");
        if (codenarc != null) {
            module = module.codenarc(codenarc ? value -> value : null);
        }
        return module;
    }

    private InferredSourceCodeQualityModule(SequencedSet<Path> configuration,
                                            Pinning pinning,
                                            CheckstyleModule checkstyleModule,
                                            PmdModule pmdModule,
                                            DetektModule detektModule,
                                            KtlintModule ktlintModule,
                                            ScalastyleModule scalastyleModule,
                                            ScalafmtModule scalafmtModule,
                                            CodeNarcModule codenarcModule,
                                            Function<CheckstyleModule, BuildExecutorModule> checkstyle,
                                            Function<PmdModule, BuildExecutorModule> pmd,
                                            Function<DetektModule, BuildExecutorModule> detekt,
                                            Function<KtlintModule, BuildExecutorModule> ktlint,
                                            Function<ScalastyleModule, BuildExecutorModule> scalastyle,
                                            Function<ScalafmtModule, BuildExecutorModule> scalafmt,
                                            Function<CodeNarcModule, BuildExecutorModule> codenarc) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.checkstyleModule = checkstyleModule;
        this.pmdModule = pmdModule;
        this.detektModule = detektModule;
        this.ktlintModule = ktlintModule;
        this.scalastyleModule = scalastyleModule;
        this.scalafmtModule = scalafmtModule;
        this.codenarcModule = codenarcModule;
        this.checkstyle = checkstyle;
        this.pmd = pmd;
        this.detekt = detekt;
        this.ktlint = ktlint;
        this.scalastyle = scalastyle;
        this.scalafmt = scalafmt;
        this.codenarc = codenarc;
    }

    public InferredSourceCodeQualityModule pinning(Pinning pinning) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule checkstyle(Function<CheckstyleModule, BuildExecutorModule> checkstyle) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule pmd(Function<PmdModule, BuildExecutorModule> pmd) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule detekt(Function<DetektModule, BuildExecutorModule> detekt) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule ktlint(Function<KtlintModule, BuildExecutorModule> ktlint) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule scalastyle(Function<ScalastyleModule, BuildExecutorModule> scalastyle) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule scalafmt(Function<ScalafmtModule, BuildExecutorModule> scalafmt) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule codenarc(Function<CodeNarcModule, BuildExecutorModule> codenarc) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), CHECKSTYLE, checkstyle,
                CheckstyleModule.configurationFile(configuration),
                () -> checkstyleModule.pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), PMD, pmd,
                PmdModule.configurationFile(configuration),
                () -> pmdModule.pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), DETEKT, detekt,
                DetektModule.configurationFile(configuration),
                () -> detektModule.pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), KTLINT, ktlint,
                KtlintModule.configurationFile(configuration),
                () -> ktlintModule.pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), SCALASTYLE, scalastyle,
                ScalastyleModule.configurationFile(configuration),
                () -> scalastyleModule.pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), SCALAFMT, scalafmt,
                ScalafmtModule.configurationFile(configuration),
                () -> scalafmtModule.pinning(pinning));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), CODENARC, codenarc,
                CodeNarcModule.configurationFile(configuration),
                () -> codenarcModule.pinning(pinning));
    }
}
