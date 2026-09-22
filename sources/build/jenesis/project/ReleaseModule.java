package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;

public class ReleaseModule implements BuildExecutorModule {

    public static final String JRELEASER = "jreleaser";

    private final Path root;
    private final String version;
    private final Path configuration;
    private final JReleaserModule jreleaser;

    public ReleaseModule(Path root, String version) {
        this(root, version, JReleaserModule.configured(Environment.NONE, root));
    }

    private ReleaseModule(Path root, String version, Path configuration) {
        this(root, version, configuration, new JReleaserModule(root, configuration, version));
    }

    public static ReleaseModule ofEnvironment(Environment environment,
                                              Path root,
                                              String version) {
        Path configuration = JReleaserModule.configured(environment, root);
        return new ReleaseModule(root,
                                 version,
                                 configuration,
                                 JReleaserModule.ofEnvironment(environment, root, configuration, version));
    }

    private ReleaseModule(Path root, String version, Path configuration, JReleaserModule jreleaser) {
        this.root = root;
        this.version = version;
        this.configuration = configuration;
        this.jreleaser = jreleaser;
    }

    public ReleaseModule configuration(Path configuration) {
        return new ReleaseModule(root, version, configuration, jreleaser.configuration(configuration));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        if (configuration != null) {
            buildExecutor.addModule(JRELEASER, jreleaser, inherited.sequencedKeySet());
        }
    }
}
