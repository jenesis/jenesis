package build.jenesis;

import module java.base;

public final class Discovery {

    private static final String LOCATION = "https://{domain}/.well-known/java-repository.properties";
    private static final Pattern SUFFIX = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,63}(\\.[A-Za-z0-9_-]{1,63})+");

    private final String uri;
    private final Repository.Connection connection;
    private final Map<String, FutureTask<Optional<Properties>>> files = new ConcurrentHashMap<>();

    public Discovery() {
        this(LOCATION, new Repository.Connection());
    }

    public static Discovery ofEnvironment(Environment environment) {
        return new Discovery(LOCATION, Repository.Connection.ofEnvironment(environment));
    }

    public Discovery(String uri, Repository.Connection connection) {
        if (!uri.contains("{domain}")) {
            throw new IllegalArgumentException("The location of a domain's file must name {domain}: " + uri);
        }
        this.uri = uri;
        this.connection = connection;
    }

    public Discovery uri(String uri) {
        return new Discovery(uri, connection);
    }

    public Discovery connection(Repository.Connection connection) {
        return new Discovery(uri, connection);
    }

    public static String domain(String namespace) {
        List<String> labels = Arrays.asList(namespace.split("\\."));
        Collections.reverse(labels);
        return String.join(".", labels);
    }

    public Optional<DiscoveredLocation> lookup(String namespace, String key) throws IOException {
        if (connection.offline() || !NAME.matcher(namespace).matches()) {
            return Optional.empty();
        }
        String[] labels = namespace.split("\\.");
        DiscoveredLocation found = null;
        for (int count = 2; count <= labels.length; count++) {
            String domain = domain(String.join(".", Arrays.copyOf(labels, count)));
            URI source = URI.create(uri.replace("{domain}", domain));
            FutureTask<Optional<Properties>> task = new FutureTask<>(() -> {
                Properties properties = new Properties();
                try (InputStream inputStream = Repository.open(connection, source, null);
                     Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                } catch (IOException e) {
                    if (e instanceof SSLException || e.getCause() instanceof SSLException) {
                        throw e;
                    }
                    return Optional.empty();
                }
                return Optional.of(properties);
            });
            FutureTask<Optional<Properties>> existing = files.putIfAbsent(domain, task);
            if (existing == null) {
                task.run();
                existing = task;
            }
            Optional<Properties> file;
            try {
                file = existing.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Interrupted while reading " + source);
            } catch (ExecutionException e) {
                switch (e.getCause()) {
                    case IOException exception -> throw exception;
                    case RuntimeException exception -> throw exception;
                    case Error error -> throw error;
                    default -> throw new IllegalStateException(e.getCause());
                }
            }
            if (file.isEmpty()) {
                continue;
            }
            Properties properties = file.get();
            String stop = value(properties, source, "stop");
            if (stop != null && !stop.equals("true") && !stop.equals("false")) {
                throw new IllegalArgumentException(source + " names stop=" + stop + ", where it expects true, the"
                        + " default, to answer alone for every name below " + domain + ", or false to let the files"
                        + " of its subdomains answer first");
            }
            if (properties.containsKey(key)) {
                String suffixes = value(properties, source, key + ".suffixes");
                List<String> admitted = null;
                if (suffixes != null) {
                    admitted = new ArrayList<>();
                    for (String suffix : suffixes.split(",", -1)) {
                        String word = suffix.strip();
                        if (!SUFFIX.matcher(word).matches()) {
                            throw new IllegalArgumentException(source + " lists the suffix '" + word + "' in " + key
                                    + ".suffixes, where a suffix is a word of letters and digits, such as SNAPSHOT"
                                    + " or rc, or none for a version without one");
                        }
                        admitted.add(word.toLowerCase(Locale.ROOT));
                    }
                }
                DiscoveredLocation location = new DiscoveredLocation(domain,
                        source,
                        key,
                        value(properties, source, key),
                        value(properties, source, key + ".since"),
                        admitted == null ? null : List.copyOf(admitted),
                        value(properties, source, key + ".latest"));
                if (location.latest() != null
                        && (location.coordinate() || !location.target().contains("{version}"))) {
                    throw new IllegalArgumentException(source + " names " + key + ".latest beside "
                            + location.target() + ", but only a template naming {version} needs to be told the"
                            + " newest version, as a root or a Maven coordinate lists its versions itself");
                }
                if (!location.coordinate() || location.template() || count == labels.length) {
                    found = location;
                }
            }
            if (!"false".equals(stop)) {
                break;
            }
        }
        return Optional.ofNullable(found);
    }

    private static String value(Properties properties, URI source, String key) {
        String value = properties.getProperty(key);
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(source + " names " + key + " without a value");
        }
        return value == null ? null : value.strip();
    }
}
