package build.jenesis.project;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.Permits;
import build.jenesis.Repository;
import build.jenesis.Resolver;

@FunctionalInterface
public interface LanguageFactory {

    Language create(Environment environment,
                    SequencedMap<String, String> properties,
                    Permits permits,
                    String group,
                    Map<String, Repository> repositories,
                    Map<String, Resolver> resolvers) throws IOException;
}
