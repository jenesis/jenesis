/**
 * The Scala counterpart to the Java code-quality demo. A
 * {@code scalastyle-config.xml} activates Scalastyle and a {@code .scalafmt.conf}
 * activates scalafmt; both inspect the Scala sources, and the same
 * {@code .scalafmt.conf} drives scalafmt as a formatter in verify mode. The
 * Scala compiler is pinned in its own {@code scalac} group while the quality
 * tools float their own {@code RELEASE}.
 *
 * @jenesis.release 25
 * @jenesis.pin org.scala-lang/scala-library 2.13.18 SHA-256/4e85d96ff7bc7dc627985523c3541b9917aaa08e956391380c42db21a2c4e5a0
 * @jenesis.pin scala.library 2.13.18
 * @jenesis.pin scalac/maven/org.scala-lang.modules/scala-asm 9.9.0-scala-1 SHA-256/75ac366e8ecb691e06a7e85041eed0f67919a646e5262fa0901225698c104375
 * @jenesis.pin scalac/maven/org.scala-lang/scala-library 3.8.4-RC3 SHA-256/f2052e9a932973699c9ef575da2ff780245a3f464dbfa28871a44fd27028fc35
 * @jenesis.pin scalac/maven/org.scala-lang/scala3-compiler_3 3.8.4-RC3 SHA-256/8c1f104682d0bda19eecca2ab0e6e90aca0c49d934fb69990fbc8a6f3799cbd9
 * @jenesis.pin scalac/maven/org.scala-lang/scala3-interfaces 3.8.4-RC3 SHA-256/3fcde09e1aad15e4a308882220dd6008db9e51223775625333201048f334098e
 * @jenesis.pin scalac/maven/org.scala-lang/scala3-library_3 3.8.4-RC3 SHA-256/a56d1dd4134af60db7d41a3005fb5e5ed59d77f8c381bb675d55ba2c60208f3d
 * @jenesis.pin scalac/maven/org.scala-lang/tasty-core_3 3.8.4-RC3 SHA-256/5fca75da0a575775dd468923fed00d8efb4f0c86ed05f7c86dc44cdf3aec6fc1
 * @jenesis.pin scalac/maven/org.scala-sbt/compiler-interface 1.10.7 SHA-256/2bacc5761e03920a228e5c9d20b33d9c51d43aaf2f52e8f839ece630966eb880
 * @jenesis.pin scalac/maven/org.scala-sbt/util-interface 1.10.7 SHA-256/1d6b91efa42b70fc064caed6d62962374e13b27737f885a87c84c667b30be625
 * @jenesis.pin scalafmt-format/maven/com.facebook/nailgun-server 1.0.1 SHA-256/c852b33463ce343d22f45686dad8e2a29f61a105bdfd95c87f22b0520a482f21
 * @jenesis.pin scalafmt-format/maven/com.github.luben/zstd-jni 1.5.6-3 SHA-256/f72ede1b39258faf81277dc58de30c71cbae4253732558d2ce10b53d8b5763d5
 * @jenesis.pin scalafmt-format/maven/com.github.plokhotnyuk.jsoniter-scala/jsoniter-scala-core_2.13 2.13.5 SHA-256/e258e292706ced7ba036882a439a1ecc841750a7fc4fa4a5f42f074e7491406d
 * @jenesis.pin scalafmt-format/maven/com.github.scopt/scopt_2.13 4.1.0 SHA-256/2e5037bda974630b046794274e344273919abf4727acfcd86352617dce68f82b
 * @jenesis.pin scalafmt-format/maven/com.lihaoyi/fansi_2.13 0.5.1 SHA-256/e50796c69261fac857469122ab75f5aab4aeef855ca414f184cb132b318c2d9d
 * @jenesis.pin scalafmt-format/maven/com.lihaoyi/sourcecode_2.13 0.4.0 SHA-256/7ce9b8c285a04280374d739d7b799408b2cfa40cc3173960848aec9ee8450fed
 * @jenesis.pin scalafmt-format/maven/com.typesafe/config 1.4.5 SHA-256/4a4b0affb22a9572409d3a6bde99ce3f2045c551cadc1ca7fe09690892c526c3
 * @jenesis.pin scalafmt-format/maven/commons-codec/commons-codec 1.17.0 SHA-256/f700de80ac270d0344fdea7468201d8b9c805e5c648331c3619f2ee067ccfc59
 * @jenesis.pin scalafmt-format/maven/commons-io/commons-io 2.16.1 SHA-256/f41f7baacd716896447ace9758621f62c1c6b0a91d89acee488da26fc477c84f
 * @jenesis.pin scalafmt-format/maven/io.airlift/aircompressor 0.27 SHA-256/fdbef3137a28f63bb0cb93487803080ede746a4ec3d421e36c6f0c305c35e5e4
 * @jenesis.pin scalafmt-format/maven/io.get-coursier.jniutils/windows-jni-utils 0.3.3 SHA-256/ab68efcf162355e20007b238d8108b7ffec0c06b467ee44cbb44ec3f8ac3a7e6
 * @jenesis.pin scalafmt-format/maven/io.get-coursier/cache-util 2.1.24 SHA-256/447741afb13b90e7f650ed89f230bf0e9d76874f80ef516e2aa3963ba56edf84
 * @jenesis.pin scalafmt-format/maven/io.get-coursier/coursier-cache_2.13 2.1.24 SHA-256/cd7f125c3e27cd7527d865ddc467a32dbe5a9a4b4bd2ee666f2a2c13032a1c93
 * @jenesis.pin scalafmt-format/maven/io.get-coursier/coursier-core_2.13 2.1.24 SHA-256/f845ff8f582d8f4c8110676078e60e7737b5cca240b7d7e835fb52a16ffa4194
 * @jenesis.pin scalafmt-format/maven/io.get-coursier/coursier-proxy-setup 2.1.24 SHA-256/bd7dfa4325fec5e2e4cb671bb42953e970a2f9141526d93aaea678a07da3ce5b
 * @jenesis.pin scalafmt-format/maven/io.get-coursier/coursier-util_2.13 2.1.24 SHA-256/5735dad5ec821e7ffba283eda5c2cf26ddea1de4c91f2e6210d8cf3a6cab5a6e
 * @jenesis.pin scalafmt-format/maven/io.get-coursier/coursier_2.13 2.1.24 SHA-256/59266d5d8a6d0005796fafa21060d9de4c38153d2abfb8c1fb689c0b3c14485d
 * @jenesis.pin scalafmt-format/maven/io.get-coursier/dependency_2.13 0.3.2 SHA-256/cd54da81c7f4e75112b0a29ab566a31628cfe7336ee129f7b9f3f8adf68a141a
 * @jenesis.pin scalafmt-format/maven/io.github.alexarchambault.windows-ansi/windows-ansi 0.0.6 SHA-256/077dbdbb977c94fc8b1fb82113cfb0aa5304d4e307c4e0688f69a9895f0da482
 * @jenesis.pin scalafmt-format/maven/io.github.alexarchambault/concurrent-reference-hash-map 1.1.0 SHA-256/552707982209afb9feffdccb2e73cc4681a1353e34a47df1bf643d1d2e0a2f61
 * @jenesis.pin scalafmt-format/maven/io.github.alexarchambault/is-terminal 0.1.1 SHA-256/73735b053b01ff47b53d28ab2d9b23fd86447f6fb6463dd462258067ad6ad3fa
 * @jenesis.pin scalafmt-format/maven/io.github.java-diff-utils/java-diff-utils 4.16 SHA-256/620403030d676a4a27f780a3acec7438dee1b1651a1c804fa6bb11bb07399a6f
 * @jenesis.pin scalafmt-format/maven/javax.inject/javax.inject 1 SHA-256/91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff
 * @jenesis.pin scalafmt-format/maven/net.java.dev.jna/jna 4.5.2 SHA-256/0c8eb7acf67261656d79005191debaba3b6bf5dd60a43735a245429381dbecff
 * @jenesis.pin scalafmt-format/maven/net.java.dev.jna/jna-platform 4.5.2 SHA-256/f1d00c167d8921c6e23c626ef9f1c3ae0be473c95c68ffa012bc7ae55a87e2d6
 * @jenesis.pin scalafmt-format/maven/org.apache.commons/commons-compress 1.26.2 SHA-256/9168a03141d8fc7eda21a2360d83cc0412bcbb1d6204d992bd48c2573cb3c6b8
 * @jenesis.pin scalafmt-format/maven/org.apache.commons/commons-lang3 3.14.0 SHA-256/7b96bf3ee68949abb5bc465559ac270e0551596fa34523fddf890ec418dde13c
 * @jenesis.pin scalafmt-format/maven/org.apache.xbean/xbean-reflect 3.7 SHA-256/104e5e9bb5a669f86722f32281960700f7ec8e3209ef51b23eb9b6d23d1629cb
 * @jenesis.pin scalafmt-format/maven/org.codehaus.plexus/plexus-archiver 4.10.0 SHA-256/4c07814ff4a39199999ae82bba1e38aa4f25637467fcac6a66ed63a76535799a
 * @jenesis.pin scalafmt-format/maven/org.codehaus.plexus/plexus-classworlds 2.6.0 SHA-256/52f77c5ec49f787c9c417ebed5d6efd9922f44a202f217376e4f94c0d74f3549
 * @jenesis.pin scalafmt-format/maven/org.codehaus.plexus/plexus-container-default 2.1.1 SHA-256/8b65840e41ca669a1a69ec1daedb3426304e560b24172cc93bb02e8c199e1ce7
 * @jenesis.pin scalafmt-format/maven/org.codehaus.plexus/plexus-io 3.5.0 SHA-256/965ed28912cf1ae4c628112c4009e0c19819bc44ed5db8af54ee5eda21036a3e
 * @jenesis.pin scalafmt-format/maven/org.codehaus.plexus/plexus-utils 4.0.1 SHA-256/96b9cc44439191d2d0635974e2d44e768736b4fb2abcb65f94cd95e41912fa8b
 * @jenesis.pin scalafmt-format/maven/org.fusesource.jansi/jansi 2.4.1 SHA-256/2e5e775a9dc58ffa6bbd6aa6f099d62f8b62dcdeb4c3c3bbbe5cf2301bc2dcc1
 * @jenesis.pin scalafmt-format/maven/org.jline/jline/jar/jdk8 3.29.0 SHA-256/ed2680487642df95379f220c09c0f77e25095713387e2bc00c2d6581bb79c804
 * @jenesis.pin scalafmt-format/maven/org.scala-lang.modules/scala-collection-compat_2.13 2.14.0 SHA-256/95986ac32df70c9ebdd96edfb276cdc038deedbe600177a45f6584022f34a13f
 * @jenesis.pin scalafmt-format/maven/org.scala-lang.modules/scala-xml_2.13 2.3.0 SHA-256/4b4d6698c74bff84a105102bbf58390980dc7bb8c40bdea4bc727040b3f966bd
 * @jenesis.pin scalafmt-format/maven/org.scala-lang/scala-compiler 2.13.18 SHA-256/2f15891fcae7aad30a3892194fb2abb6224cf7ce5d2bd90fba7f1c48682fca21
 * @jenesis.pin scalafmt-format/maven/org.scala-lang/scala-library 2.13.18 SHA-256/4e85d96ff7bc7dc627985523c3541b9917aaa08e956391380c42db21a2c4e5a0
 * @jenesis.pin scalafmt-format/maven/org.scala-lang/scala-reflect 2.13.18 SHA-256/6935ff1982b2ac93d695f15aa66921be2f602921277afe002f018fd8c7d6e29b
 * @jenesis.pin scalafmt-format/maven/org.scalameta/common2_2.13 4.17.0 SHA-256/42a4fae02d82dc7450dddd69ce1d8b8f159119132898af56169f77b8c9a623bd
 * @jenesis.pin scalafmt-format/maven/org.scalameta/common_2.13 4.17.0 SHA-256/3b5b1f67809daf6044113737af8aa4cc5c3b36f071950cfcf7d02f4fe6b1d19a
 * @jenesis.pin scalafmt-format/maven/org.scalameta/io_2.13 4.17.0 SHA-256/d09dac9887efb0aaf7bbcc5b5e20f7e7e4947800592a44359e2f31b2b6c9699d
 * @jenesis.pin scalafmt-format/maven/org.scalameta/mdoc-parser_2.13 2.9.0 SHA-256/37442a255f0e6a31c9786d7d8af4ef1e29ded76c4f44af29895b4b36d904a068
 * @jenesis.pin scalafmt-format/maven/org.scalameta/metaconfig-core_2.13 0.18.6 SHA-256/f8ed537b95758409a0dd0a9533fdd6da703914f5901f42d6f96ba07c77f58aaf
 * @jenesis.pin scalafmt-format/maven/org.scalameta/metaconfig-pprint_2.13 0.18.6 SHA-256/cbe1531b67e0c080294e3eaeea35952524c91af0c100f517ba7004607f44be46
 * @jenesis.pin scalafmt-format/maven/org.scalameta/metaconfig-typesafe-config_2.13 0.18.6 SHA-256/1ddbdfcc9f425ca385fa0e0079b8f6fc12571b2e03c9a3ae2bc0c832ca288bca
 * @jenesis.pin scalafmt-format/maven/org.scalameta/munit-diff_2.13 1.3.0 SHA-256/789f02dfc17e62626560d48522d16bc550990a4d722d27b38d215a39dd8c9a5d
 * @jenesis.pin scalafmt-format/maven/org.scalameta/parsers_2.13 4.17.0 SHA-256/c0669f252bf96db52ac32c92bf54f2853b8f192287a827626c3efe51b20f94b3
 * @jenesis.pin scalafmt-format/maven/org.scalameta/scalafmt-cli_2.13 3.11.1 SHA-256/1da5a56393f5fcf01d65c86ca91adc5f8d5448447a75bf9d8b229db5a0a84009
 * @jenesis.pin scalafmt-format/maven/org.scalameta/scalafmt-config_2.13 3.11.1 SHA-256/c058aefc9f874c78072fca15a25e72a4d3ab31bc2872266c42cc6e2651f6cbf1
 * @jenesis.pin scalafmt-format/maven/org.scalameta/scalafmt-core_2.13 3.11.1 SHA-256/f5f0e48927f0d5a32763f6b82a8427b40231db65a908c48b4438b000562c32d2
 * @jenesis.pin scalafmt-format/maven/org.scalameta/scalafmt-dynamic-core_2.13 3.11.1 SHA-256/d4efc029f83f7c3ac6f3374b33636c3d38ef24bbb4d04ba300996d0e63991f2b
 * @jenesis.pin scalafmt-format/maven/org.scalameta/scalafmt-dynamic_2.13 3.11.1 SHA-256/101275030c12c0cc8baa9b6b015b93a4500915990a088d964f41fe482366b890
 * @jenesis.pin scalafmt-format/maven/org.scalameta/scalafmt-interfaces 3.11.1 SHA-256/ec8ac6769c5960e8ee04d4fee15a4b82ba6032b3df3f4d725221ef94dd1dad90
 * @jenesis.pin scalafmt-format/maven/org.scalameta/scalafmt-macros_2.13 3.11.1 SHA-256/49a4ab5745cc0b54acdb74b3fd6017451d5699d5d327ca1bea0747bc622edc9f
 * @jenesis.pin scalafmt-format/maven/org.scalameta/scalafmt-sysops_2.13 3.11.1 SHA-256/a862c2bd21ba6b83d403a8aca33af541de356f7d39e88c1656bd2cf6a3b28a80
 * @jenesis.pin scalafmt-format/maven/org.scalameta/scalameta_2.13 4.17.0 SHA-256/7606f691512cac7e52e1c28146cc95001212aeb3737b44d5ea6f4ec51b9a454c
 * @jenesis.pin scalafmt-format/maven/org.scalameta/svm-subs 101.0.0 SHA-256/b31eb8ef90bec4c22a8ec858f5bd007bd46ce80c3dcef9dce238c6f9dd15c1a4
 * @jenesis.pin scalafmt-format/maven/org.scalameta/trees2_2.13 4.17.0 SHA-256/c1ad0ac4c5617ee25ad97b72fb2d03c48d5ee89770e7cc70b0e3e984689296ce
 * @jenesis.pin scalafmt-format/maven/org.scalameta/trees_2.13 4.17.0 SHA-256/b45d46d995e166889cbda015f541ca79afd94ada860513438e6c0ab5f4f84591
 * @jenesis.pin scalafmt-format/maven/org.slf4j/slf4j-api 1.7.36 SHA-256/d3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0
 * @jenesis.pin scalafmt-format/maven/org.tukaani/xz 1.9 SHA-256/211b306cfc44f8f96df3a0a3ddaf75ba8c5289eed77d60d72f889bb855f535e5
 * @jenesis.pin scalafmt-format/maven/org.typelevel/paiges-core_2.13 0.4.4 SHA-256/ffbd59d3648e71c5b8f4474a54121fb3512707e7901245831669aa9e85f3bbf0
 * @jenesis.pin scalafmt-format/maven/org.virtuslab.scala-cli/config_2.13 1.1.3 SHA-256/482ed69e838834aa90741e68871ca2687279b7315cc0436fbd18d9ad342e7b22
 * @jenesis.pin scalafmt-format/maven/org.virtuslab.scala-cli/specification-level_2.13 1.1.3 SHA-256/2532f59b1e1fdbc00eb2a1ee0cdc5ce16d829e2fc48aef082384f4916ced2cbd
 * @jenesis.pin scalafmt/maven/com.facebook/nailgun-server 1.0.1 SHA-256/c852b33463ce343d22f45686dad8e2a29f61a105bdfd95c87f22b0520a482f21
 * @jenesis.pin scalafmt/maven/com.github.luben/zstd-jni 1.5.6-3 SHA-256/f72ede1b39258faf81277dc58de30c71cbae4253732558d2ce10b53d8b5763d5
 * @jenesis.pin scalafmt/maven/com.github.plokhotnyuk.jsoniter-scala/jsoniter-scala-core_2.13 2.13.5 SHA-256/e258e292706ced7ba036882a439a1ecc841750a7fc4fa4a5f42f074e7491406d
 * @jenesis.pin scalafmt/maven/com.github.scopt/scopt_2.13 4.1.0 SHA-256/2e5037bda974630b046794274e344273919abf4727acfcd86352617dce68f82b
 * @jenesis.pin scalafmt/maven/com.lihaoyi/fansi_2.13 0.5.1 SHA-256/e50796c69261fac857469122ab75f5aab4aeef855ca414f184cb132b318c2d9d
 * @jenesis.pin scalafmt/maven/com.lihaoyi/sourcecode_2.13 0.4.0 SHA-256/7ce9b8c285a04280374d739d7b799408b2cfa40cc3173960848aec9ee8450fed
 * @jenesis.pin scalafmt/maven/com.typesafe/config 1.4.5 SHA-256/4a4b0affb22a9572409d3a6bde99ce3f2045c551cadc1ca7fe09690892c526c3
 * @jenesis.pin scalafmt/maven/commons-codec/commons-codec 1.17.0 SHA-256/f700de80ac270d0344fdea7468201d8b9c805e5c648331c3619f2ee067ccfc59
 * @jenesis.pin scalafmt/maven/commons-io/commons-io 2.16.1 SHA-256/f41f7baacd716896447ace9758621f62c1c6b0a91d89acee488da26fc477c84f
 * @jenesis.pin scalafmt/maven/io.airlift/aircompressor 0.27 SHA-256/fdbef3137a28f63bb0cb93487803080ede746a4ec3d421e36c6f0c305c35e5e4
 * @jenesis.pin scalafmt/maven/io.get-coursier.jniutils/windows-jni-utils 0.3.3 SHA-256/ab68efcf162355e20007b238d8108b7ffec0c06b467ee44cbb44ec3f8ac3a7e6
 * @jenesis.pin scalafmt/maven/io.get-coursier/cache-util 2.1.24 SHA-256/447741afb13b90e7f650ed89f230bf0e9d76874f80ef516e2aa3963ba56edf84
 * @jenesis.pin scalafmt/maven/io.get-coursier/coursier-cache_2.13 2.1.24 SHA-256/cd7f125c3e27cd7527d865ddc467a32dbe5a9a4b4bd2ee666f2a2c13032a1c93
 * @jenesis.pin scalafmt/maven/io.get-coursier/coursier-core_2.13 2.1.24 SHA-256/f845ff8f582d8f4c8110676078e60e7737b5cca240b7d7e835fb52a16ffa4194
 * @jenesis.pin scalafmt/maven/io.get-coursier/coursier-proxy-setup 2.1.24 SHA-256/bd7dfa4325fec5e2e4cb671bb42953e970a2f9141526d93aaea678a07da3ce5b
 * @jenesis.pin scalafmt/maven/io.get-coursier/coursier-util_2.13 2.1.24 SHA-256/5735dad5ec821e7ffba283eda5c2cf26ddea1de4c91f2e6210d8cf3a6cab5a6e
 * @jenesis.pin scalafmt/maven/io.get-coursier/coursier_2.13 2.1.24 SHA-256/59266d5d8a6d0005796fafa21060d9de4c38153d2abfb8c1fb689c0b3c14485d
 * @jenesis.pin scalafmt/maven/io.get-coursier/dependency_2.13 0.3.2 SHA-256/cd54da81c7f4e75112b0a29ab566a31628cfe7336ee129f7b9f3f8adf68a141a
 * @jenesis.pin scalafmt/maven/io.github.alexarchambault.windows-ansi/windows-ansi 0.0.6 SHA-256/077dbdbb977c94fc8b1fb82113cfb0aa5304d4e307c4e0688f69a9895f0da482
 * @jenesis.pin scalafmt/maven/io.github.alexarchambault/concurrent-reference-hash-map 1.1.0 SHA-256/552707982209afb9feffdccb2e73cc4681a1353e34a47df1bf643d1d2e0a2f61
 * @jenesis.pin scalafmt/maven/io.github.alexarchambault/is-terminal 0.1.1 SHA-256/73735b053b01ff47b53d28ab2d9b23fd86447f6fb6463dd462258067ad6ad3fa
 * @jenesis.pin scalafmt/maven/io.github.java-diff-utils/java-diff-utils 4.16 SHA-256/620403030d676a4a27f780a3acec7438dee1b1651a1c804fa6bb11bb07399a6f
 * @jenesis.pin scalafmt/maven/javax.inject/javax.inject 1 SHA-256/91c77044a50c481636c32d916fd89c9118a72195390452c81065080f957de7ff
 * @jenesis.pin scalafmt/maven/net.java.dev.jna/jna 4.5.2 SHA-256/0c8eb7acf67261656d79005191debaba3b6bf5dd60a43735a245429381dbecff
 * @jenesis.pin scalafmt/maven/net.java.dev.jna/jna-platform 4.5.2 SHA-256/f1d00c167d8921c6e23c626ef9f1c3ae0be473c95c68ffa012bc7ae55a87e2d6
 * @jenesis.pin scalafmt/maven/org.apache.commons/commons-compress 1.26.2 SHA-256/9168a03141d8fc7eda21a2360d83cc0412bcbb1d6204d992bd48c2573cb3c6b8
 * @jenesis.pin scalafmt/maven/org.apache.commons/commons-lang3 3.14.0 SHA-256/7b96bf3ee68949abb5bc465559ac270e0551596fa34523fddf890ec418dde13c
 * @jenesis.pin scalafmt/maven/org.apache.xbean/xbean-reflect 3.7 SHA-256/104e5e9bb5a669f86722f32281960700f7ec8e3209ef51b23eb9b6d23d1629cb
 * @jenesis.pin scalafmt/maven/org.codehaus.plexus/plexus-archiver 4.10.0 SHA-256/4c07814ff4a39199999ae82bba1e38aa4f25637467fcac6a66ed63a76535799a
 * @jenesis.pin scalafmt/maven/org.codehaus.plexus/plexus-classworlds 2.6.0 SHA-256/52f77c5ec49f787c9c417ebed5d6efd9922f44a202f217376e4f94c0d74f3549
 * @jenesis.pin scalafmt/maven/org.codehaus.plexus/plexus-container-default 2.1.1 SHA-256/8b65840e41ca669a1a69ec1daedb3426304e560b24172cc93bb02e8c199e1ce7
 * @jenesis.pin scalafmt/maven/org.codehaus.plexus/plexus-io 3.5.0 SHA-256/965ed28912cf1ae4c628112c4009e0c19819bc44ed5db8af54ee5eda21036a3e
 * @jenesis.pin scalafmt/maven/org.codehaus.plexus/plexus-utils 4.0.1 SHA-256/96b9cc44439191d2d0635974e2d44e768736b4fb2abcb65f94cd95e41912fa8b
 * @jenesis.pin scalafmt/maven/org.fusesource.jansi/jansi 2.4.1 SHA-256/2e5e775a9dc58ffa6bbd6aa6f099d62f8b62dcdeb4c3c3bbbe5cf2301bc2dcc1
 * @jenesis.pin scalafmt/maven/org.jline/jline/jar/jdk8 3.29.0 SHA-256/ed2680487642df95379f220c09c0f77e25095713387e2bc00c2d6581bb79c804
 * @jenesis.pin scalafmt/maven/org.scala-lang.modules/scala-collection-compat_2.13 2.14.0 SHA-256/95986ac32df70c9ebdd96edfb276cdc038deedbe600177a45f6584022f34a13f
 * @jenesis.pin scalafmt/maven/org.scala-lang.modules/scala-xml_2.13 2.3.0 SHA-256/4b4d6698c74bff84a105102bbf58390980dc7bb8c40bdea4bc727040b3f966bd
 * @jenesis.pin scalafmt/maven/org.scala-lang/scala-compiler 2.13.18 SHA-256/2f15891fcae7aad30a3892194fb2abb6224cf7ce5d2bd90fba7f1c48682fca21
 * @jenesis.pin scalafmt/maven/org.scala-lang/scala-library 2.13.18 SHA-256/4e85d96ff7bc7dc627985523c3541b9917aaa08e956391380c42db21a2c4e5a0
 * @jenesis.pin scalafmt/maven/org.scala-lang/scala-reflect 2.13.18 SHA-256/6935ff1982b2ac93d695f15aa66921be2f602921277afe002f018fd8c7d6e29b
 * @jenesis.pin scalafmt/maven/org.scalameta/common2_2.13 4.17.0 SHA-256/42a4fae02d82dc7450dddd69ce1d8b8f159119132898af56169f77b8c9a623bd
 * @jenesis.pin scalafmt/maven/org.scalameta/common_2.13 4.17.0 SHA-256/3b5b1f67809daf6044113737af8aa4cc5c3b36f071950cfcf7d02f4fe6b1d19a
 * @jenesis.pin scalafmt/maven/org.scalameta/io_2.13 4.17.0 SHA-256/d09dac9887efb0aaf7bbcc5b5e20f7e7e4947800592a44359e2f31b2b6c9699d
 * @jenesis.pin scalafmt/maven/org.scalameta/mdoc-parser_2.13 2.9.0 SHA-256/37442a255f0e6a31c9786d7d8af4ef1e29ded76c4f44af29895b4b36d904a068
 * @jenesis.pin scalafmt/maven/org.scalameta/metaconfig-core_2.13 0.18.6 SHA-256/f8ed537b95758409a0dd0a9533fdd6da703914f5901f42d6f96ba07c77f58aaf
 * @jenesis.pin scalafmt/maven/org.scalameta/metaconfig-pprint_2.13 0.18.6 SHA-256/cbe1531b67e0c080294e3eaeea35952524c91af0c100f517ba7004607f44be46
 * @jenesis.pin scalafmt/maven/org.scalameta/metaconfig-typesafe-config_2.13 0.18.6 SHA-256/1ddbdfcc9f425ca385fa0e0079b8f6fc12571b2e03c9a3ae2bc0c832ca288bca
 * @jenesis.pin scalafmt/maven/org.scalameta/munit-diff_2.13 1.3.0 SHA-256/789f02dfc17e62626560d48522d16bc550990a4d722d27b38d215a39dd8c9a5d
 * @jenesis.pin scalafmt/maven/org.scalameta/parsers_2.13 4.17.0 SHA-256/c0669f252bf96db52ac32c92bf54f2853b8f192287a827626c3efe51b20f94b3
 * @jenesis.pin scalafmt/maven/org.scalameta/scalafmt-cli_2.13 3.11.1 SHA-256/1da5a56393f5fcf01d65c86ca91adc5f8d5448447a75bf9d8b229db5a0a84009
 * @jenesis.pin scalafmt/maven/org.scalameta/scalafmt-config_2.13 3.11.1 SHA-256/c058aefc9f874c78072fca15a25e72a4d3ab31bc2872266c42cc6e2651f6cbf1
 * @jenesis.pin scalafmt/maven/org.scalameta/scalafmt-core_2.13 3.11.1 SHA-256/f5f0e48927f0d5a32763f6b82a8427b40231db65a908c48b4438b000562c32d2
 * @jenesis.pin scalafmt/maven/org.scalameta/scalafmt-dynamic-core_2.13 3.11.1 SHA-256/d4efc029f83f7c3ac6f3374b33636c3d38ef24bbb4d04ba300996d0e63991f2b
 * @jenesis.pin scalafmt/maven/org.scalameta/scalafmt-dynamic_2.13 3.11.1 SHA-256/101275030c12c0cc8baa9b6b015b93a4500915990a088d964f41fe482366b890
 * @jenesis.pin scalafmt/maven/org.scalameta/scalafmt-interfaces 3.11.1 SHA-256/ec8ac6769c5960e8ee04d4fee15a4b82ba6032b3df3f4d725221ef94dd1dad90
 * @jenesis.pin scalafmt/maven/org.scalameta/scalafmt-macros_2.13 3.11.1 SHA-256/49a4ab5745cc0b54acdb74b3fd6017451d5699d5d327ca1bea0747bc622edc9f
 * @jenesis.pin scalafmt/maven/org.scalameta/scalafmt-sysops_2.13 3.11.1 SHA-256/a862c2bd21ba6b83d403a8aca33af541de356f7d39e88c1656bd2cf6a3b28a80
 * @jenesis.pin scalafmt/maven/org.scalameta/scalameta_2.13 4.17.0 SHA-256/7606f691512cac7e52e1c28146cc95001212aeb3737b44d5ea6f4ec51b9a454c
 * @jenesis.pin scalafmt/maven/org.scalameta/svm-subs 101.0.0 SHA-256/b31eb8ef90bec4c22a8ec858f5bd007bd46ce80c3dcef9dce238c6f9dd15c1a4
 * @jenesis.pin scalafmt/maven/org.scalameta/trees2_2.13 4.17.0 SHA-256/c1ad0ac4c5617ee25ad97b72fb2d03c48d5ee89770e7cc70b0e3e984689296ce
 * @jenesis.pin scalafmt/maven/org.scalameta/trees_2.13 4.17.0 SHA-256/b45d46d995e166889cbda015f541ca79afd94ada860513438e6c0ab5f4f84591
 * @jenesis.pin scalafmt/maven/org.slf4j/slf4j-api 1.7.36 SHA-256/d3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0
 * @jenesis.pin scalafmt/maven/org.tukaani/xz 1.9 SHA-256/211b306cfc44f8f96df3a0a3ddaf75ba8c5289eed77d60d72f889bb855f535e5
 * @jenesis.pin scalafmt/maven/org.typelevel/paiges-core_2.13 0.4.4 SHA-256/ffbd59d3648e71c5b8f4474a54121fb3512707e7901245831669aa9e85f3bbf0
 * @jenesis.pin scalafmt/maven/org.virtuslab.scala-cli/config_2.13 1.1.3 SHA-256/482ed69e838834aa90741e68871ca2687279b7315cc0436fbd18d9ad342e7b22
 * @jenesis.pin scalafmt/maven/org.virtuslab.scala-cli/specification-level_2.13 1.1.3 SHA-256/2532f59b1e1fdbc00eb2a1ee0cdc5ce16d829e2fc48aef082384f4916ced2cbd
 * @jenesis.pin scalastyle/maven/com.beautiful-scala/scalastyle_2.13 1.5.1 SHA-256/469254118554648963ebeb2e8c0be0f4c093b8099ed05794b70de3c37e3ba7a1
 * @jenesis.pin scalastyle/maven/com.typesafe/config 1.4.1 SHA-256/4c0aa7e223c75c8840c41fc183d4cd3118140a1ee503e3e08ce66ed2794c948f
 * @jenesis.pin scalastyle/maven/org.scala-lang.modules/scala-collection-compat_2.13 2.5.0 SHA-256/93f8bf202ac28c4ca13562e31f6881a7770768e12b056b568139f37c025a3841
 * @jenesis.pin scalastyle/maven/org.scala-lang.modules/scala-parser-combinators_2.13 1.1.2 SHA-256/5c285b72e6dc0a98e99ae0a1ceeb4027dab9adfa441844046bd3f19e0efdcb54
 * @jenesis.pin scalastyle/maven/org.scala-lang.modules/scala-xml_2.13 1.2.0 SHA-256/213d2b7840bed5d1a1d5abfa1d72d7c7454473a6f77ea329fff0574910056fd3
 * @jenesis.pin scalastyle/maven/org.scala-lang/scala-library 2.13.6 SHA-256/f19ed732e150d3537794fd3fe42ee18470a3f707efd499ecd05a99e727ff6c8a
 * @jenesis.pin scalastyle/maven/org.scalariform/scalariform_2.13 0.2.10 SHA-256/76b6266960750e560b5a3cbbaa58074e909d0da50adf138b6e83555781bb2596
 */
module sample.scala {
    requires scala.library;

    exports sample;
}
