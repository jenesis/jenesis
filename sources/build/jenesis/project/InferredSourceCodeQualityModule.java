package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Environment;
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
    private final UnaryOperator<CheckstyleModule> checkstyle;
    private final UnaryOperator<PmdModule> pmd;
    private final UnaryOperator<DetektModule> detekt;
    private final UnaryOperator<KtlintModule> ktlint;
    private final UnaryOperator<ScalastyleModule> scalastyle;
    private final UnaryOperator<ScalafmtModule> scalafmt;
    private final UnaryOperator<CodeNarcModule> codenarc;

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

    public static InferredSourceCodeQualityModule ofEnvironment(Environment environment,
                                                                SequencedSet<Path> configuration,
                                                                Map<String, Repository> repositories,
                                                                Map<String, Resolver> resolvers) {
        InferredSourceCodeQualityModule module = new InferredSourceCodeQualityModule(configuration, null, CheckstyleModule.ofEnvironment(environment, repositories, resolvers),
                PmdModule.ofEnvironment(environment, repositories, resolvers),
                DetektModule.ofEnvironment(environment, repositories, resolvers),
                KtlintModule.ofEnvironment(environment, repositories, resolvers),
                ScalastyleModule.ofEnvironment(environment, repositories, resolvers),
                ScalafmtModule.ofEnvironment(environment, repositories, resolvers),
                CodeNarcModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                value -> value,
                value -> value);
        Boolean checkstyle = environment.flagOrNull("source.checkstyle");
        if (checkstyle != null) {
            module = module.checkstyle(checkstyle ? value -> value : null);
        }
        Boolean pmd = environment.flagOrNull("source.pmd");
        if (pmd != null) {
            module = module.pmd(pmd ? value -> value : null);
        }
        Boolean detekt = environment.flagOrNull("source.detekt");
        if (detekt != null) {
            module = module.detekt(detekt ? value -> value : null);
        }
        Boolean ktlint = environment.flagOrNull("source.ktlint");
        if (ktlint != null) {
            module = module.ktlint(ktlint ? value -> value : null);
        }
        Boolean scalastyle = environment.flagOrNull("source.scalastyle");
        if (scalastyle != null) {
            module = module.scalastyle(scalastyle ? value -> value : null);
        }
        Boolean scalafmt = environment.flagOrNull("source.scalafmt");
        if (scalafmt != null) {
            module = module.scalafmt(scalafmt ? value -> value : null);
        }
        Boolean codenarc = environment.flagOrNull("source.codenarc");
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
                                            UnaryOperator<CheckstyleModule> checkstyle,
                                            UnaryOperator<PmdModule> pmd,
                                            UnaryOperator<DetektModule> detekt,
                                            UnaryOperator<KtlintModule> ktlint,
                                            UnaryOperator<ScalastyleModule> scalastyle,
                                            UnaryOperator<ScalafmtModule> scalafmt,
                                            UnaryOperator<CodeNarcModule> codenarc) {
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

    public InferredSourceCodeQualityModule checkstyle(UnaryOperator<CheckstyleModule> checkstyle) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, append(this.checkstyle, checkstyle), pmd, detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule pmd(UnaryOperator<PmdModule> pmd) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, append(this.pmd, pmd), detekt,
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule detekt(UnaryOperator<DetektModule> detekt) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, append(this.detekt, detekt),
                ktlint, scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule ktlint(UnaryOperator<KtlintModule> ktlint) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                append(this.ktlint, ktlint), scalastyle, scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule scalastyle(UnaryOperator<ScalastyleModule> scalastyle) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, append(this.scalastyle, scalastyle), scalafmt, codenarc);
    }

    public InferredSourceCodeQualityModule scalafmt(UnaryOperator<ScalafmtModule> scalafmt) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, append(this.scalafmt, scalafmt), codenarc);
    }

    public InferredSourceCodeQualityModule codenarc(UnaryOperator<CodeNarcModule> codenarc) {
        return new InferredSourceCodeQualityModule(configuration, pinning, checkstyleModule, pmdModule,
                detektModule, ktlintModule, scalastyleModule, scalafmtModule, codenarcModule, checkstyle, pmd, detekt,
                ktlint, scalastyle, scalafmt, append(this.codenarc, codenarc));
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

    private static <T> UnaryOperator<T> append(UnaryOperator<T> previous, UnaryOperator<T> next) {
        return previous == null || next == null ? null : value -> {
            T configured = previous.apply(value);
            return configured == null ? null : next.apply(configured);
        };
    }
}
