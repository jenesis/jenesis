package sample;

import org.assertj.core.api.Assertions;

public class Sample {

    public static void main(String[] args) {
        Assertions.assertThat("verified").isNotEmpty();
    }
}
