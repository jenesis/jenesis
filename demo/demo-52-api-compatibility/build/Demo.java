package build;

import module java.base;
import build.jenesis.Project;
import build.jenesis.maven.MavenDefaultRepository;

/**
 * A compatibility check needs something to compare against, and that something has
 * to be a *released* artifact, resolved by coordinate. A demo has never published
 * anything, so this entry point publishes the previous release itself, into a
 * repository the second build reads.
 *
 * Phase one builds {@code released/} - the same coordinate at 1.0.0 - and stages it.
 * A staged tree is already a Maven repository layout, so nothing has to be copied
 * or rewritten to turn it into one. Staging produces artifacts rather than a server,
 * though, and a floating version is answered by the {@code maven-metadata.xml} a
 * repository serves next to them, so the one file that advertises 1.0.0 is written
 * here. That is what lets the baseline float, exactly as it does against a real
 * repository.
 *
 * Phase two builds this project (1.1.0) with that repository prepended to Maven
 * Central. The {@code japicmp.properties} names no baseline, so japicmp compares
 * against the last release of this module's own coordinate - which resolves out of
 * the repository phase one wrote. This is what a library that really publishes gets
 * for free.
 *
 * Run from this directory with:
 *
 *     java build/Demo.java
 */
public class Demo {

    static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("target"));
        Path released = new Project(Path.of("released"))
                .target(Path.of("target", "released"))
                .build("stage")
                .get("stage/maven");
        Files.writeString(released.resolve("build/jenesis/demo/api-compatibility/maven-metadata.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>build.jenesis.demo</groupId>
                  <artifactId>api-compatibility</artifactId>
                  <versioning>
                    <latest>1.0.0</latest>
                    <release>1.0.0</release>
                    <versions>
                      <version>1.0.0</version>
                    </versions>
                  </versioning>
                </metadata>
                """);
        System.out.println();
        System.out.println("Published the previous release into " + released);
        System.out.println();

        new Project(Path.of("."))
                .repositories(Map.of("maven", MavenDefaultRepository.of().prepend(
                        new MavenDefaultRepository(released.toUri(), null, Map.of(), _ -> {}))))
                .build(args);
    }
}
