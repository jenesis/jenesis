package build;

/**
 * A credential helper: Jenesis runs it with the address the credential is for as its last
 * argument and sends the first line it prints as a bearer token. A real helper asks a
 * server of its own; this one answers the stand-in server of the demo.
 */
public class Helper {

    public static void main(String... args) {
        System.out.println("helper-token-for-" + args[args.length - 1].hashCode());
    }
}
