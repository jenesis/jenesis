/**
 * Requiring a plain library that declares no module identity of its own.
 *
 * args4j ships as an ordinary jar: no {@code module-info}, and not even an
 * {@code Automatic-Module-Name} manifest entry. On the module path it would be
 * an automatic module named after its file (`args4j`), a name that changes with
 * the file and that this module could not stably {@code requires}. The
 * {@code @jenesis.alias} tag gives the artifact a name this project chooses -
 * {@code org.kohsuke.args4j}, matching its package - so it can be required,
 * opened to, and resolved together with its dependency graph like any module.
 *
 * @jenesis.release 25
 * @jenesis.main demo.cli.Main
 * @jenesis.alias org.kohsuke.args4j args4j/args4j
 * @jenesis.pin args4j/args4j 2.33 SHA-256/91ddeaba0b24adce72291c618c00bbdce1c884755f6c4dba9c5c46e871c69ed6
 */
module demo.cli {
    requires org.kohsuke.args4j;

    opens demo.cli to org.kohsuke.args4j;
}
