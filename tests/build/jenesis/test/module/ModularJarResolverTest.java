package build.jenesis.test.module;

import module java.base;
import java.util.jar.Attributes;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.DependencyScope;
import module org.junit.jupiter.api;
import build.jenesis.PathPlacement;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.module.ModuleVersionNegotiator;
import build.jenesis.step.Dependencies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ModularJarResolverTest {

    @TempDir
    private Path jars;

    @AfterEach
    public void clearProperties() {
        System.clearProperty("jenesis.resolver.module");
    }

    @Test
    public void system_property_picks_each_propagation_mode() throws IOException {
        Map<String, ModularJarResolver> cases = Map.of(
                "first", new ModularJarResolver(false, null, ModuleVersionNegotiator.first()),
                "ignore", new ModularJarResolver(false, null, ModuleVersionNegotiator.ignore()),
                "fail", new ModularJarResolver(false, null, ModuleVersionNegotiator.fail()));
        for (Map.Entry<String, ModularJarResolver> entry : cases.entrySet()) {
            System.setProperty("jenesis.resolver.module", entry.getKey());
            assertThat(serialize(new ModularJarResolver(false)))
                    .as("mode=%s", entry.getKey())
                    .isEqualTo(serialize(entry.getValue()));
        }
    }

    @Test
    public void system_property_is_read_case_insensitively() throws IOException {
        System.setProperty("jenesis.resolver.module", "IgNoRe");
        assertThat(serialize(new ModularJarResolver(false)))
                .isEqualTo(serialize(new ModularJarResolver(false, null, ModuleVersionNegotiator.ignore())));
    }

    @Test
    public void system_property_rejects_an_unknown_mode() {
        System.setProperty("jenesis.resolver.module", "nonsense");
        assertThatThrownBy(() -> new ModularJarResolver(false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown jenesis.resolver.module 'nonsense',"
                        + " expected one of: first, ignore, fail");
    }

    @Test
    public void the_plain_resolver_negotiates_as_first() throws IOException {
        assertThat(serialize(new ModularJarResolver(false, null, ModuleVersionNegotiator.first())))
                .as("first() names what the shorter constructors already do")
                .isEqualTo(serialize(new ModularJarResolver(false)));
    }

    @Test
    public void a_selected_negotiator_changes_the_dependencies_step_hash() throws IOException {
        BuildStepHashFunction hashFunction = BuildStepHashFunction.ofSerializationDigest("SHA-256");
        assertThat(hashFunction.hash(new Dependencies(Map.of(), Map.of(
                "module", new ModularJarResolver(false, null, ModuleVersionNegotiator.ignore())))))
                .as("a resolution decided differently must not be served from the cache")
                .isNotEqualTo(hashFunction.hash(new Dependencies(Map.of(), Map.of(
                        "module", new ModularJarResolver(false)))));
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @Test
    public void reports_an_alias_a_module_layout_cannot_provide() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "module",
                Map.of("module", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toAliasingJar("root",
                                "toolkit.lib=org.example/plain-lib",
                                require("toolkit.lib", 0));
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .as("an alias names a Maven artifact, which a coordinate that is a module name cannot")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No module found for toolkit.lib"
                        + " - root aliases it to org.example/plain-lib,"
                        + " which pure module resolution cannot provide (use a Maven-backed layout)");
    }

    @Test
    public void can_parse_module_info() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", require("transitive", 0));
                        case "transitive" -> toJar("transitive", require("last", 0));
                        case "last" -> toJar("last");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root",
                "foo/transitive",
                "foo/last");
    }

    @Test
    public void emits_followed_and_not_followed_module_edges() throws IOException {
        Resolver.Resolution resolution = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", require("a", 0), require("b", 0));
                        case "a" -> toJar("a");
                        case "b" -> toJar("b", require("a", 0));
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE);
        List<String> followedEdges = new ArrayList<>();
        List<String> notFollowedEdges = new ArrayList<>();
        for (Resolver.Edge edge : resolution.edges()) {
            (edge.followed() ? followedEdges : notFollowedEdges).add(edge.parent() + " -> " + edge.coordinate());
        }
        assertThat(followedEdges).containsExactly(
                "null -> foo/root",
                "foo/root -> foo/a",
                "foo/root -> foo/b");
        assertThat(notFollowedEdges).containsExactly("foo/b -> foo/a");
    }

    @Test
    public void skips_non_transitive_static_requires_in_compile_scope() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", require("optional", ClassFile.ACC_STATIC_PHASE));
                        case "optional" -> toJar("optional");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root");
    }

    @Test
    public void includes_static_transitive_requires_in_compile_scope() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", require("propagated",
                                ClassFile.ACC_STATIC_PHASE | ClassFile.ACC_TRANSITIVE));
                        case "propagated" -> toJar("propagated");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root",
                "foo/propagated");
    }

    @Test
    public void emits_transitive_requires_in_sorted_order_independent_of_declaration_order() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root",
                                require("zeta", 0),
                                require("alpha", 0),
                                require("middle", 0));
                        case "alpha", "middle", "zeta" -> toJar(coordinate);
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root",
                "foo/alpha",
                "foo/middle",
                "foo/zeta");
    }

    @Test
    public void skips_static_transitive_requires_in_runtime_scope() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", require("propagated",
                                ClassFile.ACC_STATIC_PHASE | ClassFile.ACC_TRANSITIVE));
                        case "propagated" -> toJar("propagated");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.RUNTIME).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root");
    }

    private static ModuleRequireInfo require(String name, int flags) {
        return ModuleRequireInfo.of(ModuleDesc.of(name), flags, null);
    }

    private static ModuleRequireInfo require(String name, int flags, String compiledVersion) {
        return ModuleRequireInfo.of(ModuleDesc.of(name), flags, compiledVersion);
    }

    private RepositoryItem toJar(String module, ModuleRequireInfo... requires) throws IOException {
        return toJar(module, null, requires);
    }

    private RepositoryItem toAliasingJar(String module,
                                         String declaration,
                                         ModuleRequireInfo... requires) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(PathPlacement.ALIASES, declaration);
        Path file = Files.createTempFile(jars, module, ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            output.putNextEntry(new JarEntry("module-info.class"));
            output.write(ClassFile.of().buildModule(ModuleAttribute.of(
                    ModuleDesc.of(module),
                    builder -> {
                        builder.requires(ModuleRequireInfo.of(ModuleDesc.of("java.base"), 0, null));
                        for (ModuleRequireInfo require : requires) {
                            builder.requires(require);
                        }
                    })));
            output.closeEntry();
        }
        return RepositoryItem.ofFile(file);
    }

    private RepositoryItem toJar(String module, String version, ModuleRequireInfo... requires) throws IOException {
        Path file = Files.createTempFile(jars, module, ".jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(file))) {
            jarOutputStream.putNextEntry(new JarEntry("module-info.class"));
            jarOutputStream.write(ClassFile.of().buildModule(ModuleAttribute.of(
                    ModuleDesc.of(module),
                    builder -> {
                        if (version != null) {
                            builder.moduleVersion(version);
                        }
                        builder.requires(ModuleRequireInfo.of(ModuleDesc.of("java.base"), 0, null));
                        for (ModuleRequireInfo require : requires) {
                            builder.requires(require);
                        }
                    })));
            jarOutputStream.closeEntry();
        }
        return RepositoryItem.ofFile(file);
    }

    @Test
    public void uses_version_from_module_info_class() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.2.3");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root/1.2.3");
    }

    @Test
    public void rejects_module_info_version_with_path_traversal() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> Optional.of(toJar("root", "../evil"))),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe version")
                .hasMessageContaining("../evil");
    }

    @Test
    public void unversioned_module_info_yields_unversioned_coordinate() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", (String) null);
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root");
    }

    @Test
    public void rejects_module_with_unexpected_name() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("imposter");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("root")
                .hasMessageContaining("imposter");
    }

    @Test
    public void input_pin_drives_versioned_fetch() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root/9.9" -> toJar("root", "9.9");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", "9.9")),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root/9.9");
    }

    @Test
    public void input_pin_rejects_mismatched_module_info_version() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root/9.9" -> toJar("root", "1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", "9.9")),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9.9")
                .hasMessageContaining("1.0");
    }

    @Test
    public void input_pin_does_not_fall_back_to_unversioned_coordinate() {
        Map<String, String> fetched = new LinkedHashMap<>();
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", "9.9")),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No module found for root");
        assertThat(fetched).containsOnlyKeys("root/9.9");
    }

    @Test
    public void tolerates_version_mismatch_when_automatic_modules_are_allowed() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(true).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root/9.9" -> toJar("root", "1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", "9.9")),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root/9.9");
    }

    @Test
    public void input_pin_supplies_version_for_unversioned_module() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root/7.0" -> toJar("root", (String) null);
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", "7.0")),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root/7.0");
    }

    @Test
    public void transitive_carries_its_own_module_info_version() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0", require("transitive", 0));
                        case "transitive" -> toJar("transitive", "2.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/transitive/2.0");
    }

    @Test
    public void mixed_versioned_and_unversioned_transitives() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0",
                                require("alpha", 0),
                                require("beta", 0));
                        case "alpha" -> toJar("alpha", "2.0");
                        case "beta" -> toJar("beta", (String) null);
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/alpha/2.0",
                "foo/beta");
    }

    @Test
    public void propagates_compiled_version_from_parent_requires() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0", require("pinned", 0, "1.0"));
                        case "pinned/1.0" -> toJar("pinned", "1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).containsOnlyKeys("root", "pinned/1.0");
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/pinned/1.0");
    }

    @Test
    public void compiled_version_falls_back_to_bare_lookup_when_absent() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0", require("plain", 0));
                        case "plain" -> toJar("plain", "2.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).containsOnlyKeys("root", "plain");
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/plain/2.0");
    }

    @Test
    public void input_pin_overrides_compiled_version_propagation() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0", require("dep", 0, "1.0"));
                        case "dep/9.9" -> toJar("dep", "9.9");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("dep", "9.9")),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).containsOnlyKeys("root", "dep/9.9");
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/dep/9.9");
    }

    @Test
    public void compiled_version_propagates_through_chain() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0", require("middle", 0, "1.0"));
                        case "middle/1.0" -> toJar("middle", "1.0", require("deep", 0, "1.0"));
                        case "deep/1.0" -> toJar("deep", "1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).containsOnlyKeys("root", "middle/1.0", "deep/1.0");
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/middle/1.0",
                "foo/deep/1.0");
    }

    @Test
    public void compiled_version_first_seen_wins_when_two_parents_disagree() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0",
                                require("middle", 0, "1.0"),
                                require("shared", 0, "1.0"));
                        case "middle/1.0" -> toJar("middle", "1.0", require("shared", 0, "2.0"));
                        case "shared/1.0" -> toJar("shared", "1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).contains(Map.entry("shared/1.0", ""));
        assertThat(dependencies).containsKey("foo/shared/1.0");
    }

    @Test
    public void ignored_propagation_looks_every_unpinned_module_up_bare() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false,
                null,
                ModuleVersionNegotiator.ignore()).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0",
                                require("middle", 0, "1.0"),
                                require("shared", 0, "1.0"));
                        case "middle" -> toJar("middle", "3.0", require("shared", 0, "2.0"));
                        case "shared" -> toJar("shared", "4.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).containsOnlyKeys("root", "middle", "shared");
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/middle/3.0",
                "foo/shared/4.0");
    }

    @Test
    public void ignored_propagation_keeps_a_pin_ahead_of_an_inline_version() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false,
                null,
                ModuleVersionNegotiator.ignore()).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root/1.0" -> toJar("root", "1.0",
                                require("middle", 0, "1.0"),
                                require("shared", 0, "1.0"));
                        case "middle" -> toJar("middle", "3.0");
                        case "shared/9.9" -> toJar("shared", "9.9");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root/1.0", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("shared", "9.9")),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).containsOnlyKeys("root/1.0", "middle", "shared/9.9");
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/middle/3.0",
                "foo/shared/9.9");
    }

    @Test
    public void failing_propagation_reports_two_parents_that_disagree() {
        assertThatThrownBy(() -> new ModularJarResolver(false,
                null,
                ModuleVersionNegotiator.fail()).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0",
                                require("middle", 0, "1.0"),
                                require("shared", 0, "1.0"));
                        case "middle/1.0" -> toJar("middle", "1.0", require("shared", 0, "2.0"));
                        case "shared/1.0" -> toJar("shared", "1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Conflicting compiled versions for module shared:"
                        + " root requires 1.0, middle requires 2.0");
    }

    @Test
    public void failing_propagation_accepts_two_parents_that_agree() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false,
                null,
                ModuleVersionNegotiator.fail()).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0",
                                require("middle", 0, "1.0"),
                                require("shared", 0, "1.0"));
                        case "middle/1.0" -> toJar("middle", "1.0", require("shared", 0, "1.0"));
                        case "shared/1.0" -> toJar("shared", "1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/middle/1.0",
                "foo/shared/1.0");
    }

    @Test
    public void failing_propagation_leaves_a_pinned_module_to_its_pin() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false,
                null,
                ModuleVersionNegotiator.fail()).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0",
                                require("middle", 0, "1.0"),
                                require("shared", 0, "1.0"));
                        case "middle/1.0" -> toJar("middle", "1.0", require("shared", 0, "2.0"));
                        case "shared/9.9" -> toJar("shared", "9.9");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("shared", "9.9")),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/middle/1.0",
                "foo/shared/9.9");
    }

    @Test
    public void every_propagation_mode_rejects_an_unsafe_compiled_version() {
        for (ModularJarResolver resolver : List.of(
                new ModularJarResolver(false, null, ModuleVersionNegotiator.first()),
                new ModularJarResolver(false, null, ModuleVersionNegotiator.ignore()),
                new ModularJarResolver(false, null, ModuleVersionNegotiator.fail()))) {
            assertThatThrownBy(() -> resolver.dependencies(
                    Runnable::run,
                    "foo",
                    Map.of("foo", (_, coordinate) -> {
                        RepositoryItem item = switch (coordinate) {
                            case "root" -> toJar("root", "1.0", require("dep", 0, "../../secret"));
                            default -> null;
                        };
                        return Optional.ofNullable(item);
                    }),
                    new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                    new LinkedHashMap<>(),
                    DependencyScope.COMPILE))
                    .as("resolver=%s", resolver)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Module root declares an unsafe compiled version '../../secret' for dep");
        }
    }

    @Test
    public void every_propagation_mode_rejects_an_unsafe_compiled_version_of_a_pinned_module() {
        for (ModularJarResolver resolver : List.of(
                new ModularJarResolver(false, null, ModuleVersionNegotiator.first()),
                new ModularJarResolver(false, null, ModuleVersionNegotiator.ignore()),
                new ModularJarResolver(false, null, ModuleVersionNegotiator.fail()))) {
            assertThatThrownBy(() -> resolver.dependencies(
                    Runnable::run,
                    "foo",
                    Map.of("foo", (_, coordinate) -> {
                        RepositoryItem item = switch (coordinate) {
                            case "root" -> toJar("root", "1.0", require("dep", 0, "../../secret"));
                            default -> null;
                        };
                        return Optional.ofNullable(item);
                    }),
                    new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                    new LinkedHashMap<>(Map.of("dep", "9.9")),
                    DependencyScope.COMPILE))
                    .as("resolver=%s", resolver)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Module root declares an unsafe compiled version '../../secret' for dep");
        }
    }

    @Test
    public void input_pin_overrides_only_named_module_others_use_class_file() throws IOException {
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0", require("transitive", 0));
                        case "transitive/9.9" -> toJar("transitive", "9.9");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("transitive", "9.9")),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/transitive/9.9");
    }

    @Test
    public void rejects_propagated_compiled_version_with_path_traversal() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0", require("dep", 0, "../../secret"));
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("../../secret");
    }

    @Test
    public void picks_highest_versioned_module_info_under_runtime() throws IOException {
        int runtime = Runtime.version().feature();
        LinkedHashMap<Integer, String> versions = new LinkedHashMap<>();
        versions.put(runtime + 100, "9.9");
        versions.put(runtime, "2.0");
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toMultiReleaseJar("root", "1.0", versions);
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root/2.0");
    }

    @Test
    public void picks_root_when_only_future_versions_exist() throws IOException {
        int runtime = Runtime.version().feature();
        LinkedHashMap<Integer, String> versions = new LinkedHashMap<>();
        versions.put(runtime + 100, "9.9");
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toMultiReleaseJar("root", "1.0", versions);
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE).artifacts();
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root/1.0");
    }

    @Test
    public void classifier_pin_drives_classified_fetch() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root-windows-x86_64/9.9" -> toJar("root", "9.9");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", ":windows-x86_64:9.9")),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).containsOnlyKeys("root-windows-x86_64/9.9");
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root-windows-x86_64/9.9");
    }

    @Test
    public void classifier_pin_without_version_uses_module_info_version() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root-windows-x86_64" -> toJar("root", "1.2.3");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", ":windows-x86_64")),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).containsOnlyKeys("root-windows-x86_64");
        assertThat(dependencies.sequencedKeySet()).containsExactly("foo/root-windows-x86_64/1.2.3");
    }

    @Test
    public void classifier_pin_applies_to_transitive_module() throws IOException {
        Map<String, String> fetched = new LinkedHashMap<>();
        SequencedMap<String, Resolver.Resolved> dependencies = new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    fetched.put(coordinate, "");
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0", require("dep", 0));
                        case "dep-win/2.0" -> toJar("dep", "2.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("dep", ":win:2.0")),
                DependencyScope.COMPILE).artifacts();
        assertThat(fetched).containsOnlyKeys("root", "dep-win/2.0");
        assertThat(dependencies.sequencedKeySet()).containsExactly(
                "foo/root/1.0",
                "foo/dep-win/2.0");
    }

    @Test
    public void classifier_pin_rejects_mismatched_module_info_version() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root-win/9.9" -> toJar("root", "1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", ":win:9.9")),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("9.9")
                .hasMessageContaining("1.0");
    }

    @Test
    public void rejects_classifier_pin_without_classifier() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, _) -> Optional.empty()),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", ":")),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed classifier");
    }

    @Test
    public void rejects_classifier_pin_with_empty_version() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, _) -> Optional.empty()),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(Map.of("root", ":win:")),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed classifier");
    }

    @Test
    public void rejects_module_info_version_with_classifier_syntax() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", ":win:1.0");
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe")
                .hasMessageContaining(":win:1.0");
    }

    @Test
    public void rejects_propagated_compiled_version_with_classifier_syntax() {
        assertThatThrownBy(() -> new ModularJarResolver(false).dependencies(
                Runnable::run,
                "foo",
                Map.of("foo", (_, coordinate) -> {
                    RepositoryItem item = switch (coordinate) {
                        case "root" -> toJar("root", "1.0", require("dep", 0, ":win:1.0"));
                        default -> null;
                    };
                    return Optional.ofNullable(item);
                }),
                new LinkedHashMap<>(Map.of("root", Collections.emptyNavigableSet())),
                new LinkedHashMap<>(),
                DependencyScope.COMPILE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe compiled version")
                .hasMessageContaining(":win:1.0");
    }

    private RepositoryItem toMultiReleaseJar(String module,
                                             String rootVersion,
                                             Map<Integer, String> versions) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Multi-Release", "true");
        Path file = Files.createTempFile(jars, module, ".jar");
        try (JarOutputStream jarOutputStream = new JarOutputStream(Files.newOutputStream(file), manifest)) {
            jarOutputStream.putNextEntry(new JarEntry("module-info.class"));
            jarOutputStream.write(buildModuleInfo(module, rootVersion));
            jarOutputStream.closeEntry();
            for (Map.Entry<Integer, String> entry : versions.entrySet()) {
                jarOutputStream.putNextEntry(new JarEntry(
                        "META-INF/versions/" + entry.getKey() + "/module-info.class"));
                jarOutputStream.write(buildModuleInfo(module, entry.getValue()));
                jarOutputStream.closeEntry();
            }
        }
        return RepositoryItem.ofFile(file);
    }

    private static byte[] buildModuleInfo(String module, String version) {
        return ClassFile.of().buildModule(ModuleAttribute.of(
                ModuleDesc.of(module),
                builder -> {
                    if (version != null) {
                        builder.moduleVersion(version);
                    }
                    builder.requires(ModuleRequireInfo.of(ModuleDesc.of("java.base"), 0, null));
                }));
    }

}
