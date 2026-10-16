module demo.lines {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.lines.LinesFactory;
}
