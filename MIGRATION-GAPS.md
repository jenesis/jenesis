# Migration gaps

What 25 open-source builds needed from Jenesis that it does not offer yet, collected while moving them off Maven and
Gradle. Each item names the projects that ran into it and what they did instead: a plugin of their own, a test run or
artifact they dropped, or the old build they kept beside Jenesis.

The projects: caffeine, commons-lang, gson, guava, hibernate-validator, jackson-core, JavaHamcrest, javapoet, jhipster
(sample app), jjwt, jline3, JsonPath, JSqlParser, jsoup, junit-framework, junit-pioneer, mapstruct, micrometer,
mockito, moshi, netty, picocli, quarkus (Panache sample), spring-boot-realworld-example-app and spring-petclinic.

Sizes are a rough estimate of the change to the tool: **S** is a setting or a step's option, **M** a new capability
of an existing module, **L** a new mechanism.

## Status

About 121 gaps found along the way are fixed in the tool, among them forking `javac`, `--source`/`--target`, modular
Javadoc, resources in the sources jar, a check for a module declared twice, `kotlinc` arguments, scope and exclusions
on a pin, the operating system family as a platform token, bytecode transforms at `binary/transform` (bnd, AspectJ),
`tar.gz` dependencies, Docker arguments, reusing a dependency resolution whose inputs did not change, generated
sources in the `ide` project files, relocation POMs, `Implementation-*` manifest entries, JaCoCo coverage limits,
Dokka options, tests scanned in a dependency's jar, which also run a module's tests a second time with other settings,
formatter options with the Eclipse formatter, ktfmt and licence headers, forbiddenapis, jdeps, jdeprscan and revapi checks, Kotlin sources below `META-INF/versions`, Javadoc at the release the sources are compiled for, annotation processors that need a module nothing requires, and classifier variants
(`classifiers.properties`, `@jenesis.pin <module> :<classifier>`).

30 more are answered by the migration briefing (`skill/migrate`, `skill/registry`) rather than by the tool, because a
plugin or a small change to the project covers them: OSGi manifests from a `binary/transform` plugin, resource
filtering and extra source roots from a plugin at `binary/compiled` or `binary/generated`, a tool whose packages split
across a plugin layer forked instead, `os-maven-plugin`, the git revision, pinning every profile, `test-jpms`
suites that run on the class path, and where each jar embeds its SBOM
(`META-INF/sbom/<groupId>/<artifactId>.cdx.json`) compared to the CycloneDX plugins.

What follows is what remains.

## Hit in four projects or more

| Gap | Size | Projects |
|---|---|---|
| **No compile avoidance.** A dependency's jar is compared by its bytes, so any change to it recompiles every module that depends on it, even when its API is unchanged. | L | gson, caffeine, micrometer, moshi |

Compile avoidance matters most: it is the largest share of the difference to Gradle on a build after an edit
(caffeine 16 min against 13, micrometer 11 min against 7:40, moshi 238 s against 67 s).

## Compilers

| Gap | Size | Projects |
|---|---|---|
| Kotlin: no `-Xfriend-paths` for tests (S), no KSP (M). | S/M | moshi (version-coupled workaround, KSP plugin) |
| No native compilation (C, Rust, JNI). Prebuilt libraries can be published as a classifier variant, but not built. | L | netty (natives stay on Maven) |

## Dependency resolution

| Gap | Size | Projects |
|---|---|---|
| A Kotlin Multiplatform artifact resolves to its root jar, which holds no classes, and nothing reports it. Without Gradle module metadata the platform variant is not found; at the least, the build should fail and name the variant to depend on. | M | spring-boot-realworld-example-app (clikt), micrometer, moshi |

## Partial or unconfirmed

- When a test JVM writes outside its own folder, the build names the folder it wrote to but does not prevent the
  write (quarkus).
- Whether a plugin's dependency group can resolve a sibling module of the project is not settled (moshi).

## Deliberately unsupported

These are decisions rather than gaps. The briefing says so and names the change to the project that replaces each:

- shading and relocation;
- more than one release per module, including compiled stubs excluded from the jar - the stubs become a module of
  their own, required as `static`, and a newer call becomes a multi-release class;
- a Java 8 or older base built from `module-info.java`;
- support for one framework or platform - GWT, Android, Angular and Node front ends, Asciidoctor, `quarkus:dev`
  and `@QuarkusTest` on the module path: the tool keeps to general-purpose functions, and each of these is a plugin,
  for the community to publish (guava, mockito, jhipster, picocli, hibernate-validator, quarkus);
- Apache RAT, the release audit of one foundation's policy, which a plugin runs; and animal-sniffer, whose JDK
  signatures are what `--release` checks as it compiles, while its Android signatures are Android's concern;
- checking a dependency's jar for what the module path refuses, as a jar that names a module but holds a class
  outside any package: the JVM names the jar and the cause before any code runs, and `skill/execute` lists the
  common causes with the fix for each (JSqlParser, whose JavaCC fork does this);
- a path below `target/` in `process-test.properties`: a plugin hands the tests a file as a resource of the test
  module, written below `classes/` at its `binary/compiled`, and a framework that wants a file path in a system
  property gets one from a `LauncherSessionListener` of the tests that copies the resource out
  (`skill/extend`; quarkus, mockito);
- a development-only scope: Maven has none, and what keeps Spring DevTools out of a deliverable is Spring Boot's
  own plugins (`excludeDevtools` of `repackage`, Gradle's `developmentOnly`), so it stays in a Maven profile that
  a property activates, which Jenesis does not read (spring-petclinic);
- white-box tests through `--patch-module`;
- Gradle's highest-version conflict resolution;
- a root location for configuration of the tests alone or the main code alone - a file that differs for the tests
  sits in each test half's `src/test/build.jenesis/` (jline3, caffeine, hibernate-validator);
- Groovy before 4 and Scala 2 (junit-framework, picocli);
- Java code that uses Groovy types: Groovy compiles after `javac`, so Groovy uses Java and not the reverse, and such
  Java moves to a module that requires the Groovy one (jline3);
- a variable for the module's own coordinates in `process-*.properties` - a test reads them at run time from the
  tested jar's `pom.properties` or `Implementation-*` manifest entries, or the module carries a
  `process-test.properties` of its own (netty);
- tests on a JDK other than the build's - a CI matrix runs the whole build again with `jenesis.toolchain.version`
  (JavaHamcrest, JsonPath, junit-pioneer, jsoup, commons-lang, jjwt);
- retrying a failed test, which belongs to the test framework (JUnit Pioneer's `@RetryingTest`, TestNG's
  `IRetryAnalyzer`), and a JVM per test class - each test module runs in a JVM of its own, so a test that needs one
  becomes a module (micrometer, mockito, caffeine);
- overriding a `pom.xml` `${...}` property from the command line;
- Gradle module metadata, war and ear archives, and running Docker containers as part of a build.

## Suggested order

1. **Failing on a class-less Kotlin Multiplatform root.**
2. **Kotlin.** Friend paths for tests, then KSP.
3. **Compile avoidance.** Compare a dependency by a fingerprint of its API rather than its bytes. The largest change,
   and the one a build after an edit shows most.
