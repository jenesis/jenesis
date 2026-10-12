package build;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Make;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;

public class Planted {

    static void main(String[] args) throws Exception {
        Environment environment = new Environment(Make.settings(Path.of(".")).keys());
        InferredMultiProjectAssembler stock = InferredMultiProjectAssembler.ofEnvironment(environment);
        Project project = Project.ofEnvironment(environment, Path.of(".")).assembler((descriptor, repositories, resolvers) -> stock
                .apply(descriptor.sources("stamp"), repositories, resolvers)
                .mapBuild(build -> (sub, inherited) -> {
                    sub.addStep("stamp", (_, context, arguments) -> {
                        Path target = context.next().resolve(BuildStep.SOURCES);
                        for (BuildStepArgument argument : arguments.values()) {
                            Path sources = argument.folder().resolve(BuildStep.SOURCES);
                            try (Stream<Path> files = Files.walk(sources)) {
                                for (Path file : files.filter(Files::isRegularFile).toList()) {
                                    Path destination = target.resolve(sources.relativize(file));
                                    Files.createDirectories(destination.getParent());
                                    Files.copy(file, destination);
                                }
                            }
                        }
                        Files.writeString(Files.createDirectories(target.resolve("demo/reproducible")).resolve("built.txt"),
                                Instant.now().toString());
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    }, descriptor.sources().stream());
                    build.accept(sub, inherited);
                }));
        try {
            project.build(args);
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
