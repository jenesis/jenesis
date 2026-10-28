package build.jenesis.maven;

import module java.base;
import build.jenesis.DependencyScope;

public enum MavenDependencyScope {

    COMPILE, RUNTIME, PROVIDED, TEST, SYSTEM, IMPORT;

    boolean reduces(MavenDependencyScope scope) {
        return scope != null && ordinal() > scope.ordinal();
    }

    boolean places(DependencyScope scope) {
        return switch (this) {
            case COMPILE -> true;
            case PROVIDED, SYSTEM -> scope == DependencyScope.COMPILE;
            case RUNTIME -> scope == DependencyScope.RUNTIME;
            case TEST, IMPORT -> false;
        };
    }

    static MavenDependencyScope of(Set<DependencyScope> scopes) {
        return scopes.contains(DependencyScope.COMPILE)
                ? scopes.contains(DependencyScope.RUNTIME) ? COMPILE : PROVIDED
                : scopes.contains(DependencyScope.RUNTIME) ? RUNTIME : TEST;
    }

    static MavenDependencyScope of(String scope) {
        return switch (scope == null ? "" : scope.trim()) {
            case "compile", "" -> COMPILE;
            case "provided" -> PROVIDED;
            case "runtime" -> RUNTIME;
            case "test" -> TEST;
            case "system" -> SYSTEM;
            case "import" -> IMPORT;
            default -> throw new IllegalArgumentException("Unknown Maven dependency scope: " + scope);
        };
    }
}
