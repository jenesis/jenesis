package build.jenesis.maven;

import module java.base;

public record MavenLocalPom(String groupId,
                            String artifactId,
                            String version,
                            String packaging,
                            String release,
                            String testRelease,
                            String sourceDirectory,
                            List<String> resourceDirectories,
                            String testSourceDirectory,
                            List<String> testResourceDirectories,
                            SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies,
                            SequencedMap<MavenDependencyKey, MavenDependencyValue> managedDependencies,
                            SequencedMap<MavenDependencyKey, MavenDependencyValue> bom,
                            SequencedMap<String, String> qualifiedDependencies,
                            SequencedMap<String, String> attachments,
                            SequencedSet<String> natives,
                            SequencedMap<String, String> plugins,
                            SequencedMap<String, String> testPlugins,
                            SequencedMap<String, String> aliases,
                            SequencedMap<String, String> signatures,
                            String mainClass,
                            SequencedMap<String, String> metadata,
                            boolean deploy,
                            boolean install) {

    public MavenLocalPom version(String version) {
        return new MavenLocalPom(groupId, artifactId, version, packaging, release, testRelease, sourceDirectory,
                resourceDirectories, testSourceDirectory, testResourceDirectories, dependencies, managedDependencies, bom,
                qualifiedDependencies, attachments, natives, plugins, testPlugins, aliases, signatures, mainClass, metadata,
                deploy, install);
    }

    public MavenLocalPom dependencies(SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies) {
        return new MavenLocalPom(groupId, artifactId, version, packaging, release, testRelease, sourceDirectory,
                resourceDirectories, testSourceDirectory, testResourceDirectories, dependencies, managedDependencies, bom,
                qualifiedDependencies, attachments, natives, plugins, testPlugins, aliases, signatures, mainClass, metadata,
                deploy, install);
    }

    public MavenLocalPom managedDependencies(SequencedMap<MavenDependencyKey, MavenDependencyValue> managedDependencies) {
        return new MavenLocalPom(groupId, artifactId, version, packaging, release, testRelease, sourceDirectory,
                resourceDirectories, testSourceDirectory, testResourceDirectories, dependencies, managedDependencies, bom,
                qualifiedDependencies, attachments, natives, plugins, testPlugins, aliases, signatures, mainClass, metadata,
                deploy, install);
    }

    public MavenLocalPom bom(SequencedMap<MavenDependencyKey, MavenDependencyValue> bom) {
        return new MavenLocalPom(groupId, artifactId, version, packaging, release, testRelease, sourceDirectory,
                resourceDirectories, testSourceDirectory, testResourceDirectories, dependencies, managedDependencies, bom,
                qualifiedDependencies, attachments, natives, plugins, testPlugins, aliases, signatures, mainClass, metadata,
                deploy, install);
    }
}
