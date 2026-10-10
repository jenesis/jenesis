package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Environment;
import build.jenesis.Project;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenRepositoryRelease;
import build.jenesis.module.JenesisModuleRepositoryRelease;

public class ReleaseModule implements BuildExecutorModule {

    public static final String JRELEASER = "jreleaser", JENESIS = "jenesis", MAVEN = "maven";

    private final Path root;
    private final String version;
    private final Path configuration;
    private final JReleaserModule jreleaser;
    private final JenesisModuleRepositoryRelease jenesisModuleRepositoryRelease;
    private final MavenRepositoryRelease mavenRepositoryRelease;

    public ReleaseModule(Path root, String version) {
        this(root, version, JReleaserModule.configured(Environment.NONE, root));
    }

    private ReleaseModule(Path root, String version, Path configuration) {
        this(root, version, configuration, new JReleaserModule(root, configuration, version), null, null);
    }

    public static ReleaseModule ofEnvironment(Environment environment,
                                              Path root,
                                              String version) {
        Path configuration = JReleaserModule.configured(environment, root);
        URI repository = JenesisModuleRepositoryRelease.configured(environment),
                maven = MavenRepositoryRelease.configured(environment);
        return new ReleaseModule(root,
                                 version,
                                 configuration,
                                 JReleaserModule.ofEnvironment(environment, root, configuration, version),
                                 repository == null ? null : JenesisModuleRepositoryRelease.ofEnvironment(environment, repository),
                                 maven == null ? null : MavenRepositoryRelease.ofEnvironment(environment, maven));
    }

    private ReleaseModule(Path root,
                          String version,
                          Path configuration,
                          JReleaserModule jreleaser,
                          JenesisModuleRepositoryRelease jenesisModuleRepositoryRelease,
                          MavenRepositoryRelease mavenRepositoryRelease) {
        this.root = root;
        this.version = version;
        this.configuration = configuration;
        this.jreleaser = jreleaser;
        this.jenesisModuleRepositoryRelease = jenesisModuleRepositoryRelease;
        this.mavenRepositoryRelease = mavenRepositoryRelease;
    }

    public ReleaseModule configuration(Path configuration) {
        return new ReleaseModule(root, version, configuration, jreleaser.configuration(configuration), jenesisModuleRepositoryRelease, mavenRepositoryRelease);
    }

    public ReleaseModule jenesisModuleRepositoryRelease(JenesisModuleRepositoryRelease jenesisModuleRepositoryRelease) {
        return new ReleaseModule(root, version, configuration, jreleaser, jenesisModuleRepositoryRelease, mavenRepositoryRelease);
    }

    public ReleaseModule mavenRepositoryRelease(MavenRepositoryRelease mavenRepositoryRelease) {
        return new ReleaseModule(root, version, configuration, jreleaser, jenesisModuleRepositoryRelease, mavenRepositoryRelease);
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
        if (mavenRepositoryRelease != null) {
            String maven = BuildExecutorModule.PREVIOUS + Project.STAGE + "/maven";
            if (!inherited.containsKey(maven)) {
                throw new IllegalStateException("A Maven repository is configured to release to, but this layout"
                        + " stages no Maven tree: build with the maven or modular_to_maven layout, or unset"
                        + " jenesis.release.maven.uri");
            }
            buildExecutor.addStep(MAVEN, mavenRepositoryRelease, maven);
        }
    }
}
