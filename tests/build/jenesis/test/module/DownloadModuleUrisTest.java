package build.jenesis.test.module;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildStepContext;
import build.jenesis.module.DownloadModuleUris;

import static org.assertj.core.api.Assertions.assertThat;

public class DownloadModuleUrisTest {

    @TempDir
    private Path root;

    @Test
    public void writes_prefixed_module_uris_from_the_source() throws IOException {
        Path next = Files.createDirectory(root.resolve("next"));
        Path source = Files.writeString(root.resolve("modules.properties"),
                "foo=https://example.test/foo.jar\nbar=https://example.test/bar.jar\n");
        URI location = source.toUri();
        DownloadModuleUris step = new DownloadModuleUris("module",
                (Supplier<List<URI>> & Serializable) () -> List.of(location));

        step.apply(Runnable::run,
                        new BuildStepContext(root.resolve("previous"), next, root.resolve("supplement")),
                        new LinkedHashMap<>())
                .toCompletableFuture().join();

        assertThat(next.resolve(DownloadModuleUris.URIS)).hasContent(
                "module/foo=https://example.test/foo.jar" + System.lineSeparator()
                        + "module/bar=https://example.test/bar.jar" + System.lineSeparator());
    }
}
