package build.jenesis.test.daemon;

import module java.base;

public class DaemonEntry {

    public static void main(String... selectors) {
        System.out.println("DaemonEntry saw " + List.of(selectors));
    }
}
