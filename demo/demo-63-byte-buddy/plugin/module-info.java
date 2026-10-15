module demo.plugin {
    requires build.jenesis;
    requires net.bytebuddy;
    provides build.jenesis.BuildModuleFactory with demo.plugin.EnhanceFactory;
}
