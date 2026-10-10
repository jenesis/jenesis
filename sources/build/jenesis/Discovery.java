package build.jenesis;

import module java.base;
import java.security.cert.CertificateException;

public final class Discovery {

    private static final String LOCATION = "https://{domain}/.well-known/java-repository.properties";
    private static final Pattern SUFFIX = Pattern.compile("[A-Za-z0-9]+");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,63}(\\.[A-Za-z0-9_-]{1,63})+");
    private static final Pattern SELECTED = Pattern.compile("([a-z]+)\\[([^\\]]*)]");
    private static final Pattern SELECTOR = Pattern.compile("[A-Za-z0-9_.-]+\\*?");

    private final String uri;
    private final Repository.Connection connection;
    private final int timeout;
    private final Map<String, FutureTask<Optional<Properties>>> files = new ConcurrentHashMap<>();

    public Discovery() {
        this(LOCATION, new Repository.Connection(), 5_000);
    }

    public static Discovery ofEnvironment(Environment environment) {
        return new Discovery(LOCATION,
                Repository.Connection.ofEnvironment(environment),
                environment.number("repository.discovery.timeout", 5_000));
    }

    public Discovery(String uri, Repository.Connection connection, int timeout) {
        if (!uri.contains("{domain}")) {
            throw new IllegalArgumentException("The location of a domain's file must name {domain}: " + uri);
        }
        if (timeout <= 0) {
            throw new IllegalArgumentException("The timeout for a domain's file is " + timeout + ", where it is a"
                    + " positive number of milliseconds within which the domain connects and answers each read,"
                    + " such as 5000, the default, as a domain that never answers would otherwise stall the build");
        }
        this.uri = uri;
        this.connection = connection;
        this.timeout = timeout;
    }

    public Discovery uri(String uri) {
        return new Discovery(uri, connection, timeout);
    }

    public Discovery connection(Repository.Connection connection) {
        return new Discovery(uri, connection, timeout);
    }

    public Discovery timeout(int timeout) {
        return new Discovery(uri, connection, timeout);
    }

    public static String domain(String namespace) {
        List<String> labels = Arrays.asList(namespace.split("\\."));
        Collections.reverse(labels);
        return String.join(".", labels);
    }

    public Optional<DiscoveredLocation> lookup(String namespace, String kind, String name) throws IOException {
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
                Repository.Connection once = connection.retries(0).connectTimeout(timeout).readTimeout(timeout);
                try (InputStream inputStream = Repository.open(once, source, null);
                     Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    properties.load(reader);
                } catch (IOException e) {
                    boolean handshake = false;
                    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
                        handshake |= cause instanceof SSLHandshakeException;
                        if (cause instanceof SSLPeerUnverifiedException
                                || handshake && (cause instanceof CertificateException
                                || cause instanceof CertPathValidatorException
                                || cause instanceof CertPathBuilderException)) {
                            throw new IOException(source + " is served with a certificate that does not verify, so"
                                    + " what " + domain + " publishes can neither be trusted nor taken for absent"
                                    + " (set -Djenesis.repository.insecure=true to accept such a certificate,"
                                    + " or -Djenesis.repository.discovery=false to ask no domain)", e);
                        }
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
            String delegate = value(properties, source, "delegate");
            if (delegate != null && !delegate.equals("true") && !delegate.equals("false")) {
                throw new IllegalArgumentException(source + " names delegate=" + delegate + ", where it expects false,"
                        + " the default, to answer alone for every name below " + domain + ", or true to let the files"
                        + " of its subdomains answer first");
            }
            String key = properties.containsKey(kind) ? kind : null;
            int matched = -1;
            for (String property : properties.stringPropertyNames()) {
                Matcher matcher = SELECTED.matcher(property);
                if (!matcher.matches() || !matcher.group(1).equals(kind)) {
                    continue;
                }
                String selector = matcher.group(2);
                if (!SELECTOR.matcher(selector).matches()) {
                    throw new IllegalArgumentException(source + " names " + property + ", where a selector is a module"
                            + " name or an artifact ID, such as " + kind + "[build.jenesis.launcher], or the start of"
                            + " one followed by *, such as " + kind + "[byte-buddy-*]");
                }
                int score = selector.endsWith("*")
                        ? (name.startsWith(selector.substring(0, selector.length() - 1)) ? selector.length() - 1 : -1)
                        : (name.equals(selector) ? Integer.MAX_VALUE : -1);
                if (score > matched) {
                    key = property;
                    matched = score;
                }
            }
            if (key != null) {
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
                if (!location.coordinate() || location.template() || count == labels.length || matched >= 0) {
                    found = location;
                }
            }
            if (!"true".equals(delegate)) {
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
