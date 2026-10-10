package demo.plugin;

import module build.jenesis;
import module java.base;

import build.jenesis.step.Dependencies;
import net.bytebuddy.build.Plugin;
import net.bytebuddy.build.ToStringPlugin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;

import static net.bytebuddy.matcher.ElementMatchers.named;

public class EnhanceModule implements BuildExecutorModule {

    private final String type, greeting;

    public EnhanceModule() {
        this(Collections.emptyNavigableMap());
    }

    public EnhanceModule(SequencedMap<String, String> properties) {
        type = properties.getOrDefault("type", "sample.Greeting");
        greeting = properties.getOrDefault("greeting", "Hello from a rewritten method!");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("enhance", new Enhance(type, greeting), inherited.sequencedKeySet());
    }

    private record Enhance(String type, String greeting) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            List<ClassFileLocator> classPath = new ArrayList<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (!argument.removed()) {
                    for (Path jar : Dependencies.select(argument.folder(), "main", "compile")) {
                        classPath.add(ClassFileLocator.ForJarFile.of(jar.toFile()));
                    }
                }
            }
            try (ClassFileLocator locator = new ClassFileLocator.Compound(classPath)) {
                new Plugin.Engine.Default()
                        .with(locator)
                        .apply(arguments.firstEntry().getValue().folder().resolve(BuildStep.CLASSES).toFile(),
                                Files.createDirectories(context.next().resolve(BuildStep.CLASSES)).toFile(),
                                new Plugin.Factory.Simple(new ToStringPlugin()),
                                new Plugin.Factory.Simple(new Greeting(type, greeting)));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Greeting(String type, String greeting) implements Plugin {

        @Override
        public boolean matches(TypeDescription target) {
            return target.getName().equals(type);
        }

        @Override
        public DynamicType.Builder<?> apply(DynamicType.Builder<?> builder,
                                            TypeDescription typeDescription,
                                            ClassFileLocator classFileLocator) {
            return builder.method(named("get")).intercept(FixedValue.value(greeting));
        }

        @Override
        public void close() {
        }
    }
}
