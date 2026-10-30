package calctest;

import calc.Adder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdderTest {

    @Test
    void adds_two_numbers() throws Exception {
        Path ran = Files.createDirectories(Path.of("target", "ran"));
        Files.writeString(ran.resolve("AdderTest"), "");
        Files.writeString(ran.resolve("order"),
                "AdderTest\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        assertEquals(5, new Adder().add(2, 3));
    }
}
