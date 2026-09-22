package build.jenesis;

import module java.base;

public final class MakeTool extends JenesisTool {

    @Override
    public String name() {
        return "jenesis-make";
    }

    @Override
    protected int run(Function<String, String> requested, Output output, List<String> selectors) throws IOException {
        requireInProcess(requested);
        Path root = root(requested);
        Make.Settings settings = Make.settings(root, requested);
        return Project.perform(settings.keys(),
                output,
                root,
                settings.profiles(),
                selectors.toArray(String[]::new)) == null ? 1 : 0;
    }
}
