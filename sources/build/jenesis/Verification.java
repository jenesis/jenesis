package build.jenesis;

import module java.base;

public enum Verification {

    NONE, DECLARED, STRICT;

    public static Verification fromProperty() {
        String property = System.getProperty("jenesis.dependency.signature", "none");
        try {
            return Verification.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.dependency.signature '" + property
                    + "', expected one of: none, declared, strict");
        }
    }
}
