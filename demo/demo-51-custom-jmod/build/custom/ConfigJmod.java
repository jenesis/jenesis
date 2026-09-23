package build.custom;

import module java.base;
import build.jenesis.BuildStepResult;
import build.jenesis.Project;
import build.jenesis.project.Decoration;
import build.jenesis.step.JMod;

public class ConfigJmod implements UnaryOperator<Project> {

    @Override
    public Project apply(Project project) {
        return project.decorate(new Decoration("assemble")
                .descriptor(descriptor -> descriptor.content("config"))
                .before(_ -> (sub, _) -> sub.addStep("config", (executor, context, arguments) -> {
                    Path config = Files.createDirectory(context.next().resolve(JMod.CONFIG));
                    Files.writeString(config.resolve("app.properties"),
                            "greeting=Configured in a .jmod, linked into the runtime by jlink\n");
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                })));
    }
}
