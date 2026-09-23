package build.custom;

import module java.base;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.ProjectModuleDescriptor;
import build.jenesis.project.InternalModule;

public class Substitution implements Project.Customizer {

    @Override
    public MultiProjectAssembler<? super ProjectModuleDescriptor> apply(InferredMultiProjectAssembler assembler) {
        return (descriptor, repositories, resolvers) -> assembler
                .apply(descriptor.sources("preprocess/substitute"), repositories, resolvers)
                .mapBuild(stock -> (sub, inherited) -> {
                    sub.addModule("preprocess",
                            new InternalModule("module", "tool", Path.of("plugin")),
                            Stream.concat(descriptor.sources().stream(), descriptor.manifests().stream()));
                    stock.accept(sub, inherited);
                });
    }
}
