package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.SequencedProperties;

public record Jfr(SequencedMap<String, String> options) implements ObservabilityEngine {

    public Jfr() {
        this(Collections.emptyNavigableMap());
    }

    public static Jfr ofFile(Path file) throws IOException {
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedMap<String, String> options = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.equals("filename")) {
                throw new IllegalArgumentException(file + " names a filename, which the build sets to the recording"
                        + " in the test step's reports/jfr/ - remove the filename line");
            }
            String value = properties.value(key);
            if (value == null) {
                throw new IllegalArgumentException(file + " gives " + key + " no value, but an option of the recording"
                        + " is <key>=<value>");
            } else if (value.contains(",")) {
                throw new IllegalArgumentException(file + " gives " + key + " the value " + value + ", but the value of"
                        + " an option of the recording holds no comma");
            }
            options.put(key, value);
        }
        return new Jfr(Collections.unmodifiableSequencedMap(options));
    }

    @Override
    public String name() {
        return "jfr";
    }

    @Override
    public SequencedMap<String, String> coordinates() {
        return Collections.emptyNavigableMap();
    }

    @Override
    public List<String> commands(SequencedMap<String, Path> resolved, Path output) {
        Path folder = output.resolve(BuildStep.REPORTS + "jfr");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SequencedMap<String, String> recording = new LinkedHashMap<>();
        recording.put("filename", folder.resolve("tests.jfr").toAbsolutePath().toString());
        recording.put("dumponexit", "true");
        recording.putAll(options);
        return List.of("-XX:StartFlightRecording=" + recording.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",")));
    }
}
