package build.jenesis.test.project;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorCache;
import build.jenesis.BuildExecutorCallback;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepHashFunction;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.InferredComplianceModule;
import build.jenesis.project.InferredDocumentationModule;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.InferredTestObservationModule;
import build.jenesis.project.ProjectModule;
import build.jenesis.project.ProjectModuleDescriptor;
import build.jenesis.step.Docker;
import build.jenesis.step.Inventory;
import build.jenesis.step.JPackage;
import build.jenesis.step.NativeImage;
import build.jenesis.step.ProcessBuildStep;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class InferredMultiProjectAssemblerTest {

    @TempDir
    private Path root;

    @Test
    public void main_in_module_properties_yields_main_class_argument_for_jar() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jarArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jar.properties"));
        assertThat(jarArguments.getProperty("--main-class")).isEqualTo("com.example.Entry");
    }

    @Test
    public void absent_main_in_module_properties_yields_no_jar_arguments() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jar.properties")).doesNotExist();
    }

    @Test
    public void empty_main_in_module_properties_yields_no_jar_arguments() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jar.properties")).doesNotExist();
    }

    @Test
    public void main_in_module_properties_yields_jpackage_arguments() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "artifact=demo\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jpackageArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties"));
        assertThat(jpackageArguments.getProperty("--name")).isEqualTo("demo");
        assertThat(jpackageArguments.getProperty("--main-jar")).isEqualTo("demo.jar");
        assertThat(jpackageArguments.getProperty("--main-class")).isEqualTo("com.example.Entry");
    }

    @Test
    public void snapshot_version_reaches_jpackage_app_version_verbatim() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "artifact=demo\nversion=0-SNAPSHOT\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jpackageArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties"));
        assertThat(jpackageArguments.getProperty("--app-version"))
                .as("the version is the project's to get right, so a qualifier reaches jpackage and fails there")
                .isEqualTo("0-SNAPSHOT");
    }

    @Test
    public void non_numeric_version_reaches_jpackage_app_version_verbatim() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "artifact=demo\nversion=RELEASE\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jpackageArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties"));
        assertThat(jpackageArguments.getProperty("--app-version"))
                .as("nothing is invented for a version jpackage cannot parse; it is reported by jpackage itself")
                .isEqualTo("RELEASE");
    }

    @Test
    public void describes_a_debian_package_with_the_metadata_it_supports() throws IOException {
        SequencedProperties arguments = describedPackage("deb");
        assertThat(arguments.getProperty("--description"))
                .as("a description spanning lines is passed as one argument")
                .isEqualTo("A demo project");
        assertThat(arguments.getProperty("--about-url")).isEqualTo("https://example.com/demo");
        assertThat(arguments.getProperty("--linux-deb-maintainer")).isEqualTo("dev@example.com");
        assertThat(arguments.getProperty("--linux-rpm-license-type")).isNull();
    }

    @Test
    public void describes_an_rpm_package_with_the_licences_the_project_offers() throws IOException {
        SequencedProperties arguments = describedPackage("rpm");
        assertThat(arguments.getProperty("--linux-rpm-license-type"))
                .as("a project that lists several licences may be used under any of them")
                .isEqualTo("Apache-2.0 OR MIT");
        assertThat(arguments.getProperty("--linux-deb-maintainer")).isNull();
    }

    @Test
    public void names_no_rpm_licence_type_unless_every_licence_is_identified() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false, "rpm");
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), """
                artifact=demo
                license.apache.name=The Apache Software License, Version 2.0
                license.own.name=A licence of our own
                """);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(formatted(prepareOutput, "rpm").getProperty("--linux-rpm-license-type"))
                .as("RPM expects SPDX identifiers, so a licence without one leaves the field to jpackage")
                .isNull();
    }

    @Test
    public void describes_an_application_image_only_with_what_jpackage_accepts_for_one() throws IOException {
        SequencedProperties arguments = describedPackage("app-image");
        assertThat(arguments.getProperty("--description")).isEqualTo("A demo project");
        assertThat(arguments.getProperty("--vendor")).isEqualTo("Example Ltd");
        assertThat(arguments.getProperty("--copyright"))
                .as("the copyright is passed as declared, so jpackage adds no year of its own")
                .isEqualTo("Copyright 2020 Example Ltd");
        assertThat(arguments.stringPropertyNames())
                .as("jpackage refuses the options of an installable package for an application image")
                .doesNotContain("--about-url", "--linux-deb-maintainer", "--linux-rpm-license-type");
    }

    private SequencedProperties describedPackage(String type) throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false, type);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), """
                artifact=demo
                description=A demo\\n    project
                url=https://example.com/demo
                developer.dev.name=Dev
                developer.dev.email=dev@example.com
                license.apache.name=The Apache Software License, Version 2.0
                license.mit.name=MIT
                organization.name=Example Ltd
                copyright=Copyright 2020 Example Ltd
                """);
        return formatted(fixture.execute("sub/prepare").get("sub/prepare"), type);
    }

    private static SequencedProperties formatted(Path prepareOutput, String type) throws IOException {
        SequencedProperties properties = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties"));
        Path typed = prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage-" + type + ".properties");
        if (Files.isRegularFile(typed)) {
            SequencedProperties.ofFiles(typed).forEachProperty(properties::setProperty);
        }
        return properties;
    }

    @Test
    public void describes_every_format_in_options_of_its_own() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"),
                "jpackage=app-image, deb\ndocker=example:latest\ndocker.jpackage=rpm\n");
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), """
                artifact=demo
                url=https://example.com/demo
                developer.dev.email=dev@example.com
                license.mit.name=MIT
                """);
        Path process = fixture.execute("sub/prepare").get("sub/prepare").resolve(ProcessBuildStep.PROCESS);
        assertThat(readProperties(process.resolve("jpackage.properties")).stringPropertyNames())
                .as("what one format takes and another refuses stays out of the options every format reads")
                .contains("--name")
                .doesNotContain("--about-url", "--linux-deb-maintainer", "--linux-rpm-license-type");
        assertThat(process.resolve("jpackage-app-image.properties")).doesNotExist();
        assertThat(readProperties(process.resolve("jpackage-deb.properties")).stringPropertyNames())
                .containsExactlyInAnyOrder("--about-url", "--linux-deb-maintainer");
        assertThat(readProperties(process.resolve("jpackage-rpm.properties")).stringPropertyNames())
                .as("the format the image installs is described like any other")
                .containsExactlyInAnyOrder("--about-url", "--linux-rpm-license-type");
    }

    @Test
    public void absent_main_in_module_properties_yields_no_jpackage_arguments() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties")).doesNotExist();
    }

    @Test
    public void package_type_enabled_adds_jpackage_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false, "app-image");
        Path packageOutput = fixture.execute("package/jpackage").get("package/jpackage");
        assertThat(packageOutput.resolve(JPackage.PACKAGES))
                .as("a module without a main class produces no application image")
                .doesNotExist();
    }

    @Test
    public void package_type_disabled_omits_jpackage_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/jpackage"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: jpackage - ");
    }

    @Test
    public void modular_main_yields_module_jpackage_argument() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\nmodule=com.example.foo\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jpackageArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jpackage.properties"));
        assertThat(jpackageArguments.getProperty("--module")).isEqualTo("com.example.foo/com.example.Entry");
        assertThat(jpackageArguments.stringPropertyNames())
                .as("modular launch uses --module, not the classpath --main-jar/--main-class")
                .doesNotContain("--main-jar", "--main-class");
    }

    @Test
    public void module_in_module_properties_yields_add_modules_for_jlink() throws IOException {
        Fixture fixture = setUp("module=foo\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties jlinkArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jlink.properties"));
        assertThat(jlinkArguments.getProperty("--add-modules")).isEqualTo("foo");
    }

    @Test
    public void absent_module_in_module_properties_yields_no_jlink_arguments() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("jlink.properties")).doesNotExist();
    }

    @Test
    public void a_process_command_file_in_configuration_yields_tool_arguments() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-g=\n-parameters=\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties javacArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties"));
        assertThat(javacArguments.getProperty("-g")).isEqualTo("");
        assertThat(javacArguments.getProperty("-parameters")).isEqualTo("");
    }

    @Test
    public void an_environment_file_in_configuration_yields_the_variables_of_a_tool() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("environment-test.properties"), "SAMPLE=value\nINHERITED\n");
        Files.writeString(fixture.profile().resolve("environment-java.properties"), "OTHER=value\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties variables = readProperties(prepareOutput.resolve(ProcessBuildStep.ENVIRONMENT).resolve("test.properties"));
        assertThat(variables.getProperty("SAMPLE")).isEqualTo("value");
        assertThat(variables.getProperty("INHERITED"))
                .as("a variable without a value is taken from the build's environment when the tool runs")
                .isEqualTo("");
        assertThat(readProperties(prepareOutput.resolve(ProcessBuildStep.ENVIRONMENT).resolve("java.properties"))
                .getProperty("OTHER")).isEqualTo("value");
    }

    @Test
    public void a_variable_in_a_process_or_environment_file_is_the_setting_it_names() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-Xmaxwarns=@warnings\n");
        Files.writeString(fixture.configuration().resolve("environment-test.properties"),
                "GREETING=@greeting/Hello\nLITERAL=@@literal\n");
        Path prepareOutput = fixture.execute(InferredMultiProjectAssembler.ofEnvironment(
                new Environment(Map.of("variable.warnings", "500"))), "sub/prepare").get("sub/prepare");
        assertThat(readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties"))
                .getProperty("-Xmaxwarns")).isEqualTo("500");
        SequencedProperties variables = readProperties(prepareOutput.resolve(ProcessBuildStep.ENVIRONMENT).resolve("test.properties"));
        assertThat(variables.getProperty("GREETING"))
                .as("a variable that is not set takes its default")
                .isEqualTo("Hello");
        assertThat(variables.getProperty("LITERAL")).isEqualTo("@literal");
        prepareOutput = fixture.execute(InferredMultiProjectAssembler.ofEnvironment(
                new Environment(Map.of("variable.warnings", "600"))), "sub/prepare").get("sub/prepare");
        assertThat(readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties"))
                .getProperty("-Xmaxwarns"))
                .as("a resolved variable is part of the step's key, so another value runs it again")
                .isEqualTo("600");
    }

    @Test
    public void a_variable_that_is_not_set_and_has_no_default_names_the_setting() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("environment-test.properties"), "GREETING=@greeting\n");
        assertThatThrownBy(() -> fixture.execute("sub/prepare"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("GREETING to @greeting, but jenesis.variable.greeting is not set")
                .hasMessageContaining("@greeting/<default>");
    }

    @Test
    public void a_process_command_file_adds_to_and_overrides_generated_arguments() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "version=1.0\n");
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "--module-version=9.9\n-g=\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties javacArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties"));
        assertThat(javacArguments.getProperty("--module-version"))
                .as("a configuration key overrides the build-generated one")
                .isEqualTo("9.9");
        assertThat(javacArguments.getProperty("-g"))
                .as("a configuration key without a build-generated counterpart is added")
                .isEqualTo("");
    }

    @Test
    public void a_process_command_file_refuses_an_argument_the_module_declaration_hands_the_tool() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(Files.createDirectories(fixture.manifests().resolve(ProcessBuildStep.PROCESS))
                .resolve("javac.properties"), "--release=8\n");
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "--release=17\n-g=\n");
        assertThatThrownBy(() -> fixture.execute("sub/prepare"))
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .rootCause()
                .hasMessageContaining("process-javac.properties sets --release, which the build already hands javac as"
                        + " --release 8")
                .hasMessageContaining("@jenesis.release")
                .hasMessageContaining("maven.compiler.testRelease");
    }

    @Test
    public void a_process_command_file_sets_an_argument_that_a_module_it_depends_on_sets_as_well() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(Files.createDirectories(fixture.artifacts().resolve(ProcessBuildStep.PROCESS))
                .resolve("javac.properties"), "-g=\n");
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-g=\n");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(SequencedProperties.ofFiles(prepareOutput.resolve(ProcessBuildStep.PROCESS + "javac.properties")))
                .as("only the module's own declaration hands javac an argument, not what an upstream module prepared")
                .containsEntry("-g", "");
    }

    @Test
    public void an_empty_higher_precedence_process_command_file_shadows_a_lower_one() throws IOException {
        Fixture fixture = setUp("main=\n", false, false, false);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "version=1.0\n");
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-g=\n");
        Files.writeString(fixture.profile().resolve("process-javac.properties"), "");
        Path prepareOutput = fixture.execute("sub/prepare").get("sub/prepare");
        SequencedProperties javacArguments = readProperties(prepareOutput.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties"));
        assertThat(javacArguments.getProperty("--module-version"))
                .as("the build-generated arguments remain")
                .isEqualTo("1.0");
        assertThat(javacArguments.getProperty("-g"))
                .as("the first-discovered (empty) file wins, shadowing the lower location's arguments")
                .isNull();
    }

    @Test
    public void jmod_flag_enabled_packages_a_module_archive() throws IOException {
        Fixture fixture = setUp("module=foo\n", false, false, false, null, true, false);
        Files.writeString(
                Files.createDirectory(fixture.sources.resolve(BuildStep.SOURCES)).resolve("module-info.java"),
                "module foo { }\n");
        Path jmodOutput = fixture.execute("sub/jmod").get("sub/jmod");
        assertThat(jmodOutput.resolve("jmods").resolve("foo.jmod")).isNotEmptyFile();
    }

    @Test
    public void jmod_flag_disabled_omits_jmod_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/jmod"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: jmod - ");
    }

    @Test
    public void jlink_flag_disabled_omits_jlink_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/jlink"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: jlink - ");
    }

    @Test
    public void native_image_flag_disabled_omits_native_image_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/native-image"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: native-image - ");
    }

    @Test
    public void labels_the_docker_image_with_the_metadata_of_the_module_and_the_configured_labels() throws IOException {
        Fixture fixture = setUp("main=sample.Sample\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"),
                "docker=example:latest\ndocker.label.com.example.team=core\n");
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "project=sample\nartifact=app\nversion=1\n");
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(
                Files.createDirectory(fixture.artifacts().resolve(BuildStep.ARTIFACTS)).resolve("app.jar")))) {
            jar.putNextEntry(new JarEntry("sample/Sample.class"));
            jar.closeEntry();
        }
        Path docker = fixture.execute("package/docker").get("package/docker");
        assertThat(docker.resolve(Docker.DOCKER).resolve("Dockerfile"))
                .content()
                .as("the metadata of the module reaches the image through its manifests")
                .contains("\"org.opencontainers.image.title\"=\"app\"")
                .contains("\"org.opencontainers.image.version\"=\"1\"")
                .contains("\"com.example.team\"=\"core\"");
    }

    @Test
    public void refuses_a_docker_label_without_the_image_it_belongs_to() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"), "docker.label.com.example.team=core\n");
        assertThatThrownBy(() -> fixture.execute("package/docker"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("without docker=<image>");
    }

    @Test
    public void a_process_command_file_for_one_format_reaches_that_format_alone() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false, "app-image,deb");
        Files.writeString(fixture.configuration().resolve("process-jpackage-deb.properties"), "--linux-app-release=3\n");
        Path process = fixture.execute("sub/prepare").get("sub/prepare").resolve(ProcessBuildStep.PROCESS);
        assertThat(readProperties(process.resolve("jpackage-deb.properties")).getProperty("--linux-app-release")).isEqualTo("3");
        assertThat(readProperties(process.resolve("jpackage.properties")).getProperty("--linux-app-release")).isNull();
    }

    @Test
    public void packages_every_listed_jpackage_format_and_stages_them_together() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false, "app-image,deb");
        assertThat(fixture.execute("package/jpackage"))
                .containsKeys("package/jpackage-app-image", "package/jpackage-deb", "package/jpackage");
    }

    @Test
    public void docker_jpackage_packages_its_format_beside_the_staged_ones() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"),
                "jpackage=deb\ndocker=example:latest\ndocker.jpackage=app-image\n");
        assertThat(fixture.execute("package/docker"))
                .as("the image is handed an app-image of its own, which is not staged as an installer is")
                .containsKeys("package/jpackage-app-image", "package/docker")
                .doesNotContainKey("package/jpackage");
        assertThat(fixture.execute("package/jpackage"))
                .doesNotContainKey("package/jpackage-app-image");
    }

    @Test
    public void docker_jpackage_alone_stages_no_package() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"),
                "docker=example:latest\ndocker.jpackage=app-image\n");
        assertThat(fixture.execute("package/docker")).containsKey("package/jpackage-app-image");
        assertThatThrownBy(() -> fixture.execute("package/jpackage"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: jpackage - ");
    }

    @Test
    public void docker_jpackage_reuses_a_listed_format() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"),
                "jpackage=app-image,deb\ndocker=example:latest\ndocker.jpackage=deb\n");
        assertThat(fixture.execute("package/docker"))
                .containsKeys("package/jpackage-deb", "package/docker")
                .doesNotContainKey("package/jpackage-app-image");
    }

    @Test
    public void refuses_docker_jpackage_without_the_image_it_is_installed_onto() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"), "jpackage=app-image\ndocker.jpackage=app-image\n");
        assertThatThrownBy(() -> fixture.execute("package/jpackage"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docker.jpackage without docker=<image>");
    }

    @Test
    public void refuses_a_docker_jpackage_format_that_a_linux_image_cannot_run() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"), "docker=example:latest\ndocker.jpackage=msi\n");
        assertThatThrownBy(() -> fixture.execute("package/docker"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("docker.jpackage=msi");
    }

    @Test
    public void native_image_enabled_wires_package_inventory_so_the_binary_is_stageable() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false, null, false, false, true);
        assertThat(fixture.execute("package/inventory"))
                .as("a native-image-only package phase still feeds the binary through an inventory step")
                .containsKey("package/inventory");
    }

    @Test
    public void stages_no_bill_of_materials_for_a_module_that_builds_no_native_image() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false, null, false, false, true);
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "project=sample\nartifact=app\nversion=1\n");
        assertThat(fixture.execute("package/native").get("package/native").resolve(NativeImage.NATIVE))
                .as("a module without a main class has no binary for its bill of materials to sit beside")
                .doesNotExist();
    }

    @Test
    public void launcher_enabled_lists_the_resolved_launcher_in_the_package_inventory() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"), "launcher=true\n");
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler(),
                served(Files.createDirectory(root.resolve("served"))),
                Map.of("maven", Resolver.identity()),
                "package/inventory");
        SequencedProperties inventory = SequencedProperties.ofFiles(outputs.get("package/inventory").resolve(Inventory.INVENTORY));
        assertThat(inventory.stringPropertyNames())
                .as("a launcher-only package phase still lists what it resolved, so pin reaches the launcher group")
                .anyMatch(key -> key.endsWith(".group") && inventory.getProperty(key).equals("launcher"));
    }

    @Test
    public void launcher_describes_the_application_with_the_dependencies_of_the_module_and_the_launcher() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("packaging.properties"), "launcher=true\n");
        Files.writeString(fixture.manifests().resolve(BuildStep.METADATA), "project=sample\nartifact=app\nversion=1\n");
        Files.writeString(fixture.artifacts().resolve(BuildStep.DEPENDENCIES), "main/runtime/maven/org.foo/bar/1=bar.jar\n");
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler(),
                served(Files.createDirectory(root.resolve("served"))),
                Map.of("maven", Resolver.identity()),
                "package/launcher/sbom");
        assertThat(outputs.get("package/launcher/sbom").resolve(BuildStep.REPORTS + "sbom").resolve("app-1.cdx.json"))
                .content()
                .as("the executable jar is described as an application of what the module's own SBOM lists and the launcher")
                .contains("\"type\": \"application\",\n      \"bom-ref\": \"sample/app/1\"")
                .contains("\"bom-ref\": \"org.foo/bar/1\"")
                .contains("\"bom-ref\": \"build.jenesis/build.jenesis.launcher/RELEASE\"");
    }

    @Test
    public void source_flag_enabled_adds_sources_jar_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, true, false);
        Files.createDirectory(fixture.sources.resolve(BuildStep.SOURCES));
        Files.writeString(fixture.sources.resolve(BuildStep.SOURCES).resolve("foo.java"), "// dummy");
        Path sourcesOutput = fixture.execute("sub/sources/archive").get("sub/sources/archive");
        assertThat(sourcesOutput.resolve("sources").resolve("sources.jar")).exists();
    }

    @Test
    public void sources_jar_holds_what_a_generator_added_to_the_sources() throws IOException {
        Fixture fixture = setUp("path=\n", false, true, false);
        Files.writeString(Files.createDirectories(fixture.sources.resolve(BuildStep.SOURCES + "sample")).resolve("Sample.java"),
                "package sample; public class Sample { Generated generated; }");
        Files.writeString(fixture.configuration().resolve("plugin-generator.properties"), "");
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("generator+binary/generated", (_, _) -> new GeneratingStep().asModule("generator"));
        Path sourcesOutput = fixture.execute(new InferredMultiProjectAssembler().plugins(plugins), "sub/sources/archive")
                .get("sub/sources/archive");
        try (JarFile jar = new JarFile(sourcesOutput.resolve("sources").resolve("sources.jar").toFile())) {
            assertThat(jar.stream().map(JarEntry::getName))
                    .as("a sources jar holds the generated sources the module is compiled from, as Maven's does")
                    .contains("sample/Sample.java", "sample/Generated.java");
        }
    }

    private record GeneratingStep() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            Files.writeString(Files.createDirectories(context.next().resolve(BuildStep.SOURCES + "sample")).resolve("Generated.java"),
                    "package sample; public class Generated { }");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    @Test
    public void source_flag_disabled_omits_sources_jar_step() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/sources/archive"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: sources/archive - ");
    }

    @Test
    public void javadoc_flag_enabled_adds_javadoc_sub_module() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, true);
        Files.createDirectory(fixture.sources.resolve(BuildStep.SOURCES));
        Files.writeString(fixture.sources.resolve(BuildStep.SOURCES).resolve("module-info.java"), """
                module foo {
                }
                """);
        Path javadocOutput = fixture.execute("sub/documentation/archive").get("sub/documentation/archive");
        assertThat(javadocOutput.resolve("documentation").resolve("javadoc.jar")).exists();
    }

    @Test
    public void a_process_command_file_for_javadoc_reaches_the_javadoc_of_the_documentation_jar() throws IOException {
        Fixture fixture = setUp("main=foo.Main\n", false, false, true);
        Files.writeString(fixture.configuration().resolve("process-javadoc.properties"), "-windowtitle=Configured\n");
        Files.createDirectory(fixture.sources.resolve(BuildStep.SOURCES));
        Files.writeString(fixture.sources.resolve(BuildStep.SOURCES).resolve("Foo.java"), "public class Foo {}");
        Path javadocOutput = fixture.execute("sub/documentation/archive").get("sub/documentation/archive");
        assertThat(fixture.build().resolve("sub/documentation/generate/document/javadoc/supplement/command"))
                .content()
                .contains("-windowtitle Configured");
        try (JarFile jar = new JarFile(javadocOutput.resolve("documentation").resolve("javadoc.jar").toFile())) {
            assertThat(jar.getManifest().getMainAttributes().getValue("Main-Class"))
                    .as("the main class the module's own jar names does not reach its documentation jar")
                    .isNull();
        }
    }

    @Test
    public void compares_the_api_of_a_main_module_against_its_last_release() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("japicmp.properties"), "baseline=org.example/sample/1.0\n");
        Path required = fixture.execute("sub/artifact/japicmp/required").get("sub/artifact/japicmp/required");
        assertThat(required.resolve(BuildStep.REQUIRES)).exists();
    }

    @Test
    public void compares_no_api_of_a_test_module() throws IOException {
        Fixture fixture = setUp("path=\ntest=main_artifact\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("japicmp.properties"), "baseline=org.example/sample/1.0\n");
        assertThatThrownBy(() -> fixture.execute("sub/artifact/japicmp/required"))
                .as("the tests of a module have no release of their own to be compatible with")
                .rootCause()
                .hasMessageStartingWith("Unknown selector: japicmp/required - ");
    }

    @Test
    public void javadoc_flag_disabled_omits_javadoc_sub_module() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/documentation/archive"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: documentation/archive - ");
    }

    @Test
    public void tests_flag_enabled_for_test_variant_without_framework_in_dependencies_fails_resolution()
            throws IOException {
        Fixture fixture = setUp("path=\ntest=main_artifact\n", true, false, false);
        Files.writeString(
                Files.createDirectory(fixture.sources.resolve(BuildStep.SOURCES)).resolve("Sample.java"),
                "public class Sample {}");
        assertThatThrownBy(() -> fixture.execute("sub/observed/test/resolved"))
                .rootCause()
                .hasMessageContaining("No test framework could be resolved");
    }

    @Test
    public void tests_flag_disabled_omits_test_sub_module() throws IOException {
        Fixture fixture = setUp("path=\n", false, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/test/resolved"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: test/resolved - ");
    }

    @Test
    public void abstract_test_module_omits_test_sub_module() throws IOException {
        Fixture fixture = setUp("path=\ntest=\nabstract=true\n", true, false, false);
        assertThatThrownBy(() -> fixture.execute("sub/observed/test/resolved"))
                .rootCause()
                .hasMessageStartingWith("Unknown selector: observed/test/resolved - ");
    }

    @Test
    public void editing_a_process_override_reruns_prepare_on_the_next_build() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-g=\n");
        Path first = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(readProperties(first.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties")).stringPropertyNames())
                .contains("-g");

        Files.writeString(fixture.configuration().resolve("process-javac.properties"), "-verbose=\n");
        Path second = fixture.execute("sub/prepare").get("sub/prepare");
        assertThat(readProperties(second.resolve(ProcessBuildStep.PROCESS).resolve("javac.properties")).stringPropertyNames())
                .as("editing only a process override must re-run prepare, not serve the stale one")
                .contains("-verbose")
                .doesNotContain("-g");
    }

    private static Map<String, Repository> served(Path folder) {
        return Map.of("maven", (_, coordinate, _) -> Optional.of(RepositoryItem.ofFile(Files.writeString(
                folder.resolve(coordinate.replace('/', '-') + ".jar"), coordinate))));
    }

    private Fixture setUp(String moduleProperties,
                          boolean tests,
                          boolean source,
                          boolean documentation) throws IOException {
        return setUp(moduleProperties, tests, source, documentation, null, false, false);
    }

    private Fixture setUp(String moduleProperties,
                          boolean tests,
                          boolean source,
                          boolean documentation,
                          String packageType) throws IOException {
        return setUp(moduleProperties, tests, source, documentation, packageType, false, false);
    }

    private Fixture setUp(String moduleProperties,
                          boolean tests,
                          boolean source,
                          boolean documentation,
                          String packageType,
                          boolean jmod,
                          boolean jlink) throws IOException {
        return setUp(moduleProperties, tests, source, documentation, packageType, jmod, jlink, false);
    }

    private Fixture setUp(String moduleProperties,
                          boolean tests,
                          boolean source,
                          boolean documentation,
                          String packageType,
                          boolean jmod,
                          boolean jlink,
                          boolean nativeImage) throws IOException {
        Path manifests = Files.createDirectory(root.resolve("manifests"));
        Files.writeString(manifests.resolve(BuildStep.MODULE), moduleProperties);
        Path sources = Files.createDirectory(root.resolve("sources"));
        Path artifacts = Files.createDirectory(root.resolve("artifacts"));
        Path configuration = Files.createDirectory(root.resolve("configuration"));
        Path profile = Files.createDirectory(root.resolve("profile"));
        StringBuilder packaging = new StringBuilder();
        if (jmod) {
            packaging.append("jmod=true\n");
        }
        if (jlink) {
            packaging.append("jlink=true\n");
        }
        if (nativeImage) {
            packaging.append("native=true\n");
        }
        if (packageType != null) {
            packaging.append("jpackage=").append(packageType).append("\n");
        }
        if (!packaging.isEmpty()) {
            Files.writeString(configuration.resolve("packaging.properties"), packaging.toString());
        }
        Path build = Files.createDirectory(root.resolve("build"));
        ProjectModule base = new ProjectModule() {
            @Override
            public String name() {
                return "module";
            }

            @Override
            public SequencedSet<String> dependencies() {
                return Collections.emptyNavigableSet();
            }

            @Override
            public SequencedSet<String> sources() {
                return new LinkedHashSet<>(List.of(BuildExecutorModule.PREVIOUS + "sources"));
            }

            @Override
            public SequencedSet<String> resources() {
                return Collections.emptyNavigableSet();
            }

            @Override
            public SequencedSet<String> manifests() {
                return new LinkedHashSet<>(List.of(BuildExecutorModule.PREVIOUS + "manifests"));
            }

            @Override
            public SequencedSet<String> coordinates() {
                return new LinkedHashSet<>(List.of(BuildExecutorModule.PREVIOUS + "coordinates"));
            }

            @Override
            public SequencedSet<String> artifacts() {
                return new LinkedHashSet<>(List.of(BuildExecutorModule.PREVIOUS + "artifacts"));
            }

            @Override
            public SequencedSet<String> spdx() {
                return Collections.emptyNavigableSet();
            }
        };
        ProjectModuleDescriptor descriptor = new ProjectModuleDescriptor(base)
                .configuration(profile, configuration)
                .test(tests)
                .source(source)
                .documentation(documentation);
        return new Fixture(descriptor, build, manifests, sources, artifacts, configuration, profile);
    }

    private record Fixture(ProjectModuleDescriptor descriptor,
                           Path build,
                           Path manifests,
                           Path sources,
                           Path artifacts,
                           Path configuration,
                           Path profile) {

        SequencedMap<String, Path> execute(String selector) throws IOException {
            return execute(new InferredMultiProjectAssembler(), selector);
        }

        SequencedMap<String, Path> execute(InferredMultiProjectAssembler assembler, String... selectors) throws IOException {
            return execute(assembler, Map.of(), Map.of(), selectors);
        }

        SequencedMap<String, Path> execute(InferredMultiProjectAssembler assembler,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers,
                                           String... selectors) throws IOException {
            AssemblyDescriptor assembled = assembler.apply(descriptor, repositories, resolvers);
            BuildExecutor executor = BuildExecutor.of(build,
                    Duration.ZERO,
                    new HashDigestFunction("MD5"),
                    BuildStepHashFunction.ofSerializationDigest("MD5"),
                    BuildExecutorCallback.nop(),
                    BuildExecutorCache.nop(),
                    false,
                    false,
                    0);
            executor.addSource("manifests", manifests);
            executor.addSource("sources", sources);
            executor.addSource("artifacts", artifacts);
            executor.addModule("sub", assembled.build(),
                    "manifests", "sources", "artifacts");
            for (Map.Entry<String, BuildExecutorModule> phase : assembled.tail().entrySet()) {
                executor.addModule(phase.getKey(), phase.getValue(), "sub", "manifests", "sources", "artifacts");
            }
            return executor.execute(Runnable::run, selectors).toCompletableFuture().join();
        }
    }

    @Test
    public void a_custom_module_runs_in_the_module_build_beside_a_stock_step_of_the_same_name() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        SequencedMap<String, BuildExecutorModule> custom = new LinkedHashMap<>();
        custom.put("prepare", (executor, inherited) -> executor.addStep("inputs", (_, context, arguments) -> {
            Files.writeString(context.next().resolve("inputs.txt"), String.join("\n", arguments.sequencedKeySet()));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }, inherited.sequencedKeySet()));
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler().custom(custom),
                "sub/prepare",
                "sub/custom/prepare/inputs");
        assertThat(outputs).containsKeys("sub/prepare", "sub/custom/prepare/inputs");
        assertThat(Files.readAllLines(outputs.get("sub/custom/prepare/inputs").resolve("inputs.txt")))
                .as("a custom module reads what the module build reads")
                .hasSize(3)
                .anySatisfy(input -> assertThat(input).endsWith("manifests"))
                .anySatisfy(input -> assertThat(input).endsWith("sources"))
                .anySatisfy(input -> assertThat(input).endsWith("artifacts"));
    }

    @Test
    public void binds_the_project_resources_into_every_module() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Path notice = Files.writeString(root.resolve("NOTICE"), "notice");
        Path legal = Files.createDirectories(root.resolve("legal"));
        Files.writeString(legal.resolve("THIRD-PARTY.txt"), "third party");
        SequencedMap<Path, Path> resources = new LinkedHashMap<>();
        resources.put(Path.of("META-INF/NOTICE"), notice);
        resources.put(Path.of("META-INF/legal"), legal);
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler().resources(resources),
                "sub/include");
        Path included = outputs.get("sub/include/resources").resolve(BuildStep.RESOURCES);
        assertThat(included.resolve("META-INF/NOTICE")).hasContent("notice");
        assertThat(included.resolve("META-INF/legal/THIRD-PARTY.txt"))
                .as("a folder is placed as a folder at its target")
                .hasContent("third party");
    }

    @Test
    public void wires_a_plugin_whose_properties_file_is_found_and_hands_it_the_values() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("plugin-lint.properties"), "level=strict\n");
        List<SequencedMap<String, String>> received = new ArrayList<>();
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("lint+check", (_, properties) -> {
            received.add(properties);
            return new MarkerStep().asModule("lint");
        });
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler().plugins(plugins),
                "sub/check/custom/lint");
        assertThat(received).containsExactly(new LinkedHashMap<>(Map.of("level", "strict")));
        assertThat(outputs.get("sub/check/custom/lint").resolve("marker.txt")).exists();
    }

    @Test
    public void hands_a_plugin_the_folder_of_its_module_to_resolve_inputs_against() throws IOException {
        Fixture base = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(base.configuration().resolve("plugin-lint.properties"), "@rules=rules\n");
        Path location = base.sources().resolveSibling("module");
        Fixture fixture = new Fixture(base.descriptor().location(location),
                base.build(),
                base.manifests(),
                base.sources(),
                base.artifacts(),
                base.configuration(),
                base.profile());
        List<Path> received = new ArrayList<>();
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("lint+check", (folder, _) -> {
            received.add(folder);
            return new MarkerStep().asModule("lint");
        });
        fixture.execute(new InferredMultiProjectAssembler().plugins(plugins), "sub/check/custom/lint");
        assertThat(received).containsExactly(location);
    }

    @Test
    public void does_not_wire_a_plugin_whose_properties_file_is_missing() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("lint+check", (_, _) -> new MarkerStep().asModule("lint"));
        fixture.execute(new InferredMultiProjectAssembler().plugins(plugins), "sub/check");
        assertThat(fixture.build().resolve("sub").resolve("check").resolve("custom"))
                .as("a plugin runs only where plugin-lint.properties is found")
                .doesNotExist();
    }

    @Test
    public void wires_a_plugin_into_a_nested_hook_point() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("plugin-greeting.properties"), "");
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("greeting+binary/generated", (_, _) -> new MarkerStep().asModule("greeting"));
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler().plugins(plugins),
                "sub/binary/generated/custom/greeting");
        assertThat(outputs.get("sub/binary/generated/custom/greeting").resolve("marker.txt")).exists();
    }

    @Test
    public void stages_what_a_packager_writes_into_packages() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("plugin-appimage.properties"), "");
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("appimage+package", (_, _) -> new PackageStep("app.AppImage").asModule("appimage"));
        SequencedMap<String, Path> outputs = fixture.execute(new InferredMultiProjectAssembler().plugins(plugins), "package");
        assertThat(outputs.get("package/packaged").resolve(JPackage.PACKAGES + "app.AppImage")).exists();
        assertThat(SequencedProperties.ofFiles(outputs.get("package/inventory").resolve(Inventory.INVENTORY)).stringPropertyNames())
                .as("the package reaches the stage through the module's inventory")
                .anyMatch(key -> key.endsWith(".package"));
    }

    @Test
    public void refuses_two_packagers_that_write_the_same_package() throws IOException {
        Fixture fixture = setUp("main=com.example.Entry\n", false, false, false);
        Files.writeString(fixture.configuration().resolve("plugin-first.properties"), "");
        Files.writeString(fixture.configuration().resolve("plugin-second.properties"), "");
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("first+package", (_, _) -> new PackageStep("app.AppImage").asModule("first"));
        plugins.put("second+package", (_, _) -> new PackageStep("app.AppImage").asModule("second"));
        assertThatThrownBy(() -> fixture.execute(new InferredMultiProjectAssembler().plugins(plugins), "package"))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("which another packager writes already");
    }

    private record PackageStep(String name) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            Files.writeString(Files.createDirectories(context.next().resolve(JPackage.PACKAGES)).resolve(name), "package");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    @Test
    public void refuses_a_plugin_at_an_unknown_hook_point() {
        SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
        plugins.put("lint+binary/unknown", (_, _) -> (_, _) -> {});
        assertThatThrownBy(() -> new InferredMultiProjectAssembler().plugins(plugins))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot add the plugin lint+binary/unknown")
                .hasMessageContaining("binary/generated");
    }

    private record MarkerStep() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            Files.writeString(context.next().resolve("marker.txt"), "");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    @Test
    public void sub_module_configurators_default_to_identity_and_round_trip() {
        InferredMultiProjectAssembler assembler = new InferredMultiProjectAssembler();
        assertThat(assembler.check().apply(null)).as("check configurator defaults to identity").isNull();
        assertThat(assembler.format().apply(null)).as("format configurator defaults to identity").isNull();
        assertThat(assembler.compliance().apply(null)).as("compliance configurator defaults to identity").isNull();
        assertThat(assembler.toolchain().apply(null)).as("toolchain configurator defaults to identity").isNull();
        assertThat(assembler.observe().apply(null)).as("observe configurator defaults to identity").isNull();
        assertThat(assembler.documentation().apply(null)).as("documentation configurator defaults to identity").isNull();

        Function<InferredTestObservationModule, BuildExecutorModule> custom = observe -> observe.test(null);
        assertThat(assembler.observe(custom).observe()).as("the observe wither stores the configurator").isSameAs(custom);

        Function<InferredComplianceModule, BuildExecutorModule> customCompliance = compliance -> compliance;
        assertThat(assembler.compliance(customCompliance).compliance())
                .as("the compliance wither stores the configurator").isSameAs(customCompliance);

        Function<InferredDocumentationModule, BuildExecutorModule> customDocumentation = documentation -> documentation;
        assertThat(assembler.documentation(customDocumentation).documentation())
                .as("the documentation wither stores the configurator").isSameAs(customDocumentation);
    }

    private static SequencedProperties readProperties(Path path) throws IOException {
        assertThat(path).exists();
        return SequencedProperties.ofFiles(path);
    }
}
