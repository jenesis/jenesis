package demo.cli;

import org.kohsuke.args4j.Option;

public class Options {

    @Option(name = "-name", usage = "who to greet")
    public String name = "world";

    @Option(name = "-shout", usage = "upper-case the greeting")
    public boolean shout;
}
