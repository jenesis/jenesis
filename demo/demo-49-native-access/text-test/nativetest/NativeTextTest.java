package nativetest;

import demo.natives.text.NativeText;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeTextTest {

    @Test
    void counts_the_bytes_of_a_string_through_strlen() {
        assertEquals(5, NativeText.length("hello"));
    }

    @Test
    void the_test_run_grants_the_library_native_access() {
        assertTrue(NativeText.granted());
    }
}
