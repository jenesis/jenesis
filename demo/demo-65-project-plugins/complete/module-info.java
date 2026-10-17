module demo.complete {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.complete.CompleteFactory;
}
