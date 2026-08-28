package build.jenesis.module;

import module java.base;
import module jdk.compiler;
import build.jenesis.Platform;
import javax.lang.model.SourceVersion;
import javax.tools.ToolProvider;

import static java.util.Objects.requireNonNull;

public class ModuleInfoParser {

    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private final String group;

    public ModuleInfoParser() {
        this("main");
    }

    public ModuleInfoParser(String group) {
        this.group = group;
    }

    public ModuleInfo identify(Path moduleInfo) throws IOException {
        JavacTask javac = (JavacTask) compiler.getTask(new PrintWriter(Writer.nullWriter()),
                compiler.getStandardFileManager(null, null, null),
                null,
                null,
                null,
                List.of(new SimpleJavaFileObject(moduleInfo.toUri(), JavaFileObject.Kind.SOURCE) {
                    @Override
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                        return Files.readString(moduleInfo);
                    }
                }));
        DocTrees docTrees = DocTrees.instance(javac);
        for (CompilationUnitTree unit : javac.parse()) {
            ModuleTree module = requireNonNull(unit.getModule());
            SequencedSet<String> dependencies = new LinkedHashSet<>();
            SequencedSet<String> runtimeDependencies = new LinkedHashSet<>();
            for (DirectiveTree directive : module.getDirectives()) {
                if (directive instanceof RequiresTree requires) {
                    String name = requires.getModuleName().toString();
                    if (!name.startsWith("java.") && !name.startsWith("jdk.")) {
                        dependencies.add(name);
                        if (!requires.isStatic()) {
                            runtimeDependencies.add(name);
                        }
                    }
                }
            }
            SequencedMap<String, String> aliases = new LinkedHashMap<>();
            SequencedMap<String, SequencedSet<String>> excludes = new LinkedHashMap<>();
            SequencedMap<String, SequencedSet<String>> overrides = new LinkedHashMap<>();
            SequencedMap<String, String> versions = new LinkedHashMap<>();
            SequencedMap<String, SequencedMap<String, String>> variants = new LinkedHashMap<>();
            SequencedMap<String, String> boms = new LinkedHashMap<>();
            SequencedMap<String, SequencedMap<String, String>> bomVariants = new LinkedHashMap<>();
            SequencedMap<String, String> plugins = new LinkedHashMap<>();
            SequencedMap<String, String> attachments = new LinkedHashMap<>();
            String release = null;
            String name = null;
            String description = null;
            String testOf = null;
            boolean abstractTest = false;
            String main = null;
            DocCommentTree docComment = docTrees.getDocCommentTree(TreePath.getPath(unit, module));
            if (docComment != null) {
                String summary = docComment.getFirstSentence().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining())
                        .trim();
                if (!summary.isEmpty()) {
                    name = summary.endsWith(".")
                            ? summary.substring(0, summary.length() - 1)
                            : summary;
                }
                String body = docComment.getBody().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining())
                        .trim();
                if (!body.isEmpty()) {
                    description = body;
                }
                for (DocTree tag : docComment.getBlockTags()) {
                    if (tag instanceof UnknownBlockTagTree unknown) {
                        String content = unknown.getContent().stream()
                                .map(Object::toString)
                                .collect(Collectors.joining())
                                .trim();
                        switch (unknown.getTagName()) {
                            case "jenesis.pin" -> {
                                String pin = content.replaceAll("\\s+", " ");
                                int split = pin.indexOf(' ');
                                if (split < 1 || split == pin.length() - 1) {
                                    continue;
                                }
                                String token = pin.substring(0, split);
                                String version = pin.substring(split + 1).trim();
                                String guard = null;
                                if (version.endsWith("]")) {
                                    int bracket = version.lastIndexOf('[');
                                    if (bracket < 0) {
                                        throw new IllegalArgumentException("Malformed @jenesis.pin guard '"
                                                + version
                                                + "': expected <value> [<token>,<token>...]");
                                    }
                                    String guarded = version.substring(0, bracket).trim();
                                    if (!guarded.isEmpty()) {
                                        guard = Platform.of(
                                                version.substring(bracket + 1, version.length() - 1)).canonical();
                                        version = guarded;
                                    }
                                }
                                if (token.isEmpty() || version.isEmpty()
                                        || token.startsWith("java.") || token.startsWith("jdk.")) {
                                    continue;
                                }
                                String key = expand("jenesis.pin", token);
                                if (guard == null) {
                                    versions.put(key, version);
                                } else {
                                    variants.computeIfAbsent(key, _ -> new LinkedHashMap<>()).put(guard, version);
                                }
                            }
                            case "jenesis.bom" -> {
                                String bom = content.replaceAll("\\s+", " ").trim();
                                String guard = null;
                                if (bom.endsWith("]")) {
                                    int bracket = bom.lastIndexOf('[');
                                    if (bracket < 0) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom guard '"
                                                + bom
                                                + "': expected <value> [<token>,<token>...]");
                                    }
                                    String guarded = bom.substring(0, bracket).trim();
                                    if (!guarded.isEmpty()) {
                                        guard = Platform.of(bom.substring(bracket + 1, bom.length() - 1)).canonical();
                                        bom = guarded;
                                    }
                                }
                                if (bom.isEmpty()) {
                                    continue;
                                }
                                String[] words = bom.split(" ");
                                String token = words[0], key, value;
                                String last = token.substring(token.lastIndexOf('/') + 1);
                                if (last.startsWith("pin-") && last.endsWith(".properties")) {
                                    if (words.length > 1) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom declaration '"
                                                + bom
                                                + "': a local BOM takes no version or checksum");
                                    }
                                    int first = token.indexOf('/');
                                    String qualifier = first < 0 ? group : token.substring(0, first);
                                    if (qualifier.isEmpty() || first != token.lastIndexOf('/')) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom token '"
                                                + token
                                                + "': expected [<group>/]pin-<name>.properties");
                                    }
                                    key = qualifier + "/" + last;
                                    value = "";
                                } else {
                                    key = expand("jenesis.bom", token);
                                    int first = key.indexOf('/');
                                    int second = key.indexOf('/', first + 1);
                                    if (words.length > 3) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom declaration '"
                                                + bom
                                                + "': expected <token> [<version> [<algorithm>/<hash>]]");
                                    }
                                    String version = words.length > 1 ? words[1] : "";
                                    if (version.startsWith(":")) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom version '"
                                                + version
                                                + "': a BOM cannot carry a classifier");
                                    }
                                    String checksum = words.length > 2 ? words[2] : "";
                                    if (!checksum.isEmpty() && !key.substring(first + 1, second).equals("module")) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom declaration '"
                                                + bom
                                                + "': a Maven BOM cannot carry a checksum");
                                    }
                                    if (!checksum.isEmpty() && checksum.indexOf('/') < 1) {
                                        throw new IllegalArgumentException("Malformed @jenesis.bom checksum '"
                                                + checksum
                                                + "': expected <algorithm>/<hash>");
                                    }
                                    value = checksum.isEmpty() ? version : version + " " + checksum;
                                }
                                if (guard == null) {
                                    boms.put(key, value);
                                } else {
                                    bomVariants.computeIfAbsent(key, _ -> new LinkedHashMap<>()).put(guard, value);
                                }
                            }
                            case "jenesis.plugin" -> {
                                String trimmed = content.trim();
                                int space = trimmed.indexOf(' ');
                                String group, token;
                                if (space > 0 && trimmed.substring(0, space).indexOf('/') < 0) {
                                    group = trimmed.substring(0, space).trim();
                                    token = trimmed.substring(space + 1).trim();
                                } else {
                                    group = "plugin";
                                    token = trimmed;
                                }
                                if (token.isEmpty()) {
                                    continue;
                                }
                                plugins.put(token.indexOf('/') < 0 ? "module/" + token : token, group);
                            }
                            case "jenesis.alias" -> {
                                String declaration = content.replaceAll("\\s+", " ").trim();
                                String[] words = declaration.split(" ");
                                if (words.length != 2) {
                                    throw new IllegalArgumentException("Malformed @jenesis.alias declaration '"
                                            + declaration
                                            + "': expected <module-name>"
                                            + " <groupId>/<artifactId>[/<type>[/<classifier>]]");
                                }
                                String alias = words[0];
                                if (alias.startsWith("java.") || alias.startsWith("jdk.")) {
                                    throw new IllegalArgumentException("Illegal @jenesis.alias name '"
                                            + alias
                                            + "': platform modules cannot be aliased");
                                }
                                if (alias.indexOf('/') >= 0) {
                                    throw new IllegalArgumentException("Illegal @jenesis.alias name '"
                                            + alias
                                            + "': expected a module name");
                                }
                                String[] segments = words[1].split("/", -1);
                                if (segments.length < 2 || segments.length > 4
                                        || Arrays.stream(segments).anyMatch(String::isEmpty)) {
                                    throw new IllegalArgumentException("Malformed @jenesis.alias target '"
                                            + words[1]
                                            + "': expected <groupId>/<artifactId>[/<type>[/<classifier>]]");
                                }
                                String value = words[1];
                                String previous = aliases.putIfAbsent(alias, value);
                                if (previous != null && !previous.equals(value)) {
                                    throw new IllegalArgumentException("Duplicate @jenesis.alias for "
                                            + alias
                                            + ": "
                                            + previous
                                            + " and "
                                            + value);
                                }
                            }
                            case "jenesis.exclude" -> {
                                String declaration = content.replaceAll("\\s+", " ").trim();
                                String[] words = declaration.split(" ");
                                if (words.length < 2) {
                                    throw new IllegalArgumentException("Malformed @jenesis.exclude declaration '"
                                            + declaration
                                            + "': expected <module-name> <groupId>/<artifactId>...");
                                }
                                String excluded = words[0];
                                if (excluded.startsWith("java.") || excluded.startsWith("jdk.")) {
                                    throw new IllegalArgumentException("Illegal @jenesis.exclude module '"
                                            + excluded
                                            + "': platform modules resolve no dependencies");
                                }
                                if (excluded.indexOf('/') >= 0) {
                                    throw new IllegalArgumentException("Illegal @jenesis.exclude module '"
                                            + excluded
                                            + "': expected a module name");
                                }
                                SequencedSet<String> targets = excludes.computeIfAbsent(
                                        excluded, _ -> new LinkedHashSet<>());
                                for (int index = 1; index < words.length; index++) {
                                    String[] segments = words[index].split("/", -1);
                                    if (segments.length != 2 || Arrays.stream(segments).anyMatch(String::isEmpty)) {
                                        throw new IllegalArgumentException("Malformed @jenesis.exclude target '"
                                                + words[index]
                                                + "': expected <groupId>/<artifactId>");
                                    }
                                    targets.add(words[index]);
                                }
                            }
                            case "jenesis.override" -> {
                                String declaration = content.replaceAll("\\s+", " ").trim();
                                String[] words = declaration.split(" ");
                                if (words.length < 2) {
                                    throw new IllegalArgumentException("Malformed @jenesis.override declaration '"
                                            + declaration
                                            + "': expected <module-name> <module-name>...");
                                }
                                for (String word : words) {
                                    if (word.startsWith("java.") || word.startsWith("jdk.")) {
                                        throw new IllegalArgumentException("Illegal @jenesis.override module '"
                                                + word
                                                + "': platform modules cannot be overridden or carry an override");
                                    }
                                    if (word.indexOf('/') >= 0) {
                                        throw new IllegalArgumentException("Illegal @jenesis.override module '"
                                                + word
                                                + "': expected a module name");
                                    }
                                }
                                SequencedSet<String> carriers = overrides.computeIfAbsent(
                                        words[0], _ -> new LinkedHashSet<>());
                                for (int index = 1; index < words.length; index++) {
                                    if (words[index].equals(words[0])) {
                                        throw new IllegalArgumentException("Illegal @jenesis.override for "
                                                + words[0]
                                                + ": a module cannot carry itself");
                                    }
                                    carriers.add(words[index]);
                                }
                            }
                            case "jenesis.attach" -> {
                                String attach = content.replaceAll("\\s+", " ").trim();
                                if (attach.isEmpty()) {
                                    continue;
                                }
                                int split = attach.indexOf(' ');
                                String token = split < 0 ? attach : attach.substring(0, split);
                                String arguments = split < 0 ? "" : attach.substring(split + 1).trim();
                                if (token.startsWith("java.") || token.startsWith("jdk.")) {
                                    throw new IllegalArgumentException("Illegal @jenesis.attach token '"
                                            + token
                                            + "': platform modules cannot be attached");
                                }
                                String key = expand("jenesis.attach", token);
                                String previous = attachments.putIfAbsent(key, arguments);
                                if (previous != null && !previous.equals(arguments)) {
                                    throw new IllegalArgumentException("Duplicate @jenesis.attach for "
                                            + key
                                            + ": '"
                                            + previous
                                            + "' and '"
                                            + arguments
                                            + "'");
                                }
                            }
                            case "jenesis.release" -> {
                                if (!content.isEmpty()) {
                                    release = content;
                                }
                            }
                            case "jenesis.test" -> {
                                String test = content.replaceAll("\\s+", " ").trim();
                                if (test.equals("abstract")) {
                                    abstractTest = true;
                                    testOf = "";
                                } else if (test.isEmpty() || SourceVersion.isName(test)) {
                                    testOf = test;
                                } else {
                                    throw new IllegalArgumentException("Malformed @jenesis.test value '"
                                            + test
                                            + "': expected no value, a module name, or 'abstract'");
                                }
                            }
                            case "jenesis.main" -> {
                                if (!content.isEmpty()) {
                                    main = content;
                                }
                            }
                        }
                    }
                }
            }
            return new ModuleInfo(module.getName().toString(),
                    release,
                    name,
                    description,
                    testOf,
                    abstractTest,
                    main,
                    dependencies,
                    runtimeDependencies,
                    plugins,
                    attachments,
                    aliases,
                    excludes,
                    overrides,
                    versions,
                    variants,
                    boms,
                    bomVariants);
        }
        throw new IllegalArgumentException("Expected module-info.java to contain module information");
    }

    private String expand(String tag, String token) {
        int firstSlash = token.indexOf('/');
        int secondSlash = firstSlash < 0 ? -1 : token.indexOf('/', firstSlash + 1);
        if (firstSlash < 0) {
            return group + "/module/" + token;
        } else if (secondSlash < 0) {
            if (firstSlash < 1 || firstSlash == token.length() - 1) {
                throw new IllegalArgumentException("Malformed @" + tag + " token '"
                        + token
                        + "': expected <module>, <groupId>/<artifactId>,"
                        + " or <group>/<repository>/<coordinate>");
            }
            return group + "/maven/" + token;
        } else {
            if (firstSlash < 1 || secondSlash == firstSlash + 1 || secondSlash == token.length() - 1) {
                throw new IllegalArgumentException("Malformed @" + tag + " token '"
                        + token
                        + "': expected <module>, <groupId>/<artifactId>,"
                        + " or <group>/<repository>/<coordinate>");
            }
            return token;
        }
    }
}
