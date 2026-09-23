package build.custom;

import module java.base;
import build.jenesis.BuildStepResult;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.ProjectModuleDescriptor;
import build.jenesis.step.JMod;

public class ConfigJmod implements Project.Customizer {

    @Override
    public MultiProjectAssembler<? super ProjectModuleDescriptor> apply(InferredMultiProjectAssembler assembler) {
        return (descriptor, repositories, resolvers) -> assembler
                .apply(descriptor.content("config"), repositories, resolvers)
                .mapBuild(stock -> (sub, inherited) -> {
                    sub.addStep("config", (executor, context, arguments) -> {
                        Path config = Files.createDirectory(context.next().resolve(JMod.CONFIG));
                        Files.writeString(config.resolve("app.properties"),
                                "greeting=Configured in a .jmod, linked into the runtime by jlink\n");
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    });
                    stock.accept(sub, inherited);
                });
    }
}
