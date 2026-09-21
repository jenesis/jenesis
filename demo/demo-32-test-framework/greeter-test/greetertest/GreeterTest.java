package greetertest;

import org.junit.jupiter.api.Test;
import sample.greeter.Greeter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GreeterTest {

    @Test
    void prefix_is_a_greeting() {
        assertEquals("Hello", new Greeter().prefix());
    }
}
