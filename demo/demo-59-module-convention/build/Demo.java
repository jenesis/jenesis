package build;

import module java.base;
import build.jenesis.Project;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenModuleRepository;
import build.jenesis.maven.MavenRepository;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisRepository;
import build.jenesis.Environment;
import build.jenesis.Make;

/**
 * Resolving a module from a plain Maven repository by the coordinate convention
 * Jenesis publishes with: the group is the first two dotted segments of the
 * module name, the artifact is the full module name. A
 * {@code MavenModuleRepository} applies that convention in reverse, so a
 * {@code requires demo.convention.greeter} is served by whatever Maven
 * repository the module was published to - no module registry, no coordinate
 * mapping to maintain.
 *
 * It is not wired anywhere by default: a build opts in by naming it as its
 * {@code module} repository, which is the one line this demo is about.
 *
 * Run from this directory with:
 *
 *     java build/Demo.java
 *
 * which publishes {@code greeter/} into a Maven repository under {@code target/},
 * then builds the consumer at the project root against it and runs the produced
 * module.
 */
public class Demo {

    static void main(String[] args) throws Exception {
        Environment environment = new Environment(Make.settings(Path.of(".")).keys());
        Files.createDirectories(Path.of("target"));
        Path published = Project.ofEnvironment(environment, Path.of("."))
                .root(Path.of("greeter"))
                .target(Path.of("target", "greeter"))
                .version("1.0.0")
                .build("stage")
                .get("stage/maven");

        String module = "demo.convention.greeter", group = MavenModuleRepository.groupId(module);
        System.out.println();
        System.out.println("Published " + module + " as "
                + group + ":" + module + ":1.0.0 in " + published.toUri());

        Path jar = published.resolve(Path.of(group.replace('.', File.separatorChar)))
                .resolve(module)
                .resolve("1.0.0")
                .resolve(module + "-1.0.0.jar");
        String checksum = sha256(jar);
        Path boms = Files.createDirectories(Path.of("target", "boms"));
        Files.writeString(boms.resolve("pin-greeter.properties"), module + "=1.0.0 SHA-256/" + checksum + "\n"
                + group + "/" + module + "=1.0.0 SHA-256/" + checksum + "\n");

        MavenRepository maven = new MavenDefaultRepository(published.toUri(), null, Map.of(), null);
        Map<String, Repository> repositories = Map.of("module", new MavenModuleRepository(maven), "maven", maven);

        // Build the consumer, which requires the library by its module name.
        Path modular = Project.ofEnvironment(environment, Path.of("."))
                .repositories(repositories)
                .boms(boms)
                .version("1.0.0")
                .build("stage")
                .get("stage/modular");

        Path app = modular.resolve("demo.convention.app").resolve("1.0.0").resolve("demo.convention.app.jar");
        System.out.println();
        System.out.println("Running demo.convention.app against the resolved library:");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--module-path", app + File.pathSeparator + jar,
                "--module", "demo.convention.app")
                .inheritIO()
                .start();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("Could not run demo.convention.app");
        }

        Files.writeString(jar.getParent().getParent().resolve("maven-metadata.xml"), """
                <metadata>
                  <groupId>demo.convention</groupId>
                  <artifactId>demo.convention.greeter</artifactId>
                  <versioning>
                    <release>1.0.0</release>
                    <versions>
                      <version>1.0.0</version>
                      <version>2.0.0-beta</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        Optional<RepositoryItem> floating = new MavenModuleRepository(maven).fetch(Runnable::run, module);
        System.out.println();
        System.out.println("Resolving " + module + " without a version, through that metadata:");
        System.out.println("  " + (floating.isPresent() ? "[resolved] " : "[MISSING]  ")
                + module + "-1.0.0.jar");

        Path modules = Files.createDirectories(Path.of("target", "modules", "module", "demo.other", "1.0.0"));
        Files.copy(jar, modules.resolve("demo.other.jar"), StandardCopyOption.REPLACE_EXISTING);
        String chained = "maven:2:" + published.toUri() + "|demo.convention," + Path.of("target", "modules").toUri();
        JenesisRepository chain = JenesisModuleRepository.ofEnvironment(new Environment(Map.of("module.uri", chained)::get),
                JenesisRepository.Scope.MODULE);
        System.out.println();
        System.out.println("Resolving through the configured chain, with no repository in code:");
        report(chain, module, "the company repository, by the Maven convention");
        report(chain, "demo.other", "the regular module repository");
    }

    private static void report(JenesisRepository chain, String module, String source) throws IOException {
        Optional<RepositoryItem> item = chain.fetch(Runnable::run, module + "/1.0.0");
        System.out.println("  " + (item.isPresent() ? "[resolved] " : "[MISSING]  ")
                + module + "-1.0.0.jar from " + source);
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
