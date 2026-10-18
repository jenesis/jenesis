package demo.stress;

import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

public class Counter {

    @JCStressTest
    @Outcome(id = {"1, 2", "2, 1"}, expect = ACCEPTABLE, desc = "One increment follows the other")
    @Outcome(id = "1, 1", expect = ACCEPTABLE_INTERESTING, desc = "Both read zero: an increment is lost")
    @State
    public static class Plain {

        int value;

        @Actor
        public void first(II_Result result) {
            result.r1 = ++value;
        }

        @Actor
        public void second(II_Result result) {
            result.r2 = ++value;
        }
    }

    @JCStressTest
    @Outcome(id = {"1, 2", "2, 1"}, expect = ACCEPTABLE, desc = "One increment follows the other")
    @Outcome(id = "1, 1", expect = FORBIDDEN, desc = "Both read zero: an increment is lost")
    @State
    public static class Atomic {

        final AtomicInteger value = new AtomicInteger();

        @Actor
        public void first(II_Result result) {
            result.r1 = value.incrementAndGet();
        }

        @Actor
        public void second(II_Result result) {
            result.r2 = value.incrementAndGet();
        }
    }
}
