package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;

public class LicenseHeaderModule implements BuildExecutorModule {

    public static final String FORMAT = "format", HEADER = "license-header.txt", YEAR = "$YEAR";
    private static final String YEARS = "[0-9]{4}(?:\\s*-\\s*[0-9]{4})?";
    private static final Pattern PLACEHOLDER = Pattern.compile(YEARS);
    private static final Set<String> EXTENSIONS = Set.of(".java", ".kt", ".scala", ".groovy");

    private final boolean verify;
    private final String year;

    public LicenseHeaderModule() {
        this(false, null);
    }

    public static LicenseHeaderModule ofEnvironment(Environment environment) {
        String year = environment.value("format.year");
        if (year != null && !PLACEHOLDER.matcher(year).matches()) {
            throw new IllegalArgumentException("jenesis.format.year is " + year
                    + ", but names the year a license header's " + YEAR + " is written as - a year such as 2026,"
                    + " or a range such as 2016-2026");
        }
        return new LicenseHeaderModule(false, year);
    }

    private LicenseHeaderModule(boolean verify, String year) {
        this.verify = verify;
        this.year = year;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, HEADER);
    }

    public LicenseHeaderModule verify(boolean verify) {
        return new LicenseHeaderModule(verify, year);
    }

    public LicenseHeaderModule year(String year) {
        return new LicenseHeaderModule(verify, year);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(FORMAT, new Header(verify, year), inherited.sequencedKeySet());
    }

    private record Header(boolean verify, String year) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return !verify || arguments.values().stream().anyMatch(BuildStepArgument::hasChanged);
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            String template = null;
            SequencedMap<Path, Path> sources = new TreeMap<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path candidate = argument.folder().resolve(HEADER);
                if (Files.isRegularFile(candidate)) {
                    template = Files.readString(candidate);
                }
                Path folder = argument.folder().resolve(BuildStep.SOURCES);
                if (Files.isDirectory(folder)) {
                    try (Stream<Path> files = Files.walk(folder, FileVisitOption.FOLLOW_LINKS)) {
                        files.filter(Files::isRegularFile)
                                .filter(file -> EXTENSIONS.stream().anyMatch(file.getFileName().toString()::endsWith))
                                .forEach(file -> sources.put(folder.relativize(file), file));
                    }
                }
            }
            if (template == null) {
                throw new IllegalStateException("No " + HEADER + " among the inputs of the license header step");
            }
            String header = template.replace("\r\n", "\n").stripTrailing().replaceFirst("^\n+", "");
            if (!header.startsWith("/*") && !header.startsWith("//")) {
                header = header.lines()
                        .map(line -> (" * " + line).stripTrailing())
                        .collect(Collectors.joining("\n", "/*\n", "\n */"));
            }
            Pattern expected = Pattern.compile(Arrays.stream(header.split(Pattern.quote(YEAR), -1))
                    .map(Pattern::quote)
                    .collect(Collectors.joining(YEARS)));
            SequencedMap<Path, String> deviations = new LinkedHashMap<>();
            for (Map.Entry<Path, Path> source : sources.entrySet()) {
                String content = Files.readString(source.getValue());
                String text = content.replace("\r\n", "\n");
                int start = 0;
                while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                    start++;
                }
                int end = -1;
                if (text.startsWith("/*", start)) {
                    int close = text.indexOf("*/", start + 2);
                    end = close < 0 ? -1 : close + 2;
                } else if (text.startsWith("//", start)) {
                    int line = start;
                    while (text.startsWith("//", line)) {
                        int newline = text.indexOf('\n', line);
                        end = newline < 0 ? text.length() : newline;
                        line = end + 1;
                        while (line < text.length() && (text.charAt(line) == ' ' || text.charAt(line) == '\t')) {
                            line++;
                        }
                    }
                }
                String comment = end < 0 ? null : text.substring(start, end);
                if (comment != null && expected.matcher(comment).matches()) {
                    continue;
                }
                String lower = comment == null ? "" : comment.toLowerCase(Locale.ROOT);
                boolean replaced = lower.contains("copyright") || lower.contains("license") || lower.contains("licence");
                if (verify) {
                    deviations.put(source.getKey(), replaced ? "a different license header" : "no license header");
                    continue;
                }
                String rendered = header;
                if (header.contains(YEAR)) {
                    Matcher years = PLACEHOLDER.matcher(replaced ? comment : "");
                    String written = years.find() ? years.group() : year;
                    if (written == null) {
                        throw new IllegalStateException(source.getKey() + " needs the license header written, whose "
                                + YEAR + " names no year the build may choose - set -Djenesis.format.year=<year> to"
                                + " name the one to write");
                    }
                    rendered = header.replace(YEAR, written);
                }
                String formatted = replaced
                        ? text.substring(0, start) + rendered + text.substring(end)
                        : rendered + "\n\n" + text.substring(start);
                Files.writeString(source.getValue(), content.contains("\r\n")
                        ? formatted.replace("\n", "\r\n")
                        : formatted);
            }
            if (!deviations.isEmpty()) {
                throw new IllegalStateException(deviations.size() + " of the sources do not start with the license"
                        + " header of " + HEADER + " - run with -Djenesis.format.rewrite=true to write it:\n"
                        + deviations.entrySet().stream()
                                .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("\n")));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
