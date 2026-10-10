package build.jenesis.test;

import module java.base;
import module jdk.httpserver;
import build.jenesis.Discovery;
import build.jenesis.Repository;

public final class DiscoveryServer implements AutoCloseable {

    private static final String WELL_KNOWN = "/.well-known/java-repository.properties";

    private final HttpServer server;
    private final Map<String, String> domains = new ConcurrentHashMap<>();
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final List<String> queried = new CopyOnWriteArrayList<>();
    private final Map<String, Map.Entry<Integer, List<String>>> answers = new ConcurrentHashMap<>();
    private final List<String> asked = new CopyOnWriteArrayList<>();

    public DiscoveryServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/domains/", exchange -> {
            String path = exchange.getRequestURI().getPath().substring("/domains/".length());
            String domain = path.endsWith(WELL_KNOWN) ? path.substring(0, path.length() - WELL_KNOWN.length()) : path;
            queried.add(domain);
            String content = domains.get(domain);
            respond(exchange, content == null ? 404 : 200, content == null ? "" : content);
        });
        server.createContext("/files/", exchange -> {
            byte[] content = files.get(exchange.getRequestURI().getPath().substring("/files/".length()));
            respond(exchange, content == null ? 404 : 200, content == null ? new byte[0] : content);
        });
        server.createContext("/latest/", exchange -> {
            String path = exchange.getRequestURI().getPath().substring("/latest/".length());
            asked.add(exchange.getRequestMethod() + " " + path);
            Map.Entry<Integer, List<String>> answer = answers.get(path);
            if (answer == null) {
                respond(exchange, 404, "");
                return;
            }
            for (int index = 0; index < answer.getValue().size(); index += 2) {
                exchange.getResponseHeaders().set(answer.getValue().get(index), answer.getValue().get(index + 1));
            }
            exchange.sendResponseHeaders(answer.getKey(), -1);
            exchange.close();
        });
        server.start();
    }

    public DiscoveryServer domain(String domain, String... lines) {
        domains.put(domain, String.join("\n", lines) + "\n");
        return this;
    }

    public DiscoveryServer withdraw(String domain) {
        domains.remove(domain);
        return this;
    }

    public DiscoveryServer file(String path, String content) {
        return file(path, content.getBytes(StandardCharsets.UTF_8));
    }

    public DiscoveryServer file(String path, byte[] content) {
        files.put(path, content);
        return this;
    }

    public DiscoveryServer answer(String path, int status, String... headers) {
        answers.put(path, Map.entry(status, List.of(headers)));
        return this;
    }

    public List<String> asked() {
        return asked;
    }

    public String latest() {
        return root() + "/latest/";
    }

    public List<String> queried() {
        return queried;
    }

    public String root() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    public String files() {
        return root() + "/files/";
    }

    public String uri() {
        return root() + "/domains/{domain}" + WELL_KNOWN;
    }

    public Repository.Connection connection() {
        return new Repository.Connection().insecure(true).retries(0);
    }

    public Discovery discovery() {
        return new Discovery().uri(uri()).connection(connection()).ttl(Duration.ZERO);
    }

    public DiscoveryServer context(String path, String content) {
        return context(path, content, new ArrayList<>());
    }

    public DiscoveryServer context(String path, String content, List<String> requested) {
        return context(path, content.getBytes(StandardCharsets.UTF_8), requested);
    }

    public DiscoveryServer context(String path, byte[] content, List<String> requested) {
        server.createContext(path, exchange -> {
            requested.add(exchange.getRequestURI().getPath());
            respond(exchange, 200, content);
        });
        return this;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String content) throws IOException {
        respond(exchange, status, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
