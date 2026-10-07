package demo.discovery;

import build.jenesis.MakeTool;

import java.nio.file.Path;

public class Tools {

    public static void main(String... args) throws Exception {
        Path jar = Path.of(MakeTool.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        System.out.println(new MakeTool().name() + " from " + jar.getFileName());
    }
}
