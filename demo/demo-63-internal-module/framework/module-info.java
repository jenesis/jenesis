module demo.framework {
    requires build.jenesis;
    provides build.jenesis.TestFrameworkFactory with demo.framework.PlatformFactory;
}
