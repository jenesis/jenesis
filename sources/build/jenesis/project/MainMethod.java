package build.jenesis.project;

import module java.base;
import build.jenesis.TestFramework;
import build.jenesis.TestTags;

public record MainMethod(String module, String main) implements TestFramework {

    @Override
    public String runnerModule() {
        return module;
    }

    @Override
    public String runnerClass() {
        return main;
    }

    @Override
    public boolean isMarkedBy(ModuleDescriptor module) {
        return false;
    }

    @Override
    public Set<String> reflectingModules() {
        return Set.of();
    }

    @Override
    public List<String> arguments(Path supplement,
                                  Path output,
                                  SequencedSet<String> classes,
                                  SequencedMap<String, SequencedSet<String>> methods,
                                  boolean parallel,
                                  boolean reporting) {
        return List.of();
    }

    @Override
    public List<String> tags(List<String> arguments, TestTags requested, List<TestTags> ran) {
        return arguments;
    }
}
