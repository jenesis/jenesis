package build.jenesis;

import module java.base;

public final class Execute {

    public static void main(String... arguments) throws Exception {
        List<String> options = Make.options();
        Make make = new Make("build.jenesis.Execution").daemon(false);
        Integer code = Make.relaunched(Execute.class, options, arguments);
        System.exit(code == null ? make.run(arguments) : code);
    }
}
