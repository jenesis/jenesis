package build.jenesis;

import module java.base;

@FunctionalInterface
public interface BuildStep extends Serializable {

    String SOURCES = "sources/",
            RESOURCES = "resources/",
            CLASSES = "classes/",
            ARTIFACTS = "artifacts/",
            DOCUMENTATION = "documentation/",
            REPORTS = "reports/";

    String IDENTITY = "identity.properties",
            REQUIRES = "requires.properties",
            VERSIONS = "versions.properties",
            ALIASES = "aliases.properties",
            BOMS = "boms.properties",
            MODULE = "module.properties",
            METADATA = "metadata.properties",
            EXCLUSIONS = "exclusions.properties",
            OVERRIDES = "overrides.properties",
            ATTACHMENTS = "attachments.properties",
            DEPENDENCIES = "dependencies.properties";

    default BuildExecutorModule asModule(String name) {
        return new BuildExecutorModule() {
            @Override
            public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
                buildExecutor.addStep(name, BuildStep.this, inherited.sequencedKeySet());
            }

            @Override
            public Optional<String> resolve(String path) {
                return path.equals(name) ? Optional.of("") : Optional.empty();
            }
        };
    }

    default boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(BuildStepArgument::hasChanged);
    }

    CompletionStage<BuildStepResult> apply(Executor executor,
                                           BuildStepContext context,
                                           SequencedMap<String, BuildStepArgument> arguments) throws IOException;

    static void linkOrCopy(Path link, Path existing) throws IOException {
        try {
            Files.createLink(link, existing);
        } catch (UnsupportedOperationException | FileSystemException _) {
            Files.copy(existing, link);
        }
    }

    static Path resolveContained(Path base, String relative) throws IOException {
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw new IOException("Resolved path escapes " + base + ": " + relative);
        }
        return resolved;
    }

    static boolean underMetaInfVersions(Path path) {
        return path.getNameCount() >= 2
                && path.getName(0).toString().equals("META-INF")
                && path.getName(1).toString().equals("versions");
    }

    static boolean underBuildJenesis(Path path) {
        return path.getNameCount() >= 2
                && path.getName(0).toString().equals("META-INF")
                && path.getName(1).toString().equals("build.jenesis");
    }

    static Path locate(SequencedSet<Path> folders, String fileName) {
        for (Path folder : folders) {
            Path candidate = folder.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static List<String> selectByExtension(List<Path> folders, String extension) throws IOException {
        List<String> selected = new ArrayList<>();
        for (Path folder : folders) {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(extension)) {
                        selected.add(file.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        selected.sort(null);
        return selected;
    }

    static Path selectByName(List<Path> folders, String name) throws IOException {
        for (Path folder : folders) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, name + ".*")) {
                for (Path candidate : stream) {
                    if (Files.isRegularFile(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }
}
