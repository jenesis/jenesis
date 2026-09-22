/**
 * A module with nothing to resolve, built twice from one JVM to show that a build
 * takes its settings from the command line it is handed rather than from the JVM.
 *
 * @jenesis.release 25
 * @jenesis.main demo.tools.Greeting
 */
module demo.tools {
    exports demo.tools;
}
