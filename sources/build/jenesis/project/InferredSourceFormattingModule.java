package build.jenesis.project;

import module java.base;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
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
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredSourceFormattingModule(SequencedSet<Path> configuration,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        this(configuration,
                null,
                true,
                new GoogleJavaFormatModule(repositories, resolvers),
                new PalantirJavaFormatModule(repositories, resolvers),
                new KtlintFormatModule(repositories, resolvers),
                new ScalafmtFormatModule(repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap());
    }

    public static InferredSourceFormattingModule ofEnvironment(Environment environment,
                                                               SequencedSet<Path> configuration,
                                                               Map<String, Repository> repositories,
                                                               Map<String, Resolver> resolvers) {
        InferredSourceFormattingModule module = new InferredSourceFormattingModule(configuration,
                null,
                true,
                GoogleJavaFormatModule.ofEnvironment(environment, repositories, resolvers),
                PalantirJavaFormatModule.ofEnvironment(environment, repositories, resolvers),
                KtlintFormatModule.ofEnvironment(environment, repositories, resolvers),
                ScalafmtFormatModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap());
        Boolean rewrite = environment.flagOrNull("format.rewrite");
        if (rewrite != null) {
            module = module.verify(!rewrite);
        }
        Boolean java = environment.flagOrNull("format.java");
        if (java != null) {
            module = module.java(java ? value -> value : null);
        }
        Boolean ktlint = environment.flagOrNull("format.ktlint");
        if (ktlint != null) {
            module = module.ktlint(ktlint ? value -> value : null);
        }
        Boolean scalafmt = environment.flagOrNull("format.scalafmt");
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
                                           Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt,
                                           SequencedMap<String, BuildExecutorModule> custom) {
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
        this.custom = custom;
    }

    public InferredSourceFormattingModule pinning(Pinning pinning) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule verify(boolean verify) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule java(Function<BuildExecutorModule, BuildExecutorModule> java) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule ktlint(Function<KtlintFormatModule, BuildExecutorModule> ktlint) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule scalafmt(Function<ScalafmtFormatModule, BuildExecutorModule> scalafmt) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
    }

    public InferredSourceFormattingModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredSourceFormattingModule(configuration,
                pinning,
                verify,
                googleModule,
                palantirModule,
                ktlintModule,
                scalafmtModule,
                java,
                ktlint,
                scalafmt,
                custom);
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
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}
