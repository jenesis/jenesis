package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Environment;
import build.jenesis.Project;

import static org.assertj.core.api.Assertions.assertThat;

public class SourcesJarTest {

    @TempDir
    private Path root;

    @Test
    public void holds_the_resources_of_a_pom_module_beside_its_sources() throws IOException {
        Files.writeString(root.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>sample</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0</version>
                  <properties>
                    <maven.compiler.release>25</maven.compiler.release>
                  </properties>
                </project>
                """);
        Files.writeString(Files.createDirectories(root.resolve("src/main/java/sample")).resolve("Sample.java"), """
                package sample;

                public class Sample {
                }
                """);
        Files.writeString(Files.createDirectories(root.resolve("src/main/resources/META-INF/proguard"))
                .resolve("sample.pro"), "-keep class sample.Sample");
        Files.writeString(Files.createDirectories(root.resolve("src/main/resources/sample")).resolve("sample.txt"), "sample");
        Project.ofEnvironment(new Environment(Map.of("project.sources", "true")).out(_ -> { }), root).build();
        Path jar;
        try (Stream<Path> files = Files.walk(root.resolve("target"))) {
            jar = files.filter(file -> file.getFileName().toString().endsWith("-sources.jar")).findFirst().orElseThrow();
        }
        try (JarFile file = new JarFile(jar.toFile())) {
            assertThat(file.stream().map(JarEntry::getName).toList())
                    .as("a sources jar holds the resources as Maven's source plugin does")
                    .contains("sample/Sample.java", "META-INF/proguard/sample.pro", "sample/sample.txt");
        }
    }
}
