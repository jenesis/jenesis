package build.jenesis;

import module java.base;

public final class MakeTool extends JenesisTool {

    @Override
    public String name() {
        return "jenesis-make";
    }

    @Override
    protected int run(Environment environment, List<String> selectors) throws IOException {
        requireInProcess(environment);
        Path root = root(environment);
        Make.Settings settings = Make.settings(root, environment.keys());
        return Project.perform(environment.keys(settings.keys()),
                               root,
                               settings.profiles(),
                               selectors.toArray(String[]::new)) == null ? 1 : 0;
    }
}
