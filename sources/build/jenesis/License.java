package build.jenesis;

import module java.base;

public record License(String id, String category, String name, String url) implements Serializable {

    public String label() {
        return id != null ? id : name != null ? name : url;
    }

    public License identified(Map<String, String> aliases) {
        if (id != null) {
            return this;
        }
        String identified = name == null ? null : aliases.get(name.toLowerCase(Locale.ROOT).trim());
        if (identified == null && url != null) {
            identified = aliases.get(url.toLowerCase(Locale.ROOT).trim()
                    .replaceFirst("^https?://(www\\.)?", "")
                    .replaceFirst("\\.(txt|html?|php|md)$", "")
                    .replaceFirst("/+$", ""));
        }
        return identified == null ? this : new License(identified, category, name, url);
    }
}
