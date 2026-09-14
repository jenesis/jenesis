package build.jenesis;

import module java.base;

public enum KeyExpiry {

    IGNORED, SIGNING, CURRENT;

    public static KeyExpiry fromProperty() {
        String property = System.getProperty("jenesis.openpgp.expiry", "signing");
        try {
            return KeyExpiry.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.openpgp.expiry '" + property
                    + "', expected one of: ignored, signing, current");
        }
    }
}
