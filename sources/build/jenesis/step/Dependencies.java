package build.jenesis.step;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.DependencyScope;
import build.jenesis.License;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

import static java.util.Objects.requireNonNull;

public class Dependencies implements BuildStep {

    public static final String SPDX = "spdx.properties";
    public static final String GRAPH = "graph.properties";
    public static final String LICENSES = "licenses.properties";
    public static final String ALIASED = "aliased.properties";
    public static final String INTERNAL = "internal.properties";
    public static final String RESOLVED = "resolved/";
    public static final String MODULAR = "modular.properties";
    public static final String MODULAR_PATH = "modular/";

    private final transient Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Pinning pinning;
    private final String group;

    public Dependencies(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(repositories, resolvers, null, null);
    }

    private Dependencies(Map<String, Repository> repositories,
                         Map<String, Resolver> resolvers,
                         Pinning pinning,
                         String group) {
        this.repositories = repositories;
        this.resolvers = new LinkedHashMap<>(resolvers);
        this.pinning = pinning;
        this.group = group;
    }

    public Dependencies pinning(Pinning pinning) {
        return new Dependencies(repositories, resolvers, pinning, group);
    }

    public Dependencies group(String group) {
        return new Dependencies(repositories, resolvers, pinning, group);
    }

    public static SequencedMap<String, String> bomEntries(SequencedProperties properties, String group) {
        SequencedMap<String, String> entries = new LinkedHashMap<>();
        Resolver.bomEntries(properties).forEach((key, value) -> entries.put(group + "/" + key, value));
        return entries;
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(REQUIRES),
                Path.of(VERSIONS),
                Path.of(ALIASES),
                Path.of(BOMS),
                Path.of(EXCLUSIONS),
                Path.of(OVERRIDES),
                Path.of(SPDX)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        boolean pinned = pinning != Pinning.IGNORE;
        Map<String, String> aliases = new LinkedHashMap<>(DEFAULT_ALIASES);
        Map<String, String> categories = new LinkedHashMap<>(DEFAULT_CATEGORIES);
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path file = argument.folder().resolve(SPDX);
            if (Files.isRegularFile(file)) {
                SequencedProperties properties = SequencedProperties.ofFiles(file);
                for (String key : properties.stringPropertyNames()) {
                    String value = properties.getProperty(key).trim();
                    if (key.startsWith("alias/")) {
                        String name = key.substring("alias/".length()).toLowerCase(Locale.ROOT).trim();
                        if (value.isEmpty()) {
                            aliases.remove(name);
                        } else {
                            aliases.put(name, value);
                        }
                    } else if (key.startsWith("category/")) {
                        String identifier = key.substring("category/".length()).trim();
                        if (value.isEmpty()) {
                            categories.remove(identifier);
                        } else {
                            categories.put(identifier, value);
                        }
                    } else {
                        throw new IllegalArgumentException("Expected key to be prefixed: " + key);
                    }
                }
            }
        }
        SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>>> requires = new LinkedHashMap<>();
        SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>> versions = new LinkedHashMap<>();
        SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>> moduleAliases = new LinkedHashMap<>();
        SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>> moduleOverrides = new LinkedHashMap<>();
        SequencedMap<String, String> bomTokens = new LinkedHashMap<>();
        SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedSet<String>>>>> exclusions = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path requiresFile = argument.folder().resolve(REQUIRES);
            if (Files.exists(requiresFile)) {
                SequencedProperties properties = SequencedProperties.ofFiles(requiresFile);
                for (String key : properties.stringPropertyNames()) {
                    String[] parts = split(key);
                    if (parts == null) {
                        continue;
                    }
                    requires.computeIfAbsent(parts[0], _ -> new LinkedHashMap<>())
                            .computeIfAbsent(parts[1], _ -> new LinkedHashMap<>())
                            .computeIfAbsent(parts[2], _ -> new LinkedHashMap<>())
                            .merge(parts[3], properties.getProperty(key), (left, right) -> left.isEmpty() ? right : left);
                }
            }
            Path versionsFile = argument.folder().resolve(VERSIONS);
            if (Files.exists(versionsFile)) {
                SequencedProperties properties = SequencedProperties.ofFiles(versionsFile);
                for (String key : properties.stringPropertyNames()) {
                    int first = key.indexOf('/');
                    int second = first < 1 ? -1 : key.indexOf('/', first + 1);
                    if (first < 1 || second <= first || second == key.length() - 1) {
                        throw new IllegalArgumentException("Malformed version pin '"
                                + key
                                + "' in "
                                + versionsFile
                                + ": expected <group>/<repository>/<coordinate>");
                    }
                    versions.computeIfAbsent(key.substring(0, first), _ -> new LinkedHashMap<>())
                            .computeIfAbsent(key.substring(first + 1, second), _ -> new LinkedHashMap<>())
                            .putIfAbsent(key.substring(second + 1), properties.getProperty(key));
                }
            }
            Path aliasesFile = argument.folder().resolve(ALIASES);
            if (Files.exists(aliasesFile)) {
                SequencedProperties properties = SequencedProperties.ofFiles(aliasesFile);
                for (String key : properties.stringPropertyNames()) {
                    int first = key.indexOf('/');
                    int second = first < 1 ? -1 : key.indexOf('/', first + 1);
                    if (first < 1 || second <= first || second == key.length() - 1) {
                        throw new IllegalArgumentException("Malformed module alias '"
                                + key
                                + "' in "
                                + aliasesFile
                                + ": expected <group>/<repository>/<module-name>");
                    }
                    moduleAliases.computeIfAbsent(key.substring(0, first), _ -> new LinkedHashMap<>())
                            .computeIfAbsent(key.substring(first + 1, second), _ -> new LinkedHashMap<>())
                            .putIfAbsent(key.substring(second + 1), properties.getProperty(key));
                }
            }
            Path overridesFile = argument.folder().resolve(OVERRIDES);
            if (Files.exists(overridesFile)) {
                SequencedProperties properties = SequencedProperties.ofFiles(overridesFile);
                for (String key : properties.stringPropertyNames()) {
                    int first = key.indexOf('/');
                    int second = first < 1 ? -1 : key.indexOf('/', first + 1);
                    if (first < 1 || second <= first || second == key.length() - 1) {
                        throw new IllegalArgumentException("Malformed module override '"
                                + key
                                + "' in "
                                + overridesFile
                                + ": expected <group>/<repository>/<module-name>");
                    }
                    moduleOverrides.computeIfAbsent(key.substring(0, first), _ -> new LinkedHashMap<>())
                            .computeIfAbsent(key.substring(first + 1, second), _ -> new LinkedHashMap<>())
                            .putIfAbsent(key.substring(second + 1), properties.getProperty(key));
                }
            }
            Path bomsFile = argument.folder().resolve(BOMS);
            if (Files.exists(bomsFile)) {
                SequencedProperties properties = SequencedProperties.ofFiles(bomsFile);
                for (String key : properties.stringPropertyNames()) {
                    String reference = key.startsWith("bom/")
                            ? key.substring(4)
                            : key.startsWith("entry/") ? key.substring(6) : null;
                    int first = reference == null ? -1 : reference.indexOf('/');
                    int second = first < 1 ? -1 : reference.indexOf('/', first + 1);
                    if (first < 1 || second <= first || second == reference.length() - 1) {
                        throw new IllegalArgumentException("Malformed BOM reference '"
                                + key
                                + "' in "
                                + bomsFile
                                + ": expected bom/<group>/<repository>/<coordinate>"
                                + " or entry/<group>/<repository>/<coordinate>");
                    }
                    bomTokens.putIfAbsent(key, properties.getProperty(key));
                }
            }
            Path exclusionsFile = argument.folder().resolve(EXCLUSIONS);
            if (Files.exists(exclusionsFile)) {
                SequencedProperties properties = SequencedProperties.ofFiles(exclusionsFile);
                for (String key : properties.stringPropertyNames()) {
                    String[] parts = split(key);
                    if (parts == null) {
                        continue;
                    }
                    SequencedSet<String> excludes = new LinkedHashSet<>();
                    String value = properties.getProperty(key);
                    if (!value.isEmpty()) {
                        excludes.addAll(Arrays.asList(value.split(",")));
                    }
                    exclusions.computeIfAbsent(parts[0], _ -> new LinkedHashMap<>())
                            .computeIfAbsent(parts[1], _ -> new LinkedHashMap<>())
                            .computeIfAbsent(parts[2], _ -> new LinkedHashMap<>())
                            .put(parts[3], excludes);
                }
            }
        }
        if (group != null) {
            requires.keySet().retainAll(Set.of(group));
            moduleAliases.keySet().retainAll(Set.of(group));
            moduleOverrides.keySet().retainAll(Set.of(group));
            bomTokens.keySet().removeIf(token -> {
                int first = token.indexOf('/'), second = token.indexOf('/', first + 1);
                return second < 0 || !group.equals(token.substring(first + 1, second));
            });
            exclusions.keySet().retainAll(Set.of(group));
            versions.keySet().retainAll(Set.of(group));
        }
        Path libs = Files.createDirectories(context.next().resolve(RESOLVED));
        SequencedMap<String, Path> previousArtifacts = new LinkedHashMap<>();
        SequencedSet<String> previousInternal = new LinkedHashSet<>();
        if (context.previous() != null) {
            Path previousInternalFile = context.previous().resolve(INTERNAL);
            if (Files.exists(previousInternalFile)) {
                for (String dependency : SequencedProperties.ofFiles(previousInternalFile).stringPropertyNames()) {
                    previousInternal.add(dependency.substring(dependency.indexOf('/') + 1));
                }
            }
            Path previousIndex = context.previous().resolve(DEPENDENCIES);
            if (Files.exists(previousIndex)) {
                SequencedProperties.ofFiles(previousIndex).forEachProperty((key, value) -> {
                    String[] parts = split(key);
                    if (parts == null) {
                        return;
                    }
                    int space = value.indexOf(' ');
                    Path file = context.previous()
                            .resolve(space < 0 ? value : value.substring(0, space))
                            .normalize();
                    if (Files.exists(file) && !previousInternal.contains(parts[3])) {
                        previousArtifacts.putIfAbsent(parts[3], file);
                    }
                });
            }
        }
        Map<String, Repository> wrapped = new LinkedHashMap<>();
        repositories.forEach((name, repository) -> {
            Repository effective = repository;
            if (!previousArtifacts.isEmpty()) {
                effective = effective.prepend((_, coordinate) -> Optional
                        .ofNullable(previousArtifacts.get(coordinate))
                        .map(RepositoryItem::ofFile));
            }
            wrapped.put(name, effective.materialized(libs));
        });
        SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>> managed = new LinkedHashMap<>();
        if (!bomTokens.isEmpty()) {
            SequencedMap<String, String> merged = new LinkedHashMap<>();
            SequencedMap<String, String> covering = new LinkedHashMap<>();
            SequencedProperties resolvedBoms = new SequencedProperties();
            for (Map.Entry<String, String> token : bomTokens.entrySet()) {
                if (token.getKey().startsWith("entry/")) {
                    String key = token.getKey().substring(6);
                    merged.put(key, token.getValue());
                    covering.put(key, token.getValue());
                    continue;
                }
                String reference = token.getKey().substring(4);
                int first = reference.indexOf('/');
                int second = reference.indexOf('/', first + 1);
                String group = reference.substring(0, first);
                String repo = reference.substring(first + 1, second);
                String coordinate = reference.substring(second + 1);
                String value = token.getValue();
                int space = value.indexOf(' ');
                String version = space < 0 ? value : value.substring(0, space);
                String checksum = space < 0 ? "" : value.substring(space + 1).trim();
                if (wrapped.get(Resolver.base(repo)) == null) {
                    throw new IllegalArgumentException("Unknown repository for BOM: " + reference);
                }
                boolean verify = pinning != Pinning.VERSIONS && pinning != Pinning.IGNORE;
                Resolver.Bom bom;
                try {
                    bom = resolvers.getOrDefault(Resolver.base(repo), Resolver.identity()).bom(executor,
                            repo,
                            wrapped,
                            coordinate,
                            version,
                            verify ? checksum : null,
                            pinning == Pinning.IGNORE);
                } catch (RuntimeException e) {
                    throw new IllegalStateException("Failed to fetch BOM " + reference, e);
                }
                if (pinning == Pinning.STRICT && bom.verifiable() && checksum.isEmpty() && !bom.internal()) {
                    throw new IllegalStateException("No checksum pinned for BOM "
                            + reference
                            + " (strict pinning is enabled)");
                }
                if (!version.isEmpty() && !bom.version().isEmpty()) {
                    if (bom.verifiable()) {
                        resolvedBoms.setProperty("bom/" + reference + "/" + bom.version(),
                                context.next().toAbsolutePath().relativize(bom.file().toAbsolutePath())
                                        .toString().replace(File.separatorChar, '/'));
                    } else {
                        resolvedBoms.setProperty("version/" + reference, bom.version());
                    }
                }
                for (Map.Entry<String, String> entry : bom.entries().entrySet()) {
                    String key = group + "/" + entry.getKey();
                    merged.put(key, entry.getValue());
                    if (bom.verifiable()) {
                        covering.put(key, entry.getValue());
                    } else {
                        covering.remove(key);
                    }
                }
            }
            for (Map.Entry<String, String> entry : covering.entrySet()) {
                resolvedBoms.setProperty("entry/" + entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : merged.entrySet()) {
                String key = entry.getKey();
                int first = key.indexOf('/');
                int second = key.indexOf('/', first + 1);
                managed.computeIfAbsent(key.substring(0, first), _ -> new LinkedHashMap<>())
                        .computeIfAbsent(key.substring(first + 1, second), _ -> new LinkedHashMap<>())
                        .putIfAbsent(key.substring(second + 1), entry.getValue());
            }
            if (!resolvedBoms.isEmpty()) {
                resolvedBoms.store(context.next().resolve(BOMS));
            }
        }
        SequencedProperties resolved = new SequencedProperties();
        SequencedMap<String, Resolver.Resolved> materialized = new LinkedHashMap<>();
        SequencedMap<String, Alias> aliasTargets = new LinkedHashMap<>();
        for (SequencedMap<String, SequencedMap<String, String>> byRepository : moduleAliases.values()) {
            for (SequencedMap<String, String> byAlias : byRepository.values()) {
                byAlias.forEach((alias, token) -> merge(aliasTargets, alias, token, LOCAL));
            }
        }
        SequencedMap<String, Overridden> overrideTargets = new LinkedHashMap<>();
        for (SequencedMap<String, SequencedMap<String, String>> byRepository : moduleOverrides.values()) {
            for (SequencedMap<String, String> byModule : byRepository.values()) {
                byModule.forEach((module, carriers) -> merge(overrideTargets,
                        module,
                        PathPlacement.overrides(module + "=" + carriers, "a local @jenesis.override declaration")
                                .get(module),
                        "a local @jenesis.override declaration"));
            }
        }
        if (!overrideTargets.isEmpty()
                && resolvers.values().stream().allMatch(resolver -> resolver.managedPrefixes().isEmpty())) {
            throw new IllegalArgumentException("Cannot override "
                    + overrideTargets.sequencedKeySet()
                    + ": a module override needs a layout where module names resolve to Maven coordinates"
                    + " - drop the declaration or build with jenesis.project.layout=modular_to_maven");
        }
        SequencedSet<String> aliasTokens = new LinkedHashSet<>();
        for (Alias alias : aliasTargets.values()) {
            aliasTokens.add(alias.token());
        }
        SequencedMap<String, String> modules = new LinkedHashMap<>();
        SequencedMap<String, Boolean> explicit = new LinkedHashMap<>();
        SequencedProperties graph = new SequencedProperties();
        SequencedProperties licenses = new SequencedProperties();
        int edge = 0;
        for (Map.Entry<String, SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>>> groupEntry : requires.entrySet()) {
            String group = groupEntry.getKey();
            for (String scope : groupEntry.getValue().sequencedKeySet()) {
                DependencyScope intent = scope.equals("compile") ? DependencyScope.COMPILE : DependencyScope.RUNTIME;
                for (Map.Entry<String, SequencedMap<String, String>> repoEntry : groupEntry.getValue().get(scope).entrySet()) {
                    String repo = repoEntry.getKey();
                    Resolver resolver = requireNonNull(resolvers.get(Resolver.base(repo)), "Unknown resolver: " + Resolver.base(repo));
                    SequencedMap<String, SequencedSet<String>> repoExclusions = exclusions
                            .getOrDefault(group, new LinkedHashMap<>())
                            .getOrDefault(scope, new LinkedHashMap<>())
                            .getOrDefault(repo, new LinkedHashMap<>());
                    SequencedMap<String, SequencedSet<String>> coordinates = new LinkedHashMap<>();
                    SequencedMap<String, SequencedSet<String>> deferred = new LinkedHashMap<>();
                    for (String coordinate : repoEntry.getValue().sequencedKeySet()) {
                        if (overrideTargets.containsKey(coordinate)) {
                            continue;
                        }
                        SequencedSet<String> excludes = repoExclusions.getOrDefault(coordinate, Collections.emptyNavigableSet());
                        if (aliasTokens.contains(coordinate)) {
                            deferred.put(coordinate, excludes);
                        } else {
                            coordinates.put(coordinate, excludes);
                        }
                    }
                    SequencedMap<String, SequencedMap<String, String>> groupVersions = versions
                            .getOrDefault(group, new LinkedHashMap<>());
                    SequencedMap<String, SequencedMap<String, String>> groupManaged = managed
                            .getOrDefault(group, new LinkedHashMap<>());
                    List<SequencedMap<String, String>> scoped = new ArrayList<>();
                    List<SequencedMap<String, String>> scopedManaged = new ArrayList<>();
                    scoped.add(groupVersions.getOrDefault(repo, new LinkedHashMap<>()));
                    scopedManaged.add(groupManaged.getOrDefault(repo, new LinkedHashMap<>()));
                    for (String managedPrefix : resolver.managedPrefixes()) {
                        scoped.add(groupVersions.getOrDefault(managedPrefix, new LinkedHashMap<>()));
                        scopedManaged.add(groupManaged.getOrDefault(managedPrefix, new LinkedHashMap<>()));
                    }
                    SequencedMap<String, String> bom = new LinkedHashMap<>();
                    if (!pinned) {
                        for (SequencedMap<String, String> entries : scopedManaged) {
                            entries.forEach((coordinate, value) -> {
                                int space = value.indexOf(' ');
                                bom.putIfAbsent(coordinate, space < 0 ? value : value.substring(0, space));
                            });
                        }
                    }
                    for (SequencedMap<String, String> pins : scoped) {
                        if (pinning == Pinning.VERSIONS) {
                            pins.forEach((coordinate, value) -> {
                                int space = value.indexOf(' ');
                                bom.putIfAbsent(coordinate, space < 0 ? value : value.substring(0, space));
                            });
                        } else if (pinned) {
                            pins.forEach(bom::putIfAbsent);
                        } else {
                            pins.forEach((coordinate, value) -> {
                                int space = value.indexOf(' ');
                                String qualified = space < 0 ? value : value.substring(0, space);
                                if (coordinates.containsKey(coordinate)) {
                                    bom.putIfAbsent(coordinate, qualified);
                                } else if (qualified.startsWith(":")) {
                                    int divider = qualified.indexOf(':', 1);
                                    bom.putIfAbsent(coordinate, divider < 0
                                            ? qualified
                                            : qualified.substring(0, divider));
                                }
                            });
                        }
                    }
                    if (pinned) {
                        for (SequencedMap<String, String> entries : scopedManaged) {
                            if (pinning == Pinning.VERSIONS) {
                                entries.forEach((coordinate, value) -> {
                                    int space = value.indexOf(' ');
                                    bom.putIfAbsent(coordinate, space < 0 ? value : value.substring(0, space));
                                });
                            } else {
                                entries.forEach(bom::putIfAbsent);
                            }
                        }
                    }
                    Resolver.Resolution resolution = resolver.dependencies(executor,
                            repo,
                            wrapped,
                            coordinates,
                            bom,
                            intent);
                    if (!deferred.isEmpty()) {
                        SequencedMap<String, SequencedSet<String>> absent = new LinkedHashMap<>();
                        for (Map.Entry<String, SequencedSet<String>> entry : deferred.entrySet()) {
                            if (!resolution.vertices().containsKey(repo + "/" + entry.getKey())) {
                                absent.put(entry.getKey() + "/LATEST", entry.getValue());
                            }
                        }
                        if (!absent.isEmpty()) {
                            coordinates.putAll(absent);
                            resolution = resolver.dependencies(executor, repo, wrapped, coordinates, bom, intent);
                        }
                    }
                    if (!overrideTargets.isEmpty() && !coordinates.isEmpty()) {
                        SequencedSet<String> overridden = new LinkedHashSet<>();
                        resolution.vertices().forEach((coordinate, node) -> {
                            if (node.module() != null && overrideTargets.containsKey(node.module())) {
                                overridden.add(artifactName(coordinate.substring(coordinate.indexOf('/') + 1)));
                            }
                        });
                        if (!overridden.isEmpty()) {
                            coordinates.replaceAll((_, excludes) -> {
                                SequencedSet<String> merged = new LinkedHashSet<>(excludes);
                                merged.addAll(overridden);
                                return merged;
                            });
                            resolution = resolver.dependencies(executor, repo, wrapped, coordinates, bom, intent);
                        }
                    }
                    for (Map.Entry<String, Resolver.Resolved> entry : resolution.artifacts().entrySet()) {
                        String coordinate = entry.getKey().substring(entry.getKey().indexOf('/') + 1);
                        String declared = repoEntry.getValue().get(coordinate);
                        String value = declared != null && !declared.isEmpty() ? declared : entry.getValue().checksum();
                        String transitiveKey = group + "/" + scope + "/" + entry.getKey();
                        resolved.setProperty(transitiveKey, value);
                        materialized.putIfAbsent(transitiveKey, entry.getValue());
                    }
                    for (Resolver.Edge dependency : resolution.edges()) {
                        graph.setProperty("edge/" + edge++, String.join("\t",
                                group,
                                scope,
                                repo,
                                Boolean.toString(dependency.followed()),
                                text(dependency.scope()),
                                text(dependency.version()),
                                text(dependency.parent()),
                                dependency.coordinate()));
                    }
                    for (Map.Entry<String, Resolver.Vertex> entry : resolution.vertices().entrySet()) {
                        Resolver.Vertex node = entry.getValue();
                        graph.setProperty("vertex/" + group + "/" + scope + "/" + entry.getKey(), String.join("\t",
                                text(node.resolvedVersion()),
                                text(node.module()),
                                Boolean.toString(node.automatic()),
                                Boolean.toString(node.internal())));
                        String versioned = node.resolvedVersion() == null
                                ? entry.getKey()
                                : entry.getKey() + "/" + node.resolvedVersion();
                        explicit.putIfAbsent(versioned, node.module() != null && !node.automatic());
                        if (node.module() != null) {
                            modules.putIfAbsent(node.module(), versioned);
                        }
                        if (!node.licenses().isEmpty()) {
                            String licenseKey = node.resolvedVersion() == null
                                    ? entry.getKey()
                                    : entry.getKey() + "/" + node.resolvedVersion();
                            for (int i = 0; i < node.licenses().size(); i++) {
                                License license = node.licenses().get(i);
                                String id = license.id();
                                if (id == null && license.name() != null) {
                                    id = aliases.get(license.name().toLowerCase(Locale.ROOT).trim());
                                }
                                String category = license.category();
                                if (category == null && id != null) {
                                    category = categories.get(id);
                                }
                                if (id != null) {
                                    licenses.setProperty(licenseKey + "#" + i + "#id", id);
                                }
                                if (category != null) {
                                    licenses.setProperty(licenseKey + "#" + i + "#category", category);
                                }
                                if (license.name() != null) {
                                    licenses.setProperty(licenseKey + "#" + i + "#name", license.name());
                                }
                                if (license.url() != null) {
                                    licenses.setProperty(licenseKey + "#" + i + "#url", license.url());
                                }
                            }
                        }
                    }
                }
            }
        }
        SequencedMap<String, Path> placed = new LinkedHashMap<>();
        SequencedMap<String, String> checksums = new LinkedHashMap<>();
        SequencedMap<String, Boolean> internals = new LinkedHashMap<>();
        for (Map.Entry<String, Resolver.Resolved> entry : materialized.entrySet()) {
            String key = entry.getKey();
            int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
            if (first < 0 || second < 0) {
                continue;
            }
            String dependency = key.substring(second + 1);
            Resolver.Resolved artifact = entry.getValue();
            String value = resolved.getProperty(key);
            Path file = placed.get(dependency);
            if (file == null) {
                if (artifact.internal()) {
                    file = libs.resolve(PathPlacement.fileName(
                            dependency.substring(dependency.indexOf('/') + 1)));
                    if (!Files.exists(file)) {
                        BuildStep.linkOrCopy(file, artifact.file());
                    }
                } else {
                    file = artifact.file();
                }
                placed.put(dependency, file);
                internals.put(dependency, artifact.internal());
            }
            checksums.merge(dependency, value, (left, right) -> {
                if (right.isEmpty()) {
                    return left;
                }
                if (!left.isEmpty() && !left.equals(right)) {
                    throw new IllegalStateException("Conflicting checksums pinned for " + dependency + ": " + left + " and " + right);
                }
                return left.isEmpty() ? right : left;
            });
        }
        SequencedMap<String, String> aliased = rename(placed, aliasTargets, modules, explicit, libs);
        for (Map.Entry<String, Overridden> entry : overrideTargets.entrySet()) {
            for (String carrier : entry.getValue().carriers()) {
                if (!modules.containsKey(carrier)) {
                    throw new IllegalArgumentException("Module override "
                            + entry.getKey()
                            + " declared by "
                            + entry.getValue().origin()
                            + " names "
                            + carrier
                            + " which no resolved dependency carries"
                            + " - require the carrier or drop the override");
                }
            }
        }
        for (Map.Entry<String, SequencedMap<String, SequencedMap<String, String>>> byGroup : moduleOverrides.entrySet()) {
            SequencedSet<String> scopes = requires
                    .getOrDefault(byGroup.getKey(), Collections.emptyNavigableMap())
                    .sequencedKeySet();
            for (SequencedMap<String, String> byModule : byGroup.getValue().values()) {
                for (String module : byModule.sequencedKeySet()) {
                    Path file = libs.resolve(BuildExecutorModule.encode(module) + ".jar");
                    if (!Files.exists(file)) {
                        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file))) {
                            output.putNextEntry(new JarEntry("module-info.class"));
                            output.write(carrying(module, overrideTargets.get(module).carriers()));
                            output.closeEntry();
                        }
                    }
                    placed.put("module/" + module, file);
                    for (String scope : scopes) {
                        String key = byGroup.getKey() + "/" + scope + "/module/" + module;
                        materialized.put(key, new Resolver.Resolved(file, "", false));
                        resolved.setProperty(key, "");
                    }
                }
            }
        }
        SequencedProperties index = new SequencedProperties();
        for (Map.Entry<String, Resolver.Resolved> entry : materialized.entrySet()) {
            String key = entry.getKey();
            int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
            if (first < 0 || second < 0) {
                continue;
            }
            String value = resolved.getProperty(key);
            String relative = context.next()
                    .relativize(placed.get(key.substring(second + 1)))
                    .toString()
                    .replace(File.separatorChar, '/');
            index.setProperty(key, value.isEmpty() ? relative : relative + " " + value);
        }
        if (!aliased.isEmpty()) {
            SequencedProperties properties = new SequencedProperties();
            aliased.forEach(properties::setProperty);
            properties.store(context.next().resolve(ALIASED));
        }
        SequencedProperties produced = new SequencedProperties();
        internals.forEach((dependency, internal) -> {
            if (internal) {
                produced.setProperty(dependency, "");
            }
        });
        if (!produced.isEmpty()) {
            produced.store(context.next().resolve(INTERNAL));
        }
        if (pinning == Pinning.STRICT) {
            Set<Path> pinnedFiles = new HashSet<>();
            for (Map.Entry<String, String> entry : checksums.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    pinnedFiles.add(placed.get(entry.getKey()));
                }
            }
            for (Map.Entry<String, String> entry : checksums.entrySet()) {
                if (entry.getValue().isEmpty()
                        && !internals.get(entry.getKey())
                        && !pinnedFiles.contains(placed.get(entry.getKey()))) {
                    throw new IllegalStateException("No checksum pinned for " + entry.getKey() + " (strict pinning is enabled)");
                }
            }
        }
        index.store(context.next().resolve(DEPENDENCIES));
        graph.store(context.next().resolve(GRAPH));
        licenses.store(context.next().resolve(LICENSES));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static final String LOCAL = "a local @jenesis.alias declaration";

    private record Alias(String token, String origin) {
    }

    private record Overridden(SequencedSet<String> carriers, String origin) {
    }

    private static void merge(SequencedMap<String, Overridden> declared,
                              String module,
                              SequencedSet<String> carriers,
                              String origin) {
        Overridden previous = declared.putIfAbsent(module, new Overridden(carriers, origin));
        if (previous != null && !previous.carriers().equals(carriers)) {
            throw new IllegalArgumentException("Module override "
                    + module
                    + " is declared for "
                    + previous.carriers()
                    + " by "
                    + previous.origin()
                    + " and for "
                    + carriers
                    + " by "
                    + origin);
        }
    }

    private static String artifactName(String coordinate) {
        int slash = coordinate.indexOf('/');
        int second = slash < 0 ? -1 : coordinate.indexOf('/', slash + 1);
        return second < 0 ? coordinate : coordinate.substring(0, second);
    }

    private static byte[] carrying(String module, SequencedSet<String> carriers) {
        return ClassFile.of().buildModule(ModuleAttribute.of(ModuleDesc.of(module), builder -> {
            builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null);
            carriers.forEach(carrier -> builder.requires(
                    ModuleDesc.of(carrier), ClassFile.ACC_TRANSITIVE, null));
        }));
    }

    private static void merge(SequencedMap<String, Alias> declared,
                              String alias,
                              String token,
                              String origin) {
        Alias previous = declared.putIfAbsent(alias, new Alias(token, origin));
        if (previous != null && !previous.token().equals(token)) {
            throw new IllegalArgumentException("Module alias "
                    + alias
                    + " is declared for "
                    + previous.token()
                    + " by "
                    + previous.origin()
                    + " and for "
                    + token
                    + " by "
                    + origin);
        }
    }

    private static SequencedMap<String, String> rename(SequencedMap<String, Path> placed,
                                                       SequencedMap<String, Alias> declared,
                                                       SequencedMap<String, String> modules,
                                                       SequencedMap<String, Boolean> explicit,
                                                       Path libs) throws IOException {
        SequencedMap<String, String> coordinates = new LinkedHashMap<>();
        for (String dependency : placed.sequencedKeySet()) {
            int first = dependency.indexOf('/'), last = dependency.lastIndexOf('/');
            if (first > 0 && last > first) {
                coordinates.putIfAbsent(dependency.substring(first + 1, last), dependency);
            }
        }
        for (Map.Entry<String, Path> entry : placed.entrySet()) {
            if (!explicit.getOrDefault(entry.getKey(), true) || Files.isDirectory(entry.getValue())) {
                continue;
            }
            String origin = entry.getValue().getFileName().toString();
            for (Map.Entry<String, String> declaration : PathPlacement.aliases(entry.getValue()).entrySet()) {
                merge(declared, declaration.getKey(), declaration.getValue(), origin);
            }
        }
        SequencedMap<String, String> aliased = new LinkedHashMap<>(), owners = new LinkedHashMap<>();
        for (Map.Entry<String, Alias> entry : declared.entrySet()) {
            String alias = entry.getKey(), token = entry.getValue().token();
            String coordinate = coordinates.get(token);
            if (coordinate == null) {
                throw new IllegalArgumentException("Module alias "
                        + alias
                        + " declared by "
                        + entry.getValue().origin()
                        + " does not name a resolved dependency: "
                        + token
                        + (entry.getValue().origin().equals(LOCAL)
                        ? " - require the target or drop the alias"
                        : " - stop excluding the target"));
            }
            String module = modules.get(alias);
            if (module != null) {
                throw new IllegalArgumentException("Module alias "
                        + alias
                        + " collides with module "
                        + alias
                        + " resolved from "
                        + module
                        + " - require it directly");
            }
            ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(placed.get(coordinate));
            if (descriptor != null) {
                throw new IllegalArgumentException("Target of module alias "
                        + alias
                        + " is already the "
                        + (descriptor.isAutomatic() ? "automatic" : "named")
                        + " module "
                        + descriptor.name()
                        + " - require "
                        + descriptor.name()
                        + " instead of aliasing "
                        + token);
            }
            String previous = owners.putIfAbsent(coordinate, alias);
            if (previous != null) {
                throw new IllegalArgumentException(coordinate
                        + " is aliased as both "
                        + previous
                        + " and "
                        + alias
                        + " - a jar can carry only one module name");
            }
            aliased.put(alias, coordinate);
        }
        SequencedMap<String, String> names = new LinkedHashMap<>();
        aliased.forEach((alias, coordinate) -> names.put(coordinate, alias));
        SequencedMap<String, Path> ordered = new LinkedHashMap<>();
        names.keySet().forEach(dependency -> ordered.put(dependency, placed.get(dependency)));
        ordered.putAll(placed);
        SequencedMap<String, Claim> claims = new LinkedHashMap<>(), carriers = new LinkedHashMap<>();
        SequencedMap<Path, Path> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : ordered.entrySet()) {
            String dependency = entry.getKey();
            Path source = entry.getValue();
            if (Files.isDirectory(source)) {
                continue;
            }
            Path taken = renamed.get(source);
            if (taken != null) {
                placed.put(dependency, taken);
                continue;
            }
            String coordinate = dependency.substring(dependency.indexOf('/') + 1);
            String alias = names.get(dependency);
            String module;
            if (alias != null) {
                module = alias;
            } else {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(source);
                module = descriptor == null ? null : descriptor.name();
            }
            String name = module == null
                    ? PathPlacement.fileName(coordinate)
                    : PathPlacement.fileName(coordinate, module, alias == null);
            if (module != null) {
                Claim carrier = carriers.putIfAbsent(module, new Claim(dependency, source));
                if (carrier != null && !carrier.file().equals(source)) {
                    throw new IllegalArgumentException(carrier.dependency()
                            + " and "
                            + dependency
                            + " both carry module "
                            + module
                            + " - a module path resolves whichever of the two comes first,"
                            + " so drop one with @jenesis.exclude");
                }
            }
            Claim previous = claims.putIfAbsent(name, new Claim(dependency, source));
            if (previous != null && !previous.file().equals(source)) {
                throw new IllegalArgumentException(previous.dependency()
                        + " and "
                        + dependency
                        + " are both materialized as "
                        + name
                        + " - two artifacts cannot share one file name");
            }
            Path target = libs.resolve(name);
            if (!source.equals(target)) {
                if (source.startsWith(libs)) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.deleteIfExists(target);
                    BuildStep.linkOrCopy(target, source);
                }
                placed.put(dependency, target);
            }
            renamed.put(source, target);
            if (alias != null) {
                PathPlacement.aliased(target, alias, "Target " + dependency);
                Files.writeString(PathPlacement.declaration(target), alias);
            }
        }
        return aliased;
    }

    private record Claim(String dependency, Path file) {
    }

    private static Path index(Path folder) {
        Path modular = folder.resolve(MODULAR);
        if (Files.exists(modular)) {
            return modular;
        }
        Path dependencies = folder.resolve(BuildStep.DEPENDENCIES);
        return Files.exists(dependencies) ? dependencies : null;
    }

    public static List<Path> select(Path folder, String group, String scope) throws IOException {
        Path file = index(folder);
        if (file == null) {
            return List.of();
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedSet<Path> selected = new LinkedHashSet<>();
        String prefix = group + "/" + scope + "/";
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String value = properties.getProperty(key);
                int space = value.indexOf(' ');
                Path jar = folder.resolve(space < 0 ? value : value.substring(0, space)).normalize();
                if (Files.exists(jar)) {
                    selected.add(jar);
                }
            }
        }
        return new ArrayList<>(selected);
    }

    public static List<Path> all(Path folder) throws IOException {
        Path file = index(folder);
        if (file == null) {
            return List.of();
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedSet<Path> selected = new LinkedHashSet<>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            int space = value.indexOf(' ');
            Path jar = folder.resolve(space < 0 ? value : value.substring(0, space)).normalize();
            if (Files.exists(jar)) {
                selected.add(jar);
            }
        }
        return new ArrayList<>(selected);
    }

    public static SequencedMap<String, Path> internal(Path folder) throws IOException {
        Path file = index(folder), graphFile = folder.resolve(GRAPH);
        if (file == null || !Files.exists(graphFile)) {
            return new LinkedHashMap<>();
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedMap<String, Path> selected = new LinkedHashMap<>();
        for (Map.Entry<String, Resolver.Resolution> entry : graph(List.of(graphFile), List.of()).entrySet()) {
            for (Map.Entry<String, Resolver.Vertex> vertex : entry.getValue().vertices().entrySet()) {
                if (!vertex.getValue().internal() || vertex.getValue().resolvedVersion() == null) {
                    continue;
                }
                String coordinate = vertex.getKey() + "/" + vertex.getValue().resolvedVersion();
                String value = properties.getProperty(entry.getKey() + "/" + coordinate);
                if (value == null) {
                    continue;
                }
                int space = value.indexOf(' ');
                Path jar = folder.resolve(space < 0 ? value : value.substring(0, space)).normalize();
                if (Files.exists(jar)) {
                    selected.putIfAbsent(coordinate, jar);
                }
            }
        }
        return selected;
    }

    private static final Map<String, String> DEFAULT_ALIASES = Map.ofEntries(
            Map.entry("apache license 2.0", "Apache-2.0"),
            Map.entry("apache license, version 2.0", "Apache-2.0"),
            Map.entry("apache license version 2.0", "Apache-2.0"),
            Map.entry("apache software license - version 2.0", "Apache-2.0"),
            Map.entry("the apache software license, version 2.0", "Apache-2.0"),
            Map.entry("the apache license, version 2.0", "Apache-2.0"),
            Map.entry("apache 2.0", "Apache-2.0"),
            Map.entry("apache-2.0", "Apache-2.0"),
            Map.entry("asl 2.0", "Apache-2.0"),
            Map.entry("apache license", "Apache-2.0"),
            Map.entry("mit license", "MIT"),
            Map.entry("the mit license", "MIT"),
            Map.entry("mit", "MIT"),
            Map.entry("mit-0", "MIT-0"),
            Map.entry("bsd license", "BSD-2-Clause"),
            Map.entry("the bsd license", "BSD-2-Clause"),
            Map.entry("bsd-2-clause", "BSD-2-Clause"),
            Map.entry("bsd 2-clause license", "BSD-2-Clause"),
            Map.entry("bsd-3-clause", "BSD-3-Clause"),
            Map.entry("bsd 3-clause license", "BSD-3-Clause"),
            Map.entry("the bsd 3-clause license", "BSD-3-Clause"),
            Map.entry("new bsd license", "BSD-3-Clause"),
            Map.entry("revised bsd license", "BSD-3-Clause"),
            Map.entry("eclipse distribution license - v 1.0", "BSD-3-Clause"),
            Map.entry("eclipse distribution license (new bsd license)", "BSD-3-Clause"),
            Map.entry("eclipse public license - v 1.0", "EPL-1.0"),
            Map.entry("eclipse public license 1.0", "EPL-1.0"),
            Map.entry("eclipse public license v1.0", "EPL-1.0"),
            Map.entry("epl-1.0", "EPL-1.0"),
            Map.entry("eclipse public license - v 2.0", "EPL-2.0"),
            Map.entry("eclipse public license 2.0", "EPL-2.0"),
            Map.entry("eclipse public license v2.0", "EPL-2.0"),
            Map.entry("eclipse public license", "EPL-2.0"),
            Map.entry("epl-2.0", "EPL-2.0"),
            Map.entry("gnu lesser general public license", "LGPL-2.1-or-later"),
            Map.entry("lesser general public license", "LGPL-2.1-or-later"),
            Map.entry("gnu lesser general public license, version 2.1", "LGPL-2.1-only"),
            Map.entry("lgpl-2.1", "LGPL-2.1-only"),
            Map.entry("lgpl 2.1", "LGPL-2.1-only"),
            Map.entry("lgpl-3.0", "LGPL-3.0-only"),
            Map.entry("lgpl", "LGPL-2.1-or-later"),
            Map.entry("gnu general public license, version 2", "GPL-2.0-only"),
            Map.entry("gnu general public license v2.0", "GPL-2.0-only"),
            Map.entry("gpl-2.0", "GPL-2.0-only"),
            Map.entry("gnu general public license, version 2 with the classpath exception", "GPL-2.0-with-classpath-exception"),
            Map.entry("gpl-2.0-with-classpath-exception", "GPL-2.0-with-classpath-exception"),
            Map.entry("gnu general public license, version 3", "GPL-3.0-only"),
            Map.entry("gpl-3.0", "GPL-3.0-only"),
            Map.entry("gnu affero general public license", "AGPL-3.0-or-later"),
            Map.entry("affero general public license", "AGPL-3.0-or-later"),
            Map.entry("agpl-3.0", "AGPL-3.0-only"),
            Map.entry("mozilla public license 2.0", "MPL-2.0"),
            Map.entry("mozilla public license, version 2.0", "MPL-2.0"),
            Map.entry("mpl 2.0", "MPL-2.0"),
            Map.entry("mpl-2.0", "MPL-2.0"),
            Map.entry("mozilla public license 1.1", "MPL-1.1"),
            Map.entry("common development and distribution license 1.0", "CDDL-1.0"),
            Map.entry("cddl 1.0", "CDDL-1.0"),
            Map.entry("cddl-1.0", "CDDL-1.0"),
            Map.entry("common development and distribution license 1.1", "CDDL-1.1"),
            Map.entry("cddl 1.1", "CDDL-1.1"),
            Map.entry("cddl-1.1", "CDDL-1.1"),
            Map.entry("isc license", "ISC"),
            Map.entry("isc", "ISC"),
            Map.entry("boost software license 1.0", "BSL-1.0"),
            Map.entry("boost software license", "BSL-1.0"),
            Map.entry("bsl-1.0", "BSL-1.0"),
            Map.entry("the unlicense", "Unlicense"),
            Map.entry("unlicense", "Unlicense"),
            Map.entry("cc0 1.0 universal", "CC0-1.0"),
            Map.entry("cc0", "CC0-1.0"),
            Map.entry("public domain", "CC0-1.0"),
            Map.entry("the zlib/libpng license", "Zlib"),
            Map.entry("zlib", "Zlib"),
            Map.entry("python software foundation license", "PSF-2.0"),
            Map.entry("wtfpl", "WTFPL"));

    private static final Map<String, String> DEFAULT_CATEGORIES = Map.ofEntries(
            Map.entry("Apache-2.0", "permissive"),
            Map.entry("MIT", "permissive"),
            Map.entry("MIT-0", "permissive"),
            Map.entry("BSD-2-Clause", "permissive"),
            Map.entry("BSD-3-Clause", "permissive"),
            Map.entry("ISC", "permissive"),
            Map.entry("BSL-1.0", "permissive"),
            Map.entry("Zlib", "permissive"),
            Map.entry("PSF-2.0", "permissive"),
            Map.entry("Unlicense", "public-domain"),
            Map.entry("CC0-1.0", "public-domain"),
            Map.entry("WTFPL", "public-domain"),
            Map.entry("EPL-1.0", "weak-copyleft"),
            Map.entry("EPL-2.0", "weak-copyleft"),
            Map.entry("MPL-1.1", "weak-copyleft"),
            Map.entry("MPL-2.0", "weak-copyleft"),
            Map.entry("CDDL-1.0", "weak-copyleft"),
            Map.entry("CDDL-1.1", "weak-copyleft"),
            Map.entry("LGPL-2.1-only", "weak-copyleft"),
            Map.entry("LGPL-2.1-or-later", "weak-copyleft"),
            Map.entry("LGPL-3.0-only", "weak-copyleft"),
            Map.entry("LGPL-3.0-or-later", "weak-copyleft"),
            Map.entry("GPL-2.0-with-classpath-exception", "weak-copyleft"),
            Map.entry("GPL-2.0-only", "strong-copyleft"),
            Map.entry("GPL-2.0-or-later", "strong-copyleft"),
            Map.entry("GPL-3.0-only", "strong-copyleft"),
            Map.entry("GPL-3.0-or-later", "strong-copyleft"),
            Map.entry("AGPL-3.0-only", "network-copyleft"),
            Map.entry("AGPL-3.0-or-later", "network-copyleft"));

    public static SequencedMap<String, Resolver.Resolution> graph(Iterable<Path> graphFiles,
                                                                  Iterable<Path> licenseFiles) throws IOException {
        SequencedMap<String, SequencedMap<Integer, String[]>> licenseEntries = new LinkedHashMap<>();
        for (Path file : licenseFiles) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(file);
            for (String key : properties.stringPropertyNames()) {
                int last = key.lastIndexOf('#');
                int prior = last < 1 ? -1 : key.lastIndexOf('#', last - 1);
                if (prior < 1) {
                    continue;
                }
                int index;
                try {
                    index = Integer.parseInt(key.substring(prior + 1, last));
                } catch (NumberFormatException _) {
                    continue;
                }
                String[] entry = licenseEntries
                        .computeIfAbsent(key.substring(0, prior), _ -> new TreeMap<>())
                        .computeIfAbsent(index, _ -> new String[4]);
                switch (key.substring(last + 1)) {
                    case "id" -> entry[0] = properties.getProperty(key);
                    case "category" -> entry[1] = properties.getProperty(key);
                    case "name" -> entry[2] = properties.getProperty(key);
                    case "url" -> entry[3] = properties.getProperty(key);
                    default -> {
                    }
                }
            }
        }
        SequencedMap<String, List<License>> licenses = new LinkedHashMap<>();
        licenseEntries.forEach((coordinate, byIndex) -> licenses.put(coordinate,
                byIndex.values().stream().map(entry -> new License(entry[0], entry[1], entry[2], entry[3])).toList()));
        SequencedMap<String, SequencedSet<Resolver.Edge>> edges = new LinkedHashMap<>();
        SequencedMap<String, SequencedMap<String, Resolver.Vertex>> vertices = new LinkedHashMap<>();
        for (Path file : graphFiles) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(file);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (key.startsWith("edge/")) {
                    String[] parts = value.split("\t", -1);
                    if (parts.length != 8) {
                        continue;
                    }
                    edges.computeIfAbsent(parts[0] + "/" + parts[1], _ -> new LinkedHashSet<>())
                            .add(new Resolver.Edge(
                                    parts[6].isEmpty() ? null : parts[6],
                                    parts[7],
                                    parts[5].isEmpty() ? null : parts[5],
                                    parts[4].isEmpty() ? null : parts[4],
                                    Boolean.parseBoolean(parts[3])));
                } else if (key.startsWith("vertex/")) {
                    String rest = key.substring("vertex/".length());
                    int first = rest.indexOf('/');
                    int second = first < 0 ? -1 : rest.indexOf('/', first + 1);
                    if (second < 0) {
                        continue;
                    }
                    String groupScope = rest.substring(0, second);
                    String coordinate = rest.substring(second + 1);
                    String[] parts = value.split("\t", -1);
                    String resolvedVersion = parts[0].isEmpty() ? null : parts[0];
                    List<License> declared = resolvedVersion == null
                            ? List.of()
                            : licenses.getOrDefault(coordinate + "/" + resolvedVersion, List.of());
                    vertices.computeIfAbsent(groupScope, _ -> new LinkedHashMap<>())
                            .putIfAbsent(coordinate, new Resolver.Vertex(
                                    resolvedVersion,
                                    parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null,
                                    parts.length > 2 && Boolean.parseBoolean(parts[2]),
                                    parts.length > 3 && Boolean.parseBoolean(parts[3]),
                                    declared));
                }
            }
        }
        SequencedMap<String, Resolver.Resolution> result = new LinkedHashMap<>();
        SequencedSet<String> groupScopes = new LinkedHashSet<>(edges.sequencedKeySet());
        groupScopes.addAll(vertices.sequencedKeySet());
        for (String groupScope : groupScopes) {
            result.put(groupScope, new Resolver.Resolution(
                    new LinkedHashMap<>(),
                    new ArrayList<>(edges.getOrDefault(groupScope, new LinkedHashSet<>())),
                    vertices.getOrDefault(groupScope, new LinkedHashMap<>())));
        }
        return result;
    }

    private static String[] split(String key) {
        int first = key.indexOf('/');
        if (first < 1) {
            return null;
        }
        int second = key.indexOf('/', first + 1);
        if (second < 0) {
            return null;
        }
        int third = key.indexOf('/', second + 1);
        if (third < 0) {
            return null;
        }
        return new String[] {
                key.substring(0, first),
                key.substring(first + 1, second),
                key.substring(second + 1, third),
                key.substring(third + 1)};
    }
}
