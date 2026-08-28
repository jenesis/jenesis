/**
 * Requiring an API whose packages a dependency already ships.
 *
 * A package has exactly one owning module, and a library that needs an API
 * requires the module owning it. Tomcat Embed is the notable exception, and it
 * is a mistake on its side: it copies the Jakarta Servlet API's packages into
 * its own jar and exports them under its own name, so {@code jakarta.servlet}
 * and {@code org.apache.tomcat.embed.core} both claim them. Requiring
 * {@code jakarta.servlet} here resolves the API artifact too and no module path
 * can carry the package twice; requiring Tomcat instead compiles, but writes a
 * server implementation into this module's descriptor and its published pom.
 *
 * {@code @jenesis.override} states the relationship once. Jenesis places a
 * module named {@code jakarta.servlet} that holds no packages and requires the
 * carrier transitively, so this module reads Tomcat's copy under the API's
 * name, and it drops the artifact that would have carried those packages a
 * second time.
 *
 * @jenesis.release 25
 * @jenesis.main demo.override.Main
 * @jenesis.override jakarta.servlet org.apache.tomcat.embed.core
 * @jenesis.pin org.apache.tomcat.embed.core 11.0.24
 * @jenesis.pin org.apache.tomcat.embed/tomcat-embed-core 11.0.24 SHA-256/e7b966dcaac8c5ffa4f10e44031ebf5b71f2123560c08ac8b7120028615aea08
 * @jenesis.pin org.apache.tomcat/tomcat-annotations-api 11.0.24 SHA-256/98294a0c51879caff193f60cfd82e6ab551b7530d4fb618a24e6df63e40125d4
 */
module demo.override {
    requires jakarta.servlet;
    requires org.apache.tomcat.embed.core;
}
