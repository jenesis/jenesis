package sample.adder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

public class Adder {

    static {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT), platform = (os.startsWith("windows")
                ? "windows"
                : os.startsWith("mac") ? "macos" : "linux") + "-" + System.getProperty("os.arch");
        String name = "native/" + platform + "/" + System.mapLibraryName("adder");
        try (InputStream in = Adder.class.getModule().getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("No native library for " + platform + " in the jar: " + name);
            }
            Path library = Files.createTempDirectory("adder").resolve(System.mapLibraryName("adder"));
            Files.copy(in, library, StandardCopyOption.REPLACE_EXISTING);
            System.load(library.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static native int add(int a, int b);
}
