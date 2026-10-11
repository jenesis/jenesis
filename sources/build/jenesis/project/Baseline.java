package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

record Baseline(String tool, String baseline, List<String> runtime, String configuration) implements BuildStep {

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String coordinate = tool + "/baseline/" + (baseline == null
                ? released(arguments)
                : qualified(baseline));
        SequencedProperties requires = new SequencedProperties();
        for (String artifact : runtime) {
            requires.setProperty(tool + "/runtime/maven/" + artifact + "/RELEASE", "");
        }
        requires.setProperty(coordinate, "");
        requires.store(context.next().resolve(BuildStep.REQUIRES));
        SequencedProperties exclusions = new SequencedProperties();
        exclusions.setProperty(coordinate, "*/*");
        exclusions.store(context.next().resolve(BuildStep.EXCLUSIONS));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private String qualified(String baseline) {
        return switch (baseline.split("/", -1).length) {
            case 2 -> "maven/" + baseline + "/RELEASE";
            case 3 -> "maven/" + baseline;
            case 4 -> baseline;
            default -> throw new IllegalArgumentException("Malformed " + tool + " baseline '"
                    + baseline
                    + "': expected <groupId>/<artifactId>, <groupId>/<artifactId>/<version>"
                    + " or <repository>/<groupId>/<artifactId>/<version>");
        };
    }

    private String released(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path file = argument.folder().resolve(BuildStep.METADATA);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties metadata = SequencedProperties.ofFiles(file);
            String project = metadata.value("project"), artifact = metadata.value("artifact");
            if (project != null && artifact != null) {
                return "maven/" + project + "/" + artifact + "/RELEASE";
            }
        }
        throw new IllegalStateException("No Maven coordinate to compare against among the inputs of the "
                + tool + " step: declare baseline=<groupId>/<artifactId> in " + configuration);
    }
}
