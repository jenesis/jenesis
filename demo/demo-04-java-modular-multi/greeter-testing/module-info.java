/**
 * The shared test infrastructure of this project. The @jenesis.test abstract tag
 * marks it as a test module that declares no tests of its own, so it is compiled
 * and read by the test module, but is never executed and never staged.
 *
 * @jenesis.release 25
 * @jenesis.test abstract
 * @jenesis.pin org.slf4j 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 * @jenesis.pin org.slf4j/slf4j-api 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 */
module demo.greeter.testing {
    requires demo.greeter;
    exports greetertesting;
}
