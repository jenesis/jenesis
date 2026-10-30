package calctest;

import calc.Subtractor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubtractorTest {

    @Test
    void subtracts_two_numbers() throws Exception {
        Path ran = Files.createDirectories(Path.of("target", "ran"));
        Files.writeString(ran.resolve("SubtractorTest"), "");
        Files.writeString(ran.resolve("order"),
                "SubtractorTest\n",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        assertEquals(2, new Subtractor().subtract(5, 3));
    }
}
