package build.jenesis.step;

import module java.base;

public abstract class JdkProcessBuildStep extends ProcessBuildStep {

    @SuppressWarnings("unused")
    private final String version = Runtime.version().toString();

    protected JdkProcessBuildStep(String command, Function<List<String>, ? extends ProcessHandler> factory) {
        super(command, factory);
    }

    protected JdkProcessBuildStep(String command,
                                  Function<List<String>, ? extends ProcessHandler> factory,
                                  BiConsumer<Boolean, String> printing) {
        super(command, factory, printing);
    }
}
