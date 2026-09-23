package build.custom;

import module java.base;
import build.jenesis.BuildStepResult;
import build.jenesis.Project;
import build.jenesis.step.JMod;

public class ConfigJmod implements UnaryOperator<Project<?>> {

    @Override
    public Project<?> apply(Project<?> project) {
        return project.assembler((descriptor, repositories, resolvers) -> project.assembler()
                .apply(descriptor.content("config"), repositories, resolvers)
                .mapBuild(inner -> (sub, inherited) -> {
                    sub.addStep("config", (executor, context, arguments) -> {
                        Path config = Files.createDirectory(context.next().resolve(JMod.CONFIG));
                        Files.writeString(config.resolve("app.properties"),
                                "greeting=Configured in a .jmod, linked into the runtime by jlink\n");
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    });
                    inner.accept(sub, inherited);
                }));
    }
}
