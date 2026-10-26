/**
 * Annotation processor demo.
 *
 * Runs the Immutables annotation processor over a modular Java source. The
 * processor is declared by module name with an @jenesis.plugin tag and is
 * resolved onto the processor path; it is never discovered implicitly from the
 * module path, even though the very same module is also a compile dependency.
 *
 * @jenesis.release 25
 * @jenesis.plugin org.immutables.value
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-bom/pom 2.21.2 SHA-256/bc6c549e632927bb9d8a0ada20c8467b5007d3b797c7f5db03a9b60d1de59be3
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-parent/pom 2.21 SHA-256/3851df627faeb46887b956104630b74a8bbb5f7de22e974cf15982e20e0373de
 * @jenesis.pin maven/com.fasterxml/oss-parent/pom 75 SHA-256/fcbbf1c18c9047e6917d384f2064e21b42a58fc85fb0523a9e2e015eff7c619d
 * @jenesis.pin maven/com.fasterxml/oss-parent/pom 76 SHA-256/109e6c526eb2dbda3a1b21700fec4be2ddab24005ab9f1f80aa934591a1d8beb
 * @jenesis.pin maven/org.immutables/immutables/pom 2.12.2 SHA-256/7364948e7c6da506c61aa75542a5d9601ec0e13817112d7df3ebbbcf62797720
 * @jenesis.pin maven/org.immutables/value/pom 2.12.2 SHA-256/afd47922808ac379d42b2f40247864d9fb9895ed28cf82d705608c4e553eb012
 * @jenesis.pin maven/org.junit/junit-bom/pom 5.9.1 SHA-256/b163c1cfc8fc1fd58b457a00d586c04c46e986d75904e9ca54c03a97d65b496c
 * @jenesis.pin maven/tools.jackson/jackson-bom/pom 3.1.0 SHA-256/abf90b664283a6b4ecd16d7ca3e2b31a6c5cc495c1079a77992549c4829420b8
 * @jenesis.pin org.immutables.value 2.12.2 SHA-256/fa9582d54d079bae233f3e580b5a1241417fcdd3e7049ece9f8ca85c8edd49e1
 * @jenesis.pin org.immutables/value 2.12.2 SHA-256/fa9582d54d079bae233f3e580b5a1241417fcdd3e7049ece9f8ca85c8edd49e1
 * @jenesis.pin plugin/maven/com.fasterxml.jackson/jackson-bom/pom 2.21.2 SHA-256/bc6c549e632927bb9d8a0ada20c8467b5007d3b797c7f5db03a9b60d1de59be3
 * @jenesis.pin plugin/maven/com.fasterxml.jackson/jackson-parent/pom 2.21 SHA-256/3851df627faeb46887b956104630b74a8bbb5f7de22e974cf15982e20e0373de
 * @jenesis.pin plugin/maven/com.fasterxml/oss-parent/pom 75 SHA-256/fcbbf1c18c9047e6917d384f2064e21b42a58fc85fb0523a9e2e015eff7c619d
 * @jenesis.pin plugin/maven/com.fasterxml/oss-parent/pom 76 SHA-256/109e6c526eb2dbda3a1b21700fec4be2ddab24005ab9f1f80aa934591a1d8beb
 * @jenesis.pin plugin/maven/org.immutables/immutables/pom 2.12.2 SHA-256/7364948e7c6da506c61aa75542a5d9601ec0e13817112d7df3ebbbcf62797720
 * @jenesis.pin plugin/maven/org.immutables/value 2.12.2 SHA-256/fa9582d54d079bae233f3e580b5a1241417fcdd3e7049ece9f8ca85c8edd49e1
 * @jenesis.pin plugin/maven/org.immutables/value/pom 2.12.2 SHA-256/afd47922808ac379d42b2f40247864d9fb9895ed28cf82d705608c4e553eb012
 * @jenesis.pin plugin/maven/org.junit/junit-bom/pom 5.9.1 SHA-256/b163c1cfc8fc1fd58b457a00d586c04c46e986d75904e9ca54c03a97d65b496c
 * @jenesis.pin plugin/maven/tools.jackson/jackson-bom/pom 3.1.0 SHA-256/abf90b664283a6b4ecd16d7ca3e2b31a6c5cc495c1079a77992549c4829420b8
 * @jenesis.pin plugin/module/org.immutables.value 2.12.2 SHA-256/fa9582d54d079bae233f3e580b5a1241417fcdd3e7049ece9f8ca85c8edd49e1
 */
module demo.annotations {
    requires static org.immutables.value;

    exports demo.annotations;
}
