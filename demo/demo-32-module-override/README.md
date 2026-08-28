Module override demo
====================

Require an API whose packages a dependency already ships.

This is not a situation you should normally meet. The module system gives every
package exactly one owning module, and a library that needs an API declares
`requires` on the module that owns it. Almost every library does. Tomcat Embed is
the notable exception, and it is a mistake on Tomcat's side: rather than depending
on the Jakarta Servlet API, it copies the API's packages into its own jar and then
declares a module descriptor that *exports them under its own name*. The module
`org.apache.tomcat.embed.core` exports `jakarta.servlet`, `jakarta.servlet.http`
and their siblings itself, and its jar declares no dependency on
`jakarta.servlet-api` at all. The same holds for `org.apache.tomcat.embed.el`,
which claims `jakarta.el`, and for `org.apache.tomcat.embed.websocket`, which
claims both `jakarta.websocket` and `jakarta.websocket.client`.

Two modules then export one package, which is precisely what a module descriptor
is supposed to make impossible. Only Tomcat can fix that, by requiring the API
instead of absorbing it. Until it does, a project that uses Tomcat Embed has to
work around it, and this demo is that workaround.

It leaves a modular application with two bad options. Requiring
`jakarta.servlet` resolves the API artifact as well, and the module path then
carries `jakarta.servlet` twice:

    error: module demo.override reads package jakarta.servlet
           from both jakarta.servlet and org.apache.tomcat.embed.core

Requiring `org.apache.tomcat.embed.core` instead compiles, but writes a server
implementation into this module's descriptor and into its published pom, where an
API belongs. Consumers then inherit Tomcat whether they want it or not.

`@jenesis.override` states the relationship once:

    /**
     * @jenesis.override jakarta.servlet org.apache.tomcat.embed.core
     */
    module demo.override {
        requires jakarta.servlet;
        requires org.apache.tomcat.embed.core;
    }

The tag reads `@jenesis.override <module-name> <module-name>...`: the first name
is the module being replaced, the rest are the modules that already carry its
packages. Jenesis then does two things. It places a module named `jakarta.servlet`
that holds no packages of its own and requires each carrier `transitive`, so
every module reading it reads Tomcat's copy under the API's name - readability is
what a `requires` grants, and the packages come from the carrier's own unqualified
exports. And it drops every resolved artifact that declares the overridden module,
so the closure holds those packages exactly once, whether the artifact was
required directly or arrived through somebody else's pom.

Because the generated pom is the resolved closure, the drop reaches a consumer
too: a Maven build flattening this project's dependencies onto a class path gets
Tomcat's copy of `jakarta.servlet` and no second one. The declaration also travels
to Jenesis consumers through the `Jenesis-Overrides` manifest header of the
produced jar.

The module descriptor still says `requires jakarta.servlet`, which is the point:
the published contract names the API, not the server that happens to implement it
here.

Two limits are worth knowing. A qualified `exports ... to jakarta.servlet` or
`opens ... to jakarta.servlet` grants access to the placed module, which holds no
code, not to the carrier that does the reflecting; open to the carrier, or
unqualified. And requiring the placed module reads everything the carrier exports,
so code can compile against `org.apache.catalina` while only declaring
`requires jakarta.servlet`.

The tag applies to the MODULAR_TO_MAVEN layout, where module names resolve to
Maven coordinates. Declaring it elsewhere is an error.

Run it with:

    java build/jenesis/Execute.java

which prints the module the servlet API is actually read from:

    jakarta.servlet is read from org.apache.tomcat.embed.core, required as jakarta.servlet
