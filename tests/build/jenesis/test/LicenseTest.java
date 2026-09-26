package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.License;

import static org.assertj.core.api.Assertions.assertThat;

public class LicenseTest {

    private static final Map<String, String> ALIASES = Map.of(
            "the apache software license, version 2.0", "Apache-2.0",
            "opensource.org/licenses/mit", "MIT");

    @Test
    public void identifies_a_licence_by_its_name_regardless_of_case() {
        assertThat(new License(null, null, "The Apache Software License, Version 2.0", null).identified(ALIASES).id())
                .isEqualTo("Apache-2.0");
    }

    @Test
    public void identifies_a_licence_by_its_url_without_scheme_or_extension() {
        assertThat(new License(null, null, "Some name", "https://www.opensource.org/licenses/MIT.html").identified(ALIASES).id())
                .isEqualTo("MIT");
    }

    @Test
    public void keeps_an_identifier_it_carries_and_leaves_an_unknown_licence_unidentified() {
        assertThat(new License("EPL-2.0", null, "The Apache Software License, Version 2.0", null).identified(ALIASES).id())
                .isEqualTo("EPL-2.0");
        assertThat(new License(null, null, "A licence of our own", null).identified(ALIASES).id()).isNull();
    }
}
