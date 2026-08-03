/**
 * A modular project whose versions come from bills of materials instead of
 * per-dependency {@code @jenesis.pin} tags. The Maven BOM reference fetches
 * {@code org.slf4j:slf4j-bom} from Maven Central and imports its
 * {@code <dependencyManagement>}; the file reference imports
 * {@code bom-platform.properties} from the project's BOM location (default:
 * the configuration location), pinning the {@code org.slf4j} module
 * coordinate a Maven BOM cannot express.
 *
 * @jenesis.release 25
 * @jenesis.bom org.slf4j/slf4j-bom 2.0.16
 * @jenesis.bom bom-platform.properties
 */
module demo.bom {
    requires org.slf4j;

    exports sample;
}
