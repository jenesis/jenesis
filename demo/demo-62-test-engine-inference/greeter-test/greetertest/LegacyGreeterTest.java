package greetertest;

import org.junit.Test;
import sample.greeter.Greeter;

import static org.junit.Assert.assertEquals;

public class LegacyGreeterTest {

    @Test
    public void prefix_is_a_greeting() {
        assertEquals("Hello", new Greeter().prefix());
    }
}
