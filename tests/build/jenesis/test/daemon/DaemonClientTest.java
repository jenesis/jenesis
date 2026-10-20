package build.jenesis.test.daemon;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.daemon.DaemonClient;
import build.jenesis.daemon.Worker;

import static org.assertj.core.api.Assertions.assertThat;

public class DaemonClientTest {

    @TempDir
    private Path root;

    @Test
    public void starts_a_daemon_once_and_hands_it_every_later_build_until_it_is_stopped() throws Exception {
        Files.createDirectories(root.resolve("sources"));
        Files.writeString(root.resolve("sources").resolve("module-info.java"), "module daemon.client {}\n");
        DaemonClient client = new DaemonClient(root,
                List.of("-cp", Path.of(Worker.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                        .toString()),
                "build.jenesis.Project",
                new LinkedHashMap<>(Map.of("jenesis.daemon.options", "-Xmx256m", "jenesis.daemon.idle", "60")));
        String folder = root.resolve(".jenesis").toString();
        try {
            assertThat(client.dispatch("engine", "help")).isEqualTo(0);
            assertThat(daemons(folder))
                    .as("the first build starts a daemon serving this folder")
                    .hasSize(1);
            String port = Files.readString(root.resolve(".jenesis").resolve(Worker.PORT));
            assertThat(client.dispatch("engine", "help")).isEqualTo(0);
            assertThat(Files.readString(root.resolve(".jenesis").resolve(Worker.PORT)))
                    .as("the second build is served by the daemon the first one started")
                    .isEqualTo(port);
        } finally {
            assertThat(client.dispatch("engine", "--stop")).isEqualTo(0);
        }
        assertThat(daemons(folder))
                .as("the stop returns once the daemon's process has ended, so nothing holds its log open")
                .isEmpty();
        assertThat(root.resolve(".jenesis").resolve(Worker.PORT))
                .as("a stopped daemon takes its port file with it")
                .doesNotExist();
    }

    private static List<ProcessHandle> daemons(String folder) {
        return ProcessHandle.allProcesses()
                .filter(process -> process.info().arguments().stream().flatMap(Arrays::stream).anyMatch(folder::equals))
                .toList();
    }
}
