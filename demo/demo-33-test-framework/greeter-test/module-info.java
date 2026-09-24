/**
 * @jenesis.release 25
 * @jenesis.test demo.greeter
 * @jenesis.pin junit/junit 4.13.2 SHA-256/8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3
 * @jenesis.pin org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin org.hamcrest/hamcrest-core 1.3 SHA-256/66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9
 * @jenesis.pin org.jspecify/jspecify 1.0.0 SHA-256/1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab
 * @jenesis.pin org.junit.jupiter.engine 6.1.3 SHA-256/414559e78bbfa3bdb2eab009ac3ab1e22369ac31117c270a0bd666da115c5c6c
 * @jenesis.pin org.junit.jupiter.migrationsupport 6.1.3 SHA-256/4a14527853ed8449d17ee6602693ae666437e0614708b910341552bd78d06075
 * @jenesis.pin org.junit.jupiter/junit-jupiter-api 6.1.3 SHA-256/555d6cf20fa1710884dd01b86cc5785397ba73e21ada2d4b784f5f1a14dcafc4
 * @jenesis.pin org.junit.jupiter/junit-jupiter-engine 6.1.3 SHA-256/414559e78bbfa3bdb2eab009ac3ab1e22369ac31117c270a0bd666da115c5c6c
 * @jenesis.pin org.junit.jupiter/junit-jupiter-migrationsupport 6.1.3 SHA-256/4a14527853ed8449d17ee6602693ae666437e0614708b910341552bd78d06075
 * @jenesis.pin org.junit.platform.console 6.1.3 SHA-256/913554ad65b9420936822889a7268a6793a942e5f77ad8b653b8ec05068fa3be
 * @jenesis.pin org.junit.platform/junit-platform-commons 6.1.3 SHA-256/a4774ae923c109544034aeae7c762cf017fbcdd1342403ba90e0921984b608c3
 * @jenesis.pin org.junit.platform/junit-platform-console 6.1.3 SHA-256/913554ad65b9420936822889a7268a6793a942e5f77ad8b653b8ec05068fa3be
 * @jenesis.pin org.junit.platform/junit-platform-engine 6.1.3 SHA-256/21ad7ad3a35beda16387979317be1833a72a71d812f9a66ee4b9a7bafb56334c
 * @jenesis.pin org.junit.platform/junit-platform-launcher 6.1.3 SHA-256/1dec64b1fc0b7c47a11302aa5b4d37d8160c041b396e27067709ce775601a7b6
 * @jenesis.pin org.junit.platform/junit-platform-reporting 6.1.3 SHA-256/d584a16cd87da5609af5e35428ed4f40b192681a2487dc59d02ecb62f6c417c2
 * @jenesis.pin org.junit.vintage.engine 6.1.3 SHA-256/37a1e6ade75c09c5385c2830b7cda21c4b1f24a5f36ed7cd7397bc40f7dc71bc
 * @jenesis.pin org.junit.vintage/junit-vintage-engine 6.1.3 SHA-256/37a1e6ade75c09c5385c2830b7cda21c4b1f24a5f36ed7cd7397bc40f7dc71bc
 * @jenesis.pin org.opentest4j.reporting/open-test-reporting-tooling-spi 0.2.5 SHA-256/df237b68847637747f0bfdb88fa9cdd9c72cc85550fad0c41ddb33869a5ca516
 * @jenesis.pin org.opentest4j/opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 */
module demo.greeter.test {

    requires demo.greeter;
    requires org.junit.jupiter.migrationsupport;
}
