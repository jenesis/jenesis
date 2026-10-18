package suite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ModeTest {

    @Test
    void runs_in_the_mode_its_module_sets() {
        String mode = System.getProperty("demo.mode");
        assertNotNull(mode, "a module that scans this suite sets demo.mode in its process-test.properties");
        System.out.println("ModeTest ran in " + mode + " mode");
    }
}
