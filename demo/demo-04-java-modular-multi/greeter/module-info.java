/**
 * The library module of a multi-module modular project. It requires the
 * external org.slf4j module and re-exports it with {@code requires transitive},
 * so the consumer module reads slf4j transitively without declaring it itself.
 *
 * @jenesis.release 25
 * @jenesis.pin maven/org.slf4j/slf4j-api/pom 2.0.16 SHA-256/b1a00f5b1c4dbe62b805d65d23911a6f77063889d7cb1e86fe8389d6190473f7
 * @jenesis.pin maven/org.slf4j/slf4j-bom/pom 2.0.16 SHA-256/0566048ec825cdf28758620af64d9e14ae38a9cd8748dd6fafa6df4a4194c279
 * @jenesis.pin maven/org.slf4j/slf4j-parent/pom 2.0.16 SHA-256/09a0b4cc814d7274616c9b16d4c0fd9aaf1ecc813334de5131e547271b1982e5
 * @jenesis.pin org.slf4j 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 * @jenesis.pin org.slf4j/slf4j-api 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 */
module demo.greeter {
    requires transitive org.slf4j;
    exports sample.greeter;
}
