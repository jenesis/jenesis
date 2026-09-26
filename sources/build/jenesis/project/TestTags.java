package build.jenesis.project;

import module java.base;

public record TestTags(SequencedSet<String> included, SequencedSet<String> excluded) {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_.\\-]+");

    public static final TestTags ALL = new TestTags(Collections.emptyNavigableSet(), Collections.emptyNavigableSet());

    public TestTags {
        included = Collections.unmodifiableSequencedSet(new TreeSet<>(included));
        excluded = Collections.unmodifiableSequencedSet(new TreeSet<>(excluded));
    }

    public static TestTags parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return ALL;
        }
        SequencedSet<String> included = new TreeSet<>(), excluded = new TreeSet<>();
        for (String candidate : expression.split(",")) {
            String entry = candidate.trim();
            if (entry.isEmpty()) {
                continue;
            }
            boolean negated = entry.startsWith("!");
            String name = negated ? entry.substring(1).trim() : entry;
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("A test tag selection is a comma-separated list of tag names, each"
                        + " optionally preceded by ! to leave out the tests carrying it, not " + entry
                        + " in " + expression);
            }
            (negated ? excluded : included).add(name);
        }
        return new TestTags(included, excluded);
    }

    public boolean all() {
        return included.isEmpty() && excluded.isEmpty();
    }

    public boolean coveredBy(List<TestTags> ran) {
        if (included.isEmpty()) {
            return ran.stream().anyMatch(earlier -> earlier.included.isEmpty() && excluded.containsAll(earlier.excluded));
        }
        return included.stream().allMatch(tag -> ran.stream().anyMatch(earlier ->
                (earlier.included.isEmpty() || earlier.included.contains(tag)) && excluded.containsAll(earlier.excluded)));
    }

    @Override
    public String toString() {
        return Stream.concat(included.stream(), excluded.stream().map(tag -> "!" + tag)).collect(Collectors.joining(","));
    }
}
