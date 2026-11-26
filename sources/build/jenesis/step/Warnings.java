package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Palette;
import build.jenesis.SequencedProperties;

public class Warnings implements BuildStep {

    private static final String PRINTED = "printed.properties";

    private final Function<SequencedMap<String, BuildStepArgument>, SequencedMap<String, String>> warnings;
    private final transient Consumer<String> printing;
    private final transient Palette palette;

    public <F extends Function<SequencedMap<String, BuildStepArgument>, SequencedMap<String, String>> & Serializable> Warnings(
            F warnings) {
        this(warnings, null, Palette.NONE);
    }

    private Warnings(Function<SequencedMap<String, BuildStepArgument>, SequencedMap<String, String>> warnings,
                     Consumer<String> printing,
                     Palette palette) {
        this.warnings = warnings;
        this.printing = printing;
        this.palette = palette;
    }

    public Warnings printing(Consumer<String> printing, Palette palette) {
        return new Warnings(warnings, printing, palette);
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return printing != null;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        if (printing != null) {
            Path file = context.persistent().resolve(PRINTED);
            SequencedProperties printed = Files.isRegularFile(file)
                    ? SequencedProperties.ofFiles(file)
                    : new SequencedProperties(), current = new SequencedProperties();
            warnings.apply(arguments).forEach((warning, label) -> {
                if (!label.equals(printed.getProperty(warning))) {
                    printing.accept("%s%-11s%s %s".formatted(palette.warning(), label, palette.reset(), warning));
                }
                current.setProperty(warning, label);
            });
            current.store(file);
        }
        return CompletableFuture.completedStage(new BuildStepResult().next(context.previous() == null).cached(false));
    }
}
