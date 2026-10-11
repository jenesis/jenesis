package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;

public class LicenseHeaderModule implements BuildExecutorModule {

    public static final String FORMAT = "format", HEADER = "license-header.txt";
    private static final Set<String> EXTENSIONS = Set.of(".java", ".kt", ".scala", ".groovy");

    private final boolean verify;

    public LicenseHeaderModule() {
        this(false);
    }

    private LicenseHeaderModule(boolean verify) {
        this.verify = verify;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, HEADER);
    }

    public LicenseHeaderModule verify(boolean verify) {
        return new LicenseHeaderModule(verify);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(FORMAT, new Header(verify), inherited.sequencedKeySet());
    }

    private record Header(boolean verify) implements BuildStep {

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
                if (header.equals(comment)) {
                    continue;
                }
                String lower = comment == null ? "" : comment.toLowerCase(Locale.ROOT);
                boolean replaced = lower.contains("copyright") || lower.contains("license") || lower.contains("licence");
                if (verify) {
                    deviations.put(source.getKey(), replaced ? "a different license header" : "no license header");
                    continue;
                }
                String formatted = replaced
                        ? text.substring(0, start) + header + text.substring(end)
                        : header + "\n\n" + text.substring(start);
                Files.writeString(source.getValue(), content.contains("\r\n")
                        ? formatted.replace("\n", "\r\n")
                        : formatted);
            }
            if (!deviations.isEmpty()) {
                throw new IllegalStateException("Not every source starts with the license header of " + HEADER
                        + " - run with -Djenesis.format.rewrite=true to write it:\n"
                        + deviations.entrySet().stream()
                                .map(entry -> "  " + entry.getKey() + ": " + entry.getValue())
                                .collect(Collectors.joining("\n")));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
