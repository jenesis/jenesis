package build;

import module java.base;
import build.jenesis.Project;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenModuleRepository;
import build.jenesis.maven.MavenRepository;

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
        // Publish the library the way any Jenesis project publishes: the
        // modular_to_maven layout stages a jar and a generated POM into a tree
        // that is itself a Maven repository, here under target/greeter.
        Files.createDirectories(Path.of("target"));
        Path published = new Project(Path.of("."))
                .root(Path.of("greeter"))
                .target(Path.of("target", "greeter"))
                .version("1.0.0")
                .build("stage")
                .get("stage/maven");

        // The coordinate nothing declared: derived from the module name alone.
        String module = "demo.convention.greeter", group = MavenModuleRepository.groupId(module);
        System.out.println();
        System.out.println("Published " + module + " as "
                + group + ":" + module + ":1.0.0 in " + published.toUri());

        // A pin covering the artifact just built. A project that consumes a
        // released library commits this file; here it is written per run because
        // the library is produced moments earlier, in this very build.
        Path jar = published.resolve(Path.of(group.replace('.', File.separatorChar)))
                .resolve(module)
                .resolve("1.0.0")
                .resolve(module + "-1.0.0.jar");
        String checksum = sha256(jar);
        Path boms = Files.createDirectories(Path.of("target", "boms"));
        Files.writeString(boms.resolve("pin-greeter.properties"), module + "=1.0.0 SHA-256/" + checksum + "\n"
                + group + "/" + module + "=1.0.0 SHA-256/" + checksum + "\n");

        // Wire the repository: the module repository maps the module name onto
        // the Maven coordinate and serves the POM, the Maven repository serves
        // the artifacts of the resolved closure. Both are the repository the
        // library was published to - in a real build, the no-argument
        // constructor reads the repository chain of jenesis.maven.uri instead.
        MavenRepository maven = new MavenDefaultRepository(published.toUri(), null, Map.of(), null);
        Map<String, Repository> repositories = Map.of("module", new MavenModuleRepository(maven), "maven", maven);

        // Build the consumer, which requires the library by its module name.
        Path modular = new Project(Path.of("."))
                .repositories(repositories)
                .boms(boms)
                .version("1.0.0")
                .build("stage")
                .get("stage/modular");

        // Run it: the consumer's own modular jar plus the published library it
        // resolved, which is the proof that the convention found the artifact.
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

        // A requires without a pinned version floats, and the convention reads
        // the maven-metadata.xml a repository manager maintains beside the
        // artifacts to answer it. A staged tree carries none, so write the file
        // such a manager would publish: two versions, one of them a prerelease
        // that a floating requires must not pick - and only the release was ever
        // staged, so picking the other one resolves nothing.
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
    }

    private static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
