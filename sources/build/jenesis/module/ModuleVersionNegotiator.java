package build.jenesis.module;

import module java.base;

@FunctionalInterface
public interface ModuleVersionNegotiator extends Serializable {

    CompiledVersion negotiate(String module, CompiledVersion recorded, CompiledVersion declared);

    record CompiledVersion(String version, String origin) implements Serializable {
    }

    @SuppressWarnings("unchecked")
    static <S extends Supplier<ModuleVersionNegotiator> & Serializable> S first() {
        return (S) (Supplier<ModuleVersionNegotiator> & Serializable)
                () -> (_, recorded, declared) -> recorded == null ? declared : recorded;
    }

    @SuppressWarnings("unchecked")
    static <S extends Supplier<ModuleVersionNegotiator> & Serializable> S ignore() {
        return (S) (Supplier<ModuleVersionNegotiator> & Serializable) () -> (_, _, _) -> null;
    }

    @SuppressWarnings("unchecked")
    static <S extends Supplier<ModuleVersionNegotiator> & Serializable> S fail() {
        return (S) (Supplier<ModuleVersionNegotiator> & Serializable) () -> (module, recorded, declared) -> {
            if (recorded == null) {
                return declared;
            } else if (!recorded.version().equals(declared.version())) {
                throw new IllegalStateException("Conflicting compiled versions for module " + module + ": "
                        + recorded.origin() + " requires " + recorded.version()
                        + ", " + declared.origin() + " requires " + declared.version());
            }
            return recorded;
        };
    }
}
