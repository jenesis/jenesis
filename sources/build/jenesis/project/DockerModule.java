package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Docker;

public class DockerModule implements BuildExecutorModule {

    public static final String IMAGE = "image";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String group;
    private final String from;
    private final List<String> diff;

    public DockerModule(Map<String, Repository> repositories,
                        Map<String, Resolver> resolvers,
                        String from) {
        this(new Dependencies(repositories, resolvers), null, "docker", from, List.of());
    }

    public static DockerModule ofEnvironment(Environment environment,
                                             Map<String, Repository> repositories,
                                             Map<String, Resolver> resolvers,
                                             String from) {
        return new DockerModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "docker",
                from,
                List.of());
    }

    private DockerModule(Dependencies dependencies,
                         Pinning pinning,
                         String group,
                         String from,
                         List<String> diff) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.group = group;
        this.from = from;
        this.diff = diff;
    }

    public DockerModule pinning(Pinning pinning) {
        return new DockerModule(dependencies, pinning, group, from, diff);
    }

    public DockerModule group(String group) {
        return new DockerModule(dependencies, pinning, group, from, diff);
    }

    public DockerModule from(String from) {
        return new DockerModule(dependencies, pinning, group, from, diff);
    }

    public DockerModule diff(List<String> diff) {
        return new DockerModule(dependencies, pinning, group, from, diff.stream()
                .map(DockerModule::coordinate)
                .toList());
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        Docker docker = new Docker(from);
        if (diff.isEmpty()) {
            buildExecutor.addStep(IMAGE, docker, inherited.sequencedKeySet());
            return;
        }
        buildExecutor.addStep(REQUIRED, new Requires(group, diff), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES, dependencies.pinning(pinning).group(group), resolveInputs);
        SequencedSet<String> imageInputs = new LinkedHashSet<>();
        imageInputs.add(DEPENDENCIES);
        imageInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(IMAGE, docker.diff(group), imageInputs);
    }

    private static String coordinate(String declared) {
        String[] tokens = declared.split(":", -1);
        if (Arrays.stream(tokens).anyMatch(token -> token.isBlank() || token.indexOf('/') >= 0)
                || tokens.length > 3) {
            throw new IllegalArgumentException("docker.diff names '" + declared + "', which is neither a module name"
                    + " nor a Maven coordinate <groupId>:<artifactId>[:<version>]");
        }
        return switch (tokens.length) {
            case 1 -> "module/" + tokens[0];
            case 2 -> "maven/" + tokens[0] + "/" + tokens[1] + "/RELEASE";
            default -> "maven/" + tokens[0] + "/" + tokens[1] + "/" + tokens[2];
        };
    }

    private record Requires(String group, List<String> coordinates) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return false;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties requires = new SequencedProperties();
            for (String coordinate : coordinates) {
                requires.setProperty(group + "/runtime/" + coordinate, "");
            }
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
