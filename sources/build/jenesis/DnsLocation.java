package build.jenesis;

import module java.base;
import build.jenesis.maven.MavenDefaultVersionNegotiator;

public record DnsLocation(String name, String key, String target, String since, List<String> suffixes) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]*)}");
    private static final String CLASSIFIER = "-classifier", TYPE = "type";
    private static final List<Map.Entry<String, String>> CHECKSUMS = List.of(Map.entry("sha512", "SHA-512"),
            Map.entry("sha256", "SHA-256"),
            Map.entry("sha1", "SHA-1"));

    public boolean admits(String version) {
        if (version == null) {
            return since == null && suffixes == null;
        }
        if (since != null && MavenDefaultVersionNegotiator.compareVersions(version, since) < 0) {
            return false;
        }
        if (suffixes == null) {
            return true;
        }
        int dash = version.indexOf('-');
        if (dash < 0) {
            return suffixes.contains("none");
        }
        String qualifier = version.substring(dash + 1).toLowerCase(Locale.ROOT);
        for (String suffix : suffixes) {
            if (qualifier.startsWith(suffix) && (qualifier.length() == suffix.length()
                    || !Character.isLetter(qualifier.charAt(suffix.length())))) {
                return true;
            }
        }
        return false;
    }

    public boolean template() {
        return PLACEHOLDER.matcher(target).find();
    }

    public String suffix(String namespace) {
        String[] labels = namespace.split("\\.");
        int below = name.split("\\.").length - 1;
        if (below > labels.length || !name.equals(DnsLookup.name(String.join(".", Arrays.copyOf(labels, below))))) {
            throw new IllegalArgumentException(namespace + " is not below the TXT record of " + name);
        }
        return below == labels.length
                ? ""
                : "-" + String.join("-", Arrays.copyOfRange(labels, below, labels.length));
    }

    public URI root(Repository.Connection connection) {
        if (template()) {
            throw new IllegalStateException("The TXT record of " + name + " names a template, not a root: " + target);
        }
        return secured(URI.create(target.endsWith("/") ? target : target + "/"), connection);
    }

    public Optional<URI> resolve(Map<String, String> values, Repository.Connection connection) {
        return expand(values).map(text -> secured(URI.create(text), connection));
    }

    public Optional<String> expand(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(target);
        Set<String> used = new HashSet<>();
        while (matcher.find()) {
            String placeholder = matcher.group(1);
            if (!values.containsKey(placeholder)) {
                throw new IllegalArgumentException("The TXT record of " + name + " uses the placeholder {"
                        + placeholder + "}, but a " + key + "= record knows only "
                        + values.keySet().stream().sorted().map(key -> "{" + key + "}").toList());
            }
            if (values.get(placeholder) == null) {
                return Optional.empty();
            }
            used.add(placeholder);
        }
        if (!used.contains(CLASSIFIER) && values.get(CLASSIFIER) != null && !values.get(CLASSIFIER).isEmpty()
                || !used.contains(TYPE) && values.get(TYPE) != null && !values.get(TYPE).equals("jar")) {
            return Optional.empty();
        }
        return Optional.of(matcher.reset().replaceAll(result -> Matcher.quoteReplacement(values.get(result.group(1)))));
    }

    public Optional<RepositoryItem> fetch(Map<String, String> values,
                                          boolean validate,
                                          Repository.Connection connection,
                                          Consumer<String> printing,
                                          Palette palette) throws IOException {
        URI location = resolve(values, connection).orElse(null);
        byte[] bytes = location == null ? null : read(location, connection);
        if (bytes == null) {
            return Optional.empty();
        }
        if (validate && values.get(TYPE) != null && target.contains("{" + TYPE + "}")) {
            for (Map.Entry<String, String> checksum : CHECKSUMS) {
                Map<String, String> sibling = new HashMap<>(values);
                sibling.put(TYPE, values.get(TYPE) + "." + checksum.getKey());
                byte[] published = read(resolve(sibling, connection).orElseThrow(), connection);
                if (published == null) {
                    continue;
                }
                String expected = new String(published, StandardCharsets.US_ASCII).strip().split("\\s+")[0];
                String actual;
                try {
                    actual = HexFormat.of().formatHex(MessageDigest.getInstance(checksum.getValue()).digest(bytes));
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("The JDK offers no " + checksum.getValue(), e);
                }
                if (!actual.equalsIgnoreCase(expected)) {
                    throw new IllegalStateException("Failed checksum validation for " + location
                            + " against its " + checksum.getValue() + " checksum, named by the TXT record of " + name);
                }
                break;
            }
        }
        if (printing != null) {
            printing.accept("%s%-11s%s %s".formatted(palette.info(), "[FETCHED]", palette.reset(), location));
        }
        return Optional.of(() -> new ByteArrayInputStream(bytes));
    }

    private URI secured(URI uri, Repository.Connection connection) {
        if (!"https".equals(uri.getScheme()) && !("http".equals(uri.getScheme()) && connection.insecure())) {
            throw new IllegalStateException("The TXT record of " + name + " points to " + uri
                    + ", but a location named by DNS is only read over https"
                    + " (set -Djenesis.repository.insecure=true to allow plaintext http)");
        }
        return uri;
    }

    private static byte[] read(URI uri, Repository.Connection connection) throws IOException {
        try (InputStream inputStream = Repository.open(connection, uri, null)) {
            return inputStream.readAllBytes();
        } catch (FileNotFoundException _) {
            return null;
        }
    }
}
