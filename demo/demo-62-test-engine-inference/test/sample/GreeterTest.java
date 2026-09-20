package sample;

import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

class GreeterTest {

    @Test
    void prefix_is_a_greeting() {
        assertThat(new Greeter().prefix()).isEqualTo("Hello");
    }
}
