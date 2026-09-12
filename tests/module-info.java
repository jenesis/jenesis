/**
 * Test sources for {@code build.jenesis}.
 *
 * @jenesis.release 25
 * @jenesis.test build.jenesis
 * @jenesis.pin net.bytebuddy 1.15.11 SHA-256/fa08998aae1e7bdae83bde0712c50e8444d71c0e0c196bb2247ade8d4ad0eb90
 * @jenesis.pin net.bytebuddy/byte-buddy 1.15.11 SHA-256/fa08998aae1e7bdae83bde0712c50e8444d71c0e0c196bb2247ade8d4ad0eb90
 * @jenesis.pin org.apiguardian.api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin org.assertj.core 3.27.0 SHA-256/0b4d14008475fb362c2db090bc89c41b864d870216ccf8e8188fb60eb112ad68
 * @jenesis.pin org.assertj/assertj-core 3.27.0 SHA-256/0b4d14008475fb362c2db090bc89c41b864d870216ccf8e8188fb60eb112ad68
 * @jenesis.pin org.junit.jupiter 5.11.3 SHA-256/ac7578efed162367c3ddc006338e07d4571510fd9866642ea93d5b9e4ed2f665
 * @jenesis.pin org.junit.jupiter.api 5.11.3 SHA-256/5d8147a60f49453973e250ed68701b7ff055964fe2462fc2cb1ec1d6d44889ba
 * @jenesis.pin org.junit.jupiter.engine 5.11.3 SHA-256/e62420c99f7c0d59a2159a2ef63e61877e9c80bd722c03ca8bf3bdcea050a589
 * @jenesis.pin org.junit.jupiter.params 5.11.3 SHA-256/0f798ebec744c4e6605fd4f2072f41a8e989e2d469e21db5aa67cf799c0b51ec
 * @jenesis.pin org.junit.jupiter/junit-jupiter 5.11.3 SHA-256/ac7578efed162367c3ddc006338e07d4571510fd9866642ea93d5b9e4ed2f665
 * @jenesis.pin org.junit.jupiter/junit-jupiter-api 5.11.3 SHA-256/5d8147a60f49453973e250ed68701b7ff055964fe2462fc2cb1ec1d6d44889ba
 * @jenesis.pin org.junit.jupiter/junit-jupiter-engine 5.11.3 SHA-256/e62420c99f7c0d59a2159a2ef63e61877e9c80bd722c03ca8bf3bdcea050a589
 * @jenesis.pin org.junit.jupiter/junit-jupiter-params 5.11.3 SHA-256/0f798ebec744c4e6605fd4f2072f41a8e989e2d469e21db5aa67cf799c0b51ec
 * @jenesis.pin org.junit.platform.commons 1.11.3 SHA-256/be262964b0b6b48de977c61d4f931df8cf61e80e750cc3f3a0a39cdd21c1008c
 * @jenesis.pin org.junit.platform.console 1.11.3 SHA-256/a21b34807eb7d8aa56295d152ff7e0988bd22bbd5f17086c10f42b5c5ac46033
 * @jenesis.pin org.junit.platform.engine 1.11.3 SHA-256/0043f72f611664735da8dc9a308bf12ecd2236b05339351c4741edb4d8fab0da
 * @jenesis.pin org.junit.platform.launcher 1.11.3 SHA-256/b4727459201b0011beb0742bd807421a1fc8426b116193031ed87825bc2d4f04
 * @jenesis.pin org.junit.platform.reporting 1.11.3 SHA-256/b8e19dbebcae7d1ff30b9d767047fbf3694027c33dfa423b371693b7f6679ed1
 * @jenesis.pin org.junit.platform/junit-platform-commons 1.11.3 SHA-256/be262964b0b6b48de977c61d4f931df8cf61e80e750cc3f3a0a39cdd21c1008c
 * @jenesis.pin org.junit.platform/junit-platform-engine 1.11.3 SHA-256/0043f72f611664735da8dc9a308bf12ecd2236b05339351c4741edb4d8fab0da
 * @jenesis.pin org.opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 * @jenesis.pin org.opentest4j/opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 * @jenesis.signature OpenPGP/B4AC8CDC141AF0AE468D16921DA784CCB5C46DD5 net.bytebuddy/*
 * @jenesis.signature OpenPGP/BE685132AFD2740D9095F9040CC0B712FEE75827 org.assertj/*
 * @jenesis.signature OpenPGP/FF6E2C001948C5F2F38B0CC385911F425EC61B51 org.apiguardian/* org.junit.jupiter/* org.junit.platform/* org.opentest4j/*
 */
open module build.jenesis.test {

    requires build.jenesis;
    requires java.compiler;
    requires jdk.httpserver;
    requires org.junit.jupiter;
    requires org.assertj.core;
}
