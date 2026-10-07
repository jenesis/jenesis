package build.jenesis;

import module java.base;

public final class DnsLookup {

    private static final String DEFAULT_RESOLVER = "https://cloudflare-dns.com/dns-query", LABEL = "_java";
    private static final Pattern SUFFIX = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,63}(\\.[A-Za-z0-9_-]{1,63})+");
    private static final int TXT = 16, NOERROR = 0, NXDOMAIN = 3;

    private final URI uri;
    private final boolean secure;
    private final Repository.Connection connection;
    private final Map<String, Answer> answers = new ConcurrentHashMap<>();

    public DnsLookup() {
        this(URI.create(DEFAULT_RESOLVER), true, new Repository.Connection());
    }

    public static DnsLookup ofEnvironment(Environment environment) {
        return new DnsLookup(URI.create(environment.value("dns.uri", DEFAULT_RESOLVER)),
                             environment.flag("dns.secure", true),
                             Repository.Connection.ofEnvironment(environment));
    }

    public DnsLookup(URI uri, boolean secure, Repository.Connection connection) {
        this.uri = uri;
        this.secure = secure;
        this.connection = connection;
    }

    public DnsLookup uri(URI uri) {
        return new DnsLookup(uri, secure, connection);
    }

    public DnsLookup secure(boolean secure) {
        return new DnsLookup(uri, secure, connection);
    }

    public DnsLookup connection(Repository.Connection connection) {
        return new DnsLookup(uri, secure, connection);
    }

    public static String name(String namespace) {
        List<String> labels = Arrays.asList(namespace.split("\\."));
        Collections.reverse(labels);
        return LABEL + "." + String.join(".", labels);
    }

    public Optional<DnsLocation> lookup(String namespace, String... keys) throws IOException {
        if (!NAME.matcher(namespace).matches()) {
            return Optional.empty();
        }
        String[] labels = namespace.split("\\.");
        for (int count = labels.length; count >= 2; count--) {
            String name = name(String.join(".", Arrays.copyOf(labels, count)));
            Answer answer = answers.get(name);
            if (answer == null) {
                answer = query(name);
                answers.put(name, answer);
            }
            List<Map.Entry<String, String>> found = new ArrayList<>();
            for (String text : answer.texts()) {
                for (String key : keys) {
                    if (text.startsWith(key + "=")) {
                        found.add(Map.entry(key, text.substring(key.length() + 1).strip()));
                    }
                }
            }
            if (found.size() > 1) {
                throw new IllegalStateException("The DNS name " + name + " holds " + found.size() + " TXT records"
                        + " starting with "
                        + Arrays.stream(keys).map(key -> key + "=").collect(Collectors.joining(" or "))
                        + ", but what it says must be unambiguous: " + found);
            }
            if (!found.isEmpty()) {
                if (secure && !answer.authenticated()) {
                    throw new IllegalStateException("The TXT record of " + name + " is not authenticated by DNSSEC,"
                            + " so anyone on the path to the resolver could have written it: sign the zone, or set"
                            + " -Djenesis.dns.secure=false to accept an unsigned record");
                }
                String value = found.getFirst().getValue();
                String[] tokens = value.split("\\s+");
                Map<String, String> attributes = new HashMap<>();
                for (int index = 1; index < tokens.length; index++) {
                    int equals = tokens[index].indexOf('=');
                    String attribute = equals < 0 ? tokens[index] : tokens[index].substring(0, equals);
                    String form = switch (attribute) {
                        case "since" -> "since=<version>";
                        case "suffixes" -> "suffixes=<suffix>[,<suffix>...]";
                        default -> throw new IllegalArgumentException("The TXT record of " + name
                                + " holds the attribute '" + attribute + "', but the attributes after a location are"
                                + " since=<version> and suffixes=<suffix>[,<suffix>...]");
                    };
                    if (attributes.containsKey(attribute) || equals < 0 || equals == tokens[index].length() - 1) {
                        throw new IllegalArgumentException("The TXT record of " + name + " must name " + form
                                + " once and with a value: " + value);
                    }
                    attributes.put(attribute, tokens[index].substring(equals + 1));
                }
                List<String> suffixes = null;
                if (attributes.containsKey("suffixes")) {
                    suffixes = new ArrayList<>();
                    for (String suffix : attributes.get("suffixes").split(",", -1)) {
                        if (!SUFFIX.matcher(suffix).matches()) {
                            throw new IllegalArgumentException("The TXT record of " + name + " lists the suffix '"
                                    + suffix + "', where a suffix is a word of letters and digits, such as SNAPSHOT"
                                    + " or rc, or none for a version without one: " + value);
                        }
                        suffixes.add(suffix.toLowerCase(Locale.ROOT));
                    }
                }
                return Optional.of(new DnsLocation(name,
                        found.getFirst().getKey(),
                        tokens[0],
                        attributes.get("since"),
                        suffixes == null ? null : List.copyOf(suffixes)));
            }
        }
        return Optional.empty();
    }

    private Answer query(String name) throws IOException {
        URI query = URI.create(uri + (uri.getRawQuery() == null ? "?" : "&")
                + "name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&type=TXT");
        Map<?, ?> response;
        try (InputStream inputStream = Repository.open(connection,
                                                       query,
                                                       null,
                                                       Map.of("Accept", "application/dns-json"))) {
            Object parsed = Json.parse(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            if (!(parsed instanceof Map<?, ?> map)) {
                throw new IOException("The DNS resolver " + uri + " did not answer a JSON object for " + name);
            }
            response = map;
        }
        int status = response.get("Status") instanceof Number number ? number.intValue() : -1;
        if (status != NOERROR && status != NXDOMAIN) {
            throw new IOException("The DNS resolver " + uri + " answered status " + status + " for " + name
                    + " (2 is SERVFAIL, which is also how a resolver reports a DNSSEC signature that does not verify)");
        }
        List<String> texts = new ArrayList<>();
        if (response.get("Answer") instanceof List<?> records) {
            for (Object record : records) {
                if (record instanceof Map<?, ?> map
                        && map.get("type") instanceof Number kind
                        && kind.intValue() == TXT
                        && map.get("data") instanceof String data) {
                    texts.add(text(data));
                }
            }
        }
        return new Answer(List.copyOf(texts), Boolean.TRUE.equals(response.get("AD")));
    }

    private static String text(String data) {
        if (!data.startsWith("\"")) {
            return data;
        }
        StringBuilder builder = new StringBuilder(data.length());
        boolean quoted = false;
        for (int index = 0; index < data.length(); index++) {
            char character = data.charAt(index);
            if (character == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                continue;
            } else if (character != '\\' || index + 1 == data.length()) {
                builder.append(character);
            } else if (index + 3 < data.length()
                    && Character.isDigit(data.charAt(index + 1))
                    && Character.isDigit(data.charAt(index + 2))
                    && Character.isDigit(data.charAt(index + 3))) {
                builder.append((char) Integer.parseInt(data.substring(index + 1, index + 4)));
                index += 3;
            } else {
                builder.append(data.charAt(++index));
            }
        }
        return builder.toString();
    }

    private record Answer(List<String> texts, boolean authenticated) {
    }
}
