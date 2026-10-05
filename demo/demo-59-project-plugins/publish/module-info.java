module demo.publish {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.publish.PublishModule;
}
