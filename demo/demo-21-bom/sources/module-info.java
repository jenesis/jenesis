/**
 * A modular project whose versions come from bills of materials. The Maven
 * BOM reference fetches {@code org.slf4j:slf4j-bom} from Maven Central and
 * imports its {@code <dependencyManagement>}; since a Maven BOM carries no
 * hashes, the {@code pin} goal pins the artifacts resolved through it - the
 * {@code slf4j-api} line below - which is what lets the build pass strict
 * pinning. The file reference imports {@code pin-lang3.properties} from
 * the project's BOM location (default: the configuration location); its
 * entries carry their own hashes, so {@code org.apache.commons.lang3} needs
 * no pin lines at all. The bare {@code org.slf4j} module version is
 * hand-declared, as a Maven BOM cannot version a module name.
 *
 * @jenesis.release 25
 * @jenesis.bom pin-lang3.properties
 * @jenesis.bom org.slf4j/slf4j-bom 2.0.16
 * @jenesis.pin maven/org.apache.commons/commons-lang3/pom 3.20.0 SHA-256/7ca83b2709c1e7a9e03b576cd41422190379489a80866e542f8c8b955411a2aa
 * @jenesis.pin maven/org.apache.commons/commons-parent/pom 92 SHA-256/94f6c027b15f6402995dd1b2c7ea35bfe1ebc88b6832b3134368b41998721953
 * @jenesis.pin maven/org.apache/apache/pom 35 SHA-256/ea297dcd114136e8b8e8b630230d52a76c2fc69f6c5db25d672b1857000728b8
 * @jenesis.pin maven/org.junit/junit-bom/pom 5.13.4 SHA-256/d7a08a99b2502f0bb68cd4e1f984f0bf69324aaa208bd0f73366c03fc3548a42
 * @jenesis.pin maven/org.slf4j/slf4j-api/pom 2.0.16 SHA-256/b1a00f5b1c4dbe62b805d65d23911a6f77063889d7cb1e86fe8389d6190473f7
 * @jenesis.pin maven/org.slf4j/slf4j-bom/pom 2.0.16 SHA-256/0566048ec825cdf28758620af64d9e14ae38a9cd8748dd6fafa6df4a4194c279
 * @jenesis.pin maven/org.slf4j/slf4j-parent/pom 2.0.16 SHA-256/09a0b4cc814d7274616c9b16d4c0fd9aaf1ecc813334de5131e547271b1982e5
 * @jenesis.pin org.apache.commons.lang3 3.20.0 SHA-256/69e5c9fa35da7a51a5fd2099dfe56a2d8d32cf233e2f6d770e796146440263f4
 * @jenesis.pin org.slf4j 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 * @jenesis.pin org.slf4j/slf4j-api 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 */
module demo.bom {
    requires org.slf4j;
    requires org.apache.commons.lang3;

    exports sample;
}
