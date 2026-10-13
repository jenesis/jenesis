package sample.natives;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Natives {

    public static String variant() throws IOException {
        String variant = read("variant.txt"), library = read("library.txt");
        return (variant == null ? "the main jar" : variant) + (library == null ? "" : ", with " + library);
    }

    private static String read(String name) throws IOException {
        try (InputStream in = Natives.class.getModule().getResourceAsStream(name)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }
}
