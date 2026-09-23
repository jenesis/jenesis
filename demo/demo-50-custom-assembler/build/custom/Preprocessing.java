package build.custom;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepResult;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;

public class Preprocessing implements UnaryOperator<Project<InferredMultiProjectAssembler>>, Serializable {

    @Override
    public Project<InferredMultiProjectAssembler> apply(Project<InferredMultiProjectAssembler> project) {
        return project.assembler(assembler -> assembler.merge(descriptor -> descriptor.sources("preprocess"),
                (descriptor, stock) -> (sub, inherited) -> {
                    sub.addStep("preprocess", (executor, context, arguments) -> {
                        Path target = context.next().resolve(BuildStep.SOURCES);
                        for (BuildStepArgument argument : arguments.values()) {
                            if (argument.removed()) {
                                continue;
                            }
                            Path sources = argument.folder().resolve(BuildStep.SOURCES);
                            if (!Files.isDirectory(sources)) {
                                continue;
                            }
                            Files.walkFileTree(sources, new SimpleFileVisitor<Path>() {
                                @Override
                                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                                        throws IOException {
                                    Files.createDirectories(target.resolve(sources.relativize(directory)));
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                                        throws IOException {
                                    Path destination = target.resolve(sources.relativize(file));
                                    if (file.toString().endsWith(".java")) {
                                        String content = Files.readString(file);
                                        String substituted = content.replace("${greeting}",
                                                "Hello from a source preprocessed by a custom assembler!");
                                        if (!substituted.equals(content)) {
                                            System.out.println("custom-assembler: substituted ${greeting} in "
                                                    + sources.relativize(file));
                                        }
                                        Files.writeString(destination, substituted);
                                    } else {
                                        BuildStep.linkOrCopy(destination, file);
                                    }
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                        }
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    }, descriptor.sources().stream());
                    stock.accept(sub, inherited);
                }));
    }
}
