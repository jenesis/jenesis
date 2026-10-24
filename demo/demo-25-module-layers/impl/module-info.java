/**
 * The provider, isolated in the layer with the jackson-core it pins. It requires the API module,
 * which resolves from the library rather than from the layer, so the {@code Report} it provides is
 * the very class the library looks up.
 *
 * It also declares a layer of its own, to show that nesting is unbounded: a module isolated in one
 * layer may isolate a dependency of its own in another, and reaches it exactly as the library
 * reaches this one.
 *
 * @jenesis.release 25
 * @jenesis.pin build.jenesis.launcher 0.6.0 SHA-256/38a0381e95f6980b965b11bc05958482d0550f3050f901a574bf30b26536539b
 * @jenesis.pin build.jenesis/build.jenesis.launcher 0.6.0 SHA-256/38a0381e95f6980b965b11bc05958482d0550f3050f901a574bf30b26536539b
 * @jenesis.pin com.fasterxml.jackson.core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 * @jenesis.pin com.fasterxml.jackson.core/jackson-core 2.15.4 SHA-256/8dc9210dd285db366f45f518dd1e6a9ccfeb0f1a8e184a899fe96d29edf1fd94
 * @jenesis.pin layer:inner/maven/com.fasterxml.jackson.core/jackson-core 2.13.5 SHA-256/48f36a025311d0464ad8dda4512a20c79e279a9550f63f3179d731d94482474b
 * @jenesis.pin layer:inner/maven/com.fasterxml.jackson.core/jackson-core/pom 2.13.5 SHA-256/ad91ab6025eb48ae3262280c42efebb57b70d25c00ab0a69c5a3c7ca3a8078ee
 * @jenesis.pin layer:inner/maven/com.fasterxml.jackson/jackson-base/pom 2.13.5 SHA-256/f2e41218dd50b92ce4983c5d2c04209dd61cd1e00fc125e3600aa9eaf4501dea
 * @jenesis.pin layer:inner/maven/com.fasterxml.jackson/jackson-bom/pom 2.13.5 SHA-256/0a26b6f34ab93f9cfa80179888c6bf425f1c17dcd9c11db6561393930fdb746a
 * @jenesis.pin layer:inner/maven/com.fasterxml.jackson/jackson-parent/pom 2.13 SHA-256/2bba89978172af1effcb4d143d09921a3f3082ca4dcf122b1ded98bf55b2ad57
 * @jenesis.pin layer:inner/maven/com.fasterxml/oss-parent/pom 43 SHA-256/e5585cc1c37079b2e3817a8997945736f158831844d59d0e4d3a45b27611f926
 * @jenesis.pin maven/build.jenesis/build.jenesis.launcher/pom 0.6.0 SHA-256/62237345fd053bdd3b0c4681f6b65d79d856eb07bb761b9cd9520626f6ba12ce
 * @jenesis.pin maven/com.fasterxml.jackson.core/jackson-core/pom 2.15.4 SHA-256/73e2105b84d751aeb20d1f39f92b5a53b336211ec9abe82345255355f0575a12
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-base/pom 2.15.4 SHA-256/74e3ad3c08aa3710299f3582538e07ed8c5026c3c128b66a6189763d74e58c88
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-bom/pom 2.15.4 SHA-256/6cc2d925fcadf5cd225254cbf4e4a78bb9af9194d018717733d598461e35fd98
 * @jenesis.pin maven/com.fasterxml.jackson/jackson-parent/pom 2.15 SHA-256/6cdf97bc66f389f63e36850d2f552d1216688f8e5a58727d3f6a98edf8675cde
 * @jenesis.pin maven/com.fasterxml/oss-parent/pom 50 SHA-256/f5da55dd7b88fb170c46801d17774a652fb2f4581fb5b1d0a5fc86aa182b8577
 * @jenesis.pin maven/org.junit/junit-bom/pom 5.9.2 SHA-256/2ed07d65845131f5336a86476c9a4056b59d0b58b9815ab3679bb0f36f35f705
 * @jenesis.layer inner api demo.layers.spi
 * @jenesis.layer inner provider demo.layers.nested
 */
module demo.layers.impl {
    requires build.jenesis.launcher;
    requires com.fasterxml.jackson.core;
    requires demo.layers.spi;

    provides demo.layers.spi.Report with demo.layers.impl.JacksonReport;
}
