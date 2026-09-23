package build.custom;

import module java.base;
import build.jenesis.Project;
import build.jenesis.project.InternalModule;

public class Substitution implements UnaryOperator<Project> {

    @Override
    public Project apply(Project project) {
        return project.assembler((descriptor, repositories, resolvers) -> project.assembler()
                .apply(descriptor.sources("preprocess/substitute"), repositories, resolvers)
                .mapBuild(inner -> (sub, inherited) -> {
                    sub.addModule("preprocess",
                            new InternalModule("module", "tool", project.root().resolve("plugin")),
                            Stream.concat(descriptor.sources().stream(), descriptor.manifests().stream()));
                    inner.accept(sub, inherited);
                }));
    }
}
