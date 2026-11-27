package build.jenesis.project;

import module java.base;
import build.jenesis.TestFramework;
import build.jenesis.TestTags;
import java.lang.classfile.Attributes;

public record JUnit4() implements TestFramework {

    private static final Set<String> MARKERS = Set.of("Lorg/junit/Test;", "Lorg/junit/runner/RunWith;");
    private static final Pattern PASSED = Pattern.compile("OK \\((\\d+) tests?\\)");
    private static final Pattern FAILED = Pattern.compile("Tests run: (\\d+), +Failures: (\\d+)");
    private static final Pattern FAILURES = Pattern.compile("There (was 1 failure|were \\d+ failures):");
    private static final Pattern FRAME = Pattern.compile("\\s+(at .+|\\.\\.\\. \\d+ (more|trimmed))");

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
    public boolean holdsTests(ClassModel type) {
        return type.interfaces().stream().anyMatch(implemented -> implemented.asInternalName().equals("junit/framework/Test"))
                || type.methods().stream().anyMatch(method -> method.methodName().equalsString("suite")
                        && method.flags().has(AccessFlag.STATIC)
                        && method.methodTypeSymbol().parameterCount() == 0)
                || Stream.<AttributedElement>concat(Stream.of(type), type.methods().stream())
                        .flatMap(element -> element.findAttribute(Attributes.runtimeVisibleAnnotations()).stream())
                        .flatMap(annotations -> annotations.annotations().stream())
                        .anyMatch(annotation -> MARKERS.contains(annotation.className().stringValue()));
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
                                  boolean parallel,
                                  boolean reporting) {
        if (!methods.isEmpty()) {
            throw new IllegalArgumentException("JUnit4 does not support running individual methods");
        }
        return List.copyOf(classes);
    }

    @Override
    public Optional<Summary> summary(Path supplement, Path output) throws IOException {
        Path printed = supplement.resolve("output");
        if (!Files.isRegularFile(printed)) {
            return Optional.empty();
        }
        long run = -1, failed = 0;
        List<String> failures = new ArrayList<>();
        boolean failing = false, located = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(printed),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher passed = PASSED.matcher(line), counted = FAILED.matcher(line);
                if (passed.matches()) {
                    run = Long.parseLong(passed.group(1));
                    failed = 0;
                } else if (counted.matches()) {
                    run = Long.parseLong(counted.group(1));
                    failed = Long.parseLong(counted.group(2));
                } else if (FAILURES.matcher(line).matches()) {
                    failures.clear();
                    failing = true;
                } else if (line.equals("FAILURES!!!")) {
                    failing = false;
                }
                boolean frame = FRAME.matcher(line).matches();
                if (failing && !line.isBlank() && !(frame && located)) {
                    failures.add(line);
                }
                located = frame;
            }
        }
        return run < 0 ? Optional.empty() : Optional.of(new Summary(run, run - failed, failed, -1, -1, failures));
    }

    @Override
    public List<String> tags(List<String> arguments, TestTags requested, List<TestTags> ran) {
        List<String> selection = new ArrayList<>();
        SequencedSet<String> included = new TreeSet<>();
        Set<String> excluded = null;
        boolean everything = false;
        for (String alternative : requested.alternatives()) {
            SequencedSet<String> tagged = new TreeSet<>(), untagged = new TreeSet<>();
            for (String literal : TestTags.literals(alternative)) {
                if (literal.startsWith("-")) {
                    untagged.add(literal.substring(1));
                } else {
                    tagged.add(literal);
                }
            }
            if (requested.alternatives().size() == 1) {
                tagged.forEach(category -> selection.add("--filter=org.junit.experimental.categories.IncludeCategories="
                        + category));
            } else if (tagged.size() > 1 || excluded != null && !excluded.equals(untagged)) {
                throw new IllegalArgumentException("JUnit4 runs the tests of all of several categories, or of any of"
                        + " several categories while leaving the same categories out, so it cannot run " + requested
                        + " - write one alternative, or each alternative as at most one category, with the same"
                        + " categories preceded by - in every alternative");
            } else {
                included.addAll(tagged);
                everything |= tagged.isEmpty();
            }
            excluded = untagged;
        }
        if (!everything && !included.isEmpty()) {
            selection.add("--filter=org.junit.experimental.categories.IncludeCategories=" + String.join(",", included));
        }
        SequencedSet<String> excludedCategories = new TreeSet<>(excluded == null ? Set.of() : excluded);
        if (ran.stream().allMatch(earlier -> !earlier.all() && earlier.alternatives().stream()
                .allMatch(alternative -> !alternative.contains("+") && !alternative.startsWith("-")))) {
            ran.forEach(earlier -> excludedCategories.addAll(earlier.alternatives()));
        }
        if (!excludedCategories.isEmpty()) {
            selection.add("--filter=org.junit.experimental.categories.ExcludeCategories="
                    + String.join(",", excludedCategories));
        }
        selection.addAll(arguments);
        return selection;
    }
}
