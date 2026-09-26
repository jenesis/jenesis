package build;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Execution;
import build.jenesis.Make;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;

public class Demo {

    static void main(String[] args) throws Exception {
        Environment environment = new Environment(Make.settings(Path.of(".")).keys());
        InferredMultiProjectAssembler checked = InferredMultiProjectAssembler.ofEnvironment(environment).check(check -> check.custom("placeholders", (_, _, arguments) -> {
            for (BuildStepArgument argument : arguments.values()) {
                Path sources = argument.folder().resolve(BuildStep.SOURCES);
                if (argument.removed() || !Files.isDirectory(sources)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(sources)) {
                    for (Path file : files.filter(file -> file.toString().endsWith(".java")).toList()) {
                        if (Files.readString(file).contains("${")) {
                            throw new IllegalStateException("Unsubstituted placeholder in " + sources.relativize(file));
                        }
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }));
        Project project = Project.ofEnvironment(environment, Path.of(".")).assembler((descriptor, repositories, resolvers) -> checked
                .apply(descriptor.sources("preprocess"), repositories, resolvers)
                .mapBuild(stock -> (sub, inherited) -> {
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
                            try (Stream<Path> files = Files.walk(sources)) {
                                for (Path file : files.toList()) {
                                    Path destination = target.resolve(sources.relativize(file));
                                    if (Files.isDirectory(file)) {
                                        Files.createDirectories(destination);
                                    } else if (file.toString().endsWith(".java")) {
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
                                }
                            }
                        }
                        return CompletableFuture.completedStage(new BuildStepResult(true));
                    }, descriptor.sources().stream());
                    stock.accept(sub, inherited);
                }));
        System.exit(new Execution(project).execute(args));
    }
}
