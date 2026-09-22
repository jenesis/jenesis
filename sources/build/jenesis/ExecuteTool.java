package build.jenesis;

import module java.base;

public final class ExecuteTool extends JenesisTool {

    @Override
    public String name() {
        return "jenesis-exec";
    }

    @Override
    protected int run(Function<String, String> requested, Output output, List<String> arguments)
            throws IOException, InterruptedException {
        requireInProcess(requested);
        if (SequencedProperties.flag(requested, "execute.docker")) {
            throw new IllegalStateException("A dockerized program cannot be run by the " + name()
                    + " tool, because a container replaces the process it runs in"
                    + " - unset jenesis.execute.docker, or run the " + name() + " command instead");
        }
        Path root = root(requested);
        Make.Settings settings = Make.settings(root, requested);
        SequencedMap<String, Path> outputs = Project.perform(settings.keys(),
                output,
                root,
                settings.profiles(),
                Project.BUILD);
        if (outputs == null) {
            return 1;
        }
        return Execution.ofKeys(settings.keys(), Project.ofKeys(settings.keys(), output, root))
                .execute(outputs, arguments.toArray(String[]::new));
    }
}
