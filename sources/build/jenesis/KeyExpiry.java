package build.jenesis;

import module java.base;

public enum KeyExpiry {

    IGNORED, SIGNING, CURRENT;

    public static KeyExpiry fromProperty() {
        String property = System.getProperty("jenesis.signature.expiry", "signing");
        try {
            return KeyExpiry.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.signature.expiry '" + property
                    + "', expected one of: ignored, signing, current");
        }
    }
}
