package build;

import module java.base;
import build.jenesis.Project;
import build.jenesis.project.InferredMultiProjectAssembler;
import static build.jenesis.SequencedProperties.SYSTEM;

public class Demo {

    static void main(String[] args) throws Exception {
        expectFailure("a reference comparison of two strings, with ReferenceEquality promoted to an error",
                () -> Project.ofKeys(SYSTEM, Path.of(".")).build());
        System.out.println();
        wipe();
        Project.ofKeys(SYSTEM, Path.of("."))
                .assembler(InferredMultiProjectAssembler.ofKeys(SYSTEM).toolchain(toolchain ->
                        toolchain.compiler(compiler -> compiler.errorprone(null))))
                .build();
        System.out.println();
        System.out.println("Error Prone blocked the build; javac alone compiles the same sources.");
    }

    private static void expectFailure(String description, Build build) throws IOException {
        wipe();
        try {
            build.run();
        } catch (Throwable _) {
            System.out.println("[blocked] " + description);
            return;
        }
        throw new AssertionError("Build was expected to fail but succeeded: " + description);
    }

    private static void wipe() throws IOException {
        Path target = Path.of("target");
        if (!Files.isDirectory(target)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(target)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    @FunctionalInterface
    private interface Build {
        void run() throws Exception;
    }
}
