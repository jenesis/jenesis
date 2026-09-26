Module override demo
====================

Require an API whose packages a dependency already ships.

This is not a situation you should normally meet. The module system gives every
package exactly one owning module, and a library that needs an API declares
`requires` on the module that owns it. Almost every library does. Tomcat Embed is
the notable exception, and it is a mistake on its side: rather than depending on
the Jakarta Servlet API, it copies the API's packages into its own jar and then
declares a module descriptor that *exports them under its own name*. The module
`org.apache.tomcat.embed.core` exports `jakarta.servlet`, `jakarta.servlet.http`
and their siblings itself, and its jar declares no dependency on
`jakarta.servlet-api` at all. `org.apache.tomcat.embed.el` does the same to
`jakarta.el`, and `org.apache.tomcat.embed.websocket` to `jakarta.websocket` and
`jakarta.websocket.client`.

Two modules then export one package, which is precisely what a module descriptor
is supposed to make impossible. Only Tomcat can fix that, by requiring the APIs
instead of absorbing them. Until it does, a project that uses Tomcat Embed has to
work around it, and this demo is that workaround.

What breaks, and why a library cannot route around it
-----------------------------------------------------

The damage is not confined to code you write. Any *modular* library built against
the servlet API names it the only way a module can, in its descriptor. This demo
requires one such library, the Jakarta Server Pages API, whose `module-info`
states:

    module jakarta.servlet.jsp {
        requires transitive jakarta.servlet;
        requires transitive jakarta.el;
        ...
    }

Modules of those names must be on the path or `jakarta.servlet.jsp` does not
resolve at all - and a module can neither read a package from a module that does
not declare it, nor be talked out of the name its descriptor states. Tomcat
supplies the packages but not the names, so with Tomcat alone the library is
unusable, and with the API artifacts added alongside Tomcat the packages are
carried twice. Dropping the override tags from this demo shows both halves at
once:

    error: module not found: jakarta.el
    error: module demo.override reads package jakarta.servlet
           from both jakarta.servlet and org.apache.tomcat.embed.core

The first is `jakarta.servlet.jsp` failing to resolve. The second is this module's
own `requires jakarta.servlet` colliding with Tomcat. There is no arrangement of
`requires` that satisfies both: a modular servlet library and Tomcat Embed cannot
share a module path unaided.

The override
------------

`@jenesis.override` states the relationship once per module:

    /**
     * @jenesis.override jakarta.servlet org.apache.tomcat.embed.core
     * @jenesis.override jakarta.el org.apache.tomcat.embed.el
     */
    module demo.override {
        requires jakarta.servlet;
        requires jakarta.servlet.jsp;
        requires org.apache.tomcat.embed.core;
        requires org.apache.tomcat.embed.el;
    }

The tag reads `@jenesis.override <module-name> <module-name>...`: the first name
is the module being replaced, the rest are the modules that already carry its
packages. Jenesis then does two things. It places a module of that name which
holds no packages of its own and requires each carrier `transitive`, so everything
reading it reads Tomcat's copy under the API's name - readability is what a
`requires` grants, and the packages come from the carrier's own unqualified
exports. And it drops every resolved artifact that declares the overridden module,
so the closure holds those packages exactly once, whether the artifact was required
directly or arrived through somebody else's pom.

The result is a build where the third-party modular library resolves untouched and
reads Tomcat:

    jakarta.servlet.Filter is read from org.apache.tomcat.embed.core
    jakarta.servlet.jsp.JspFactory is read from jakarta.servlet.jsp
    jakarta.servlet.jsp declares transitive jakarta.servlet
    jakarta.servlet.jsp declares transitive jakarta.el
    filter: demo.override.TimingFilter

`jakarta.servlet.jsp` was compiled against the real API and never recompiled. It
asks for `jakarta.servlet`, gets a module of that name, and reads Tomcat's classes
through it. The `TimingFilter` in this demo is ordinary servlet code, compiled the
same way.

What consumers see
------------------

Because the generated pom is the resolved closure, the drop reaches a consumer
too: a Maven build flattening this project's dependencies onto a class path gets
Tomcat's copy of `jakarta.servlet` and no second one. The declaration also travels
to Jenesis consumers through the `Jenesis-Overrides` manifest header of the
produced jar.

The module descriptor still says `requires jakarta.servlet`, which is the point:
the published contract names the API, not the server that happens to implement it
here.

Limits
------

Two follow from the placed module holding no code. A qualified
`exports ... to jakarta.servlet` or `opens ... to jakarta.servlet` grants access to
it rather than to the carrier that does the reflecting; open to the carrier, or
unqualified. And requiring it reads everything the carrier exports, so code can
compile against `org.apache.catalina` while only declaring `requires
jakarta.servlet`.

The tag applies to the MODULAR_TO_MAVEN layout, where module names resolve to
Maven coordinates. Declaring it elsewhere is an error.

Run it with:

    java build/jenesis/Execute.java
