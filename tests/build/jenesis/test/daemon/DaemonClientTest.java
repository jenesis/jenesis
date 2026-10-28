package build.jenesis.test.daemon;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.daemon.DaemonClient;
import build.jenesis.daemon.Worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

public class DaemonClientTest {

    @TempDir
    private Path temporary;

    @Test
    public void starts_a_daemon_once_and_hands_it_every_later_build_until_it_is_stopped() throws Exception {
        Path root = project("first"), folder = temporary.resolve("daemons");
        DaemonClient client = client(root, folder, Map.of());
        try {
            assertThat(client.dispatch("engine", "help")).isEqualTo(0);
            List<String> ports = ports(folder);
            assertThat(ports).hasSize(1);
            assertThat(client.dispatch("engine", "help")).isEqualTo(0);
            assertThat(ports(folder))
                    .as("the second build is served by the daemon the first one started")
                    .isEqualTo(ports);
        } finally {
            assertThat(client.dispatch("engine", "--stop")).isEqualTo(0);
        }
        assertThat(ports(folder))
                .as("a stopped daemon takes its port file with it")
                .isEmpty();
        assertThatNoException()
                .as("the stop returns once the daemon's process has ended, so nothing holds its log open")
                .isThrownBy(() -> {
                    try (Stream<Path> logs = Files.walk(folder)) {
                        for (Path log : logs.filter(file -> file.getFileName().toString().equals("daemon.log"))
                                .toList()) {
                            Files.delete(log);
                        }
                    }
                });
    }

    @Test
    public void hands_the_builds_of_two_projects_to_one_daemon_where_daemons_are_shared() throws Exception {
        Path folder = temporary.resolve("daemons");
        DaemonClient first = client(project("first"), folder, Map.of("jenesis.daemon.shared", "true")),
                second = client(project("second"), folder, Map.of("jenesis.daemon.shared", "true"));
        try {
            assertThat(first.dispatch("engine", "help")).isEqualTo(0);
            assertThat(second.dispatch("engine", "help")).isEqualTo(0);
            assertThat(ports(folder))
                    .as("a project that runs the same engine on the same JVM reuses the daemon another started")
                    .hasSize(1);
        } finally {
            first.dispatch("engine", "--stop");
        }
    }

    @Test
    public void keeps_a_daemon_per_project_unless_daemons_are_shared() throws Exception {
        Path folder = temporary.resolve("daemons");
        DaemonClient first = client(project("first"), folder, Map.of()),
                second = client(project("second"), folder, Map.of());
        try {
            assertThat(first.dispatch("engine", "help")).isEqualTo(0);
            assertThat(second.dispatch("engine", "help")).isEqualTo(0);
            assertThat(ports(folder)).hasSize(2);
        } finally {
            first.dispatch("engine", "--stop");
        }
    }

    @Test
    public void stops_the_daemon_used_least_recently_to_stay_within_the_limit() throws Exception {
        Path folder = temporary.resolve("daemons");
        Map<String, String> settings = Map.of("jenesis.daemon.limit", "1");
        DaemonClient first = client(project("first"), folder, settings),
                second = client(project("second"), folder, settings);
        try {
            assertThat(first.dispatch("engine", "help")).isEqualTo(0);
            List<String> ports = ports(folder);
            assertThat(second.dispatch("engine", "help")).isEqualTo(0);
            for (int attempt = 0; attempt < 400 && ports(folder).size() > 1; attempt++) {
                Thread.sleep(25);
            }
            assertThat(ports(folder))
                    .as("the daemon the first project started made way for the second project's")
                    .hasSize(1)
                    .doesNotContainAnyElementsOf(ports);
        } finally {
            first.dispatch("engine", "--stop");
        }
    }

    private Path project(String name) throws IOException {
        Path root = temporary.resolve(name);
        Files.createDirectories(root.resolve("sources"));
        Files.writeString(root.resolve("sources").resolve("module-info.java"), "module daemon.client {}\n");
        return root;
    }

    private static DaemonClient client(Path root, Path folder, Map<String, String> settings) throws Exception {
        SequencedMap<String, String> supplied = new LinkedHashMap<>(Map.of("jenesis.daemon.options", "-Xmx256m",
                "jenesis.daemon.idle", "60",
                "jenesis.daemon.folder", folder.toString()));
        supplied.putAll(settings);
        return new DaemonClient(root,
                List.of("-cp", Path.of(Worker.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .toString()),
                "build.jenesis.Project",
                supplied);
    }

    private static List<String> ports(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> instances = Files.list(folder)) {
            return instances.map(instance -> instance.resolve(Worker.PORT))
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .sorted()
                    .toList();
        }
    }
}
