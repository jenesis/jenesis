package sampletest

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import sample.shout

class ShoutTest {

    @Test
    fun an_internal_function_is_visible_to_the_tests() {
        assertEquals("HELLO", shout("hello"))
    }
}
