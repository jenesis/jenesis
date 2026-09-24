module demo.notice {
    requires build.jenesis;
    provides build.jenesis.BuildExecutorModule with demo.notice.NoticeModule;
}
