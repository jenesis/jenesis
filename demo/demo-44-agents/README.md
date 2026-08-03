Java agents demo
================

Attach libraries as Java agents (`-javaagent`) to a module's own executions with
the `@jenesis.attach` tag. An agent is declared once, next to the thing it
instruments, and Jenesis adds it to the right `java` command: the test JVM for a
test module, or the application launch for a module with an entry point. This
demo shows both, and the two shapes an agent can take:

- **A dependency that also attaches** - Mockito. It is a normal test dependency
  (`Mockito.mock(...)` has to compile) *and* a Java agent: its mock maker
  instruments classes at runtime, and on JDK 21+ a self-attaching agent is
  deprecated (a future JDK will forbid it). Declaring it with `@jenesis.attach`
  hands Mockito the agent up front, so the same jar sits on the test module path
  and is passed as `-javaagent` - no self-attachment, no warning.
- **An agent-only library** - the OpenTelemetry Java agent. It is never compiled
  against and never on any path; it exists only to be attached. It observes the
  application from the outside, resolved in its own `agent` scope.

Run it
------

From this directory:

    java build/Demo.java Ada Lovelace

`Demo.java` runs two steps. First the ordinary build, whose test step attaches
Mockito to the test JVM and runs the test. Then the application through
`build.jenesis.Execute`, which attaches the OpenTelemetry agent; the agent logs
its version banner (proof that it attached) and the app prints its greeting:

    == Building and testing (Mockito attaches to the test JVM) ==
    ...
    [         1 tests successful      ]
    == Running the application (OpenTelemetry attaches to the app JVM) ==
    [otel.javaagent ...] INFO ... opentelemetry-javaagent - version: 2.30.0
    Hello, Ada Lovelace, from an application instrumented by a Java agent.

The runner switches the OpenTelemetry exporters off through the standard
`OTEL_*` environment variables so the run stays quiet - the agent still attaches
and logs its banner. The first build downloads JUnit, Mockito, and the (large)
OpenTelemetry agent, so it takes a while.

You can also drive the two halves directly: `java build/jenesis/Project.java`
builds and tests (Mockito), and `java build/jenesis/Execute.java Ada Lovelace`
runs the application (OpenTelemetry).

Layout
------

    demo/demo-44-agents
    |-- build/jenesis              symlink to ../../../sources/build/jenesis
    |-- build/Demo.java            runs the build (Mockito) then the app (OpenTelemetry)
    |-- sources
    |   |-- module-info.java       @jenesis.main + @jenesis.attach OpenTelemetry (agent-only)
    |   `-- demo/agents
    |       |-- Greeter.java       a service interface, mocked in the test
    |       |-- PoliteGreeter.java the real implementation used at run time
    |       `-- Application.java   the entry point
    `-- test
        |-- module-info.java       @jenesis.test + @jenesis.attach Mockito (dependency + agent)
        `-- agents
            `-- ApplicationTest.java  mocks Greeter with Mockito

How an agent is declared
------------------------

Each attachment lives next to the execution it belongs to. The main module names
its entry point and attaches the observability agent:

    /**
     * @jenesis.main demo.agents.Application
     * @jenesis.attach io.opentelemetry.javaagent/opentelemetry-javaagent
     */
    module demo.agents {
        exports demo.agents;
    }

The test module marks itself a test of `demo.agents` and attaches Mockito, which
it also `requires`:

    /**
     * @jenesis.test demo.agents
     * @jenesis.attach org.mockito
     * ...
     */
    open module demo.agents.test {
        requires demo.agents;
        requires org.junit.jupiter;
        requires org.mockito;
    }

The token after `@jenesis.attach` follows the same grammar as `@jenesis.pin`,
with no version (the version comes from a `@jenesis.pin`, a BOM, a matching
`requires`, or floats the latest release). You can name either form:

- a **module name** - `@jenesis.attach org.mockito` - resolved through the module
  repository, exactly like a `requires` of the same name (so it works for any
  module that resolves there);
- a **Maven coordinate** - `@jenesis.attach org.mockito/mockito-core` (or the
  fully explicit `<group>/<repository>/<coordinate>`) - resolved from Maven
  Central.

Both are valid in this MODULAR_TO_MAVEN project, and both find the same
`mockito-core` jar - the one that carries the `Premain-Class` manifest attribute
a Java agent needs. Mockito is attached here by its **module name**,
`@jenesis.attach org.mockito`, to match the `requires org.mockito` right below
it: the attachment and the dependency then resolve through the same repository at
the same coordinate, so the agent jar and the module-path jar are literally the
same file. (OpenTelemetry, having no `requires`, is named by its Maven coordinate
instead.) Anything after the token is passed to the agent verbatim as its option
string, appended after `=` on the `-javaagent` argument (for example JaCoCo's
`destfile=...`); Mockito and the OpenTelemetry agent take none here.

What Jenesis does with it
-------------------------

An attached coordinate is resolved in a dedicated `agent` scope. That scope
never joins a compile or runtime path, so the OpenTelemetry agent - required by
nothing - stays off the module graph entirely. Only the declared coordinate is
attached; its transitive dependencies are resolved into the closure but not
themselves passed as agents. Before launch, Jenesis checks the resolved jar
carries a `Premain-Class` and fails with a clear message if it does not.

When the attached coordinate is *also* a dependency, as Mockito is, both scopes
resolve in the same pass to the identical artifact, so the jar on the test module
path and the jar passed as `-javaagent` are one and the same file.

The attachments reach the launched JVM through the module's build state: the
test step reads `attachments.properties` and prepends a `-javaagent:` argument
(after any observability agents such as JaCoCo), and for a main run `Execute`
reads the `agent.*` entries that `Inventory` records and does the same.

Maven projects
--------------

This demo is modular, but the feature is layout-agnostic. A `pom.xml`-based
project declares the same attachments in a project-level comment block, one
declaration per line, read only from the project's own POM:

    <!--jenesis.attach
    org.mockito/mockito-core
    io.opentelemetry.javaagent/opentelemetry-javaagent
    -->

There a declaration that matches a `test`-scoped dependency attaches to the test
run only; any other match, and an agent-only line, attach to both the main and
the test module. Because an XML comment cannot contain a double dash, write
`&#45;&#45;` for one inside an agent's options.

Pinning
-------

Agents are pinned like any other dependency. `java build/jenesis/Project.java
pin` records the OpenTelemetry agent's version and checksum in the main module,
and Mockito together with its agent transitives (Byte Buddy, Objenesis) in the
test module - so a strict-pinned build (`-Djenesis.dependency.pin=strict`)
covers the agents too. This demo ships already pinned.
