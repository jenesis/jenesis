/**
 * Jenesis.
 *
 * A build tool for Java projects, written and configured in Java itself.
 *
 * @jenesis.release 25
 * @jenesis.main build.jenesis.Make
 */
module build.jenesis {

    requires jdk.compiler;
    requires java.xml;

    exports build.jenesis;
    exports build.jenesis.daemon;
    exports build.jenesis.docker;
    exports build.jenesis.maven;
    exports build.jenesis.module;
    exports build.jenesis.project;
    exports build.jenesis.step;

    uses build.jenesis.BuildModuleFactory;
    uses build.jenesis.ObservabilityFactory;
    uses build.jenesis.project.TestFrameworkFactory;
    uses build.jenesis.project.LanguageFactory;

    provides java.util.spi.ToolProvider with build.jenesis.MakeTool,
            build.jenesis.ExecuteTool,
            build.jenesis.JpxTool;
}
