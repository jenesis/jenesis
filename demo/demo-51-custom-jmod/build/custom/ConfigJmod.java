package build.custom;

import module java.base;
import build.jenesis.BuildStepResult;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.step.JMod;

public class ConfigJmod implements UnaryOperator<Project<InferredMultiProjectAssembler>> {

    @Override
    public Project<InferredMultiProjectAssembler> apply(Project<InferredMultiProjectAssembler> project) {
        return project.assembler(assembler -> assembler.merge(descriptor -> descriptor.content("config"),
                (_, stock) -> (sub, inherited) -> {
                    sub.addStep("config", (executor, context, arguments) -> {
                        Path config = Files.createDirectory(context.next().resolve(JMod.CONFIG));
                        Files.writeString(config.resolve("app.properties"),
                                "greeting=Configured in a .jmod, linked into the runtime by jlink\n");
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    });
                    stock.accept(sub, inherited);
                }));
    }
}
