ANTLR demo
==========

Generate a parser from a grammar as part of the build. `Calc.g4` describes a
small arithmetic language; ANTLR turns it into a lexer, a parser and a visitor,
and `Calculator` walks the parse tree the visitor hands it. No generated source
is checked in.

Build and run it
----------------

From this directory:

    java build/jenesis/Execute.java

    2 * (3 + 4) - 5 = 9

Pass your own expression as arguments:

    java build/jenesis/Execute.java "10 / 2 + 3 * 4"

    10 / 2 + 3 * 4 = 17

How the grammar is wired
------------------------

Three things turn ANTLR on, and none of them is a build script.

`jenesis.plugins.properties` names the ANTLR plugin at the hook point
`binary/generated`, whose sources the module compiles with its own:

    antlr+binary/generated=build.jenesis.plugin.antlr

The grammar lives in the module's `META-INF/build.jenesis` folder, which keeps it
out of the jar:

    sources/META-INF/build.jenesis/Calc.g4

A `antlr.properties` in the configuration folder binds that folder as the
plugin's grammars and names the package the parser lands in:

    package=demo.antlr.calc
    arguments=-visitor -no-listener
    @grammars/antlr=META-INF/build.jenesis

`package` is passed to ANTLR as `-package` and also decides where the generated
files are written, so `demo/antlr/calc/CalcParser.java` compiles like any other
source of the module. `arguments` is handed to the tool verbatim - here asking
for a visitor and no listener. Both keys are optional. The binding places the
folder, relative to the module, as `antlr/` of the input `grammars`, the folder
the plugin reads every `.g4` from.

The module declares only the ANTLR *runtime* the generated code calls into:

    module demo.antlr {
        requires org.antlr.antlr4.runtime;

        exports demo.antlr;
    }

The ANTLR *tool* is not a dependency of the module. It resolves in its own
`antlr` group, pinned separately by the `pin` goal:

    @jenesis.pin antlr:tool/maven/org.antlr/antlr4 4.13.2 SHA-256/e6f0b10...
    @jenesis.pin org.antlr.antlr4.runtime 4.13.2 SHA-256/dd3e8a1...

so upgrading the parser generator never moves the runtime your program links
against, and neither one floats.

What the step produces
----------------------

ANTLR writes `.java` files next to the `.tokens` and `.interp` files it uses
while it runs. Only the sources survive the step, so the auxiliary files never
reach the jar:

    jar tf target/build/**/artifacts/demo.antlr-0-SNAPSHOT.jar

    demo/antlr/calc/CalcLexer.class
    demo/antlr/calc/CalcParser.class
    demo/antlr/calc/CalcVisitor.class
    demo/antlr/calc/CalcBaseVisitor.class
    demo/antlr/Calculator.class

Turn it off with `-Djenesis.generate.antlr=false`, the same way every other
plugin is left out.

In the editor
-------------

`ide` lists the generated parser as a generated source folder of the module,
so the editor resolves `CalcParser` and `CalcBaseVisitor` like any other class:

    java build/jenesis/Make.java ide

The folder lies in the build's output, so a build brings it up to date after
the grammar changes.
