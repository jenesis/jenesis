module demo.notice {
    requires build.jenesis;
    provides build.jenesis.BuildModuleFactory with demo.notice.NoticeFactory;
}
