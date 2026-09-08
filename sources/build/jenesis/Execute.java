package build.jenesis;

import module java.base;

public final class Execute {

    public static void main(String... arguments) throws Exception {
        System.exit(new Make("build.jenesis.Execution").daemon(false).run(arguments));
    }
}
