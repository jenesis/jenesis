package agents;

import demo.agents.Application;
import demo.agents.Greeter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationTest {

    @Test
    void delegates_to_the_greeter() {
        Greeter greeter = mock(Greeter.class);
        when(greeter.greet("Ada")).thenReturn("Hi, Ada");

        assertEquals("Hi, Ada", new Application(greeter).run("Ada"));

        verify(greeter).greet("Ada");
    }
}
