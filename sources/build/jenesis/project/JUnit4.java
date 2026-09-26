package build.jenesis.project;

import module java.base;

public record JUnit4() implements TestFramework {

    @Override
    public String runnerModule() {
        return "junit";
    }

    @Override
    public boolean isMarkedBy(ModuleDescriptor module) {
        return module.name().equals("junit");
    }

    @Override
    public Set<String> reflectingModules() {
        return Set.of("junit");
    }

    @Override
    public String runnerClass() {
        return "org.junit.runner.JUnitCore";
    }

    @Override
    public List<String> arguments(Path supplement,
                                  Path output,
                                  SequencedSet<String> classes,
                                  SequencedMap<String, SequencedSet<String>> methods,
                                  TestTags tags,
                                  List<TestTags> ran,
                                  boolean parallel,
                                  boolean reporting) {
        if (!methods.isEmpty()) {
            throw new IllegalArgumentException("JUnit4 does not support running individual methods");
        }
        if (!tags.all()) {
            throw new IllegalArgumentException("JUnit 4 cannot select @Category groups through its console runner");
        }
        return List.copyOf(classes);
    }
}
