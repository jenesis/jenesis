module demo.publish {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.publish.PublishFactory;
}
