package build;

import module java.base;
import module jdk.httpserver;

public class Repository {

    static void main(String[] args) throws IOException {
        Path folder = Files.createDirectories(Path.of(args[1])).toRealPath();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", Integer.parseInt(args[0])), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                Path file = folder.resolve(exchange.getRequestURI().getPath().substring(1)).normalize();
                if (!file.startsWith(folder) || file.equals(folder)) {
                    exchange.sendResponseHeaders(403, -1);
                } else if (exchange.getRequestMethod().equals("PUT")) {
                    Files.createDirectories(file.getParent());
                    Files.copy(exchange.getRequestBody(), file, StandardCopyOption.REPLACE_EXISTING);
                    exchange.sendResponseHeaders(201, -1);
                    System.out.println("PUT " + folder.relativize(file));
                } else if (exchange.getRequestMethod().equals("GET") && Files.isRegularFile(file)) {
                    exchange.sendResponseHeaders(200, Files.size(file));
                    Files.copy(file, exchange.getResponseBody());
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            }
        });
        server.start();
        System.out.println("A Maven repository in " + folder + " at http://localhost:" + server.getAddress().getPort() + "/");
    }
}
