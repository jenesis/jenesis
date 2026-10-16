Error Prone demo
================

Run [Error Prone](https://errorprone.info) over the sources as they compile.
Unlike Checkstyle, PMD or SpotBugs, Error Prone is not a tool the build runs
beside `javac` - it is a `javac` plugin, so it sees the same typed AST the
compiler does and reports through the compiler's own diagnostics.

`Comparison.same` compares two strings with `==`. The comparison compiles, and
it is wrong: the two strings are equal but not identical.

Run it
------

From this directory:

    java build/Demo.java

    [blocked] a reference comparison of two strings, with ReferenceEquality promoted to an error

    Error Prone blocked the build; javac alone compiles the same sources.

The demo builds twice. The first build runs with the profile `errorprone` and
fails:

    Comparison.java:12: error: [ReferenceEquality] Comparison using reference equality instead of value equality
            return left == right;
                        ^
        (see https://errorprone.info/bugpattern/ReferenceEquality)
      Did you mean 'return Objects.equals(left, right);' or 'return left.equals(right);'?

The second runs without the profile and compiles the very same sources, because
plain `javac` has nothing to say about `==`.

Declaring the plugin
--------------------

Error Prone is declared the way every compiler plugin is declared, on the module
itself:

    @jenesis.plugin javac maven/com.google.errorprone/error_prone_core

It is the same tag that names an annotation processor, with the compiler named
first, and an Error Prone plugin such as NullAway is another `@jenesis.plugin
javac` line. Until it is pinned, a coordinate declared without a version takes
the newest release, and the `pin` goal pins the closure under the compiler's own
group:

    @jenesis.pin javac/maven/com.google.errorprone/error_prone_core 2.50.0 SHA-256/40a88d3...

Nothing of that closure reaches the module's own compile or runtime path; the
module here declares no dependency at all. Declared alone, the plugin resolves
and sits unused, because `javac` runs a plugin only when an option names it.

Turning it on
-------------

The options that turn it on are the compiler's own, in a `process-javac.properties`
like any other `javac` option (see `demo-13`). This demo keeps them in the
profile `build.jenesis/errorprone/`, so that it can build with and without them;
a project of your own places the file in `build.jenesis/` itself. Each key is one
option, so the `=`, `:` and space inside an option are escaped with a backslash,
as in any properties file:

    -XDcompilePolicy\=simple
    --should-stop\=ifError\=FLOW
    -J--add-exports\=jdk.compiler/com.sun.tools.javac.api\=ALL-UNNAMED
    -J--add-exports\=jdk.compiler/com.sun.tools.javac.file\=ALL-UNNAMED
    -J--add-exports\=jdk.compiler/com.sun.tools.javac.main\=ALL-UNNAMED
    -J--add-exports\=jdk.compiler/com.sun.tools.javac.model\=ALL-UNNAMED
    -J--add-exports\=jdk.compiler/com.sun.tools.javac.parser\=ALL-UNNAMED
    -J--add-exports\=jdk.compiler/com.sun.tools.javac.processing\=ALL-UNNAMED
    -J--add-exports\=jdk.compiler/com.sun.tools.javac.tree\=ALL-UNNAMED
    -J--add-exports\=jdk.compiler/com.sun.tools.javac.util\=ALL-UNNAMED
    -J--add-opens\=jdk.compiler/com.sun.tools.javac.code\=ALL-UNNAMED
    -J--add-opens\=jdk.compiler/com.sun.tools.javac.comp\=ALL-UNNAMED
    -Xplugin\:ErrorProne\ -Xep\:ReferenceEquality\:ERROR

Why it takes all of these
-------------------------

Error Prone is not an annotation processor, which `javac` would run on its own,
but a `javac` plugin, and what makes it useful is also what makes it demanding:

- **`-Xplugin:ErrorProne`** runs the plugin. A plugin runs only when this option
  names it, and everything after the name is the plugin's own: here
  `-Xep:ReferenceEquality:ERROR` promotes one check to an error, and
  `-Xep:<Check>:OFF|WARN|ERROR`, `-XepAllErrorsAsWarnings` or
  `-XepDisableWarningsInGeneratedCode` go there as well.
- **`-XDcompilePolicy=simple`** has `javac` analyse every class completely before
  it generates any code. Error Prone checks the analysed code, and the default
  policy would hand it classes that are already partly lowered to bytecode form.
- **`--should-stop=ifError=FLOW`** keeps `javac` going through its flow analysis
  even when it has found an error, so that Error Prone reports its findings
  together with the compiler's own.
- **`-J--add-exports` and `-J--add-opens`** grant Error Prone the compiler's
  internal packages, which the `jdk.compiler` module does not export. They are
  options of the JVM that runs `javac`, hence the `-J`; the build's own JVM is
  already running and cannot take them, so a `-J` option makes `javac` run in a
  JVM of its own. Nothing else has to be configured for that.

The first two options are read when `javac` starts, and the exports must be in
place before it does, so neither the plugin nor a processor can supply them
itself. They are a restriction of Error Prone and of `javac` as they stand rather
than of the build: should a later release need fewer of them, the lines it no
longer needs go.

Turning it off
--------------

Remove the file, or the `-Xplugin` line: `javac` then compiles without the plugin,
which is what the second build of `build/Demo.java` does by leaving the profile
out.
