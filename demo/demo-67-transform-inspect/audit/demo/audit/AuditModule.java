package demo.audit;

import module java.base;
import module build.jenesis;

public class AuditModule implements BuildExecutorModule {

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("audit", new Audit(), inherited.sequencedKeySet());
    }

    private record Audit() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, String> modules = new TreeMap<>();
            Set<String> noticed = new HashSet<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(Inventory.INVENTORY);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                SequencedProperties inventory = SequencedProperties.ofFiles(file);
                for (String key : inventory.stringPropertyNames()) {
                    if (key.endsWith(".module")) {
                        modules.put(key.substring(0, key.length() - ".module".length()), inventory.getProperty(key));
                    } else if (key.endsWith(".attachment.notice")) {
                        noticed.add(key.substring(0, key.length() - ".attachment.notice".length()));
                    }
                }
            }
            modules.keySet().removeAll(noticed);
            if (!modules.isEmpty()) {
                throw new IllegalStateException("No notice is attached to " + String.join(", ", modules.values())
                        + " - add notice+transform to jenesis-plugins.properties, or switch it back on");
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
