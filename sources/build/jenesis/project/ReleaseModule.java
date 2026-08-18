package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;

public class ReleaseModule implements BuildExecutorModule {

    public static final String JRELEASER = "jreleaser";

    private final Path root;
    private final String version;

    public ReleaseModule(Path root, String version) {
        this.root = root;
        this.version = version;
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        Path configuration = JReleaserModule.configured(root);
        if (configuration != null) {
            buildExecutor.addModule(JRELEASER,
                    new JReleaserModule(root, configuration, version),
                    inherited.sequencedKeySet());
        }
    }
}
