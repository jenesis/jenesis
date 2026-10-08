module demo.plugin {
    requires build.jenesis;
    requires net.bytebuddy;
    provides build.jenesis.BuildExecutorModule with demo.plugin.GreeterModule;
}
