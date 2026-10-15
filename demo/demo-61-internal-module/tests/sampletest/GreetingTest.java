package sampletest;

import org.junit.jupiter.api.Test;
import sample.Greeting;
import sample.Sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreetingTest {

    @Test
    void greets() {
        assertTrue(Greeting.TEXT.startsWith("Hello"));
    }

    @Test
    void prints_the_greeting() {
        Sample.main(new String[0]);
    }

    @Test
    void runs_on_the_framework_the_project_declares() {
        assertEquals("platform", System.getProperty("demo.framework"));
    }
}
