/**
 * A JMH benchmark that the build generates, compiles and runs, with no build
 * script and no shaded benchmark jar. JMH needs no support of its own: it
 * generates its harness with an annotation processor, declared like any other
 * with {@code @jenesis.plugin}, and its entry point is an ordinary
 * {@code @jenesis.main} class, so {@code Execute.java} builds and runs it.
 *
 * @jenesis.release 25
 * @jenesis.main demo.bench.StringBench
 * @jenesis.plugin maven/org.openjdk.jmh/jmh-generator-annprocess
 * @jenesis.alias jmh.core org.openjdk.jmh/jmh-core
 * @jenesis.pin net.sf.jopt-simple/jopt-simple 5.0.4 SHA-256/df26cc58f235f477db07f753ba5a3ab243ebe5789d9f89ecf68dd62ea9a66c28
 * @jenesis.pin org.apache.commons/commons-math3 3.6.1 SHA-256/1e56d7b058d28b65abd256b8458e3885b674c1d588fa43cd7d1cbb9c7ef2b308
 * @jenesis.pin org.openjdk.jmh/jmh-core 1.37 SHA-256/dc0eaf2bbf0036a70b60798c785d6e03a9daf06b68b8edb0f1ba9eb3421baeb3
 * @jenesis.pin plugin/maven/net.sf.jopt-simple/jopt-simple 5.0.4 SHA-256/df26cc58f235f477db07f753ba5a3ab243ebe5789d9f89ecf68dd62ea9a66c28
 * @jenesis.pin plugin/maven/org.apache.commons/commons-math3 3.6.1 SHA-256/1e56d7b058d28b65abd256b8458e3885b674c1d588fa43cd7d1cbb9c7ef2b308
 * @jenesis.pin plugin/maven/org.openjdk.jmh/jmh-core 1.37 SHA-256/dc0eaf2bbf0036a70b60798c785d6e03a9daf06b68b8edb0f1ba9eb3421baeb3
 * @jenesis.pin plugin/maven/org.openjdk.jmh/jmh-generator-annprocess 1.37 SHA-256/6a5604b5b804e0daca1145df1077609321687734a8b49387e49f10557c186c77
 */
module demo.bench {
    requires jmh.core;
    requires jdk.unsupported;

    exports demo.bench;
    exports demo.bench.jmh_generated to jmh.core;
}
