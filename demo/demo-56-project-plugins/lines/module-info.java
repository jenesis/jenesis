module demo.lines {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.lines.LinesModule;
}
