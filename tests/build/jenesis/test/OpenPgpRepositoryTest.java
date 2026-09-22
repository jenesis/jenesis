package build.jenesis.test;

import module java.base;
import module jdk.httpserver;
import module org.junit.jupiter.api;
import build.jenesis.OpenPgpRepository;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpenPgpRepositoryTest {

    private static final String FINGERPRINT = "B4D5C1E7000000000000000000000000000000AA";

    @TempDir
    private Path root;

    @Test
    public void asks_a_key_server_the_way_the_protocol_says() throws Exception {
        List<String> asked = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            asked.add(exchange.getRequestURI().toString());
            byte[] body = "key bytes".getBytes(StandardCharsets.US_ASCII);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            Optional<RepositoryItem> item = OpenPgpRepository.ofEnvironment(new Environment(Map.of("repository.insecure", "true")::get), 
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"))
                    .local(root)
                    .fetch(Runnable::run, FINGERPRINT);
            assertThat(item).isPresent();
            try (InputStream stream = item.orElseThrow().toInputStream()) {
                assertThat(new String(stream.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo("key bytes");
            }
            assertThat(asked)
                    .as("the fingerprint is asked for by the HKP lookup the server publishes")
                    .containsExactly("/pks/lookup?op=get&options=mr&search=0x" + FINGERPRINT);
            assertThat(root.resolve(FINGERPRINT + ".gpg"))
                    .as("what a server answered is held under the fingerprint it answered for")
                    .exists();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void resolves_a_server_list_out_of_the_reference_it_names() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "referenced".getBytes(StandardCharsets.US_ASCII);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            assertThat(OpenPgpRepository.ofEnvironment(new Environment(Map.of("repository.insecure", "true", "test.servers", "http://127.0.0.1:" + server.getAddress().getPort() + "/", "openpgp.uri", "@test.servers", "openpgp.local", root.toString())::get)).fetch(Runnable::run, FINGERPRINT))
                    .as("the reference names a property whose value is the server list")
                    .isPresent();
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void asks_the_second_server_when_the_first_does_not_hold_the_key() throws Exception {
        HttpServer absent = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        absent.createContext("/", exchange -> exchange.sendResponseHeaders(404, -1));
        absent.start();
        HttpServer holding = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        holding.createContext("/", exchange -> {
            byte[] body = "second".getBytes(StandardCharsets.US_ASCII);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        holding.start();
        try {
            assertThat(OpenPgpRepository.ofEnvironment(new Environment(Map.of("repository.insecure", "true",
                            "openpgp.local", root.toString(),
                            "openpgp.uri", "http://127.0.0.1:" + absent.getAddress().getPort() + "/,"
                                    + "http://127.0.0.1:" + holding.getAddress().getPort() + "/")::get))
                    .fetch(Runnable::run, FINGERPRINT))
                    .as("a server that does not hold the key leaves the next one to answer")
                    .isPresent();
        } finally {
            absent.stop(0);
            holding.stop(0);
        }
    }

    private static HttpServer answering(int status, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            if (body == null) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                byte[] bytes = body.getBytes(StandardCharsets.US_ASCII);
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            }
        });
        server.start();
        return server;
    }

    private Optional<RepositoryItem> through(String servers) throws IOException {
        System.setProperty("jenesis.repository.insecure", "true");
        System.setProperty("jenesis.repository.retries", "0");
        System.setProperty("jenesis.openpgp.uri", servers);
        System.setProperty("jenesis.openpgp.local", root.toString());
        try {
            return OpenPgpRepository.ofEnvironment(Environment.SYSTEM).fetch(Runnable::run, FINGERPRINT);
        } finally {
            System.clearProperty("jenesis.openpgp.local");
            System.clearProperty("jenesis.openpgp.uri");
            System.clearProperty("jenesis.repository.retries");
            System.clearProperty("jenesis.repository.insecure");
        }
    }

    @Test
    public void reports_what_each_server_answered_when_none_served_the_key() throws Exception {
        HttpServer refusing = answering(403, null);
        HttpServer failing = answering(500, null);
        try {
            assertThatThrownBy(() -> through("http://127.0.0.1:" + refusing.getAddress().getPort() + "/,"
                    + "http://127.0.0.1:" + failing.getAddress().getPort() + "/"))
                    .as("a key that could not be fetched must say what stood in the way, per server")
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("403")
                    .hasMessageContaining("500");
        } finally {
            refusing.stop(0);
            failing.stop(0);
        }
    }

    @Test
    public void keeps_asking_after_a_server_answers_with_an_error() throws Exception {
        HttpServer refusing = answering(403, null);
        HttpServer holding = answering(200, "key bytes");
        try {
            assertThat(through("http://127.0.0.1:" + refusing.getAddress().getPort() + "/,"
                    + "http://127.0.0.1:" + holding.getAddress().getPort() + "/"))
                    .as("an error from one server is not the end of the list")
                    .isPresent();
        } finally {
            refusing.stop(0);
            holding.stop(0);
        }
    }

    @Test
    public void reports_nothing_held_when_every_server_answers_404() throws Exception {
        HttpServer absent = answering(404, null);
        try {
            assertThat(through("http://127.0.0.1:" + absent.getAddress().getPort() + "/"))
                    .as("a 404 everywhere is the key not being held, which is not a failure to report")
                    .isEmpty();
        } finally {
            absent.stop(0);
        }
    }

    @Test
    public void answers_from_the_cache_without_asking_anyone() throws Exception {
        Files.writeString(root.resolve(FINGERPRINT + ".gpg"), "vendored");
        Optional<RepositoryItem> item = OpenPgpRepository.ofEnvironment(Environment.SYSTEM, URI.create("http://127.0.0.1:1/"))
                .local(root)
                .fetch(Runnable::run, FINGERPRINT);
        assertThat(item)
                .as("a cached key is served without reaching a server that is not listening")
                .isPresent();
    }

    @Test
    public void refuses_a_key_server_reached_over_plaintext() {
        assertThatThrownBy(() -> OpenPgpRepository.ofEnvironment(Environment.SYSTEM, URI.create("http://127.0.0.1:1/"))
                .fetch(Runnable::run, FINGERPRINT))
                .as("a key server inherits the repository posture on plaintext")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("insecure scheme");
    }

    @Test
    public void falls_through_to_the_next_server_that_answers() throws Exception {
        Repository silent = (_, _, _) -> Optional.empty();
        Repository answering = (_, coordinate, _) -> Optional.of(
                () -> new ByteArrayInputStream(coordinate.getBytes(StandardCharsets.US_ASCII)));
        assertThat(answering.prepend(silent).fetch(Runnable::run, FINGERPRINT))
                .as("a server with no such key leaves the next one to answer")
                .isPresent();
    }
}
