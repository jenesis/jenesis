module demo.complete {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.complete.CompleteModule;
}
