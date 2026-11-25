//JAVA 25+
//SOURCES ../../../sources/build/**/*.java

package build;

import module java.base;
import build.jenesis.Make;

public class Demo {

    static void main(String[] args) throws Exception {
        Make.Result staged = new Make("build.jenesis.Project").build("stage");
        if (staged.code() != 0) {
            System.exit(staged.code());
        }
        Path module = staged.outputs().get("stage/modular").resolve("demo.jbang");
        System.out.println("Staged " + Path.of("").toAbsolutePath().relativize(module.resolve("demo.jbang.jar")));
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--module-path",
                module.toString(),
                "--module",
                "demo.jbang/sample.Sample"));
        command.addAll(List.of(args));
        System.exit(new ProcessBuilder(command).inheritIO().start().waitFor());
    }
}
