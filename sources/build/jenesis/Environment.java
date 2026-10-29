package build.jenesis;

import module java.base;

public record Environment(Map<String, String> keys, Consumer<String> out, Consumer<String> err, Path root) {

    public static final Environment NONE = new Environment(Map.of());

    public Environment {
        keys = Map.copyOf(keys);
    }

    public Environment(Map<String, String> keys) {
        this(keys, System.out::println, System.err::println, null);
    }

    public Environment keys(Map<String, String> keys) {
        return new Environment(keys, out, err, root);
    }

    public Environment out(Consumer<String> out) {
        return new Environment(keys, out, err, root);
    }

    public Environment err(Consumer<String> err) {
        return new Environment(keys, out, err, root);
    }

    public Environment root(Path root) {
        return new Environment(keys, out, err, root);
    }

    public String getProperty(String key, Make.Setting setting) {
        return Make.setting(keys, key, unconfined(key, setting));
    }

    public String getProperty(String key, String defaultValue, Make.Setting setting) {
        String value = getProperty(key, setting);
        return value == null ? defaultValue : value;
    }

    public String value(String key, Make.Setting setting) {
        return trimmed(getProperty(key, setting));
    }

    public String value(String key, String defaultValue, Make.Setting setting) {
        String value = value(key, setting);
        return value == null ? defaultValue : value;
    }

    public Path path(String key, Make.Setting setting) {
        return path(key, null, setting);
    }

    public Path path(String key, String defaultValue, Make.Setting setting) {
        return Make.path(keys, root, key, defaultValue, setting);
    }

    public Path located(String key, String part, Make.Setting setting) {
        return Make.located(keys, root, key, part, setting);
    }

    public boolean provided(String key) {
        return Make.listed(keys, "make.provided", key);
    }

    public boolean flag(String key, Make.Setting setting) {
        return flag(key, false, setting);
    }

    public boolean flag(String key, boolean defaultValue, Make.Setting setting) {
        Boolean value = flagOrNull(key, setting);
        return value == null ? defaultValue : value;
    }

    public Boolean flagOrNull(String key, Make.Setting setting) {
        return Make.parsed("jenesis." + key, getProperty(key, setting));
    }

    public int number(String key, int defaultValue, Make.Setting setting) {
        Integer value = numberOrNull(key, setting);
        return value == null ? defaultValue : value;
    }

    public long number(String key, long defaultValue, Make.Setting setting) {
        Long value = longOrNull(key, setting);
        return value == null ? defaultValue : value;
    }

    public Integer numberOrNull(String key, Make.Setting setting) {
        String value = value(key, setting);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Malformed value for jenesis." + key + ": '" + value
                    + "' (expected a whole number)");
        }
    }

    public Long longOrNull(String key, Make.Setting setting) {
        String value = value(key, setting);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Malformed value for jenesis." + key + ": '" + value
                    + "' (expected a whole number)");
        }
    }

    public List<String> entries(String key, Make.Setting setting) {
        String value = value(key, setting);
        if (value == null) {
            return null;
        }
        List<String> entries = new ArrayList<>();
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    public List<String> words(String key, Make.Setting setting) {
        String value = value(key, setting);
        return value == null ? List.of() : List.of(value.split("\\s+"));
    }

    public String variable(String origin, String value) {
        if (!value.startsWith("@") || value.startsWith("@@")) {
            return value.startsWith("@") ? value.substring(1) : value;
        }
        int slash = value.indexOf('/');
        String name = value.substring(1, slash == -1 ? value.length() : slash);
        String resolved = value("variable." + name,
                slash == -1 ? null : value.substring(slash + 1),
                Make.Setting.PROJECT);
        if (resolved == null) {
            throw new IllegalArgumentException(origin + " to " + value + ", but jenesis.variable." + name
                    + " is not set - set it, or give a default as @" + name + "/<default>");
        }
        return resolved;
    }

    private static Make.Setting unconfined(String key, Make.Setting setting) {
        if (setting == Make.Setting.CONFINED) {
            throw new IllegalArgumentException("jenesis." + key + " is read as a value, but only a location can be"
                    + " confined to the project - read it with path or located");
        }
        return setting;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
