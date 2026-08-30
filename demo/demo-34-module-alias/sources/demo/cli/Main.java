package demo.cli;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

public class Main {

    public static void main(String[] arguments) {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(arguments);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            return;
        }
        String greeting = "Hello, " + options.name + "!";
        System.out.println(options.shout ? greeting.toUpperCase(java.util.Locale.ROOT) : greeting);
    }
}
