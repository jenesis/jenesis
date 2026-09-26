package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;

public record TestNG() implements TestFramework {

    @Override
    public String runnerModule() {
        return "org.testng";
    }

    @Override
    public boolean isMarkedBy(ModuleDescriptor module) {
        return module.name().equals("org.testng");
    }

    @Override
    public Set<String> reflectingModules() {
        return Set.of("org.testng");
    }

    @Override
    public String runnerClass() {
        return "org.testng.TestNG";
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
        List<String> commands = new ArrayList<>(List.of("-d", (reporting
                ? output.resolve(BuildStep.REPORTS + "tests")
                : supplement.resolve("test-output")).toString()));
        if (tags.included().stream().anyMatch(term -> term.contains("&"))) {
            throw new IllegalArgumentException("TestNG selects the tests of any of several groups, not of all of them,"
                    + " so it cannot run " + tags + " - name each group on its own");
        }
        if (!tags.included().isEmpty()) {
            commands.add("-groups");
            commands.add(String.join(",", tags.included()));
        }
        SequencedSet<String> excluded = new LinkedHashSet<>(tags.excluded());
        if (ran.stream().allMatch(earlier -> !earlier.included().isEmpty()
                && earlier.excluded().isEmpty()
                && earlier.included().stream().noneMatch(term -> term.contains("&")))) {
            ran.forEach(earlier -> excluded.addAll(earlier.included()));
        }
        if (!excluded.isEmpty()) {
            commands.add("-excludegroups");
            commands.add(String.join(",", excluded));
        }
        if (parallel) {
            commands.add("-parallel");
            commands.add("methods");
        }
        if (!classes.isEmpty()) {
            commands.add("-testclass");
            commands.add(String.join(",", classes));
        }
        if (!methods.isEmpty()) {
            List<String> joined = new ArrayList<>();
            for (Map.Entry<String, SequencedSet<String>> entry : methods.entrySet()) {
                for (String method : entry.getValue()) {
                    joined.add(entry.getKey() + "." + method);
                }
            }
            commands.add("-methods");
            commands.add(String.join(",", joined));
        }
        return commands;
    }
}
