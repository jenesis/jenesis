package demo.layers.library.test;

import demo.layers.library.Library;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryTest {

    @Test
    void reports_from_its_own_isolated_jackson() {
        String report = new Library().report();
        assertTrue(report.contains("2.15.4"), report);
    }
}
