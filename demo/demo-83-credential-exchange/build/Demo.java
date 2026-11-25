package build;

import module java.base;
import module jdk.httpserver;
import build.jenesis.MakeTool;

/**
 * Builds this project three times against a cache server that takes no stored secret. The
 * server is a stand-in started here, on a port of this machine: it keeps what a build
 * stores in memory, answers only a request that carries a token it issued, and issues one
 * at POST /api/token for the identity token of the job, as RFC 8693 lays the exchange out.
 * The identity token is a file here; on GitHub Actions it is the token the job is issued.
 *
 *     java build/Demo.java
 *
 * prints what the server saw of each build:
 *
 *     build 1 -> 0: 1 exchange, 0 steps loaded, 21 steps stored
 *     build 2 -> 0: 1 exchange, 21 steps loaded, 0 steps stored
 *     build 3 -> 0: 0 exchanges, 21 steps loaded, 0 steps stored, asked with the token of the helper
 *     requests the server refused for want of a token: 0
 *     requests besides the exchange that carried the identity token: 0
 */
public class Demo {

    private static final String IDENTITY = "the.identity.token.of.this.job";

    public static void main(String... args) throws Exception {
        Path identity = Files.writeString(Files.createTempDirectory("demo-identity").resolve("token"), IDENTITY);
        Map<String, byte[]> stored = new ConcurrentHashMap<>();
        Set<String> issued = ConcurrentHashMap.newKeySet();
        AtomicInteger exchanges = new AtomicInteger(), loaded = new AtomicInteger(), written = new AtomicInteger(),
                refused = new AtomicInteger(), leaked = new AtomicInteger(), helped = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        String origin = "http://localhost:" + server.getAddress().getPort(), cache = origin + "/build/demo/";
        String helper = "helper-token-for-" + cache.hashCode();
        server.createContext("/api/token", exchange -> {
            try (exchange) {
                Map<String, String> form = new HashMap<>();
                for (String pair : new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).split("&")) {
                    int separator = pair.indexOf('=');
                    form.put(URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8));
                }
                boolean admitted = "urn:ietf:params:oauth:grant-type:token-exchange".equals(form.get("grant_type"))
                        && "urn:ietf:params:oauth:token-type:id_token".equals(form.get("subject_token_type"))
                        && IDENTITY.equals(form.get("subject_token"))
                        && cache.equals(form.get("resource"));
                String token = "issued-" + exchanges.incrementAndGet();
                if (admitted) {
                    issued.add(token);
                }
                byte[] answer = (admitted
                        ? "{\"access_token\":\"" + token + "\",\"issued_token_type\":"
                                + "\"urn:ietf:params:oauth:token-type:access_token\",\"token_type\":\"Bearer\","
                                + "\"expires_in\":300}"
                        : "{\"error\":\"invalid_request\",\"error_description\":\"no trust admits the token\"}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add("Cache-Control", "no-store");
                exchange.sendResponseHeaders(admitted ? 200 : 400, answer.length);
                exchange.getResponseBody().write(answer);
            }
        });
        server.createContext("/build/demo/", exchange -> {
            try (exchange) {
                byte[] body = exchange.getRequestBody().readAllBytes();
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (String.valueOf(exchange.getRequestHeaders()).contains(IDENTITY)) {
                    leaked.incrementAndGet();
                }
                String token = authorization != null && authorization.startsWith("Bearer ")
                        ? authorization.substring(7)
                        : null;
                if (!issued.contains(token) && !helper.equals(token)) {
                    refused.incrementAndGet();
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                if (helper.equals(token)) {
                    helped.incrementAndGet();
                }
                String entry = exchange.getRequestURI().getPath();
                switch (exchange.getRequestMethod()) {
                    case "PUT" -> {
                        stored.put(entry, body);
                        written.incrementAndGet();
                        exchange.sendResponseHeaders(201, -1);
                    }
                    case "GET" -> {
                        byte[] content = stored.get(entry);
                        if (content == null) {
                            exchange.sendResponseHeaders(404, -1);
                        } else {
                            loaded.incrementAndGet();
                            exchange.sendResponseHeaders(200, content.length);
                            exchange.getResponseBody().write(content);
                        }
                    }
                    default -> exchange.sendResponseHeaders(stored.containsKey(entry) ? 200 : 404, -1);
                }
            }
        });
        server.start();
        try {
            List<List<String>> builds = List.of(
                    List.of("-Djenesis.cache.exchange=/api/token", "-Djenesis.cache.identity=file " + identity),
                    List.of("-Djenesis.cache.exchange=/api/token", "-Djenesis.cache.identity=file " + identity),
                    List.of("-Djenesis.cache.helper=java build/Helper.java"));
            for (int index = 0; index < builds.size(); index++) {
                int before = exchanges.get();
                loaded.set(0);
                written.set(0);
                List<String> arguments = new ArrayList<>(List.of("-Djenesis.cache.uri=" + cache,
                        "-Djenesis.executor.rebuild=true",
                        "-Djenesis.print.progress=false"));
                arguments.addAll(builds.get(index));
                StringWriter out = new StringWriter(), err = new StringWriter();
                int code = ToolProvider.findFirst("jenesis-make").orElseGet(MakeTool::new).run(new PrintWriter(out),
                        new PrintWriter(err),
                        arguments.toArray(String[]::new));
                if (code != 0) {
                    throw new IllegalStateException("Build " + (index + 1) + " failed with " + code + "\n" + out + err);
                }
                if ((out.toString() + err).contains(IDENTITY)) {
                    throw new IllegalStateException("Build " + (index + 1) + " printed the identity token");
                }
                int exchanged = exchanges.get() - before;
                System.out.printf("build %d -> %d: %d exchange%s, %d steps loaded, %d steps stored%s%n",
                        index + 1,
                        code,
                        exchanged,
                        exchanged == 1 ? "" : "s",
                        loaded.get(),
                        written.get(),
                        helped.get() > 0 ? ", asked with the token of the helper" : "");
            }
            System.out.println("requests the server refused for want of a token: " + refused.get());
            System.out.println("requests besides the exchange that carried the identity token: " + leaked.get());
        } finally {
            server.stop(0);
        }
    }
}
