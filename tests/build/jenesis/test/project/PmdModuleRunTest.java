package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Pinning;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.project.PmdModule;

import static org.assertj.core.api.Assertions.assertThat;

public class PmdModuleRunTest {

    private static final String PINS = """
            pmd/maven/com.github.nawforce/scala-json-rpc-upickle-json-serializer_2.13 1.1.0 SHA-256/4ce9d100d26080a7b8813b6c3c6bc568c7c33b500b2b42120862c3f9fda926ff
            pmd/maven/com.github.nawforce/scala-json-rpc_2.13 1.1.0 SHA-256/0fcac451d102a687a1be2d8cb3ad4854a0beb9a26398f01db03d95f9851fa3e1
            pmd/maven/com.github.oowekyala.ooxml/nice-xml-messages 3.1 SHA-256/0b4ceb5b8362d43c9c6c49b1bd57f1c5da54d3c5c7c305fa39c0a04462bb4799
            pmd/maven/com.github.pathikrit/better-files_2.13 3.9.2 SHA-256/ba44cdde78320ea3800a8e99b5b3d0f3fc3bab0a9e9962c653b016d06e396022
            pmd/maven/com.google.code.findbugs/jsr305 3.0.2 SHA-256/766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7
            pmd/maven/com.google.code.gson/gson 2.11.0 SHA-256/57928d6e5a6edeb2abd3770a8f95ba44dce45f3b23b7a9dc2b309c581552a78b
            pmd/maven/com.google.errorprone/error_prone_annotations 2.27.0 SHA-256/24c923372c58e35d0b9f16a028929bb9aedc77521867c274f2bd0735df5ba1f5
            pmd/maven/com.google.flogger/flogger 0.8 SHA-256/bebe7cd82be6c8d5208d6e960cd4344ea10672132ef06f5d4c71a48ab442b963
            pmd/maven/com.google.flogger/flogger-system-backend 0.8 SHA-256/eb4428e483c5332381778d78c6a19da63b4fef3fa7e40f62dadabea0d7600cb4
            pmd/maven/com.google.guava/failureaccess 1.0.2 SHA-256/8a8f81cf9b359e3f6dfa691a1e776985c061ef2f223c9b2c80753e1b458e8064
            pmd/maven/com.google.guava/guava 33.0.0-jre SHA-256/f4d85c3e4d411694337cb873abea09b242b664bb013320be6105327c45991537
            pmd/maven/com.google.guava/listenablefuture 9999.0-empty-to-avoid-conflict-with-guava SHA-256/b372a037d4230aa57fbeffdef30fd6123f9c0c2db85d0aced00c91b974f33f99
            pmd/maven/com.google.j2objc/j2objc-annotations 2.8 SHA-256/f02a95fa1a5e95edb3ed859fd0fb7df709d121a35290eff8b74dce2ab7f4d6ed
            pmd/maven/com.google.protobuf/protobuf-java 3.19.6 SHA-256/6a9a2dff91dcf71f85be71ae971f6164b5a631dcd34bff08f0618535ca44ad02
            pmd/maven/com.google.summit/summit-ast 2.3.0 SHA-256/f8c489d1fcbfb45fdf0291f9acb1e3cec194209234a55048511ee4f54a607a49
            pmd/maven/com.lihaoyi/geny_2.13 0.6.2 SHA-256/26017fa73ec7fa3cd2d44a4f5d3462c58cd6ddebf7d42d40123768edf7dee43c
            pmd/maven/com.lihaoyi/mainargs_2.13 0.5.4 SHA-256/f22a85b990fc68747b8caa51394a308c2778e9b230d69a3cdd79bb8bd7d1b562
            pmd/maven/com.lihaoyi/sourcecode_2.13 0.3.1 SHA-256/0e663be9a6c81e9515bdd07c04831397e38ea648a211e85ad005300fe840a03d
            pmd/maven/com.lihaoyi/ujson_2.13 1.2.0 SHA-256/68474183a6ff657f97a91488e294cbe977b2a439fec216d5167700c72471f358
            pmd/maven/com.lihaoyi/upack_2.13 1.2.0 SHA-256/1ed89d975c11ca0c87398f57e29ace72ff00835ba87b504a83c4190d17416c26
            pmd/maven/com.lihaoyi/upickle-core_2.13 1.2.0 SHA-256/503d9d2687053a401f974e902ed095e7534f11f9b06448e03543f72c02f4e6bd
            pmd/maven/com.lihaoyi/upickle-implicits_2.13 1.2.0 SHA-256/31d16e260f6eae6c4172f430f2c0711f669cd9dff576aadd1370b8bc5472f8d4
            pmd/maven/com.lihaoyi/upickle_2.13 1.2.0 SHA-256/eba8ec18d8284cfcb10395842c254280b46f97ea2aca7f48b2b3db20205bae6f
            pmd/maven/com.thesamet.scalapb/lenses_2.13 0.11.15 SHA-256/cf85c64803b72d2663b352301d292220ecdb86755897eb57054cd734bc3a35e3
            pmd/maven/com.thesamet.scalapb/scalapb-runtime_2.13 0.11.15 SHA-256/7a5e7b4c6945d76bb05eb05905fe99b1fc6b58db3cc632d81ea7fb3879efbb77
            pmd/maven/commons-codec/commons-codec 1.15 SHA-256/b3e9f6d63a790109bf0d056611fbed1cf69055826defeb9894a71369d246ed63
            pmd/maven/info.picocli/picocli 4.7.5 SHA-256/e83a906fb99b57091d1d68ac11f7c3d2518bd7a81a9c71b259e2c00d1564c8e8
            pmd/maven/io.github.apex-dev-tools/apex-ls_2.13 5.2.0 SHA-256/091fa02fd19a159a757129e3278c1b1fee9c587a9e70aa97c28830aa502cfbde
            pmd/maven/io.github.apex-dev-tools/apex-parser 4.3.0 SHA-256/329270981e99e42ffee60369d6363614bb015fb611da4cdd9657978d217bc26e
            pmd/maven/io.github.apex-dev-tools/apex-types_2.13 1.3.0 SHA-256/9d5bbd48a29abfc704d026382f7c9bb08c8bf0b114efcd065a31fb222f84a90f
            pmd/maven/io.github.apex-dev-tools/outline-parser_2.13 1.3.0 SHA-256/e86854d0aed6b509134fd356ad7d28d7c181f0216e053384b80b662741176313
            pmd/maven/io.github.apex-dev-tools/sobject-types 60.0.1 SHA-256/970ae1d4937e1395d85d6cb134cda5b0bab36a62175beca28cd4386fe8b7570c
            pmd/maven/io.github.apex-dev-tools/standard-types 60.0.1 SHA-256/488401133a155b9b9eb9c380a9d7b29a04ad63fd60f84378c398c76d3e8a33fa
            pmd/maven/io.github.apex-dev-tools/vf-parser 1.1.0 SHA-256/9515efe1c7f81c205d01f508c82f432a72735d132fc6bb5a1ebd954d9f881457
            pmd/maven/io.methvin/directory-watcher 0.18.0 SHA-256/18f67869b0d31d39512623226220abeedd6bde486d5599e6256eab7975110754
            pmd/maven/io.methvin/directory-watcher-better-files_2.13 0.18.0 SHA-256/839d3d970babacb606bb2f2e8461c2d7c40f6e36920753c41bf9561ba638d128
            pmd/maven/javax.annotation/jsr250-api 1.0 SHA-256/a1a922d0d9b6d183ed3800dfac01d1e1eb159f0e8c6f94736931c1def54a941f
            pmd/maven/me.tongfei/progressbar 0.9.5 SHA-256/a1a086fa66f85c49bb3ca701a78cebb33647f367d4a5be8588c784d56272cc6e
            pmd/maven/net.java.dev.jna/jna 5.12.1 SHA-256/91a814ac4f40d60dee91d842e1a8ad874c62197984403d0e3c30d39e55cf53b3
            pmd/maven/net.sf.saxon/Saxon-HE 12.5 SHA-256/98c3a91e6e5aaf9b3e2b37601e04b214a6e67098493cdd8232fcb705fddcb674
            pmd/maven/net.sourceforge.pmd/pmd-ant 7.7.0 SHA-256/cd05dafecbcecca5bcb7946fa3c52d2c59b4ba1e273e6e6f45b522694b9381bd
            pmd/maven/net.sourceforge.pmd/pmd-apex 7.7.0 SHA-256/57fe6e13b89dfb3bea34411139e9e6f188a549f15543060747dbeceafe72354a
            pmd/maven/net.sourceforge.pmd/pmd-cli 7.7.0 SHA-256/ba61729debeb6bbb7f8168230f8fdaf9c3315286a88e4fc0e19022823197a0e8
            pmd/maven/net.sourceforge.pmd/pmd-coco 7.7.0 SHA-256/0106d7fb98d5f1cbdfe8162575baced8777b1c2e6f038d45c680ac095fd011cb
            pmd/maven/net.sourceforge.pmd/pmd-core 7.7.0 SHA-256/154a77eb6f32b00270c65f9796318252b0f25e5f394b1935b456fb8dc0de3563
            pmd/maven/net.sourceforge.pmd/pmd-cpp 7.7.0 SHA-256/7b61fcaf5f8ee9d66805880d528918e9dfba8c16225054bda9a0bc79fc5a1f3e
            pmd/maven/net.sourceforge.pmd/pmd-cs 7.7.0 SHA-256/9e39796ae011ad3ac3c981e9889d315ec5ded085ba0fa50ce4b85177f1a2a7f0
            pmd/maven/net.sourceforge.pmd/pmd-dart 7.7.0 SHA-256/09ae3531d07c55bbaea37477f83a47636b27cdeb3cea1aba50e58d9c75cec601
            pmd/maven/net.sourceforge.pmd/pmd-designer 7.2.0 SHA-256/d7bdecaf6ea499ed0bf2767d638657b36f286f20ac2cc3bc498c25a8a71f1c85
            pmd/maven/net.sourceforge.pmd/pmd-dist 7.7.0 SHA-256/ab31c56321ceec536a8e0591a59645b33f486f3b2cdde7afa914c1261d3b2b23
            pmd/maven/net.sourceforge.pmd/pmd-fortran 7.7.0 SHA-256/cebbd0a9b825844639feabe29e9b3fbf4fc10688d86ad2700c63905730220bf4
            pmd/maven/net.sourceforge.pmd/pmd-gherkin 7.7.0 SHA-256/18629e5a3ef54b579dc7fab1767e0e2784910dd9f28cc461a32c58bce69c0fea
            pmd/maven/net.sourceforge.pmd/pmd-go 7.7.0 SHA-256/933262390a5293dc2ec30df78106c33f6a68b24f2f5aa67991f8fd2df9f52e8a
            pmd/maven/net.sourceforge.pmd/pmd-groovy 7.7.0 SHA-256/82efcb0689c6876d6e2a5c3a42c27335e6901482f093ef2516c8e6b48eefb6e8
            pmd/maven/net.sourceforge.pmd/pmd-html 7.7.0 SHA-256/c4b51bba99f206a308bb39a2f12331413296317b35c91dcfcb6c605cefdc510a
            pmd/maven/net.sourceforge.pmd/pmd-java 7.7.0 SHA-256/95160f77a5ec3da2a3b5f4fe8968d0bfed8155c4ab583e530c49b7c9944eb968
            pmd/maven/net.sourceforge.pmd/pmd-javascript 7.7.0 SHA-256/c93ba2e7943967e6ce541e6c07eca9db819dd242c99cf2ad8ce2a757af001a06
            pmd/maven/net.sourceforge.pmd/pmd-jsp 7.7.0 SHA-256/e5d06fb8b46874a2ca14a21244d36f848edb4a85b3a1ed9010dd9367ae74aa96
            pmd/maven/net.sourceforge.pmd/pmd-julia 7.7.0 SHA-256/ee349032557dd1c8aafbac8d90b9a1b6d4b924bbb8520bebe5c92cfe4101d62f
            pmd/maven/net.sourceforge.pmd/pmd-kotlin 7.7.0 SHA-256/b2564f643d1f1349f3b052a8fd062fb26a8d49f3a0a6c7750da084eb60d1fd01
            pmd/maven/net.sourceforge.pmd/pmd-languages-deps/pom 7.7.0 SHA-256/c2baf0a9a1fcc9f82b95c95692d8a75d9c147500cfee0c2d96e965b982d66bde
            pmd/maven/net.sourceforge.pmd/pmd-lua 7.7.0 SHA-256/318f66b1f5e20caad10ce416bca3c2739d5a68c107fa1a507bd5bba4f0bb886b
            pmd/maven/net.sourceforge.pmd/pmd-matlab 7.7.0 SHA-256/6c201adb0c1149483094e752265ec96fa0a57342307e68efe263f5f157cb3860
            pmd/maven/net.sourceforge.pmd/pmd-modelica 7.7.0 SHA-256/6ee11e8bccad56aa0788363fdb77a2615206c4f327ee591221a8ad549e506b1b
            pmd/maven/net.sourceforge.pmd/pmd-objectivec 7.7.0 SHA-256/48d86fc578ee92cccf76cac0c89364e2a4cd03d4ff1f34c74967597004bef6da
            pmd/maven/net.sourceforge.pmd/pmd-perl 7.7.0 SHA-256/67dac2b726f4fe5f309fd2ab31e82e86417f5ad315af2cf99816ebdef7d47260
            pmd/maven/net.sourceforge.pmd/pmd-php 7.7.0 SHA-256/679ad2736ffb0067aa17a8c48ff30272606639e22982777a9cb9d59b56affe1a
            pmd/maven/net.sourceforge.pmd/pmd-plsql 7.7.0 SHA-256/1176e9c9d32df1cac70baa1a677fc164466037e98cf9fb175e671d0d84f40130
            pmd/maven/net.sourceforge.pmd/pmd-python 7.7.0 SHA-256/266ad9374196ab188ed6e4488a0eab7f4d4c2c6ba5f4d5fc758ce8bdb2ab05aa
            pmd/maven/net.sourceforge.pmd/pmd-ruby 7.7.0 SHA-256/9be3cdf1cb6365dbf43e5282ae25e0b523ebbefadd94a68b6106d4a7318f880b
            pmd/maven/net.sourceforge.pmd/pmd-scala_2.13 7.7.0 SHA-256/0d14fd5efc914fbf1be59b643cd2c668662d24087090b05821f65400affb63ef
            pmd/maven/net.sourceforge.pmd/pmd-swift 7.7.0 SHA-256/662d3fbd5a3cefc1bd2e272475914a17da7f4e6663d8d3a5da012014d28d35fb
            pmd/maven/net.sourceforge.pmd/pmd-tsql 7.7.0 SHA-256/c8515721ae6263006cdd7e87bf7033b0fbc51a48794cb8f17f3d1a702426115e
            pmd/maven/net.sourceforge.pmd/pmd-velocity 7.7.0 SHA-256/69e3f8b9d1dbff1c3c9e4db2236fd84cf94fc3b3fd514cb3b151061990c61862
            pmd/maven/net.sourceforge.pmd/pmd-visualforce 7.7.0 SHA-256/fed550e03961ad29486c01e8ce0d1f08ed7272007da986aef3df148b4630180a
            pmd/maven/net.sourceforge.pmd/pmd-xml 7.7.0 SHA-256/5635cdccad59e08a8018aa68005ec253934b8a769cab5dbced16587d11b7dbef
            pmd/maven/org.antlr/antlr4-runtime 4.9.3 SHA-256/131a6594969bc4f321d652ea2a33bc0e378ca312685ef87791b2c60b29d01ea5
            pmd/maven/org.apache.commons/commons-lang3 3.14.0 SHA-256/7b96bf3ee68949abb5bc465559ac270e0551596fa34523fddf890ec418dde13c
            pmd/maven/org.apache.groovy/groovy 4.0.19 SHA-256/96c650051f44cb6cdaff9d389d06eb881966d1c13d0067f69b39022cda926112
            pmd/maven/org.apache.httpcomponents.client5/httpclient5 5.1.3 SHA-256/28c759254f4e35319e078bb6ffea75676608dc12cb243b24fb3c8732522977fe
            pmd/maven/org.apache.httpcomponents.core5/httpcore5 5.1.3 SHA-256/f2bf2f2c7772169c9e30699719667ad30f9b46c4e9d7841907deb2d12d9923fe
            pmd/maven/org.apache.httpcomponents.core5/httpcore5-h2 5.1.3 SHA-256/d0e78ba15aa8ebe77982b660ac4b09a95d6e035dbdbea762577dc1c8e2935807
            pmd/maven/org.checkerframework/checker-compat-qual 2.5.3 SHA-256/d76b9afea61c7c082908023f0cbc1427fab9abd2df915c8b8a3e7a509bccbc6d
            pmd/maven/org.checkerframework/checker-qual 3.48.1 SHA-256/21e8dfe8103e125d96a329653ca81e87ac430326dbdbf299cea3dc1ae3f039a2
            pmd/maven/org.danilopianini/gson-extras 1.3.0 SHA-256/a8bec65d0eb9d5fcda6410eea9993104f8c0852f8024972d8662b4dac8258c1d
            pmd/maven/org.jetbrains.kotlin/kotlin-stdlib 1.9.24 SHA-256/858b902696da9cf585ab9d98ffc1c2712269828354dfe9107e3711b084a36468
            pmd/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk7 1.9.24 SHA-256/b6699b850ba0789f2e904279cd8bdc7bea9130ffd157cdba001fc7425d8a47b7
            pmd/maven/org.jetbrains.kotlin/kotlin-stdlib-jdk8 1.9.24 SHA-256/5b5bbfb3e1184b5e13317c3d42237fa24add443b2e7781961eea334db136adb1
            pmd/maven/org.jetbrains/annotations 13.0 SHA-256/ace2a10dc8e2d5fd34925ecac03e4988b2c0f851650c94b8cef49ba1bd111478
            pmd/maven/org.jline/jline 3.21.0 SHA-256/1e7d63a2bd1c26354ca1987e55469ea4327c4a3845c10d7a7790ca9729c49c02
            pmd/maven/org.jsoup/jsoup 1.17.2 SHA-256/f60b33b38e9d7ac93eaaa68a6c70f706bb99036494b2e2add2bfee11d09ac6f5
            pmd/maven/org.mozilla/rhino 1.7.15 SHA-256/2427fdcbc149ca0a25ccfbb7c71b01f39ad42708773a47816cd2342861766b63
            pmd/maven/org.ow2.asm/asm 9.7 SHA-256/adf46d5e34940bdf148ecdd26a9ee8eea94496a72034ff7141066b3eea5c4e9d
            pmd/maven/org.pcollections/pcollections 4.0.2 SHA-256/2bbeef5797a241300c4f7513cd546239629ed7deda4fc0c31df90bb95f5f13ef
            pmd/maven/org.scala-js/scalajs-stubs_2.13 1.0.0 SHA-256/60a58e75030081111da2e96e70140e6e370f2d1db7a353fe065b62eb757f82e3
            pmd/maven/org.scala-lang.modules/scala-collection-compat_2.13 2.8.1 SHA-256/9b8cc6028dab5813fe751950382499d655fe8777e2c4b07368eaa9d1116e049c
            pmd/maven/org.scala-lang.modules/scala-parallel-collections_2.13 1.0.0 SHA-256/fc08be49e91db44d7fe5c1ff95a322ad4500805a525cc2c4b1b91693f041c8e4
            pmd/maven/org.scala-lang.modules/scala-xml_2.13 1.3.0 SHA-256/6d96d45a7fc6fc7ab69bdbac841b48cf67ab109f048c8db375ae4effae524f39
            pmd/maven/org.scala-lang/scala-library 2.13.13 SHA-256/b58b223f9b59f88d04c9dc9ec26f4b463e93e6f296de5663dbd710a7dfce2743
            pmd/maven/org.scala-lang/scala-reflect 2.13.10 SHA-256/62bd7a7198193c5373a992c122990279e413af3307162472a5d3cbb8ecadb35e
            pmd/maven/org.scalameta/common_2.13 4.9.1 SHA-256/223c3dc10561ffe4522bb29b2d5e93d2c3d12bbc81d896b6400a31a292e40ab3
            pmd/maven/org.scalameta/parsers_2.13 4.9.1 SHA-256/9240ab9c384572e1aa9cecef91b5a1276498a63b1d0794db7da72615aa0b44de
            pmd/maven/org.scalameta/trees_2.13 4.9.1 SHA-256/d3a86bd56296d39fdf915c5f07629f60261852bd8127e11bef800e3b0c9d3e9e
            pmd/maven/org.slf4j/jul-to-slf4j 1.7.36 SHA-256/9e641fb142c5f0b0623d6222c09ea87523a41bf6bed48ac79940724010b989de
            pmd/maven/org.slf4j/slf4j-api 1.7.36 SHA-256/d3ef575e3e4979678dc01bf1dcce51021493b4d11fb7f1be8ad982877c16a1c0
            pmd/maven/org.slf4j/slf4j-simple 1.7.36 SHA-256/2f39bed943d624dfa8f4102d0571283a10870b6aa36f197a8a506f147010c10f
            pmd/maven/org.xmlresolver/xmlresolver 5.2.2 SHA-256/efc92bd7ed32b3e57095e0b3e872051ccfbbdcc980831ef33e89e38161a85222
            pmd/maven/org.xmlresolver/xmlresolver/jar/data 5.2.2 SHA-256/173904bdbd783ba0fac92c5bcc05da5d09f0ce7eed24346666ea0a239461f9b4
            """;

    private static final String VERSION = "7.7.0";

    private static final String CONFIG = """
            <?xml version="1.0"?>
            <ruleset name="test"
                     xmlns="http://pmd.sourceforge.net/ruleset/2.0.0">
                <rule ref="category/java/bestpractices.xml/SystemPrintln"/>
            </ruleset>
            """;

    @TempDir
    private Path root, project;

    @Test
    public void downloads_the_pinned_pmd_and_writes_a_report() throws IOException {
        SequencedProperties versions = new SequencedProperties();
        versions.load(new StringReader(PINS));
        versions.store(project.resolve(BuildStep.VERSIONS));
        Files.writeString(project.resolve("pmd.xml"), CONFIG);
        Path sampleDir = Files.createDirectories(project.resolve(BuildStep.SOURCES + "sample"));
        Files.writeString(sampleDir.resolve("Sample.java"), """
                package sample;
                public class Sample {
                    public void run() {
                        System.out.println("hello");
                    }
                }
                """);

        BuildExecutor executor = newExecutor();
        executor.addSource("project", project);
        executor.addModule(
                "pmd",
                new PmdModule(Map.of("maven", MavenDefaultRepository.of()), Map.of("maven", new MavenPomResolver())).pinning(Pinning.STRICT),
                "project");
        executor.execute();

        Path resolved = root.resolve("pmd").resolve("dependencies").resolve("output").resolve("resolved");
        try (Stream<Path> jars = Files.list(resolved)) {
            assertThat(jars.map(jar -> jar.getFileName().toString()))
                    .as("the pinned PMD version resolves")
                    .anyMatch(name -> name.contains("pmd-core") && name.contains(VERSION));
        }
        Path report = root.resolve("pmd").resolve("check").resolve("output").resolve("reports").resolve("pmd").resolve("pmd-report.xml");
        assertThat(report).isNotEmptyFile();
        assertThat(report).content().contains("SystemPrintln");
    }

    private BuildExecutor newExecutor() throws IOException {
        return BuildExecutor.of(root,
                Duration.ZERO,
                new HashDigestFunction("MD5"),
                BuildStepHashFunction.ofSerializationDigest("MD5"),
                BuildExecutorCallback.nop(), BuildExecutorCache.nop(), false, false, 0);
    }
}
