package greetertest;

import greetertesting.Greetings;
import org.junit.jupiter.api.Test;
import sample.greeter.Greeter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreeterTest {

    @Test
    void prefix_is_a_greeting() {
        assertTrue(new Greeter().prefix().startsWith("hello"));
    }

    @Test
    void prefix_is_not_blank() {
        assertFalse(new Greeter().prefix().isBlank());
    }

    @Test
    void reads_only_the_environment_it_declares() {
        assertNull(System.getenv("DEMO_SECRET"));
        System.out.println("greeting=" + System.getenv("DEMO_GREETING"));
    }

    @Test
    void prefix_is_loaded_from_the_packaged_resource() {
        assertTrue(Greetings.isGreeting(new Greeter().prefix()));
    }
}
