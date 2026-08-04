/**
 * A modular project whose versions come from bills of materials. The Maven
 * BOM reference fetches {@code org.slf4j:slf4j-bom} from Maven Central and
 * imports its {@code <dependencyManagement>}; since a Maven BOM carries no
 * hashes, the {@code pin} goal pins the artifacts resolved through it - the
 * {@code slf4j-api} line below - which is what lets the build pass strict
 * pinning. The file reference imports {@code bom-lang3.properties} from
 * the project's BOM location (default: the configuration location); its
 * entries carry their own hashes, so {@code org.apache.commons.lang3} needs
 * no pin lines at all. The bare {@code org.slf4j} module version is
 * hand-declared, as a Maven BOM cannot version a module name.
 *
 * @jenesis.release 25
 * @jenesis.bom bom-lang3.properties
 * @jenesis.bom org.slf4j/slf4j-bom 2.0.16
 * @jenesis.pin org.slf4j 2.0.16
 * @jenesis.pin org.slf4j/slf4j-api 2.0.16 SHA-256/a12578dde1ba00bd9b816d388a0b879928d00bab3c83c240f7013bf4196c579a
 */
module demo.bom {
    requires org.slf4j;
    requires org.apache.commons.lang3;

    exports sample;
}
