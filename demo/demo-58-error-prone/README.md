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

The demo builds twice. The first build is the ordinary one and fails:

    Comparison.java:12: error: [ReferenceEquality] Comparison using reference equality instead of value equality
            return left == right;
                        ^
        (see https://errorprone.info/bugpattern/ReferenceEquality)
      Did you mean 'return Objects.equals(left, right);' or 'return left.equals(right);'?

The second switches the plugin off through the assembler and compiles the very
same sources, because plain `javac` has nothing to say about `==`.

How the plugin is wired
-----------------------

Error Prone is declared the way every compiler plugin is declared, on the module
itself:

    @jenesis.plugin javac maven/com.google.errorprone/error_prone_core

`@jenesis.plugin <compiler> <coordinate>` resolves into the `plugin` scope of
that compiler's own group, exactly as `@jenesis.plugin kotlinc ...` does for a
Kotlin compiler plugin, and `javac` reads that group into its processor path.
There is no second way to name it: an Error Prone plugin such as NullAway is
another `@jenesis.plugin javac` line, and the `pin` goal pins the closure under
the same group:

    @jenesis.pin javac/maven/com.google.errorprone/error_prone_core 2.50.0 SHA-256/40a88d3...

Nothing of that closure reaches the module's own compile or runtime path; the
module here declares no dependency at all.

What the configuration file adds is the switch and the flags:

    arguments=-Xep:ReferenceEquality:ERROR

`arguments` is appended to the `-Xplugin:ErrorProne` argument, so every Error
Prone flag applies: `-Xep:<Check>:OFF|WARN|ERROR` to set one check's severity,
`-XepAllErrorsAsWarnings`, `-XepDisableWarningsInGeneratedCode`, and the rest.
An empty file runs Error Prone with its default set of checks, where most
findings are warnings and a handful are errors. Without the file the plugin
resolves and sits unused, because `javac` runs a plugin only when it is named;
with the file but without the declaration the build fails saying which
`@jenesis.plugin` line is missing.

Why `javac` forks
-----------------

Error Prone reads `com.sun.tools.javac` internals that `jdk.compiler` does not
export. A plugin can only be granted them by the JVM that runs the compiler,
through `--add-exports` and `--add-opens` passed as `-J` options - and `-J`
exists only when `javac` is a process of its own. So when Error Prone is
active, the `javac` step forks rather than calling the in-process
`ToolProvider`, whatever `jenesis.process.factory` says. The flags are written
into a `process/javac.properties` that the compile step reads like any other
javac configuration:

    -XDcompilePolicy=simple
    --should-stop=ifError=FLOW
    -J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
    ...
    -Xplugin:ErrorProne -Xep:ReferenceEquality:ERROR

Turning it off
--------------

Either globally, with the property:

    java -Djenesis.compile.errorprone=false build/jenesis/Make.java

or for one build, through the assembler - which is what the second half of
`build/Demo.java` does:

    new Project(Path.of("."))
            .assembler(new InferredMultiProjectAssembler().toolchain(toolchain ->
                    toolchain.compiler(compiler -> compiler.errorprone(null))))
            .build();
