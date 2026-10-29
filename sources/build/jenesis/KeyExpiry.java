package build.jenesis;

import module java.base;
import build.jenesis.Make.Setting;

public enum KeyExpiry {

    IGNORED, SIGNING, CURRENT;

    public static KeyExpiry ofEnvironment(Environment environment) {
        String property = environment.getProperty("openpgp.expiry", "signing", Setting.PROJECT);
        try {
            return KeyExpiry.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.openpgp.expiry '" + property
                    + "', expected one of: ignored, signing, current");
        }
    }
}
