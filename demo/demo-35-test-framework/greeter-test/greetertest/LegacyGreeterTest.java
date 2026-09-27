package greetertest;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import sample.greeter.Greeter;

import static org.junit.Assert.assertEquals;

@Category(Legacy.class)
public class LegacyGreeterTest {

    @Test
    public void prefix_is_a_greeting() {
        assertEquals("Hello", new Greeter().prefix());
    }
}
