package demo.licence;

import module build.jenesis;
import module java.base;

public class LicenceFactory implements BuildModuleFactory {

    @Override
    public BuildExecutorModule create(Environment environment,
                                      SequencedMap<String, String> properties,
                                      Permits permits,
                                      String group,
                                      Map<String, Repository> repositories,
                                      Map<String, Resolver> resolvers) {
        return new LicenceModule(properties);
    }
}
