package demo.plugin;

import module build.jenesis;
import module java.base;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.FixedValue;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class GreeterModule implements BuildExecutorModule {

    private final String implementation, greeting;

    public GreeterModule() {
        this(Collections.emptyNavigableMap());
    }

    public GreeterModule(SequencedMap<String, String> properties) {
        implementation = properties.getOrDefault("implementation", "sample.Greeting");
        greeting = properties.getOrDefault("greeting", "Hello from a generated class!");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("generate", new Generate(implementation, greeting));
    }

    private record Generate(String implementation, String greeting) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            new ByteBuddy()
                    .subclass(Object.class)
                    .name(implementation)
                    .implement(Supplier.class)
                    .method(named("get"))
                    .intercept(FixedValue.value(greeting))
                    .make()
                    .saveIn(Files.createDirectories(context.next().resolve(BuildStep.CLASSES)).toFile());
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
