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
            if (entry.startsWith("!")) {
                String name = entry.substring(1).trim();
                if (!NAME.matcher(name).matches()) {
                    throw refused(entry, expression);
                }
                excluded.add(name);
            } else {
                SequencedSet<String> names = new TreeSet<>();
                for (String part : entry.split("&", -1)) {
                    String name = part.trim();
                    if (!NAME.matcher(name).matches()) {
                        throw refused(entry, expression);
                    }
                    names.add(name);
                }
                included.add(String.join("&", names));
            }
        }
        return new TestTags(included, excluded);
    }

    private static IllegalArgumentException refused(String entry, String expression) {
        return new IllegalArgumentException("A test tag selection is a comma-separated list of tag names, each one"
                + " or several joined by & for the tests carrying all of them, or one preceded by ! to leave out the"
                + " tests carrying it, not " + entry + " in " + expression);
    }

    public static Set<String> names(String term) {
        return Set.of(term.split("&"));
    }

    public boolean all() {
        return included.isEmpty() && excluded.isEmpty();
    }

    public boolean coveredBy(List<TestTags> ran) {
        if (included.isEmpty()) {
            return ran.stream().anyMatch(earlier -> earlier.included.isEmpty() && excluded.containsAll(earlier.excluded));
        }
        return included.stream().allMatch(term -> ran.stream().anyMatch(earlier -> excluded.containsAll(earlier.excluded)
                && (earlier.included.isEmpty() || earlier.included.stream().anyMatch(before -> names(term).containsAll(names(before))))));
    }

    @Override
    public String toString() {
        return Stream.concat(included.stream(), excluded.stream().map(tag -> "!" + tag)).collect(Collectors.joining(","));
    }
}
