package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Environment;
import build.jenesis.Project;
import build.jenesis.SequencedProperties;
import build.jenesis.module.JenesisModuleRepositoryRelease;

public class ReleaseModule implements BuildExecutorModule {

    public static final String JRELEASER = "jreleaser", JENESIS = "jenesis";

    private final Path root;
    private final String version;
    private final Path configuration;
    private final JReleaserModule jreleaser;
    private final JenesisModuleRepositoryRelease jenesisModuleRepositoryRelease;

    public ReleaseModule(Path root, String version) {
        this(root, version, JReleaserModule.configured(Environment.NONE, root));
    }

    private ReleaseModule(Path root, String version, Path configuration) {
        this(root, version, configuration, new JReleaserModule(root, configuration, version), null);
    }

    public static ReleaseModule ofEnvironment(Environment environment,
                                              Path root,
                                              String version) {
        Path configuration = JReleaserModule.configured(environment, root);
        URI repository = JenesisModuleRepositoryRelease.configured(environment);
        return new ReleaseModule(root,
                                 version,
                                 configuration,
                                 JReleaserModule.ofEnvironment(environment, root, configuration, version),
                                 repository == null ? null : JenesisModuleRepositoryRelease.ofEnvironment(environment, repository));
    }

    private ReleaseModule(Path root,
                          String version,
                          Path configuration,
                          JReleaserModule jreleaser,
                          JenesisModuleRepositoryRelease jenesisModuleRepositoryRelease) {
        this.root = root;
        this.version = version;
        this.configuration = configuration;
        this.jreleaser = jreleaser;
        this.jenesisModuleRepositoryRelease = jenesisModuleRepositoryRelease;
    }

    public ReleaseModule configuration(Path configuration) {
        return new ReleaseModule(root, version, configuration, jreleaser.configuration(configuration), jenesisModuleRepositoryRelease);
    }

    public ReleaseModule jenesisModuleRepositoryRelease(JenesisModuleRepositoryRelease jenesisModuleRepositoryRelease) {
        return new ReleaseModule(root, version, configuration, jreleaser, jenesisModuleRepositoryRelease);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        if (configuration != null) {
            buildExecutor.addModule(JRELEASER, jreleaser, inherited.sequencedKeySet());
        }
        if (jenesisModuleRepositoryRelease != null) {
            String modular = BuildExecutorModule.PREVIOUS + Project.STAGE + "/modular";
            if (!inherited.containsKey(modular)) {
                throw new IllegalStateException("A Jenesis module repository is configured to release to, but this"
                        + " layout stages no modular tree: build with the modular or modular_to_maven layout, or"
                        + " unset jenesis.release.uri");
            }
            buildExecutor.addStep(JENESIS, jenesisModuleRepositoryRelease, modular);
        }
    }
}
