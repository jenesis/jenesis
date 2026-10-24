/**
 * An ordinary consumer. It requires the library and nothing else: the legacy tree the library needs
 * is not on its path, not in its pins, and not in its vocabulary. That is the whole point - a
 * consumer of a library that isolates its dependencies declares nothing about them.
 *
 * @jenesis.release 25
 * @jenesis.main demo.legacy.app.Main
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.6.0 SHA-256/38a0381e95f6980b965b11bc05958482d0550f3050f901a574bf30b26536539b
 * @jenesis.pin layer:beans/maven/commons-beanutils/commons-beanutils 1.9.4 SHA-256/7d938c81789028045c08c065e94be75fc280527620d5bd62b519d5838532368a
 * @jenesis.pin layer:beans/maven/commons-beanutils/commons-beanutils/pom 1.9.4 SHA-256/c35cca7b61d4678d9578cbc0b901b8717b539abf9254441da78b8fe60de064d0
 * @jenesis.pin layer:beans/maven/commons-collections/commons-collections 3.2.2 SHA-256/eeeae917917144a68a741d4c0dff66aa5c5c5fd85593ff217bced3fc8ca783b8
 * @jenesis.pin layer:beans/maven/commons-collections/commons-collections/pom 3.2.2 SHA-256/d5d81fcc288c0d8c711c302007cada4aa9a226ed1a112d4baa64cb1d6322170b
 * @jenesis.pin layer:beans/maven/commons-logging/commons-logging 1.2 SHA-256/daddea1ea0be0f56978ab3006b8ac92834afeefbd9b7e4e6316fca57df0fa636
 * @jenesis.pin layer:beans/maven/commons-logging/commons-logging/pom 1.2 SHA-256/c91ab5aa570d86f6fd07cc158ec6bc2c50080402972ee9179fe24100739fbb20
 * @jenesis.pin layer:beans/maven/org.apache.commons/commons-parent/pom 34 SHA-256/3a2e69d06d641d1f3b293126dc9e2e4ea6563bf8c36c87e0ab6fa4292d04b79c
 * @jenesis.pin layer:beans/maven/org.apache.commons/commons-parent/pom 39 SHA-256/87cd27e1a02a5c3eb6d85059ce98696bb1b44c2b8b650f0567c86df60fa61da7
 * @jenesis.pin layer:beans/maven/org.apache.commons/commons-parent/pom 47 SHA-256/8a8ecb570553bf9f1ffae211a8d4ca9ee630c17afe59293368fba7bd9b42fcb7
 * @jenesis.pin layer:beans/maven/org.apache/apache/pom 13 SHA-256/ff513db0361fd41237bef4784968bc15aae478d4ec0a9496f811072ccaf3841d
 * @jenesis.pin layer:beans/maven/org.apache/apache/pom 16 SHA-256/9f85ff2fd7d6cb3097aa47fb419ee7f0ebe869109f98aba9f4eca3f49e74a40e
 * @jenesis.pin layer:beans/maven/org.apache/apache/pom 19 SHA-256/91f7a33096ea69bac2cbaf6d01feb934cac002c48d8c8cfa9c240b40f1ec21df
 * @jenesis.pin maven/build.jenesis/build.jenesis.launcher/pom 0.6.0 SHA-256/62237345fd053bdd3b0c4681f6b65d79d856eb07bb761b9cd9520626f6ba12ce
 */
module demo.legacy.app {
    requires demo.legacy.library;
}
