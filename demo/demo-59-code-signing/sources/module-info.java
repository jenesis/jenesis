/**
 * A module whose jar is signed as part of the build. A `jarsigner.properties`
 * in the configuration folder names the key store, the alias and where the
 * store's password is read from, and the produced jar is replaced by the signed
 * one before anything downstream - the inventory, the staged repository, a
 * publication - ever sees it.
 *
 * @jenesis.release 25
 * @jenesis.main demo.signing.Greeting
 */
module demo.signing {
    exports demo.signing;
}
