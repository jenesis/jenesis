package build.jenesis.maven;

import module java.base;
import build.jenesis.License;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;

public interface MavenResolver extends Resolver {

    SequencedMap<Path, MavenLocalPom> local(Executor executor, Repository repository, Path root) throws IOException;

    Closure closure(Executor executor,
                    MavenRepository repository,
                    List<RootPom> rootPoms,
                    Map<MavenDependencyKey, MavenDependencyValue> managedCoordinates,
                    MavenDependencyScope scope,
                    String prefix) throws IOException;

    record Closure(SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies,
                   SequencedMap<String, MavenDependencyKey> roots,
                   List<Resolver.Edge> edges,
                   SequencedMap<String, List<License>> licenses,
                   SequencedMap<String, Resolver.Resolved> poms) {
    }

    static MavenResolver of(Resolver resolver) {
        if (resolver instanceof MavenResolver mavenResolver) {
            return mavenResolver;
        }
        throw new IllegalArgumentException("Resolver "
                + (resolver == null ? "null" : resolver.getClass().getName())
                + " is not a MavenResolver");
    }

    record RootPom(RepositoryItem pom,
                   String checksum,
                   String identifier,
                   boolean versionPinned,
                   List<MavenDependencyName> exclusions) {

        public RootPom(RepositoryItem pom) {
            this(pom, null, null, false, null);
        }
    }
}
