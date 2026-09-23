package build.custom;

import module java.base;
import build.jenesis.Project;
import build.jenesis.project.Decoration;
import build.jenesis.project.InternalModule;

public class Substitution implements UnaryOperator<Project> {

    @Override
    public Project apply(Project project) {
        return project.decorate(new Decoration("assemble")
                .descriptor(descriptor -> descriptor.sources("preprocess/substitute"))
                .before(descriptor -> (sub, _) -> sub.addModule("preprocess",
                        new InternalModule("module", "tool", project.root().resolve("plugin")),
                        Stream.concat(descriptor.sources().stream(), descriptor.manifests().stream()))));
    }
}
