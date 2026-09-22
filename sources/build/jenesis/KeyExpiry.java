package build.jenesis;

import module java.base;

public enum KeyExpiry {

    IGNORED, SIGNING, CURRENT;

    public static KeyExpiry ofKeys(Function<String, String> keys) {
        String property = SequencedProperties.getProperty(keys, "openpgp.expiry", "signing");
        try {
            return KeyExpiry.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.openpgp.expiry '" + property
                    + "', expected one of: ignored, signing, current");
        }
    }
}
