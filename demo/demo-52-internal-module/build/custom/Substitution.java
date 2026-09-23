package build.custom;

import module java.base;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.InternalModule;

public class Substitution implements UnaryOperator<Project<InferredMultiProjectAssembler>> {

    @Override
    public Project<InferredMultiProjectAssembler> apply(Project<InferredMultiProjectAssembler> project) {
        return project.assembler(assembler -> assembler.merge(descriptor -> descriptor.sources("preprocess/substitute"),
                (descriptor, stock) -> (sub, inherited) -> {
                    sub.addModule("preprocess",
                            new InternalModule("module", "tool", project.root().resolve("plugin")),
                            Stream.concat(descriptor.sources().stream(), descriptor.manifests().stream()));
                    stock.accept(sub, inherited);
                }));
    }
}
