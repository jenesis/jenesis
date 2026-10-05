/**
 * @jenesis.release 25
 * @jenesis.main sample.Main
 */
module demo.classpath {
    exports sample;

    provides java.util.spi.ToolProvider with sample.Greeter;
}
