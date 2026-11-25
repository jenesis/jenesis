package build.jenesis;

import module java.base;
import build.jenesis.Environment.Setting;
import build.jenesis.step.ProcessHandler;
import java.security.cert.X509Certificate;

@FunctionalInterface
public interface Repository {

    Optional<RepositoryItem> fetch(Executor executor, String coordinate, String extension) throws IOException;

    default Optional<RepositoryItem> fetch(Executor executor, String coordinate) throws IOException {
        return fetch(executor, coordinate, null);
    }

    default Repository prepend(Repository repository) {
        return (executor, coordinate, extension) -> {
            Optional<RepositoryItem> candidate = repository.fetch(executor, coordinate, extension);
            return candidate.isPresent() ? candidate : fetch(executor, coordinate, extension);
        };
    }

    default Repository cached(Path folder) {
        return cached(folder, false);
    }

    default Repository materialized(Path folder) {
        return cached(folder, true);
    }

    default Repository observed(Observer observer) {
        return (executor, coordinate, extension) -> {
            Optional<RepositoryItem> candidate = fetch(executor, coordinate, extension);
            if (candidate.isPresent()) {
                observer.fetched(coordinate, extension, candidate.get());
            }
            return candidate;
        };
    }

    default Repository spilled(Path folder) {
        return (executor, coordinate, extension) -> {
            Optional<RepositoryItem> candidate = fetch(executor, coordinate, extension);
            RepositoryItem item = candidate.orElse(null);
            if (item == null || item.file().isPresent()) {
                return candidate;
            }
            return Optional.of(item.spill(folder.resolve(PathPlacement.stem(coordinate)
                    + (extension == null ? ".jar" : "." + extension))));
        };
    }

    private Repository cached(Path folder, boolean snapshot) {
        if (folder == null) {
            return this;
        }
        ConcurrentMap<String, Path> cache = new ConcurrentHashMap<>();
        Set<String> internal = ConcurrentHashMap.newKeySet();
        Repository origin = this;
        return new Repository() {

            @Override
            public Optional<RepositoryItem> fetch(Executor executor, String coordinate, String extension)
                    throws IOException {
                return locate(executor, coordinate, extension == null ? ".jar" : "." + extension, extension);
            }

            private Optional<RepositoryItem> locate(Executor executor,
                                                    String coordinate,
                                                    String suffix,
                                                    String extension) throws IOException {
                try {
                    Path candidate = folder.resolve(PathPlacement.stem(coordinate) + suffix);
                    Path target = cache.computeIfAbsent(coordinate + suffix, key -> {
                        if (Files.exists(candidate)) {
                            return candidate;
                        }
                        try {
                            RepositoryItem item = (extension == null
                                    ? origin.fetch(executor, coordinate)
                                    : origin.fetch(executor, coordinate, extension)).orElse(null);
                            if (item == null) {
                                return null;
                            }
                            Path file = item.file().orElse(null);
                            if (file != null && (item.internal() || !snapshot && item.local())) {
                                if (item.internal()) {
                                    internal.add(key);
                                }
                                return file;
                            }
                            if (file != null) {
                                BuildStep.linkOrCopy(candidate, file);
                            } else {
                                Path temporary = Files.createTempFile(candidate.getParent(), "fetch", suffix);
                                try (InputStream inputStream = item.toInputStream()) {
                                    Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
                                } catch (Throwable t) {
                                    Files.deleteIfExists(temporary);
                                    throw t;
                                }
                                Files.move(temporary, candidate, StandardCopyOption.ATOMIC_MOVE);
                            }
                            return candidate;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    return target == null
                            ? Optional.empty()
                            : Optional.of(RepositoryItem.ofFile(target, internal.contains(coordinate + suffix)));
                } catch (UncheckedIOException e) {
                    throw e.getCause();
                }
            }
        };
    }

    static Repository empty() {
        return (_, _, _) -> Optional.empty();
    }

    static InputStream open(URI uri, Credential credential) throws IOException {
        return open(new Connection(), uri, credential);
    }

    static InputStream open(Connection connection, URI uri, Credential credential) throws IOException {
        return open(connection, uri, credential, Map.of());
    }

    static InputStream open(Connection settings,
                            URI uri,
                            Credential credential,
                            Map<String, String> headers) throws IOException {
        attempts:
        for (int attempt = 0; ; attempt++) {
            URI current = uri;
            try {
                for (int redirect = 0; redirect < 8; redirect++) {
                    String scheme = current.getScheme();
                    if (settings.offline() && !"file".equals(scheme)) {
                        throw new IOException("Refusing to fetch "
                                + current
                                + " while offline: only what the local caches hold is used"
                                + " (build once without -Djenesis.repository.offline=true to fill them)");
                    }
                    if (scheme != null && !scheme.equals("https") && !scheme.equals("file") && !settings.insecure()) {
                        throw new IllegalStateException("Refusing to fetch over insecure scheme '"
                                + scheme
                                + "': "
                                + current
                                + " (set -Djenesis.repository.insecure=true to allow plaintext repositories)");
                    }
                    URLConnection connection = connect(current, settings.insecure());
                    connection.setConnectTimeout(settings.connectTimeout());
                    connection.setReadTimeout(settings.readTimeout());
                    if (!(connection instanceof HttpURLConnection http)) {
                        return connection.getInputStream();
                    }
                    http.setInstanceFollowRedirects(false);
                    http.setRequestProperty("User-Agent", "Jenesis");
                    if (Objects.equals(uri.getScheme(), current.getScheme())
                            && uri.getHost() != null
                            && uri.getHost().equalsIgnoreCase(current.getHost())
                            && uri.getPort() == current.getPort()) {
                        String authorization = credential.target() == null
                                || Objects.equals(uri.getScheme(), credential.target().getScheme())
                                && uri.getHost().equalsIgnoreCase(credential.target().getHost())
                                && uri.getPort() == credential.target().getPort()
                                ? credential.authorization(settings)
                                : null;
                        if (authorization != null) {
                            http.setRequestProperty("Authorization", authorization);
                        }
                        for (Map.Entry<String, String> header : headers.entrySet()) {
                            http.setRequestProperty(header.getKey(), header.getValue());
                        }
                    }
                    int status = http.getResponseCode();
                    if (status >= 300 && status < 400) {
                        String location = http.getHeaderField("Location");
                        if (location != null) {
                            http.getInputStream().close();
                            current = current.resolve(location);
                            if (!"https".equals(current.getScheme()) && !"http".equals(current.getScheme())) {
                                throw new IllegalStateException("Refusing to follow a redirect to "
                                        + current
                                        + " (redirected from "
                                        + uri
                                        + "): a redirect is only followed to an http or https location");
                            }
                            continue;
                        }
                    }
                    if (status == HttpURLConnection.HTTP_NOT_FOUND || status == HttpURLConnection.HTTP_GONE) {
                        InputStream error = http.getErrorStream();
                        if (error != null) {
                            error.close();
                        }
                        throw new Missing(current);
                    }
                    if ((status == 429 || status >= 500) && attempt < settings.retries()) {
                        long delay = retryAfterMillis(http.getHeaderField("Retry-After"),
                                settings.backoff().toMillis() << Math.min(attempt, 20));
                        InputStream error = http.getErrorStream();
                        if (error != null) {
                            error.close();
                        }
                        pause(delay, uri);
                        continue attempts;
                    }
                    if (status == 429) {
                        throw new IOException(current + " answered 429 Too Many Requests after " + (attempt + 1)
                                + " attempt(s): the server limits how often this machine asks, so build again later or"
                                + " configure a mirror of this repository in its place");
                    }
                    return http.getInputStream();
                }
                throw new IOException("Exceeded redirect limit fetching " + uri);
            } catch (SocketException | SocketTimeoutException | SSLException | EOFException e) {
                if (attempt >= settings.retries()) {
                    throw new IOException("Failed to fetch "
                            + uri
                            + " after "
                            + (attempt + 1)
                            + " attempt(s): "
                            + e
                            + (e instanceof SSLHandshakeException && !settings.insecure()
                                    ? " (set -Djenesis.repository.insecure=true to accept a certificate that does not"
                                            + " verify, as a self-signed one does)"
                                    : ""), e);
                }
                pause(settings.backoff().toMillis() << attempt, uri);
            }
        }
    }

    static URLConnection connect(URI uri, boolean insecure) throws IOException {
        URLConnection connection = uri.toURL().openConnection();
        if (insecure && connection instanceof HttpsURLConnection https) {
            SSLContext context;
            try {
                context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[] {new X509ExtendedTrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }}, null);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Cannot switch off the certificate check for " + uri, e);
            }
            https.setSSLSocketFactory(context.getSocketFactory());
            https.setHostnameVerifier((_, _) -> true);
        }
        return connection;
    }

    private static void pause(long delay, URI uri) throws InterruptedIOException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while retrying " + uri);
        }
    }

    static long retryAfterMillis(String header, long fallback) {
        if (header == null) {
            return fallback;
        }
        String trimmed = header.trim();
        try {
            return Math.min(Long.parseLong(trimmed), 30) * 1000;
        } catch (NumberFormatException _) {
            try {
                long seconds = Duration.between(Instant.now(),
                        ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds();
                return Math.min(Math.max(seconds, 0), 30) * 1000;
            } catch (DateTimeParseException _) {
                return fallback;
            }
        }
    }

    enum Origin {

        USER,
        PROJECT,
        ENVIRONMENT,
        DEFAULT;

        public static Origin of(Environment environment, String key) {
            return environment.provided(key) ? PROJECT : USER;
        }
    }

    @FunctionalInterface
    interface Observer {

        void fetched(String coordinate, String extension, RepositoryItem item) throws IOException;
    }

    final class Missing extends FileNotFoundException {

        private final URI location;

        Missing(URI location) {
            super(location.toString());
            this.location = location;
        }

        public URI location() {
            return location;
        }
    }

    record Credential(Source source, Origin origin, URI target) {

        public static final Credential NONE = new Credential(new Source.Absent(), Origin.USER);
        private static final Map<Credential, Held> ISSUED = new ConcurrentHashMap<>();
        private static int scopes;

        public Credential {
            if (target != null && !"file".equals(target.getScheme()) && source instanceof Source.Exchange exchange) {
                exchange.endpoint(target);
            }
        }

        public Credential(Source source, Origin origin) {
            this(source, origin, null);
        }

        public static Credential of(Environment environment, String key, String variable) {
            String group = key.substring(0, key.indexOf('.'));
            String token = environment.getProperty(key, Setting.MACHINE);
            String fallback = token == null ? System.getenv(variable) : null;
            String exchange = environment.value(group + ".exchange", Setting.MACHINE);
            List<String> helper = environment.words(group + ".helper", Setting.MACHINE);
            List<String> named = new ArrayList<>();
            if (token != null) {
                named.add("jenesis." + key);
            }
            if (fallback != null) {
                named.add("the environment variable " + variable);
            }
            if (exchange != null) {
                named.add("jenesis." + group + ".exchange");
            }
            if (!helper.isEmpty()) {
                named.add("jenesis." + group + ".helper");
            }
            if (named.size() > 1) {
                throw new IllegalArgumentException(String.join(" and ", named) + " each name where the credential of"
                        + " jenesis." + group + ".uri comes from, but a credential has one source: a token, an"
                        + " exchange or a helper - keep one of them");
            }
            if (token != null) {
                return new Credential(new Source.Token(token), Origin.of(environment, key));
            }
            if (fallback != null) {
                return new Credential(new Source.Token(fallback), Origin.ENVIRONMENT);
            }
            if (!helper.isEmpty()) {
                List<String> command = new ArrayList<>(helper);
                if (command.getFirst().contains("/") || command.getFirst().contains("\\")) {
                    command.set(0, environment.located(group + ".helper", command.getFirst(), Setting.MACHINE)
                            .toAbsolutePath()
                            .toString());
                }
                return new Credential(new Source.Helper(command), Origin.USER);
            }
            if (exchange == null) {
                return NONE;
            }
            String located = environment.value(group + ".identity", Setting.MACHINE) == null
                    ? "exchange.identity"
                    : group + ".identity";
            String declared = environment.value(located, Setting.MACHINE);
            String[] words = declared == null ? new String[0] : declared.split("\\s+", 2);
            Identity identity;
            if (declared == null) {
                String uri = System.getenv("ACTIONS_ID_TOKEN_REQUEST_URL");
                String bearer = System.getenv("ACTIONS_ID_TOKEN_REQUEST_TOKEN");
                identity = System.getenv("JENESIS_IDENTITY_TOKEN") != null || uri == null || bearer == null
                        ? new Identity.Variable("JENESIS_IDENTITY_TOKEN")
                        : new Identity.GitHub(URI.create(uri), bearer);
            } else if (words.length == 2 && words[0].equals("env")) {
                identity = new Identity.Variable(words[1]);
            } else if (words.length == 2 && words[0].equals("file")) {
                identity = new Identity.File(environment.located(located, words[1], Setting.MACHINE));
            } else {
                throw new IllegalArgumentException("Malformed jenesis." + located + " '" + declared
                        + "': expected 'env <variable>' or 'file <path>', so that no identity token is a value"
                        + " anything records");
            }
            SequencedMap<String, String> parameters = new LinkedHashMap<>();
            List<String> entries = environment.entries(group + ".parameters", Setting.MACHINE);
            for (String entry : entries == null ? List.<String>of() : entries) {
                int separator = entry.indexOf('=');
                if (separator < 1) {
                    throw new IllegalArgumentException("Malformed jenesis." + group + ".parameters entry '" + entry
                            + "': expected <name>=<value>, or <name>= to leave a parameter out");
                }
                parameters.put(entry.substring(0, separator).strip(), entry.substring(separator + 1).strip());
            }
            String encoding = environment.value(group + ".encoding", "form", Setting.MACHINE);
            return new Credential(new Source.Exchange(exchange, identity)
                    .audience(environment.value(group + ".audience", Setting.MACHINE))
                    .parameters(parameters)
                    .encoding(switch (encoding) {
                        case "form" -> Encoding.FORM;
                        case "json" -> Encoding.JSON;
                        default -> throw new IllegalArgumentException("Malformed jenesis." + group + ".encoding '"
                                + encoding + "': expected form, as RFC 8693 lays the request out, or json");
                    }), Origin.USER);
        }

        public static Closeable scope() {
            synchronized (ISSUED) {
                scopes++;
            }
            AtomicBoolean closed = new AtomicBoolean();
            return () -> {
                if (closed.compareAndSet(false, true)) {
                    synchronized (ISSUED) {
                        if (--scopes == 0) {
                            ISSUED.clear();
                        }
                    }
                }
            };
        }

        public Credential source(Source source) {
            return new Credential(source, origin, target);
        }

        public Credential origin(Origin origin) {
            return new Credential(source, origin, target);
        }

        public Credential target(URI target) {
            return new Credential(source, origin, target);
        }

        public Credential grant(Origin target) {
            return origin == Origin.PROJECT
                    || target == Origin.DEFAULT
                    || target == Origin.PROJECT
                    || origin == Origin.ENVIRONMENT && target != Origin.ENVIRONMENT
                    ? source(new Source.Absent())
                    : this;
        }

        public String authorization(Connection connection) throws IOException {
            String token = token(connection);
            return token == null || !(source instanceof Source.Issuer) ? token : "Bearer " + token;
        }

        public String token(Connection connection) throws IOException {
            return switch (source) {
                case Source.Absent _ -> null;
                case Source.Token(String value) -> value;
                case Source.Issuer issuer -> {
                    if (target == null) {
                        throw new IllegalStateException("A credential that is issued for a repository names none:"
                                + " hand it the address it is for with target(...)");
                    }
                    if (connection.offline()) {
                        throw new IOException("Refusing to obtain a credential for " + target + " while offline:"
                                + " only what the local caches hold is used (build once without"
                                + " -Djenesis.repository.offline=true to fill them)");
                    }
                    Held held = ISSUED.compute(this, (_, current) -> {
                        if (current != null
                                && (current.issued().expiry() == null
                                        || Instant.now().isBefore(current.issued().expiry()))) {
                            return current;
                        }
                        try {
                            return new Held(issuer.issue(target, connection), null);
                        } catch (IOException e) {
                            return new Held(new Issued(null, Instant.now().plusSeconds(60)), e);
                        }
                    });
                    if (held.failure() != null) {
                        throw new IOException(held.failure().getMessage(), held.failure());
                    }
                    yield held.issued().token();
                }
            };
        }

        @Override
        public String toString() {
            return "Credential[source="
                    + source.getClass().getSimpleName()
                    + ", origin="
                    + origin
                    + ", target="
                    + target
                    + "]";
        }

        public enum Encoding {

            FORM,
            JSON
        }

        public record Issued(String token, Instant expiry) {

            @Override
            public String toString() {
                return "Issued[expiry=" + expiry + "]";
            }
        }

        private record Held(Issued issued, IOException failure) {
        }

        public sealed interface Source {

            record Absent() implements Source {
            }

            record Token(String value) implements Source {

                @Override
                public String toString() {
                    return "Token[***]";
                }
            }

            sealed interface Issuer extends Source {

                Issued issue(URI target, Connection connection) throws IOException;
            }

            record Exchange(String location,
                            Identity identity,
                            String audience,
                            Map<String, String> parameters,
                            Encoding encoding) implements Issuer {

                public Exchange(String location, Identity identity) {
                    this(location, identity, null, Map.of(), Encoding.FORM);
                }

                public Exchange audience(String audience) {
                    return new Exchange(location, identity, audience, parameters, encoding);
                }

                public Exchange parameters(Map<String, String> parameters) {
                    return new Exchange(location, identity, audience, parameters, encoding);
                }

                public Exchange encoding(Encoding encoding) {
                    return new Exchange(location, identity, audience, parameters, encoding);
                }

                public URI endpoint(URI target) {
                    URI endpoint;
                    try {
                        endpoint = target.resolve(new URI(location));
                    } catch (URISyntaxException e) {
                        throw new IllegalArgumentException("Malformed address of a token exchange '" + location
                                + "': expected a path on the host of " + target + ", as /api/token", e);
                    }
                    if (!Objects.equals(endpoint.getScheme(), target.getScheme())
                            || endpoint.getHost() == null
                            || !endpoint.getHost().equalsIgnoreCase(target.getHost())
                            || endpoint.getPort() != target.getPort()) {
                        throw new IllegalArgumentException("Refusing to exchange an identity token at " + endpoint
                                + " for a credential of " + target + ": a token is exchanged only with the server it"
                                + " is then sent to, at the same scheme, host and port - name the exchange as a path"
                                + " on that server, as /api/token, or obtain the credential with a helper");
                    }
                    return endpoint;
                }

                @Override
                public Issued issue(URI target, Connection connection) throws IOException {
                    URI endpoint = endpoint(target);
                    if (!"https".equals(endpoint.getScheme()) && !connection.insecure()) {
                        throw new IllegalStateException("Refusing to exchange an identity token over insecure scheme '"
                                + endpoint.getScheme()
                                + "': "
                                + endpoint
                                + " (set -Djenesis.repository.insecure=true, or -Djenesis.cache.insecure=true for"
                                + " a cache server, to allow plaintext)");
                    }
                    SequencedMap<String, String> request = new LinkedHashMap<>();
                    request.put("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
                    request.put("subject_token", identity.token(audience == null
                            ? endpoint.getScheme() + "://" + endpoint.getRawAuthority()
                            : audience, connection));
                    request.put("subject_token_type", "urn:ietf:params:oauth:token-type:id_token");
                    request.put("resource", target.toString());
                    parameters.forEach((name, value) -> {
                        if (value.isEmpty()) {
                            request.remove(name);
                        } else {
                            request.put(name, value);
                        }
                    });
                    HttpURLConnection http = (HttpURLConnection) connect(endpoint, connection.insecure());
                    try {
                        http.setRequestMethod("POST");
                        http.setConnectTimeout(connection.connectTimeout());
                        http.setReadTimeout(connection.readTimeout());
                        http.setInstanceFollowRedirects(false);
                        http.setUseCaches(false);
                        http.setRequestProperty("User-Agent", "Jenesis");
                        http.setRequestProperty("Accept", "application/json");
                        http.setRequestProperty("Content-Type", switch (encoding) {
                            case FORM -> "application/x-www-form-urlencoded";
                            case JSON -> "application/json";
                        });
                        http.setDoOutput(true);
                        try (OutputStream stream = http.getOutputStream()) {
                            stream.write((switch (encoding) {
                                case FORM -> request.entrySet().stream()
                                        .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                                                + "="
                                                + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                                        .collect(Collectors.joining("&"));
                                case JSON -> request.entrySet().stream()
                                        .map(entry -> "\"" + Json.escaped(entry.getKey())
                                                + "\":\""
                                                + Json.escaped(entry.getValue())
                                                + "\"")
                                        .collect(Collectors.joining(",", "{", "}"));
                            }).getBytes(StandardCharsets.UTF_8));
                        }
                        int status = http.getResponseCode();
                        Object answer = null;
                        try (InputStream stream = status == HttpURLConnection.HTTP_OK
                                ? http.getInputStream()
                                : http.getErrorStream()) {
                            if (stream != null) {
                                answer = Json.parse(new String(stream.readNBytes(1 << 20), StandardCharsets.UTF_8));
                            }
                        } catch (RuntimeException _) {
                        }
                        Map<?, ?> members = answer instanceof Map<?, ?> map ? map : Map.of();
                        if (status != HttpURLConnection.HTTP_OK) {
                            throw new IOException("The token exchange at " + endpoint + " for " + target
                                    + " answered " + status
                                    + (members.get("error") instanceof String error ? " " + error : "")
                                    + (members.get("error_description") instanceof String description
                                            ? ": " + description
                                            : "")
                                    + ", so that no credential was issued for the identity token of " + identity);
                        }
                        if (!(members.get("access_token") instanceof String token) || token.isBlank()) {
                            throw new IOException("The token exchange at " + endpoint + " for " + target
                                    + " answered without an access_token, the member RFC 8693 names a token in:"
                                    + " a server answering otherwise is asked by a helper");
                        }
                        if (members.get("token_type") instanceof String type && !type.equalsIgnoreCase("Bearer")) {
                            throw new IOException("The token exchange at " + endpoint + " for " + target
                                    + " issued a token of type " + type + ", but only a Bearer token is sent");
                        }
                        long lifetime = members.get("expires_in") instanceof Number seconds ? seconds.longValue() : -1;
                        return new Issued(token, lifetime < 0
                                ? null
                                : Instant.now().plusSeconds(lifetime - Math.min(30, lifetime / 10)));
                    } finally {
                        http.disconnect();
                    }
                }
            }

            record Helper(List<String> command) implements Issuer {

                @Override
                public Issued issue(URI target, Connection connection) throws IOException {
                    List<String> arguments = new ArrayList<>(command);
                    arguments.add(target.toString());
                    Process process;
                    try {
                        process = ProcessHandler.OfProcess.start(new ProcessBuilder(arguments)
                                .redirectError(ProcessBuilder.Redirect.DISCARD));
                    } catch (IOException e) {
                        throw new IOException("The credential helper " + command.getFirst() + " did not start for "
                                + target + ": " + e.getMessage(), e);
                    }
                    try {
                        process.getOutputStream().close();
                        if (!process.waitFor(Math.max(connection.readTimeout(), 1), TimeUnit.MILLISECONDS)) {
                            throw new IOException("The credential helper " + command.getFirst() + " did not answer for "
                                    + target + " within " + connection.readTimeout() + " milliseconds");
                        }
                        if (process.exitValue() != 0) {
                            throw new IOException("The credential helper " + command.getFirst() + " ended with "
                                    + process.exitValue() + " for " + target + ", so that no credential was issued");
                        }
                        String token = new String(process.getInputStream().readNBytes(1 << 16), StandardCharsets.UTF_8)
                                .lines()
                                .map(String::strip)
                                .filter(line -> !line.isEmpty())
                                .findFirst()
                                .orElse(null);
                        return new Issued(token, null);
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        throw new InterruptedIOException("Interrupted while the credential helper "
                                + command.getFirst() + " ran for " + target);
                    } finally {
                        process.destroyForcibly();
                    }
                }
            }
        }

        public sealed interface Identity {

            String token(String audience, Connection connection) throws IOException;

            record Variable(String name) implements Identity {

                @Override
                public String token(String audience, Connection connection) throws IOException {
                    String value = System.getenv(name);
                    if (value == null || value.isBlank()) {
                        throw new IOException("The environment variable " + name + " holds no identity token to"
                                + " exchange: run where GitHub Actions issues one, under permissions: id-token: write,"
                                + " or name where the job's token is read from as jenesis.exchange.identity=env"
                                + " <variable> or file <path>");
                    }
                    return value.strip();
                }
            }

            record File(Path path) implements Identity {

                @Override
                public String token(String audience, Connection connection) throws IOException {
                    String value;
                    try {
                        value = Files.readString(path).strip();
                    } catch (IOException e) {
                        throw new IOException("No identity token to exchange is read from " + path + ": " + e, e);
                    }
                    if (value.isEmpty()) {
                        throw new IOException("The file " + path + " holds no identity token to exchange");
                    }
                    return value;
                }
            }

            record GitHub(URI uri, String bearer) implements Identity {

                @Override
                public String token(String audience, Connection connection) throws IOException {
                    URI request = URI.create(uri
                            + (uri.getRawQuery() == null ? "?" : "&")
                            + "audience="
                            + URLEncoder.encode(audience, StandardCharsets.UTF_8));
                    String text;
                    try (InputStream stream = open(connection,
                            request,
                            new Credential(new Source.Token("Bearer " + bearer), Origin.USER, uri))) {
                        text = new String(stream.readNBytes(1 << 20), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new IOException("GitHub Actions issued no identity token for the audience " + audience
                                + ": " + e.getMessage(), e);
                    }
                    Object answer;
                    try {
                        answer = Json.parse(text);
                    } catch (RuntimeException e) {
                        throw new IOException("GitHub Actions issued no identity token for the audience " + audience
                                + ": its answer is no JSON document", e);
                    }
                    if (!(answer instanceof Map<?, ?> members) || !(members.get("value") instanceof String token)) {
                        throw new IOException("GitHub Actions issued no identity token for the audience " + audience
                                + ": its answer names none as value");
                    }
                    return token;
                }

                @Override
                public String toString() {
                    return "GitHub[uri=" + uri.getScheme() + "://" + uri.getRawAuthority() + "]";
                }
            }
        }
    }

    record Connection(int retries,
                      Duration backoff,
                      boolean insecure,
                      int connectTimeout,
                      int readTimeout,
                      boolean offline) {

        public Connection {
            if (retries < 0) {
                throw new IllegalArgumentException("Retries cannot be negative: " + retries);
            }
            if (backoff.isNegative()) {
                throw new IllegalArgumentException("Backoff cannot be negative: " + backoff);
            }
            if (connectTimeout < 0) {
                throw new IllegalArgumentException("Connect timeout cannot be negative: " + connectTimeout);
            }
            if (readTimeout < 0) {
                throw new IllegalArgumentException("Read timeout cannot be negative: " + readTimeout);
            }
        }

        public Connection() {
            this(Environment.NONE);
        }

        public static Connection ofEnvironment(Environment environment) {
            return new Connection(environment);
        }

        private Connection(Environment environment) {
            this(environment.number("repository.retries", 2, Setting.PROJECT),
                 Duration.ofMillis(environment.number("repository.backoff", 125, Setting.PROJECT)),
                 environment.flag("repository.insecure", Setting.MACHINE),
                 environment.number("repository.connect", 10_000, Setting.PROJECT),
                 environment.number("repository.read", 30_000, Setting.PROJECT),
                 environment.flag("repository.offline", Setting.PROJECT));
        }

        public Connection retries(int retries) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection backoff(Duration backoff) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection insecure(boolean insecure) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection connectTimeout(int connectTimeout) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection readTimeout(int readTimeout) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection offline(boolean offline) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }
    }

    static Repository ofUris(Map<String, URI> uris) {
        return ofUris(uris, null);
    }

    static <F extends BiFunction<URI, String, Optional<URI>> & Serializable> Repository ofUris(
            Map<String, URI> uris,
            F versionResolver) {
        return ofUris(uris, versionResolver, new Connection().retries(0).backoff(Duration.ZERO), null);
    }

    static <F extends BiFunction<URI, String, Optional<URI>> & Serializable> Repository ofUris(
            Map<String, URI> uris,
            F versionResolver,
            Connection connection,
            Consumer<URI> callback) {
        return (_, coordinate, extension) -> {
            if (extension != null) {
                return Optional.empty();
            }
            URI candidate = uris.get(coordinate);
            if (candidate == null && versionResolver != null) {
                int slash = coordinate.lastIndexOf('/');
                if (slash > 0) {
                    URI base = uris.get(coordinate.substring(0, slash));
                    if (base != null) {
                        candidate = versionResolver.apply(base, coordinate.substring(slash + 1)).orElse(null);
                    }
                }
            }
            if (candidate == null) {
                return Optional.empty();
            }
            URI uri = candidate;
            if (callback != null) {
                callback.accept(uri);
            }
            if (Objects.equals("file", uri.getScheme())) {
                return Optional.of(RepositoryItem.ofFile(Path.of(uri), true));
            } else {
                return Optional.of(() -> open(connection, uri, Credential.NONE));
            }
        };
    }

    static Repository ofFiles(Map<String, Path> files) {
        return (_, coordinate, extension) -> {
            Path file = extension == null ? files.get(coordinate) : null;
            return file == null ? Optional.empty() : Optional.of(RepositoryItem.ofFile(file));
        };
    }

    static Repository files() {
        return (_, coordinate, extension) -> {
            Path file = Paths.get(extension == null ? coordinate : coordinate + "." + extension);
            return Files.exists(file) ? Optional.of(RepositoryItem.ofFile(file)) : Optional.empty();
        };
    }

    static Map<String, Repository> ofProperties(String suffix,
                                                Iterable<Path> folders,
                                                BiFunction<Path, String, URI> resolver,
                                                Path cache) throws IOException {
        return ofProperties(suffix, folders, resolver, null, cache);
    }

    static <F extends BiFunction<URI, String, Optional<URI>> & Serializable> Map<String, Repository> ofProperties(
            String suffix,
            Iterable<Path> folders,
            BiFunction<Path, String, URI> resolver,
            F versionResolver,
            Path cache) throws IOException {
        Map<String, Map<String, URI>> artifacts = new HashMap<>();
        for (Path folder : folders) {
            Path file = folder.resolve(suffix);
            if (Files.exists(file)) {
                SequencedProperties properties = SequencedProperties.ofFiles(file);
                for (String coordinate : properties.stringPropertyNames()) {
                    String location = properties.getProperty(coordinate);
                    if (!location.isEmpty()) {
                        int index = coordinate.indexOf('/');
                        artifacts.computeIfAbsent(
                                coordinate.substring(0, index),
                                _ -> new HashMap<>()).put(coordinate.substring(index + 1), resolver.apply(folder, location));
                    }
                }
            }
        }
        return artifacts.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), Repository.ofUris(entry.getValue(), versionResolver).cached(cache)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static Map<String, Repository> prepend(Map<String, ? extends Repository> left,
                                           Map<String, ? extends Repository> right) {
        return Stream.concat(left.entrySet().stream(), right.entrySet().stream()).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                Repository::prepend));
    }
}
