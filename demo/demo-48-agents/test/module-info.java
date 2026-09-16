/**
 * @jenesis.release 25
 * @jenesis.test demo.agents
 * @jenesis.attach org.mockito
 * @jenesis.pin net.bytebuddy/byte-buddy 1.17.7 SHA-256/3575dcb8a98faf943d3c1595c47a16047c4fce8a83ebbb26262f1a2f67546357
 * @jenesis.pin net.bytebuddy/byte-buddy-agent 1.17.7 SHA-256/a9ba887dca252ad61b7d5153294f34e6f3bdf4b2736b04373d13615a695fc0ff
 * @jenesis.pin org.apiguardian/apiguardian-api 1.1.2 SHA-256/b509448ac506d607319f182537f0b35d71007582ec741832a1f111e5b5b70b38
 * @jenesis.pin org.junit.jupiter 5.11.3
 * @jenesis.pin org.junit.jupiter/junit-jupiter 5.11.3 SHA-256/ac7578efed162367c3ddc006338e07d4571510fd9866642ea93d5b9e4ed2f665
 * @jenesis.pin org.junit.jupiter/junit-jupiter-api 5.11.3 SHA-256/5d8147a60f49453973e250ed68701b7ff055964fe2462fc2cb1ec1d6d44889ba
 * @jenesis.pin org.junit.jupiter/junit-jupiter-engine 5.11.3 SHA-256/e62420c99f7c0d59a2159a2ef63e61877e9c80bd722c03ca8bf3bdcea050a589
 * @jenesis.pin org.junit.jupiter/junit-jupiter-params 5.11.3 SHA-256/0f798ebec744c4e6605fd4f2072f41a8e989e2d469e21db5aa67cf799c0b51ec
 * @jenesis.pin org.junit.platform.console 1.11.3
 * @jenesis.pin org.junit.platform/junit-platform-commons 1.11.3 SHA-256/be262964b0b6b48de977c61d4f931df8cf61e80e750cc3f3a0a39cdd21c1008c
 * @jenesis.pin org.junit.platform/junit-platform-console 1.11.3 SHA-256/a21b34807eb7d8aa56295d152ff7e0988bd22bbd5f17086c10f42b5c5ac46033
 * @jenesis.pin org.junit.platform/junit-platform-engine 1.11.3 SHA-256/0043f72f611664735da8dc9a308bf12ecd2236b05339351c4741edb4d8fab0da
 * @jenesis.pin org.junit.platform/junit-platform-launcher 1.11.3 SHA-256/b4727459201b0011beb0742bd807421a1fc8426b116193031ed87825bc2d4f04
 * @jenesis.pin org.junit.platform/junit-platform-reporting 1.11.3 SHA-256/b8e19dbebcae7d1ff30b9d767047fbf3694027c33dfa423b371693b7f6679ed1
 * @jenesis.pin org.mockito 5.23.0
 * @jenesis.pin org.mockito/mockito-core 5.23.0 SHA-256/ae295bebd5d11fab97ab297815dc7617188b86003cbce3dfd5c0d5c3a6cc4a0c
 * @jenesis.pin org.objenesis/objenesis 3.3 SHA-256/02dfd0b0439a5591e35b708ed2f5474eb0948f53abf74637e959b8e4ef69bfeb
 * @jenesis.pin org.opentest4j/opentest4j 1.3.0 SHA-256/48e2df636cab6563ced64dcdff8abb2355627cb236ef0bf37598682ddf742f1b
 */
open module demo.agents.test {
    requires demo.agents;
    requires org.junit.jupiter;
    requires org.mockito;
}
