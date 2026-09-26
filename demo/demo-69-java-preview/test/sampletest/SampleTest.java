package sampletest;

import org.junit.jupiter.api.Test;
import sample.Sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleTest {

    @Test
    void matches_a_long_that_fits_an_int() {
        assertEquals("42 fits an int (42)", Sample.describe(42));
    }

    @Test
    void matches_a_long_that_does_not_fit_an_int() {
        assertEquals("4294967296 needs a long (4294967296)", Sample.describe(4294967296L));
    }
}
