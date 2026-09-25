package demo.notice;

import module java.base;
import module build.jenesis;

public class NoticeModule implements BuildExecutorModule {

    private final String holder;

    public NoticeModule() {
        this(Collections.emptyNavigableMap());
    }

    public NoticeModule(SequencedMap<String, String> properties) {
        holder = properties.getOrDefault("holder", "its authors");
    }

    @Override
    public void accept(BuildExecutor executor, SequencedMap<String, Path> inherited) {
        executor.addStep("notice", new Notice(holder), inherited.sequencedKeySet());
    }

    private record Notice(String holder) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            BuildStepArgument legal = arguments.get("../inputs/legal");
            String header = legal == null || legal.removed()
                    ? null
                    : Files.readString(legal.folder().resolve("HEADER.txt")).strip();
            SequencedProperties additions = new SequencedProperties();
            List<String> project = new ArrayList<>();
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
                    if (!key.endsWith(".module")) {
                        continue;
                    }
                    String prefix = key.substring(0, key.length() - ".module".length());
                    List<String> lines = new ArrayList<>();
                    lines.add(inventory.getProperty(key) + " - Copyright " + holder);
                    if (header != null) {
                        lines.add(header);
                    }
                    for (String dependency : inventory.stringPropertyNames()) {
                        String index = dependency.substring(Math.min(dependency.length(), prefix.length() + ".dependency.".length()));
                        if (dependency.startsWith(prefix + ".dependency.") && index.matches("[0-9]+")
                                && "main".equals(inventory.getProperty(dependency + ".group"))) {
                            lines.add("  includes " + inventory.getProperty(dependency).split(" ")[0]);
                        }
                    }
                    Path notice = Files.createDirectories(context.next().resolve("notices").resolve(prefix)).resolve("NOTICE.txt");
                    Files.write(notice, lines);
                    project.addAll(lines);
                    additions.setProperty(prefix + ".attachment.notice", "notices/" + prefix + "/NOTICE.txt");
                }
            }
            additions.store(context.next().resolve(Inventory.INVENTORY));
            Files.write(Files.createDirectories(context.next().resolve("project")).resolve("NOTICE.txt"), project);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}
