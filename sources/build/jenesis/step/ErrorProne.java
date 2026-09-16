package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

public class ErrorProne implements BuildStep {

    private static final List<String> EXPORTED = List.of("api",
            "file",
            "main",
            "model",
            "parser",
            "processing",
            "tree",
            "util");
    private static final List<String> OPENED = List.of("code", "comp");

    private final String compiler;
    private final List<String> arguments;

    public ErrorProne() {
        this("javac", List.of());
    }

    private ErrorProne(String compiler, List<String> arguments) {
        this.compiler = compiler;
        this.arguments = arguments;
    }

    public ErrorProne compiler(String compiler) {
        return new ErrorProne(compiler, arguments);
    }

    public ErrorProne arguments(List<String> arguments) {
        return new ErrorProne(compiler, arguments);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        boolean declared = false;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            declared |= !Dependencies.select(argument.folder(), compiler, "plugin").isEmpty();
        }
        if (!declared) {
            throw new IllegalStateException("No " + compiler + " plugin resolved for Error Prone"
                    + " (declare @jenesis.plugin " + compiler
                    + " maven/com.google.errorprone/error_prone_core in the module declaration)");
        }
        SequencedProperties options = new SequencedProperties();
        options.setProperty("-XDcompilePolicy=simple", "");
        options.setProperty("--should-stop=ifError=FLOW", "");
        for (String exported : EXPORTED) {
            options.setProperty("-J--add-exports=jdk.compiler/com.sun.tools.javac." + exported + "=ALL-UNNAMED", "");
        }
        for (String opened : OPENED) {
            options.setProperty("-J--add-opens=jdk.compiler/com.sun.tools.javac." + opened + "=ALL-UNNAMED", "");
        }
        options.setProperty(this.arguments.isEmpty()
                ? "-Xplugin:ErrorProne"
                : "-Xplugin:ErrorProne " + String.join(" ", this.arguments), "");
        Path folder = Files.createDirectories(context.next().resolve(ProcessBuildStep.PROCESS));
        options.store(folder.resolve(compiler + ".properties"));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}
