package demo.plugin;

import module build.jenesis;
import module java.base;

@BuildModuleName("greeting")
public class GreetingFactory implements BuildModuleFactory {

    @Override
    public BuildExecutorModule create(Environment environment,
                                      SequencedMap<String, String> properties,
                                      Permits permits,
                                      String group,
                                      Map<String, Repository> repositories,
                                      Map<String, Resolver> resolvers) {
        return new GreetingModule(properties);
    }
}
