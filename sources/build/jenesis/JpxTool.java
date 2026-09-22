package build.jenesis;

import module java.base;

public final class JpxTool extends JenesisTool {

    @Override
    public String name() {
        return "jpx";
    }

    @Override
    protected int run(Function<String, String> requested, Output output, List<String> arguments)
            throws IOException, InterruptedException {
        return Jpx.run(requested, output, arguments.toArray(String[]::new));
    }
}
