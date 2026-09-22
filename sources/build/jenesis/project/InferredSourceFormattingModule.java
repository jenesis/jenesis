package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Output;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.step.Bind;
import build.jenesis.SequencedProperties;

public class InferredSourceFormattingModule implements BuildExecutorModule {

    private static final String JAVA = "java",
            KTLINT = "ktlint",
            SCALAFMT = "scalafmt";

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final boolean verify;
    private final GoogleJavaFormatModule googleModule;
    private final PalantirJavaFormatModule palantirModule;
    private final KtlintFormatModule ktlintModule;
    private final ScalafmtFormatModule scalafmtModule;
    private final Function<BuildExecutorModule, BuildExecutorModule> java;
    private final Function<KtlintFormatModule, BuildExecutorModule> ktlint;
    private final Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt;

    public InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration, null, true, new GoogleJavaFormatModule(repositories, resolvers),
             new PalantirJavaFormatModule(repositories, resolvers),
             new KtlintFormatModule(repositories, resolvers),
             new ScalafmtFormatModule(repositories, resolvers),
             value -> value,
             value -> value,
             value -> value);
    }

    public static InferredSourceFormattingModule ofKeys(Function<String, String> keys,
                                                        Output output,
                                                        SequencedSet<Path> configuration,
                                                        Map<String, Repository> repositories,
                                                        Map<String, Resolver> resolvers) {
        InferredSourceFormattingModule module = new InferredSourceFormattingModule(configuration, null, true, GoogleJavaFormatModule.ofKeys(keys, output, repositories, resolvers),
                PalantirJavaFormatModule.ofKeys(keys, output, repositories, resolvers),
                KtlintFormatModule.ofKeys(keys, output, repositories, resolvers),
                ScalafmtFormatModule.ofKeys(keys, output, repositories, resolvers),
                value -> value,
                value -> value,
                value -> value);
        Boolean rewrite = SequencedProperties.flagOrNull(keys, "format.rewrite");
        if (rewrite != null) {
            module = module.verify(!rewrite);
        }
        Boolean java = SequencedProperties.flagOrNull(keys, "format.java");
        if (java != null) {
            module = module.java(java ? value -> value : null);
        }
        Boolean ktlint = SequencedProperties.flagOrNull(keys, "format.ktlint");
        if (ktlint != null) {
            module = module.ktlint(ktlint ? value -> value : null);
        }
        Boolean scalafmt = SequencedProperties.flagOrNull(keys, "format.scalafmt");
        if (scalafmt != null) {
            module = module.scalafmt(scalafmt ? value -> value : null);
        }
        return module;
    }

    private InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                           Pinning pinning,
                                           boolean verify,
                                           GoogleJavaFormatModule googleModule,
                                           PalantirJavaFormatModule palantirModule,
                                           KtlintFormatModule ktlintModule,
                                           ScalafmtFormatModule scalafmtModule,
                                           Function<BuildExecutorModule, BuildExecutorModule> java,
                                           Function<KtlintFormatModule, BuildExecutorModule> ktlint,
                                           Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.verify = verify;
        this.googleModule = googleModule;
        this.palantirModule = palantirModule;
        this.ktlintModule = ktlintModule;
        this.scalafmtModule = scalafmtModule;
        this.java = java;
        this.ktlint = ktlint;
        this.scalafmt = scalafmt;
    }

    public InferredSourceFormattingModule pinning(Pinning pinning) {
        return new InferredSourceFormattingModule(configuration, pinning, verify, googleModule, palantirModule,
                ktlintModule, scalafmtModule, java, ktlint, scalafmt);
    }

    public InferredSourceFormattingModule verify(boolean verify) {
        return new InferredSourceFormattingModule(configuration, pinning, verify, googleModule, palantirModule,
                ktlintModule, scalafmtModule, java, ktlint, scalafmt);
    }

    public InferredSourceFormattingModule java(Function<BuildExecutorModule, BuildExecutorModule> java) {
        return new InferredSourceFormattingModule(configuration, pinning, verify, googleModule, palantirModule,
                ktlintModule, scalafmtModule, java, ktlint, scalafmt);
    }

    public InferredSourceFormattingModule ktlint(Function<KtlintFormatModule, BuildExecutorModule> ktlint) {
        return new InferredSourceFormattingModule(configuration, pinning, verify, googleModule, palantirModule,
                ktlintModule, scalafmtModule, java, ktlint, scalafmt);
    }

    public InferredSourceFormattingModule scalafmt(Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt) {
        return new InferredSourceFormattingModule(configuration, pinning, verify, googleModule, palantirModule,
                ktlintModule, scalafmtModule, java, ktlint, scalafmt);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Bind.configuredByProperties(buildExecutor, inherited.sequencedKeySet(), JAVA, java,
                BuildStep.locate(configuration, "javaformat.properties"),
                properties -> switch (properties.value("formatter")) {
                    case "google" -> googleModule.pinning(pinning).verify(verify);
                    case "palantir" -> palantirModule.pinning(pinning).verify(verify);
                    case null -> null;
                    default -> throw new IllegalArgumentException("Unknown Java format: " + properties.value("formatter"));
                });
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), KTLINT, ktlint,
                KtlintFormatModule.configurationFile(configuration),
                () -> ktlintModule.pinning(pinning).verify(verify));
        Bind.configured(buildExecutor, inherited.sequencedKeySet(), SCALAFMT, scalafmt,
                ScalafmtFormatModule.configurationFile(configuration),
                () -> scalafmtModule.pinning(pinning).verify(verify));
    }
}
