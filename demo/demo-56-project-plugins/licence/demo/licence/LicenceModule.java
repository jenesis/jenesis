package demo.licence;

import module java.base;
import module build.jenesis;

public class LicenceModule implements BuildExecutorModule {

    private final String name;

    public LicenceModule() {
        this(Collections.emptyNavigableMap());
    }

    public LicenceModule(SequencedMap<String, String> properties) {
        name = properties.getOrDefault("name", "Apache License, Version 2.0");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("licence", new Licence(name), inherited.sequencedKeySet());
    }

    private record Licence(String name) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            BuildStepArgument legal = arguments.get("../inputs/legal");
            Path header = legal == null || legal.removed() ? null : legal.folder().resolve("HEADER.txt");
            if (header == null || !Files.isRegularFile(header) || !Files.readString(header).contains(name)) {
                throw new IllegalStateException("legal/HEADER.txt does not name the " + name
                        + " - the project is released under it, so its header must say so");
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
