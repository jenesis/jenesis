package build.jenesis;

import module java.base;

public final class Execute {

    public static void main(String... arguments) throws Exception {
        SequencedMap<String, String> named = new LinkedHashMap<>();
        String[] remaining = Make.partitioned(arguments, named);
        List<String> options = Make.options(named);
        Make make = new Make("build.jenesis.Execution", Make.ambient(named)).daemon(false);
        Integer code = Make.relaunched(Execute.class, options, remaining);
        System.exit(code == null ? make.run(remaining) : code);
    }
}
