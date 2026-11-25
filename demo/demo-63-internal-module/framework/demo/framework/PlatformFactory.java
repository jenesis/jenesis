package demo.framework;

import module build.jenesis;
import module java.base;
import build.jenesis.TestFramework;
import build.jenesis.TestFrameworkFactory;
import build.jenesis.TestTags;
import build.jenesis.project.JUnitPlatform;

public class PlatformFactory implements TestFrameworkFactory {

    @Override
    public TestFramework create(Environment environment,
                                SequencedMap<String, String> properties,
                                Permits permits,
                                String group,
                                Map<String, Repository> repositories,
                                Map<String, Resolver> resolvers) {
        if (!properties.isEmpty()) {
            throw new IllegalArgumentException("The platform plugin takes no values, but was handed "
                    + properties.sequencedKeySet());
        }
        return new Platform(new JUnitPlatform(), group);
    }

    public record Platform(JUnitPlatform platform, String name) implements TestFramework {

        @Override
        public String runnerModule() {
            return platform.runnerModule();
        }

        @Override
        public String runnerClass() {
            return platform.runnerClass();
        }

        @Override
        public boolean isMarkedBy(ModuleDescriptor module) {
            return platform.isMarkedBy(module);
        }

        @Override
        public Set<String> reflectingModules() {
            return platform.reflectingModules();
        }

        @Override
        public SequencedMap<String, String> missingCoordinates(List<ModuleDescriptor> modules) {
            return platform.missingCoordinates(modules);
        }

        @Override
        public boolean holdsTests(ClassModel type) {
            return platform.holdsTests(type);
        }

        @Override
        public Map<String, String> systemProperties() {
            Map<String, String> properties = new LinkedHashMap<>(platform.systemProperties());
            properties.put("demo.framework", name);
            return properties;
        }

        @Override
        public TestFramework runningOn(List<Path> jars) throws IOException {
            return new Platform(platform.runningOn(jars), name);
        }

        @Override
        public List<String> arguments(Path supplement,
                                      Path output,
                                      SequencedSet<String> classes,
                                      SequencedMap<String, SequencedSet<String>> methods,
                                      boolean parallel,
                                      boolean reporting) {
            return platform.arguments(supplement, output, classes, methods, parallel, reporting);
        }

        @Override
        public List<String> tags(List<String> arguments, TestTags requested, List<TestTags> ran) {
            return platform.tags(arguments, requested, ran);
        }

        @Override
        public List<String> engines(List<String> arguments, String engines) {
            return platform.engines(arguments, engines);
        }
    }
}
