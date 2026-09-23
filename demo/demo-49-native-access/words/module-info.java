/**
 * A library that uses demo.natives.text's native function, and therefore names
 * it with @jenesis.native. The name is recorded in this module's jar, so a
 * module that runs this one can discover that it has to grant the same.
 *
 * @jenesis.release 25
 * @jenesis.native demo.natives.text
 */
module demo.natives.words {
    requires transitive demo.natives.text;
    exports demo.natives.words;
}
