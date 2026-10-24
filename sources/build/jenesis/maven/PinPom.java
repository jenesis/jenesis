package build.jenesis.maven;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Permits;
import build.jenesis.Pinning;
import build.jenesis.Platform;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.module.PinModuleInfo;
import build.jenesis.step.Inventory;

public class PinPom implements BuildStep {

    private static final Pattern DEPENDENCY_MANAGEMENT = Pattern.compile(
            "(?s)([ \\t]*)<dependencyManagement>.*?</dependencyManagement>\\s*\\n");
    private static final Pattern PROJECT_CLOSE = Pattern.compile("\\n([ \\t]*)</project>");
    private static final Pattern CHECKSUM_COMMENT = Pattern.compile("[ \\t]*<!--\\s*Checksum/[^>]*-->\\s*\\n");
    private static final Pattern INDENT = Pattern.compile("\\n([ \\t]+)<");
    private static final Pattern PIN_COMMENT = Pattern.compile("(?s)([ \\t]*)<!--\\s*jenesis\\.pin\\b(.*?)-->\\s*\\n");
    private static final Pattern MANAGED_DEPENDENCY = Pattern.compile("(?s)[ \\t]*<dependency>.*?</dependency>[ \\t]*\\n");
    private static final Pattern EMPTY_MANAGEMENT = Pattern.compile(
            "\\s*<dependencyManagement>\\s*<dependencies>\\s*</dependencies>\\s*</dependencyManagement>\\s*");

    private final transient Permits permits;

    private final String path;
    private final List<Path> pomFiles;
    private final transient HashDigestFunction hashFunction;
    private final Platform platform;
    private final PinModuleInfo.Retain retain;

    public PinPom(String path, List<Path> pomFiles, HashDigestFunction hashFunction) {
        this(path, pomFiles, hashFunction, new Platform(), PinModuleInfo.Retain.GROUPS, Pinning.permits());
    }

    public static PinPom ofEnvironment(Environment environment,
                                       String path,
                                       List<Path> pomFiles,
                                       HashDigestFunction hashFunction) {
        return new PinPom(path, pomFiles, hashFunction)
                .permits(Pinning.permits(environment))
                .platform(Platform.ofEnvironment(environment))
                .retain(PinModuleInfo.Retain.ofEnvironment(environment));
    }

    private PinPom(String path,
                   List<Path> pomFiles,
                   HashDigestFunction hashFunction,
                   Platform platform,
                   PinModuleInfo.Retain retain,
                   Permits permits) {
        this.permits = permits;
        this.path = path;
        this.pomFiles = List.copyOf(pomFiles);
        this.hashFunction = hashFunction;
        this.platform = platform;
        this.retain = retain;
    }

    public PinPom permits(Permits permits) {
        return new PinPom(path, pomFiles, hashFunction, platform, retain, permits);
    }

    public PinPom platform(Platform platform) {
        return new PinPom(path, pomFiles, hashFunction, platform, retain, permits);
    }

    public PinPom retain(PinModuleInfo.Retain retain) {
        return new PinPom(path, pomFiles, hashFunction, platform, retain, permits);
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return true;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Permits permits = this.permits;
        if (permits == null) {
            return pin(arguments);
        }
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to rewrite the pins of " + path, e);
        }
        try {
            return pin(arguments);
        } finally {
            permits.release();
        }
    }

    private CompletionStage<BuildStepResult> pin(SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, Inventory.Dependency> closure = Inventory.closure(arguments.values(), path);
        Set<String> internal = collectInternal(Inventory.identities(arguments.values()));
        Set<String> declared = new HashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path module = argument.folder().resolve(MODULE), requires = argument.folder().resolve(REQUIRES);
            if (!Files.isRegularFile(module)
                    || !path.equals(SequencedProperties.ofFiles(module).getProperty("path"))
                    || !Files.isRegularFile(requires)) {
                continue;
            }
            for (String key : SequencedProperties.ofFiles(requires).stringPropertyNames()) {
                int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
                if (first > 0 && second > first) {
                    declared.add(key.substring(0, first) + key.substring(second));
                }
            }
        }
        SequencedMap<String, String> entries = collectEntries(closure, internal, declared, hashFunction);
        SequencedMap<String, Inventory.Dependency> poms = Inventory.poms(arguments.values(), path);
        Set<String> groups = new HashSet<>();
        Stream.concat(closure.values().stream(), poms.values().stream()).forEach(dependency -> groups.add(dependency.group()));
        Predicate<String> retained = retain.retains(groups);
        for (Map.Entry<String, Inventory.Dependency> pom : poms.entrySet()) {
            String version = pom.getKey().substring(pom.getKey().lastIndexOf('/') + 1);
            if (!version.endsWith("-SNAPSHOT")) {
                entries.putIfAbsent(pom.getKey(), version + " " + pom.getValue().checksum(hashFunction));
            }
        }
        for (Path pomFile : pomFiles) {
            String existing = Files.readString(pomFile);
            SequencedMap<String, String> qualified = new TreeMap<>(entries);
            Matcher pinMatcher = PIN_COMMENT.matcher(existing);
            StringBuilder pinned = new StringBuilder();
            while (pinMatcher.find()) {
                pinned.append(pinMatcher.group(2)).append('\n');
            }
            List<String> preserved = pinned.isEmpty() ? List.of() : preserveGuarded(pinned.toString(), qualified);
            for (String line : pinned.toString().replace("&#45;", "-").split("\n")) {
                String trimmed = line.trim().replaceAll("\\s+", " ");
                int space = trimmed.indexOf(' ');
                if (space > 0 && !trimmed.endsWith(")")) {
                    String value = trimmed.substring(space + 1), key = Resolver.pinKey(expand(trimmed.substring(0, space)), value);
                    if (!entries.containsKey(key) && retained.test(key)) {
                        qualified.putIfAbsent(key, value);
                    }
                }
            }
            String updated = existing, indent;
            Matcher dependencyManagementMatcher = DEPENDENCY_MANAGEMENT.matcher(existing);
            if (dependencyManagementMatcher.find()) {
                indent = dependencyManagementMatcher.group(1);
                String management = MANAGED_DEPENDENCY.matcher(dependencyManagementMatcher.group()).replaceAll(dependency ->
                        CHECKSUM_COMMENT.matcher(dependency.group()).find() ? "" : Matcher.quoteReplacement(dependency.group()));
                updated = existing.substring(0, dependencyManagementMatcher.start())
                        + (EMPTY_MANAGEMENT.matcher(management).matches() ? "" : management)
                        + existing.substring(dependencyManagementMatcher.end());
            } else {
                Matcher indentMatcher = INDENT.matcher(existing);
                indent = indentMatcher.find() ? indentMatcher.group(1) : "    ";
            }
            StringBuilder requires = new StringBuilder();
            if (!qualified.isEmpty() || !preserved.isEmpty()) {
                requires.append(indent).append("<!--jenesis.pin\n");
                for (String line : preserved) {
                    requires.append(indent).append(line.replace("--", "&#45;&#45;")).append("\n");
                }
                qualified.forEach((key, value) -> requires.append(indent)
                        .append((token(key) + " " + value).replace("--", "&#45;&#45;"))
                        .append("\n"));
                requires.append(indent).append("-->\n");
            }
            Matcher requiresMatcher = PIN_COMMENT.matcher(updated);
            if (requiresMatcher.find()) {
                StringBuilder rewritten = new StringBuilder();
                requiresMatcher.appendReplacement(rewritten, Matcher.quoteReplacement(requires.toString()));
                while (requiresMatcher.find()) {
                    requiresMatcher.appendReplacement(rewritten, "");
                }
                updated = requiresMatcher.appendTail(rewritten).toString();
            } else if (!requires.isEmpty()) {
                Matcher projectCloseMatcher = PROJECT_CLOSE.matcher(updated);
                if (!projectCloseMatcher.find()) {
                    throw new IllegalStateException("No </project> tag in " + pomFile);
                }
                updated = updated.substring(0, projectCloseMatcher.start() + 1)
                        + requires
                        + updated.substring(projectCloseMatcher.start() + 1);
            }
            updated = CHECKSUM_COMMENT.matcher(updated).replaceAll("");
            if (!updated.equals(existing)) {
                Files.writeString(pomFile, updated);
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static String token(String key) {
        String token = Resolver.pinToken(key);
        if (!token.startsWith("main/maven/")) {
            return token;
        }
        String coordinate = token.substring("main/maven/".length());
        return coordinate.indexOf('/') == coordinate.lastIndexOf('/') ? coordinate : token.substring("main/".length());
    }

    private static String expand(String token) {
        int first = token.indexOf('/');
        if (first < 0) {
            return "main/module/" + token;
        } else if (token.indexOf('/', first + 1) < 0) {
            return "main/maven/" + token;
        }
        return token.startsWith("maven/") ? "main/" + token : token;
    }

    static SequencedMap<String, String> collectEntries(SequencedMap<String, Inventory.Dependency> closure,
                                                       Set<String> internal,
                                                       Set<String> declared,
                                                       HashDigestFunction hashFunction) throws IOException {
        SequencedMap<String, String> entries = new TreeMap<>();
        for (Map.Entry<String, Inventory.Dependency> dependency : closure.entrySet()) {
            String group = dependency.getValue().group();
            String key = dependency.getKey().substring(group.length() + 1);
            if (internal.contains(key)) {
                continue;
            }
            int lastSlash = key.lastIndexOf('/');
            int firstSlash = key.indexOf('/');
            if (lastSlash <= 0 || lastSlash == firstSlash) {
                continue;
            }
            String coordinate = key.substring(0, lastSlash);
            String version = key.substring(lastSlash + 1);
            String checksum = version.endsWith("-SNAPSHOT") ? null : dependency.getValue().checksum(hashFunction);
            String value = checksum == null ? version : version + " " + checksum;
            if (declared.contains(dependency.getKey())) {
                entries.put(group + "/" + coordinate, value);
            } else {
                entries.putIfAbsent(group + "/" + coordinate, value);
            }
        }
        return entries;
    }

    private List<String> preserveGuarded(String block, SequencedMap<String, String> qualified) {
        record Pin(String token, String value, String guard) {
        }
        List<Pin> pins = new ArrayList<>();
        Set<String> guarded = new LinkedHashSet<>();
        for (String line : block.replace("&#45;", "-").split("\n")) {
            String trimmed = line.trim().replaceAll("\\s+", " ");
            if (trimmed.isEmpty()) {
                continue;
            }
            int space = trimmed.indexOf(' ');
            if (space < 1) {
                continue;
            }
            String token = trimmed.substring(0, space);
            String value = trimmed.substring(space + 1).trim();
            String guard = null;
            if (value.endsWith(")")) {
                int bracket = value.lastIndexOf('(');
                if (bracket > 0 && !value.substring(0, bracket).trim().isEmpty()) {
                    guard = value.substring(bracket + 1, value.length() - 1);
                    value = value.substring(0, bracket).trim();
                }
            }
            pins.add(new Pin(token, value, guard));
            if (guard != null) {
                guarded.add(expand(token));
            }
        }
        if (guarded.isEmpty()) {
            return List.of();
        }
        for (String key : guarded) {
            String resolved = qualified.remove(key);
            Integer fallback = null, matched = null;
            int specificity = 0;
            boolean ambiguous = false;
            for (int index = 0; index < pins.size(); index++) {
                Pin pin = pins.get(index);
                if (!expand(pin.token()).equals(key)) {
                    continue;
                }
                if (pin.guard() == null) {
                    fallback = index;
                    continue;
                }
                Platform guard = Platform.of(pin.guard());
                if (!platform.matches(guard)) {
                    continue;
                }
                if (guard.tokens().size() > specificity) {
                    matched = index;
                    specificity = guard.tokens().size();
                    ambiguous = false;
                } else if (guard.tokens().size() == specificity) {
                    ambiguous = true;
                }
            }
            Integer winner = matched != null ? matched : fallback;
            if (resolved != null && winner != null && !ambiguous) {
                Pin pin = pins.get(winner);
                pins.set(winner, new Pin(pin.token(), resolved, pin.guard()));
            }
        }
        List<String> preserved = new ArrayList<>();
        for (Pin pin : pins) {
            if (guarded.contains(expand(pin.token()))) {
                preserved.add(pin.token() + " " + pin.value() + (pin.guard() == null ? "" : " (" + pin.guard() + ")"));
            }
        }
        return preserved;
    }

    static Set<String> collectInternal(Set<String> identities) {
        Set<String> internal = new LinkedHashSet<>();
        for (String coord : identities) {
            internal.add(coord);
            int firstSlash = coord.indexOf('/');
            int lastSlash = coord.lastIndexOf('/');
            if (firstSlash > 0 && lastSlash > firstSlash) {
                internal.add(coord.substring(0, lastSlash));
            }
        }
        return internal;
    }
}
