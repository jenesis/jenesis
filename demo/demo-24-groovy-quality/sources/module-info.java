/**
 * The Groovy counterpart to the Java code-quality demo. A {@code codenarc.xml}
 * activates CodeNarc, which lints the Groovy sources. There is no inferred
 * Groovy formatter, so this demo is lint-only. The Groovy compiler is pinned in
 * its own {@code groovyc} group while CodeNarc floats its own {@code RELEASE}.
 *
 * @jenesis.release 25
 * @jenesis.pin codenarc/maven/com.github.javaparser/javaparser-core 3.26.2 SHA-256/3e3e0c65d57d12797dbead3df1ebb28e7583737d0cd1f2a898dba6febd50ab88
 * @jenesis.pin codenarc/maven/com.thoughtworks.qdox/qdox 1.12.1 SHA-256/21fba22f830e9268f07cf4ab2d99e8181abbdcb0cb91ee0228eb3cb918dcdd1d
 * @jenesis.pin codenarc/maven/org.apache.ant/ant 1.10.15 SHA-256/763acda4a69588c9ea8817a952851ff0c2fc4bffa1d081c2565dc407f29d5794
 * @jenesis.pin codenarc/maven/org.apache.ant/ant-antlr 1.10.15 SHA-256/619e361609d446fe6ab67d1d45b8656e68644cbd7c45bbed5c2c8816408d8dd5
 * @jenesis.pin codenarc/maven/org.apache.ant/ant-junit 1.10.15 SHA-256/9a455eeb4bedc1d8bcd4b8099d4b1f18f1bf1bc977cd6e389a106fe0cf366711
 * @jenesis.pin codenarc/maven/org.apache.ant/ant-launcher 1.10.15 SHA-256/5c8551990307a032336d98ddaed549a39a689f07d4d4c6b950601bf22b3d6a1b
 * @jenesis.pin codenarc/maven/org.apache.groovy/groovy 6.0.0-alpha-1 SHA-256/f98453919a23cb8cfa36dcf7176fdcf13350cb2baa65236b081a601848f0350f
 * @jenesis.pin codenarc/maven/org.apache.groovy/groovy-ant 4.0.24 SHA-256/c2c1cffa463e396fa7c928cd115fc0cf602bf7f10d322d7fbbeb193759967e72
 * @jenesis.pin codenarc/maven/org.apache.groovy/groovy-docgenerator 4.0.24 SHA-256/86ef6a7e6c50bcee25b85ffd9fe59708a10978737223dae50e1bbcf0cff0bef1
 * @jenesis.pin codenarc/maven/org.apache.groovy/groovy-groovydoc 4.0.24 SHA-256/9345bb7b4cda6219ad5355a99213797cf3969cf0aa948067c30563a0dbb5ccc0
 * @jenesis.pin codenarc/maven/org.apache.groovy/groovy-json 4.0.24 SHA-256/e813df50d6544b90bbdc3552105be9a69758d9134e3b6990d882f35fca3c858f
 * @jenesis.pin codenarc/maven/org.apache.groovy/groovy-templates 4.0.24 SHA-256/b3b11cc4a84badc6d030d2160fc2d05ac142017c122355cef04597c185be7ec0
 * @jenesis.pin codenarc/maven/org.apache.groovy/groovy-xml 4.0.24 SHA-256/0bb344d6005a03091a6dc639e235c3d36c7af978e656ae7f2a1d95002e8f3f0c
 * @jenesis.pin codenarc/maven/org.codenarc/CodeNarc 3.7.0-groovy-4.0 SHA-256/059246e61ad0a8f2234b5da7fd224a1443b3672bc9448108e0f89a93bb18493e
 * @jenesis.pin codenarc/maven/org.gmetrics/GMetrics-Groovy4 2.1.0 SHA-256/485c98e78e76200e58dff9c374979e81f73fb5796645ba99bdc37c28fee33d90
 * @jenesis.pin codenarc/maven/org.slf4j/slf4j-api 1.7.35 SHA-256/84cbd60deaf9e18db8cb181e43db4e63f7de353cfcaf654a76d85b22da4d2762
 * @jenesis.pin codenarc/maven/org.slf4j/slf4j-simple 2.1.0-alpha1 SHA-256/014fedac7a32288ed6f8f72a1007e7fb32aec5bfedb271467e496e0953482f75
 * @jenesis.pin groovyc/maven/org.apache.groovy/groovy 6.0.0-alpha-1 SHA-256/f98453919a23cb8cfa36dcf7176fdcf13350cb2baa65236b081a601848f0350f
 * @jenesis.pin org.apache.groovy 5.0.6
 * @jenesis.pin org.apache.groovy/groovy 5.0.6 SHA-256/32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394
 */
module sample {
    requires org.apache.groovy;
    exports sample;
}
