package build.jenesis;

import module java.base;
import java.util.jar.Attributes;

public enum PathPlacement {

    CLASS_PATH(false) {
        @Override
        public boolean test(Path path) {
            return false;
        }

        @Override
        public PathPlacement forModuleInfo(boolean moduleInfoPresent) {
            return moduleInfoPresent ? INFERRED : this;
        }
    },

    MODULE_PATH(true) {
        @Override
        public boolean test(Path path) {
            return true;
        }
    },

    INFERRED(true) {
        @Override
        public boolean test(Path path) {
            return moduleDescriptor(path) != null;
        }
    };

    public static final String ALIASES = "Jenesis-Aliases";

    private final boolean modular;

    PathPlacement(boolean modular) {
        this.modular = modular;
    }

    public boolean modular() {
        return modular;
    }

    public abstract boolean test(Path path) throws IOException;

    public static boolean automatic(Path file) {
        ModuleDescriptor descriptor = moduleDescriptor(file);
        return descriptor != null && descriptor.isAutomatic();
    }

    public PathPlacement forModuleInfo(boolean moduleInfoPresent) {
        return moduleInfoPresent ? this : CLASS_PATH;
    }

    public static Path declaration(Path jar) {
        return jar.resolveSibling(jar.getFileName() + ".module");
    }

    public static ModuleDescriptor moduleDescriptor(Path path) {
        try {
            if (Files.isDirectory(path)) {
                Path moduleInfo = path.resolve("module-info.class");
                if (!Files.exists(moduleInfo)) {
                    return null;
                }
                try (InputStream input = Files.newInputStream(moduleInfo)) {
                    return ModuleDescriptor.read(input);
                }
            }
            try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
                JarEntry moduleInfo = jar.getJarEntry("module-info.class");
                if (moduleInfo != null) {
                    try (InputStream input = jar.getInputStream(moduleInfo)) {
                        return ModuleDescriptor.read(input);
                    }
                }
                Manifest manifest = jar.getManifest();
                String automatic = manifest == null
                        ? null
                        : manifest.getMainAttributes().getValue("Automatic-Module-Name");
                if (automatic != null) {
                    return ModuleDescriptor.newAutomaticModule(automatic).build();
                }
            }
            Path declaration = declaration(path);
            return Files.isRegularFile(declaration)
                    ? ModuleDescriptor.newAutomaticModule(Files.readString(declaration).trim()).build()
                    : null;
        } catch (IOException | RuntimeException _) {
            return null;
        }
    }

    public static SequencedMap<String, String> aliases(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return new LinkedHashMap<>();
        }
        String declaration;
        try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
            Manifest manifest = jar.getManifest();
            declaration = manifest == null ? null : manifest.getMainAttributes().getValue(ALIASES);
        } catch (ZipException _) {
            return new LinkedHashMap<>();
        }
        return aliases(declaration, path.toString());
    }

    public static SequencedMap<String, String> aliases(String declaration, String origin) {
        SequencedMap<String, String> aliases = new LinkedHashMap<>();
        if (declaration == null || declaration.isBlank()) {
            return aliases;
        }
        for (String entry : declaration.split(",")) {
            String pair = entry.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String alias = equals < 0 ? "" : pair.substring(0, equals).trim();
            String target = equals < 0 ? "" : pair.substring(equals + 1).trim();
            if (alias.isEmpty() || target.isEmpty() || target.indexOf('/') < 1) {
                throw new IllegalArgumentException("Malformed " + ALIASES + " entry '"
                        + pair
                        + "' in "
                        + origin
                        + ": expected <module-name>=<groupId>/<artifactId>[/<type>[/<classifier>]]");
            }
            String previous = aliases.putIfAbsent(alias, target);
            if (previous != null && !previous.equals(target)) {
                throw new IllegalArgumentException("Conflicting " + ALIASES + " entries for "
                        + alias
                        + " in "
                        + origin
                        + ": "
                        + previous
                        + " and "
                        + target);
            }
        }
        return aliases;
    }

    public static void aliased(Path file, String alias, String origin) {
        Set<ModuleReference> found;
        try {
            found = ModuleFinder.of(file).findAll();
        } catch (FindException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalArgumentException(origin
                    + " cannot be aliased as "
                    + alias
                    + ": "
                    + cause.getMessage(), e);
        }
        ModuleDescriptor descriptor = found.size() == 1 ? found.iterator().next().descriptor() : null;
        if (descriptor == null || !descriptor.isAutomatic() || !descriptor.name().equals(alias)) {
            throw new IllegalArgumentException(origin
                    + " cannot be aliased as "
                    + alias
                    + ": "
                    + file.getFileName()
                    + (descriptor == null
                    ? " does not describe a single module"
                    : " describes the "
                    + (descriptor.isAutomatic() ? "automatic" : "named")
                    + " module "
                    + descriptor.name()));
        }
    }

    public static String mainClass(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
            Manifest manifest = jar.getManifest();
            return manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        }
    }
}
