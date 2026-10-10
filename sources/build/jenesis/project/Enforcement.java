package build.jenesis.project;

import module java.base;
import build.jenesis.Environment;

public enum Enforcement {

    IGNORE, WARN, STRICT;

    public static Enforcement ofEnvironment(Environment environment, String key) {
        String value = environment.value(key, "warn");
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis." + key + " '" + value
                    + "', expected one of: ignore, warn, strict");
        }
    }
}
