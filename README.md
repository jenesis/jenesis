Jenesis
=======

[![release](https://img.shields.io/github/v/release/jenesis/jenesis?label=release)](https://github.com/jenesis/jenesis/releases/latest)
![build](https://github.com/jenesis/jenesis/actions/workflows/build.yml/badge.svg)

> ### [Jenesis](https://jenesis.build) - a modern Java build tool
> _Java-native config, plugin-free, with `module-info.java` treated as a feature, not an afterthought._

**A build tool for Java, written in Java.** The engine ships *with* your project as plain source under
`build/jenesis/` and is launched by the JDK directly, so there is no wrapper binary, no fetched plugin tree and
no daemon. Modules declared with `module-info.java` drive the build, every step is content-hashed so unchanged
work is reused, and every dependency can be pinned by version *and* by the checksum of the artifact. It needs a
JDK 25 or newer, and nothing else. This repository also holds **jpx**, which runs an already-published module
with one command - `npx` for the module path.

📖 **The user documentation lives at [jenesis.build](https://jenesis.build).** Everything about using the
tool - layouts, dependencies, pinning, packaging, publishing, and the jpx command - is there. What follows is
for people working *on* this repository.

Getting Jenesis
---------------

Pick whichever fits how you want to manage versions; all three land at the same on-disk state.

```bash
sdk install jenesis && jenesis-init      # SDKMAN, then initialise a project
curl -fsSL https://get.jenesis.build | bash   # bootstrap into the current project
git submodule add --depth 1 https://github.com/jenesis/jenesis.git .jenesis \
    && ln -s ../.jenesis/sources/build/jenesis build/jenesis
```

Then, from the project root:

```bash
java build/jenesis/Project.java                        # build
java build/jenesis/Jpx.java org.junit.platform.console --version   # run a published module
```

See [jenesis.build/tool/getting-started](https://jenesis.build/tool/getting-started/) for the details.

What is in this repository
--------------------------

| Path | Contents |
|------|----------|
| `sources/` | The build tool itself, module `build.jenesis` - including `Project`, `Execute` and `Jpx`. |
| `tests/` | Its tests, module `build.jenesis.test`. |
| `demo/` | ~46 self-contained example projects, one per feature, indexed by [`demo/README.md`](demo/README.md). |
| `sdk/` | The SDKMAN distribution layout and its shell-script tests (`sdk/jenesis`, `sdk/jpx`). `jenesis` runs the version recorded in a project's `build/jenesis/jenesis.version`, installing it where the package manager can; `jenesis-run` runs the installed version as it stands. |
| `build/jenesis` | A symlink to `sources/build/jenesis`, so the project builds itself with itself. |
| `benchmark/` | The performance harness and its methodology, see [`benchmark/README.md`](benchmark/README.md). |
| `install.sh` | The script served at `get.jenesis.build`. |
| `pom.xml` | A plain Maven build of the same sources, kept as a fallback (below). |

Building it
-----------

The project builds itself. Because `build/jenesis` is a symlink into `sources/`, the engine you just edited is
the engine that runs:

```bash
java build/jenesis/Project.java          # compile, test, package
java build/jenesis/Project.java stage    # also lay out the release tree under target/stage/
```

A root `pom.xml` is present, so the layout auto-detects as `MAVEN`; the CI builds it under all four layouts.
Delete `target/` for a clean run.

### When the build tool is broken

The tool builds itself, so a defect in it can leave you unable to build - or, worse, able to build but not to
trust the result. The root `pom.xml` exists for exactly that: it compiles `sources/` and `tests/` and runs the
same test suite with no Jenesis involvement at all.

```bash
mvn test
```

Reach for it to bisect a regression, to check whether a failure is in the tool or in the code under test, and
to validate a change before trusting the self-hosted build again. It is a validation path only - it produces no
release tree, no pinning, no staging - so a green `mvn test` means the sources and tests are sound, not that
the build tool works.

Demos
-----

Every feature has a runnable project under `demo/`, each with its own `build/jenesis` symlink, so it runs in
isolation from inside its own directory:

```bash
cd demo/demo-01-java-pom && java build/jenesis/Project.java
```

Most are driven by the shipped `Project.java`; the ones that customise or drive the build themselves ship a
`build/Demo.java` instead. [`demo/README.md`](demo/README.md) is a guided tour and says which is which. A new
feature is expected to arrive with a demo, because CI runs them all.

Continuous integration
----------------------

- **`.github/workflows/build.yml`** ("Test Jenesis Tool") runs on every push to `main` and every pull request,
  across Linux, macOS and Windows:
  - `demos` - every demo, each with a verification command asserting what it should have produced;
  - `project` - this project itself, under each of the four layouts, with `stage`;
  - `native-image` - the GraalVM demo, on a GraalVM runner, executing the produced binary;
  - `sdk` - stages the jar, populates the SDKMAN layout and runs the `sdk/jenesis` and `sdk/jpx` script tests.

  Every build runs under `-Djenesis.dependency.pin=strict`, so an unpinned dependency fails CI: run `pin` and
  commit the result when you change one. Each job uploads its `target/` on failure.

- **`.github/workflows/benchmark.yml`** is on-demand (`workflow_dispatch`) and runs `benchmark/benchmark.sh`
  unattended on all three runners.

- **`.github/workflows/release.yml`** runs after a successful build on `main` - see below.

Releasing
---------

A release is triggered by running the **Release Jenesis** workflow from the Actions tab. Both of its inputs
are optional:

- `sha` releases that commit; left empty, the head of the branch the workflow runs on is released.
- `tag` releases under that tag (`v1.2.3` or `1.2.3`); left empty, the highest `vX.Y.Z` tag is taken and the
  minor bumped (`v0.9.0` → `0.10.0`). A tag that is not `vX.Y.Z` is rejected.

The workflow builds with `jenesis.project.version` set, then hands `target/stage/maven/output/` to
[JReleaser](https://jreleaser.org) (`jreleaser.yml`), which signs and uploads to Maven Central, publishes the
`sdk/jenesis-<version>.zip` distribution to SDKMAN, Homebrew and Scoop, and cuts the matching `v<version>` tag.
`project.properties` carries the POM metadata that a module declaration cannot express.

Credentials never enter the build: it stops at the unsigned, validated bundle.

Working on the code
-------------------

Read [jenesis.build](https://jenesis.build) first: the concepts a contributor needs - the step graph, layouts,
incremental change detection, the extension points - are documented there rather than duplicated here. Beyond
that the source is the reference, every public type under `sources/build/jenesis/` being small enough to read
end to end, with the tests as executable documentation of the API. `java build/jenesis/Project.java skill`
prints the same material as an agent briefing.

Two conventions govern the code here:

- **Steps talk through folders, never through step names.** A step finds its input at a well-known path inside
  each predecessor's output - `sources/`, `classes/`, `artifacts/` - and writes its own output under the names
  its consumers look up the same way. Never inspect a predecessor's *name* to decide what an input is: that
  rule is what lets a step be spliced between two others without either noticing.
- **Anything that should trigger a rebuild belongs in a serialized field.** A step's cache key is a hash of its
  serialized state, so a knob in a non-`transient` field re-runs the step when it changes while a value
  hard-coded in a method body does not - changing a step's *logic* invalidates nothing on its own.

Bugs, questions and design discussion belong in the [issue tracker](https://github.com/jenesis/jenesis/issues).

License
-------

Apache License 2.0 - see [LICENSE](LICENSE).
