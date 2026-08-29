package demo.bench;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class StringBench {

    @Param({"8", "64"})
    public int length;

    private String text;

    public StringBench() {
        this.text = "";
    }

    @Benchmark
    public String concatenated() {
        String result = "";
        for (int index = 0; index < length; index++) {
            result = result + index % 10;
        }
        return result;
    }

    @Benchmark
    public String appended() {
        StringBuilder result = new StringBuilder(text);
        for (int index = 0; index < length; index++) {
            result.append(index % 10);
        }
        return result.toString();
    }

    public static void main(String[] args) throws RunnerException {
        new Runner(new OptionsBuilder()
                .include(StringBench.class.getName())
                .forks(0)
                .warmupIterations(1)
                .warmupTime(TimeValue.milliseconds(500))
                .measurementIterations(2)
                .measurementTime(TimeValue.milliseconds(500))
                .build()).run();
    }
}
