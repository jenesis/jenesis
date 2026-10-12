package sampletest;

import org.junit.jupiter.api.Test;
import sample.Sample;
import sample.pure.Pure;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleTest {

    @Test
    void greeting_is_loaded_from_the_packaged_resource() {
        String greeting = new Sample().greet();
        assertTrue(greeting.contains("packaged resource"), greeting);
        assertTrue(greeting.contains("Kotlin"), greeting);
    }

    @Test
    void the_kotlin_class_of_the_running_release_is_loaded_from_the_multi_release_jar() {
        String message = new Pure().message();
        assertTrue(message.endsWith(Runtime.version().feature() >= 25 ? "in its Java 25 version" : "present"), message);
    }
}
