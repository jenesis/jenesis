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
 * @jenesis.pin maven/net.sf.jopt-simple/jopt-simple/pom 5.0.4 SHA-256/6a67763b76afcd9c80b95e5c5e24782d18cc1b0e3d9b454ad3f8754c76b76815
 * @jenesis.pin maven/org.apache.commons/commons-math3/pom 3.6.1 SHA-256/fad72336ea7d7dd06da103144e3740db508fa4b17d9c54d7847737edc24a7e60
 * @jenesis.pin maven/org.apache.commons/commons-parent/pom 39 SHA-256/87cd27e1a02a5c3eb6d85059ce98696bb1b44c2b8b650f0567c86df60fa61da7
 * @jenesis.pin maven/org.apache/apache/pom 16 SHA-256/9f85ff2fd7d6cb3097aa47fb419ee7f0ebe869109f98aba9f4eca3f49e74a40e
 * @jenesis.pin maven/org.openjdk.jmh/jmh-core/pom 1.37 SHA-256/04453be006f06f86d7c43f3c492f7b4eb3362680cae4f1ee80ba65db23373f5a
 * @jenesis.pin maven/org.openjdk.jmh/jmh-parent/pom 1.37 SHA-256/0c24f216f3637dde7639114f70273a697f8546f7a4c6d5acd4cc6daee9bef4c9
 * @jenesis.pin maven/org.sonatype.oss/oss-parent/pom 7 SHA-256/b51f8867c92b6a722499557fc3a1fdea77bdf9ef574722fe90ce436a29559454
 * @jenesis.pin net.sf.jopt-simple/jopt-simple 5.0.4 SHA-256/df26cc58f235f477db07f753ba5a3ab243ebe5789d9f89ecf68dd62ea9a66c28
 * @jenesis.pin org.apache.commons/commons-math3 3.6.1 SHA-256/1e56d7b058d28b65abd256b8458e3885b674c1d588fa43cd7d1cbb9c7ef2b308
 * @jenesis.pin org.openjdk.jmh/jmh-core 1.37 SHA-256/dc0eaf2bbf0036a70b60798c785d6e03a9daf06b68b8edb0f1ba9eb3421baeb3
 * @jenesis.pin plugin/maven/net.sf.jopt-simple/jopt-simple 5.0.4 SHA-256/df26cc58f235f477db07f753ba5a3ab243ebe5789d9f89ecf68dd62ea9a66c28
 * @jenesis.pin plugin/maven/net.sf.jopt-simple/jopt-simple/pom 5.0.4 SHA-256/6a67763b76afcd9c80b95e5c5e24782d18cc1b0e3d9b454ad3f8754c76b76815
 * @jenesis.pin plugin/maven/org.apache.commons/commons-math3 3.6.1 SHA-256/1e56d7b058d28b65abd256b8458e3885b674c1d588fa43cd7d1cbb9c7ef2b308
 * @jenesis.pin plugin/maven/org.apache.commons/commons-math3/pom 3.6.1 SHA-256/fad72336ea7d7dd06da103144e3740db508fa4b17d9c54d7847737edc24a7e60
 * @jenesis.pin plugin/maven/org.apache.commons/commons-parent/pom 39 SHA-256/87cd27e1a02a5c3eb6d85059ce98696bb1b44c2b8b650f0567c86df60fa61da7
 * @jenesis.pin plugin/maven/org.apache/apache/pom 16 SHA-256/9f85ff2fd7d6cb3097aa47fb419ee7f0ebe869109f98aba9f4eca3f49e74a40e
 * @jenesis.pin plugin/maven/org.openjdk.jmh/jmh-core 1.37 SHA-256/dc0eaf2bbf0036a70b60798c785d6e03a9daf06b68b8edb0f1ba9eb3421baeb3
 * @jenesis.pin plugin/maven/org.openjdk.jmh/jmh-core/pom 1.37 SHA-256/04453be006f06f86d7c43f3c492f7b4eb3362680cae4f1ee80ba65db23373f5a
 * @jenesis.pin plugin/maven/org.openjdk.jmh/jmh-generator-annprocess 1.37 SHA-256/6a5604b5b804e0daca1145df1077609321687734a8b49387e49f10557c186c77
 * @jenesis.pin plugin/maven/org.openjdk.jmh/jmh-generator-annprocess/pom 1.37 SHA-256/e4240265b5425c39f1cf2733afda3aec3b139dd193e794d55137bec9240ff476
 * @jenesis.pin plugin/maven/org.openjdk.jmh/jmh-parent/pom 1.37 SHA-256/0c24f216f3637dde7639114f70273a697f8546f7a4c6d5acd4cc6daee9bef4c9
 * @jenesis.pin plugin/maven/org.sonatype.oss/oss-parent/pom 7 SHA-256/b51f8867c92b6a722499557fc3a1fdea77bdf9ef574722fe90ce436a29559454
 */
module demo.bench {
    requires jmh.core;
    requires jdk.unsupported;

    exports demo.bench;
    exports demo.bench.jmh_generated to jmh.core;
}
