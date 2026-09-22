package build.jenesis;

import module java.base;

public enum Verification {

    NONE, DECLARED, STRICT;

    public static Verification ofKeys(Function<String, String> keys) {
        String property = SequencedProperties.getProperty(keys, "dependency.signature", "none");
        try {
            return Verification.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.dependency.signature '" + property
                    + "', expected one of: none, declared, strict");
        }
    }
}
