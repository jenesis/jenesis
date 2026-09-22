package build.jenesis;

import module java.base;

public final class JpxTool extends JenesisTool {

    @Override
    public String name() {
        return "jpx";
    }

    @Override
    protected int run(Environment environment, List<String> arguments)
            throws IOException, InterruptedException {
        return Jpx.run(environment, arguments.toArray(String[]::new));
    }
}
