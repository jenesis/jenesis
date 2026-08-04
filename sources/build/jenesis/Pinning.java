package build.jenesis;

import module java.base;

public enum Pinning {

    STRICT,

    VERSIONS,

    IGNORE;

    public static Pinning fromProperty() {
        String property = System.getProperty("jenesis.dependency.pin");
        if (property == null) {
            return null;
        }
        try {
            return Pinning.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.dependency.pin '" + property
                    + "', expected one of: strict, versions, ignore");
        }
    }
}
