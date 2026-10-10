package sample.greeter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreeterTest {

    @Test
    @Tag("slow")
    void prefix_is_a_greeting() {
        assertTrue(new Greeter().prefix().startsWith("Hello"));
    }

    @Test
    void prefix_is_not_blank() {
        assertFalse(new Greeter().prefix().isBlank());
    }

    @Test
    void runs_in_the_folder_of_its_module() throws IOException {
        assertTrue(Files.readString(Path.of("pom.xml")).contains("<artifactId>greeter</artifactId>"));
    }
}
