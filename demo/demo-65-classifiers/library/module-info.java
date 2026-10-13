module demo.library {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.library.LibraryModule;
}
