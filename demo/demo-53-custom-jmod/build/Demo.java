package build;

import module java.base;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Make;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.step.JMod;

public class Demo {

    static void main(String[] args) throws Exception {
        Environment environment = new Environment(Make.settings(Path.of(".")).keys());
        InferredMultiProjectAssembler assembler = InferredMultiProjectAssembler.ofEnvironment(environment);
        Project.ofEnvironment(environment, Path.of(".")).assembler((descriptor, repositories, resolvers) -> assembler
                .apply(descriptor.content("config"), repositories, resolvers)
                .mapBuild(stock -> (sub, inherited) -> {
                    sub.addStep("config", (executor, context, arguments) -> {
                        Path config = Files.createDirectory(context.next().resolve(JMod.CONFIG));
                        Files.writeString(config.resolve("app.properties"),
                                "greeting=Configured in a .jmod, linked into the runtime by jlink\n");
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    });
                    stock.accept(sub, inherited);
                })).build("stage");
    }
}
