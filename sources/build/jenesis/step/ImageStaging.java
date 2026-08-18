package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

public class ImageStaging implements BuildStep {

    private final String key;
    private final boolean perModule;

    public ImageStaging(String key) {
        this(key, false);
    }

    public ImageStaging(String key, boolean perModule) {
        this.key = key;
        this.perModule = perModule;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String suffix = "." + key;
        for (Map.Entry<String, BuildStepArgument> entry0 : arguments.entrySet()) {
            BuildStepArgument argument = entry0.getValue();
            if (argument.removed()) {
                continue;
            }
            String module = moduleOf(argument.folder());
            Path inventoryFile = argument.folder().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String entry : inventory.stringPropertyNames()) {
                if (!entry.endsWith(suffix)) {
                    continue;
                }
                Path image = argument.folder().resolve(inventory.getProperty(entry)).normalize();
                if (!Files.isDirectory(image)) {
                    continue;
                }
                Path target = perModule ? context.next().resolve(module) : context.next();
                Files.createDirectories(target);
                Files.walkFileTree(image, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                        Files.createDirectories(target.resolve(image.relativize(directory).toString()));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Path destination = target.resolve(image.relativize(file).toString());
                        if (!Files.exists(destination)) {
                            BuildStep.linkOrCopy(destination, file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static String moduleOf(Path folder) {
        for (Path path = folder; path != null; path = path.getParent()) {
            Path name = path.getFileName();
            if (name != null && name.toString().startsWith("module")) {
                String decoded = URLDecoder.decode(name.toString(), StandardCharsets.UTF_8).replace('/', '-');
                while (decoded.endsWith("-")) {
                    decoded = decoded.substring(0, decoded.length() - 1);
                }
                return decoded.isEmpty() ? "module" : decoded;
            }
        }
        return "module";
    }
}
