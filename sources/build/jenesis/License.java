package build.jenesis;

import module java.base;

public record License(String id, String category, String name, String url) implements Serializable {

    public String label() {
        return id != null ? id : name != null ? name : url;
    }
}
