package build.jenesis.test;

import module java.base;
import module jdk.httpserver;
import build.jenesis.DnsLookup;
import build.jenesis.Json;
import build.jenesis.Repository;

public final class DnsServer implements AutoCloseable {

    private final HttpServer server;
    private final Map<String, List<String>> records = new ConcurrentHashMap<>();
    private final Map<String, String> files = new ConcurrentHashMap<>();
    private final List<String> queried = new CopyOnWriteArrayList<>();
    private volatile boolean authenticated = true;

    public DnsServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/dns-query", exchange -> {
            String name = null;
            for (String parameter : exchange.getRequestURI().getRawQuery().split("&")) {
                if (parameter.startsWith("name=")) {
                    name = URLDecoder.decode(parameter.substring(5), StandardCharsets.UTF_8);
                }
            }
            queried.add(name);
            List<String> data = records.get(name);
            StringJoiner answers = new StringJoiner(",");
            for (String value : data == null ? List.<String>of() : data) {
                answers.add("{\"name\":\"" + name + "\",\"type\":16,\"TTL\":300,\"data\":\""
                        + Json.escaped(value) + "\"}");
            }
            respond(exchange, 200, "{\"Status\":" + (data == null ? 3 : 0)
                    + ",\"AD\":" + authenticated
                    + ",\"Answer\":[" + answers + "]}");
        });
        server.createContext("/files/", exchange -> {
            String content = files.get(exchange.getRequestURI().getPath().substring("/files/".length()));
            respond(exchange, content == null ? 404 : 200, content == null ? "" : content);
        });
        server.start();
    }

    public DnsServer record(String name, String... texts) {
        records.put(name, List.of(texts));
        return this;
    }

    public DnsServer file(String path, String content) {
        files.put(path, content);
        return this;
    }

    public DnsServer authenticated(boolean authenticated) {
        this.authenticated = authenticated;
        return this;
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

    public URI resolver() {
        return URI.create(root() + "/dns-query");
    }

    public Repository.Connection connection() {
        return new Repository.Connection().insecure(true).retries(0);
    }

    public DnsLookup lookup() {
        return new DnsLookup().uri(resolver()).connection(connection());
    }

    public DnsServer context(String path, String content) {
        return context(path, content, new ArrayList<>());
    }

    public DnsServer context(String path, String content, List<String> requested) {
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
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            exchange.getResponseBody().write(body);
        }
        exchange.close();
    }
}
