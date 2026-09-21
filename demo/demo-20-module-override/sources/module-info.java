/**
 * Requiring an API whose packages a dependency already ships.
 *
 * A package has exactly one owning module, and a library that needs an API
 * requires the module owning it. Tomcat Embed is the notable exception, and it
 * is a mistake on its side: it copies the Jakarta Servlet API's packages into
 * its own jar and exports them under its own name, so {@code jakarta.servlet}
 * and {@code org.apache.tomcat.embed.core} both claim them. The same holds for
 * {@code jakarta.el}, which {@code org.apache.tomcat.embed.el} claims.
 *
 * That breaks any modular library built against the API. This module requires
 * {@code jakarta.servlet.jsp}, whose descriptor states
 * {@code requires transitive jakarta.servlet} and
 * {@code requires transitive jakarta.el}. Modules of those names must be on the
 * path or it does not resolve, and Tomcat supplies neither name; resolving the
 * API artifacts as well makes two modules export one package.
 *
 * {@code @jenesis.override} states the relationship once per module. Jenesis
 * places modules of those names that hold no packages and require the carrier
 * transitively, so the library resolves and reads Tomcat's copies.
 *
 * @jenesis.release 25
 * @jenesis.main demo.override.Main
 * @jenesis.override jakarta.servlet org.apache.tomcat.embed.core
 * @jenesis.override jakarta.el org.apache.tomcat.embed.el
 * @jenesis.pin jakarta.servlet.jsp 4.0.0
 * @jenesis.pin jakarta.servlet.jsp/jakarta.servlet.jsp-api 4.0.0 SHA-256/873b7d0c2b5734ef8847634299b67ce879080cdece8426147522c4db8e37c14e
 * @jenesis.pin org.apache.tomcat.embed.core 11.0.24
 * @jenesis.pin org.apache.tomcat.embed.el 11.0.24
 * @jenesis.pin org.apache.tomcat.embed/tomcat-embed-core 11.0.24 SHA-256/e7b966dcaac8c5ffa4f10e44031ebf5b71f2123560c08ac8b7120028615aea08
 * @jenesis.pin org.apache.tomcat.embed/tomcat-embed-el 11.0.24 SHA-256/ad0546f12dace008aacce18ba13222137760b310ca719e173d3e13021b9af6c6
 * @jenesis.pin org.apache.tomcat/tomcat-annotations-api 11.0.24 SHA-256/98294a0c51879caff193f60cfd82e6ab551b7530d4fb618a24e6df63e40125d4
 */
module demo.override {
    requires jakarta.servlet;
    requires jakarta.servlet.jsp;
    requires org.apache.tomcat.embed.core;
    requires org.apache.tomcat.embed.el;

    exports demo.override;
}
