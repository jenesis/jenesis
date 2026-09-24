package demo.plugin;

import module java.base;
import module build.jenesis;

import org.json.JSONObject;

public class GreetingModule implements BuildExecutorModule {

    private final String greeting;

    public GreetingModule() {
        this(Collections.emptyNavigableMap());
    }

    public GreetingModule(SequencedMap<String, String> properties) {
        greeting = properties.getOrDefault("greeting", "Hello from a generated source!");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("greeting", new Generate(greeting));
    }

    private record Generate(String greeting) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path folder = Files.createDirectories(context.next().resolve(BuildStep.SOURCES).resolve("sample"));
            Files.writeString(folder.resolve("Greeting.java"), """
                    package sample;

                    public class Greeting {

                        public static final String TEXT = %s;
                    }
                    """.formatted(JSONObject.quote(greeting)));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
