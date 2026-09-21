/**
 * The Kotlin counterpart to the Java code-quality demo. A {@code detekt.yml}
 * activates detekt and an {@code .editorconfig} activates ktlint; both lint the
 * Kotlin sources, and the same {@code .editorconfig} drives ktlint as a
 * formatter in verify mode. The Kotlin compiler is pinned in its own
 * {@code kotlinc} group, separately from the {@code kotlin.stdlib} the module
 * ships against, while the quality tools float their own {@code RELEASE}.
 *
 * @jenesis.release 25
 * @jenesis.pin detekt/maven/dev.drewhamilton.poko/poko-annotations-jvm 0.17.1 SHA-256/940e6d50445bc6b0ae26ad414ec7b953a3e4e802dc7756cc14d56958bc97cc31
 * @jenesis.pin detekt/maven/io.github.davidburstrom.contester/contester-breakpoint 0.2.0 SHA-256/672cbebb5d45a72b35dd81fd6127e187451bb6fb7fba35315bbdf2f57cfce835
 * @jenesis.pin detekt/maven/io.github.detekt.sarif4k/sarif4k-jvm 0.6.0 SHA-256/b3ac96dd97acba8318dbe26f6a432d6c6db91c46c780805e8928b8103e5763dc
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-api 1.23.8 SHA-256/dd5b84d420904d5c564aab115d36e6290a9d7daf6955923099015618f2b5c83f
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-cli 1.23.8 SHA-256/ead7ccd320bf304cece189438d6a384747c76545a0f880d9c91d5ca5d2d30a63
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-core 1.23.8 SHA-256/1981dea8e4e2e8541af2d83e4f8d3581ce647cfe63175b9eb9ac2a07849d74c9
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-metrics 1.23.8 SHA-256/718e8f71f5872986e4f5cd4887a41e26aa0aeff42e99cf4a42582291b8738cc4
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-parser 1.23.8 SHA-256/5cdf45a0172d934d6e7401cd43838e7b954f7adb117eed8358fcae0b177e90e5
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-psi-utils 1.23.8 SHA-256/9505fa9d4f9a771d256a5d415b5d51ffd7a24e5019f6a60e5a69a12633dcf7ba
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-html 1.23.8 SHA-256/8068a15e07718e3bdbf501ede5f666812fb5f9fb1450db060449534c05e6722a
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-md 1.23.8 SHA-256/cc5b90b1476cef99e112162dbcd1db32b040284a768f3c975a49ec0b63980ca3
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-sarif 1.23.8 SHA-256/c9f9221fc57ed1fbd1374de5c4da6c069160f892a71f83b551d3a32c8cbad13d
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-txt 1.23.8 SHA-256/41e85ca3587abce9a03f8dcb2a67e05fcf59b7518fa016c9350ea73c79f9c54a
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-report-xml 1.23.8 SHA-256/d71abaa98890cae8a618839b1309c013dff39c6bd7d0de0b704e6193e027ef09
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules 1.23.8 SHA-256/a3ee516f3837fbc01d5c3b86f5dc7be7dba81345bfc57c4eb58af12f6923a560
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-complexity 1.23.8 SHA-256/3a169746e38b93eebb8eb7e10dedb06657ca294bc81cf675a735f85b5f371e6e
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-coroutines 1.23.8 SHA-256/afe8c973c17457f714f05795046115188c03f4506903bb25b84da846e2e56816
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-documentation 1.23.8 SHA-256/d25367855d8ccd156f283e1cd6de0fd15b8f3e9530a970206f41d619fb4fbdcd
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-empty 1.23.8 SHA-256/888641114789ac43292d44836221565da47a9228f7fb7dfe0c6b3f94e7f58afa
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-errorprone 1.23.8 SHA-256/fee1ee765168a5896162ebe39b34fa421c0a5b769abc8f150f9e3596912a49c4
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-exceptions 1.23.8 SHA-256/889ffc72aff0624132e0b10932332c71d24f5bab8f1c10c20bf5ac592ca2caf6
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-naming 1.23.8 SHA-256/ed1992b1bdb0494567805b0a8145ce978f46e5ad743e2e83bf84bd5ec661baeb
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-performance 1.23.8 SHA-256/dbf5e06e2fa18a6cf57ae2621e4274c163bb5bbb351a5db75f724f86f22d436b
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-rules-style 1.23.8 SHA-256/af2644c226d2ba0679ea86bca52ef90c8d9f64f446efa9d74cbd0e6311b038a8
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-tooling 1.23.8 SHA-256/7e93e9a23b478f70128893b06748673f912100b7ef03040d7d0331e26d30d092
 * @jenesis.pin detekt/maven/io.gitlab.arturbosch.detekt/detekt-utils 1.23.8 SHA-256/f75fd7e924b9267d9ec661859ca913102de4a8f5895b09685ce10797dc26d056
 * @jenesis.pin detekt/maven/org.jcommander/jcommander 1.85 SHA-256/fa7552d2831a2b20778d86851d093edca68fbc0a77f792b6223110e4fae67a70
 * @jenesis.pin detekt/maven/org.jetbrains.intellij.deps/trove4j 1.0.20200330 SHA-256/c5fd725bffab51846bf3c77db1383c60aaaebfe1b7fe2f00d23fe1b7df0a439d
 * @jenesis.pin detekt/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable 2.0.21 SHA-256/9fa8cdd1de0dccffe154c997d423ec6b5f53cd6d9177e3a77a9b0de03fb1bc81
 * @jenesis.pin detekt/maven/org.jetbrains.kotlin/kotlin-daemon-embeddable 2.0.21 SHA-256/b1a0a73c5022f8dd05a638c6b76b2bd7361818a1f3860ff2644133b1dd2bdb03
 * @jenesis.pin detekt/maven/org.jetbrains.kotlin/kotlin-reflect 1.6.10 SHA-256/3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203
 * @jenesis.pin detekt/maven/org.jetbrains.kotlin/kotlin-script-runtime 2.0.21 SHA-256/9c111f8d08ade455566272d561921adc2b2cb6b7a4ccee38d9829c5e3a1ca6a3
 * @jenesis.pin detekt/maven/org.jetbrains.kotlin/kotlin-stdlib 2.0.21 SHA-256/f31cc53f105a7e48c093683bbd5437561d1233920513774b470805641bedbc09
 * @jenesis.pin detekt/maven/org.jetbrains.kotlin/kotlin-stdlib-common 1.7.20 SHA-256/e0e91962bc0007338bf5b1739f62927ac32d14ba3d827fa608ab4e5351729d5d
 * @jenesis.pin detekt/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk7 1.7.20 SHA-256/524da3c1a2ad56fd52c4ae2272ef3de421de8d2047ab1c51fc306d351243f2f5
 * @jenesis.pin detekt/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk8 1.7.20 SHA-256/1da0d306c995945e1f807240ef64b5cd2dd5ac58612afb1a8596143d10b7ded5
 * @jenesis.pin detekt/maven/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.6.4 SHA-256/c24c8bb27bb320c4a93871501a7e5e0c61607638907b197aef675513d4c820be
 * @jenesis.pin detekt/maven/org.jetbrains.kotlinx/kotlinx-html-jvm 0.8.1 SHA-256/98bda1c78a5028a134ceb25b63f5c130c89349730d35fd47ef7490b6bf0b63b3
 * @jenesis.pin detekt/maven/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm 1.4.1 SHA-256/eba7f1c854296e4ce1418fb01360f8f10c5683e7c45aa3472018417a067636f3
 * @jenesis.pin detekt/maven/org.jetbrains.kotlinx/kotlinx-serialization-json-jvm 1.4.1 SHA-256/af604c46737121d4225fdb60ef0e17766a3c94b7c1c9ef76b4e3a5c7733d557e
 * @jenesis.pin detekt/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
 * @jenesis.pin detekt/maven/org.snakeyaml/snakeyaml-engine 2.7 SHA-256/4053f878c171692aab8782f53a3974f43e55e2b6ed12c3682b36a46968c5ded1
 * @jenesis.pin kotlin.stdlib 1.9.10
 * @jenesis.pin kotlinc/maven/org.jetbrains.kotlin/kotlin-build-tools-api 2.4.0-RC2 SHA-256/ca5f829936a739cb90146359159a8ecdfe2f1a0ae21f8e2da5868b6413320a1d
 * @jenesis.pin kotlinc/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable 2.4.0-RC2 SHA-256/923181ab0d2dc773b36d526081567bd7755f06e26d32c0f8a6537a31cf0a0229
 * @jenesis.pin kotlinc/maven/org.jetbrains.kotlin/kotlin-daemon-embeddable 2.4.0-RC2 SHA-256/2fc1f1ee5f7fccda091bf34473c6cee44f131ea1b7da9f600a706fdc68716dba
 * @jenesis.pin kotlinc/maven/org.jetbrains.kotlin/kotlin-reflect 1.6.10 SHA-256/3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203
 * @jenesis.pin kotlinc/maven/org.jetbrains.kotlin/kotlin-script-runtime 2.4.0-RC2 SHA-256/676934238966d037834d8285bfd7bd14c0c57f3b7ddd597f7ebd9e3c574a812f
 * @jenesis.pin kotlinc/maven/org.jetbrains.kotlin/kotlin-stdlib 2.4.0-RC2 SHA-256/c67ed4aa99b5766e016f7c1a5d76424b67512cc8b6ca3a9f4ea97526bfee7a5e
 * @jenesis.pin kotlinc/maven/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.8.0 SHA-256/9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5
 * @jenesis.pin kotlinc/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
 * @jenesis.pin ktlint-format/maven/ch.qos.logback/logback-classic 1.3.16 SHA-256/3f63cbc593b91670f75890ab256aa47c123a9b125185939a272c720ca1417c2c
 * @jenesis.pin ktlint-format/maven/ch.qos.logback/logback-core 1.3.16 SHA-256/f153c7d582ad19219c906dd728df335232790663f5b3d2db187ac83d9e5a3b8b
 * @jenesis.pin ktlint-format/maven/com.github.ajalt.clikt/clikt-core-jvm 5.0.3 SHA-256/69cba94a2291f74b78c88239cd8b67eaf3f08e0dad297749abe6477b3e9b81b1
 * @jenesis.pin ktlint-format/maven/com.github.ajalt.clikt/clikt-jvm 5.0.3 SHA-256/9cd19e04782f461dffeb183d17e576984febb7934932cb406646ac41e428863b
 * @jenesis.pin ktlint-format/maven/com.github.ajalt.colormath/colormath-jvm 3.6.0 SHA-256/59f741adfe62053066782d8b1a45afd06685a4bc64b33277e54876b993ed885c
 * @jenesis.pin ktlint-format/maven/com.github.ajalt.mordant/mordant-core-jvm 3.0.1 SHA-256/9cf9b46d1f49f2d6cf2635462b29dc59e0c29b1fb2f085b3312888bbe9c7cd31
 * @jenesis.pin ktlint-format/maven/com.github.ajalt.mordant/mordant-jvm 3.0.1 SHA-256/9ed3b976fcccc78da746d49866fa8ebb8f10530a93c544ea0420259a607dd95e
 * @jenesis.pin ktlint-format/maven/com.github.ajalt.mordant/mordant-jvm-ffm-jvm 3.0.1 SHA-256/2041c2f5f7b87095b115cb19155ee9257451854d08e5b8671165b43bc7e114cf
 * @jenesis.pin ktlint-format/maven/com.github.ajalt.mordant/mordant-jvm-graal-ffi-jvm 3.0.1 SHA-256/6dd4bebc164aeacddacc8f98e8f871e00fd21ce7bc2eb0d18230ea83bddfc86a
 * @jenesis.pin ktlint-format/maven/com.github.ajalt.mordant/mordant-jvm-jna-jvm 3.0.1 SHA-256/41063442c8891b2774536a9b87a5062a7fd20e6f1949974c6da72f49472d6f4d
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli 1.8.0 SHA-256/eb540f5ad442ba0b3392c7947c87929dab709ac056fdde7e300beaffcb86e243
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-baseline 1.8.0 SHA-256/be11607d9cebcc1456ab6ccb2578484c0f63c0288ad0fff278813d7251b8f6ed
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-checkstyle 1.8.0 SHA-256/c8c0789cf5e146d9bf8f57b1e8d1af928f1eac5653a4fbc0ccfcbe5d7906e427
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-core 1.8.0 SHA-256/554d980c1e8b0afd6404dcc77e6022e0466273d1300fda5a6d25a85759cbafaa
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-format 1.8.0 SHA-256/0f6639379e3341ffea2ca6aff2b72e904e778cdcba31f885edb412270e591bb8
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-html 1.8.0 SHA-256/9c45e72d24ae81b2b1c028fcf95c8d8f914a050fcf6029e2d4c2cd9c55340d72
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-json 1.8.0 SHA-256/115db6ad3a6eb18b33bb44a0f7b8dc7639b429ba1fa4fc39863d163a3003efb5
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-plain 1.8.0 SHA-256/05bd03b465fe6bfe885777adf8c251bfe6ddc004086dbfcdfacb0061936b111d
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-plain-summary 1.8.0 SHA-256/003eb83d33467f43bf32b6cb7390d387a823301344fd0705750f9cf4fa42fb28
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-reporter-sarif 1.8.0 SHA-256/8dd41428e258947f9731e7c14f80b795b7d19321c3b4f8eac7549dc335824f35
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-cli-ruleset-core 1.8.0 SHA-256/3cde07dd36b3d2b0ad9bf6320a15d74a1d16999361a2943ec89a77035f88cef8
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-logger 1.8.0 SHA-256/323a2587bc5d658acac2ab84081be1a91ec272447ddae23e889ffeeea03a6a19
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-rule-engine 1.8.0 SHA-256/26b2bc7b840dad219d56a276f11fe4a39c35e567a061d719a50914b30f403772
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-rule-engine-core 1.8.0 SHA-256/bd99dbc888be66aea31db45f4dce5ca56f465afbf02fee639bd2346d2cd864ec
 * @jenesis.pin ktlint-format/maven/com.pinterest.ktlint/ktlint-ruleset-standard 1.8.0 SHA-256/82972b4cb2e9cd682840061b2de9d342044846f0d816ecc58f50958824760d69
 * @jenesis.pin ktlint-format/maven/dev.drewhamilton.poko/poko-annotations-jvm 0.20.1 SHA-256/173fc443f30b8185295c9c9b9401272be84f553e5835e3767c7daafe5be606c9
 * @jenesis.pin ktlint-format/maven/io.github.detekt.sarif4k/sarif4k-jvm 0.6.0 SHA-256/b3ac96dd97acba8318dbe26f6a432d6c6db91c46c780805e8928b8103e5763dc
 * @jenesis.pin ktlint-format/maven/io.github.oshai/kotlin-logging-jvm 7.0.13 SHA-256/c373f7f4c9efc85c4f70f11519f2a1b7878bc0ed75a325364d034af3fa21f776
 * @jenesis.pin ktlint-format/maven/net.java.dev.jna/jna 5.14.0 SHA-256/34ed1e1f27fa896bca50dbc4e99cf3732967cec387a7a0d5e3486c09673fe8c6
 * @jenesis.pin ktlint-format/maven/org.ec4j.core/ec4j-core 1.1.1 SHA-256/d73dee44fefb725dfc658267465fe5a768a666313d8acd701209f5836f1431d8
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable 2.2.21 SHA-256/9588fa696ac5507af764fd5660df6894185e12704ec1bc3f5cb772af4987f295
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlin/kotlin-daemon-embeddable 2.2.21 SHA-256/91c0673c22e44b054ae0314d323860ebe1f3aff3f0ebc74fff82c4733dde09be
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlin/kotlin-reflect 1.6.10 SHA-256/3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlin/kotlin-script-runtime 2.2.21 SHA-256/2b1519b427b514d1536c1b425674b03fe914af637924028e9959c6625442df51
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlin/kotlin-stdlib 2.2.21 SHA-256/6558a3d233da56a20934b32159f9db5f86ed5816ef098f78a2c223dc6abb79dd
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlin/kotlin-stdlib-common 1.7.20 SHA-256/e0e91962bc0007338bf5b1739f62927ac32d14ba3d827fa608ab4e5351729d5d
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk7 1.7.20 SHA-256/524da3c1a2ad56fd52c4ae2272ef3de421de8d2047ab1c51fc306d351243f2f5
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk8 1.7.20 SHA-256/1da0d306c995945e1f807240ef64b5cd2dd5ac58612afb1a8596143d10b7ded5
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.8.0 SHA-256/9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm 1.4.1 SHA-256/eba7f1c854296e4ce1418fb01360f8f10c5683e7c45aa3472018417a067636f3
 * @jenesis.pin ktlint-format/maven/org.jetbrains.kotlinx/kotlinx-serialization-json-jvm 1.4.1 SHA-256/af604c46737121d4225fdb60ef0e17766a3c94b7c1c9ef76b4e3a5c7733d557e
 * @jenesis.pin ktlint-format/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
 * @jenesis.pin ktlint-format/maven/org.slf4j/slf4j-api 2.0.7 SHA-256/5d6298b93a1905c32cda6478808ac14c2d4a47e91535e53c41f7feeb85d946f4
 * @jenesis.pin ktlint/maven/ch.qos.logback/logback-classic 1.3.16 SHA-256/3f63cbc593b91670f75890ab256aa47c123a9b125185939a272c720ca1417c2c
 * @jenesis.pin ktlint/maven/ch.qos.logback/logback-core 1.3.16 SHA-256/f153c7d582ad19219c906dd728df335232790663f5b3d2db187ac83d9e5a3b8b
 * @jenesis.pin ktlint/maven/com.github.ajalt.clikt/clikt-core-jvm 5.0.3 SHA-256/69cba94a2291f74b78c88239cd8b67eaf3f08e0dad297749abe6477b3e9b81b1
 * @jenesis.pin ktlint/maven/com.github.ajalt.clikt/clikt-jvm 5.0.3 SHA-256/9cd19e04782f461dffeb183d17e576984febb7934932cb406646ac41e428863b
 * @jenesis.pin ktlint/maven/com.github.ajalt.colormath/colormath-jvm 3.6.0 SHA-256/59f741adfe62053066782d8b1a45afd06685a4bc64b33277e54876b993ed885c
 * @jenesis.pin ktlint/maven/com.github.ajalt.mordant/mordant-core-jvm 3.0.1 SHA-256/9cf9b46d1f49f2d6cf2635462b29dc59e0c29b1fb2f085b3312888bbe9c7cd31
 * @jenesis.pin ktlint/maven/com.github.ajalt.mordant/mordant-jvm 3.0.1 SHA-256/9ed3b976fcccc78da746d49866fa8ebb8f10530a93c544ea0420259a607dd95e
 * @jenesis.pin ktlint/maven/com.github.ajalt.mordant/mordant-jvm-ffm-jvm 3.0.1 SHA-256/2041c2f5f7b87095b115cb19155ee9257451854d08e5b8671165b43bc7e114cf
 * @jenesis.pin ktlint/maven/com.github.ajalt.mordant/mordant-jvm-graal-ffi-jvm 3.0.1 SHA-256/6dd4bebc164aeacddacc8f98e8f871e00fd21ce7bc2eb0d18230ea83bddfc86a
 * @jenesis.pin ktlint/maven/com.github.ajalt.mordant/mordant-jvm-jna-jvm 3.0.1 SHA-256/41063442c8891b2774536a9b87a5062a7fd20e6f1949974c6da72f49472d6f4d
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli 1.8.0 SHA-256/eb540f5ad442ba0b3392c7947c87929dab709ac056fdde7e300beaffcb86e243
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-reporter-baseline 1.8.0 SHA-256/be11607d9cebcc1456ab6ccb2578484c0f63c0288ad0fff278813d7251b8f6ed
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-reporter-checkstyle 1.8.0 SHA-256/c8c0789cf5e146d9bf8f57b1e8d1af928f1eac5653a4fbc0ccfcbe5d7906e427
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-reporter-core 1.8.0 SHA-256/554d980c1e8b0afd6404dcc77e6022e0466273d1300fda5a6d25a85759cbafaa
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-reporter-format 1.8.0 SHA-256/0f6639379e3341ffea2ca6aff2b72e904e778cdcba31f885edb412270e591bb8
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-reporter-html 1.8.0 SHA-256/9c45e72d24ae81b2b1c028fcf95c8d8f914a050fcf6029e2d4c2cd9c55340d72
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-reporter-json 1.8.0 SHA-256/115db6ad3a6eb18b33bb44a0f7b8dc7639b429ba1fa4fc39863d163a3003efb5
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-reporter-plain 1.8.0 SHA-256/05bd03b465fe6bfe885777adf8c251bfe6ddc004086dbfcdfacb0061936b111d
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-reporter-plain-summary 1.8.0 SHA-256/003eb83d33467f43bf32b6cb7390d387a823301344fd0705750f9cf4fa42fb28
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-reporter-sarif 1.8.0 SHA-256/8dd41428e258947f9731e7c14f80b795b7d19321c3b4f8eac7549dc335824f35
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-cli-ruleset-core 1.8.0 SHA-256/3cde07dd36b3d2b0ad9bf6320a15d74a1d16999361a2943ec89a77035f88cef8
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-logger 1.8.0 SHA-256/323a2587bc5d658acac2ab84081be1a91ec272447ddae23e889ffeeea03a6a19
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-rule-engine 1.8.0 SHA-256/26b2bc7b840dad219d56a276f11fe4a39c35e567a061d719a50914b30f403772
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-rule-engine-core 1.8.0 SHA-256/bd99dbc888be66aea31db45f4dce5ca56f465afbf02fee639bd2346d2cd864ec
 * @jenesis.pin ktlint/maven/com.pinterest.ktlint/ktlint-ruleset-standard 1.8.0 SHA-256/82972b4cb2e9cd682840061b2de9d342044846f0d816ecc58f50958824760d69
 * @jenesis.pin ktlint/maven/dev.drewhamilton.poko/poko-annotations-jvm 0.20.1 SHA-256/173fc443f30b8185295c9c9b9401272be84f553e5835e3767c7daafe5be606c9
 * @jenesis.pin ktlint/maven/io.github.detekt.sarif4k/sarif4k-jvm 0.6.0 SHA-256/b3ac96dd97acba8318dbe26f6a432d6c6db91c46c780805e8928b8103e5763dc
 * @jenesis.pin ktlint/maven/io.github.oshai/kotlin-logging-jvm 7.0.13 SHA-256/c373f7f4c9efc85c4f70f11519f2a1b7878bc0ed75a325364d034af3fa21f776
 * @jenesis.pin ktlint/maven/net.java.dev.jna/jna 5.14.0 SHA-256/34ed1e1f27fa896bca50dbc4e99cf3732967cec387a7a0d5e3486c09673fe8c6
 * @jenesis.pin ktlint/maven/org.ec4j.core/ec4j-core 1.1.1 SHA-256/d73dee44fefb725dfc658267465fe5a768a666313d8acd701209f5836f1431d8
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlin/kotlin-compiler-embeddable 2.2.21 SHA-256/9588fa696ac5507af764fd5660df6894185e12704ec1bc3f5cb772af4987f295
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlin/kotlin-daemon-embeddable 2.2.21 SHA-256/91c0673c22e44b054ae0314d323860ebe1f3aff3f0ebc74fff82c4733dde09be
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlin/kotlin-reflect 1.6.10 SHA-256/3277ac102ae17aad10a55abec75ff5696c8d109790396434b496e75087854203
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlin/kotlin-script-runtime 2.2.21 SHA-256/2b1519b427b514d1536c1b425674b03fe914af637924028e9959c6625442df51
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlin/kotlin-stdlib 2.2.21 SHA-256/6558a3d233da56a20934b32159f9db5f86ed5816ef098f78a2c223dc6abb79dd
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlin/kotlin-stdlib-common 1.7.20 SHA-256/e0e91962bc0007338bf5b1739f62927ac32d14ba3d827fa608ab4e5351729d5d
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk7 1.7.20 SHA-256/524da3c1a2ad56fd52c4ae2272ef3de421de8d2047ab1c51fc306d351243f2f5
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk8 1.7.20 SHA-256/1da0d306c995945e1f807240ef64b5cd2dd5ac58612afb1a8596143d10b7ded5
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm 1.8.0 SHA-256/9860906a1937490bf5f3b06d2f0e10ef451e65b95b269f22daf68a3d1f5065c5
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm 1.4.1 SHA-256/eba7f1c854296e4ce1418fb01360f8f10c5683e7c45aa3472018417a067636f3
 * @jenesis.pin ktlint/maven/org.jetbrains.kotlinx/kotlinx-serialization-json-jvm 1.4.1 SHA-256/af604c46737121d4225fdb60ef0e17766a3c94b7c1c9ef76b4e3a5c7733d557e
 * @jenesis.pin ktlint/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
 * @jenesis.pin ktlint/maven/org.slf4j/slf4j-api 2.0.7 SHA-256/5d6298b93a1905c32cda6478808ac14c2d4a47e91535e53c41f7feeb85d946f4
 * @jenesis.pin org.jetbrains.kotlin/kotlin-stdlib 1.9.10 SHA-256/55e989c512b80907799f854309f3bc7782c5b3d13932442d0379d5c472711504
 * @jenesis.pin org.jetbrains.kotlin/kotlin-stdlib-common 1.9.10 SHA-256/cde3341ba18a2ba262b0b7cf6c55b20c90e8d434e42c9a13e6a3f770db965a88
 * @jenesis.pin org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
 */
module sample.kotlin {
    requires kotlin.stdlib;

    exports sample;
}
