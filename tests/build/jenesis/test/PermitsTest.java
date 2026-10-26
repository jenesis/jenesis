package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Permits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PermitsTest {

    @Test
    public void compound_permits_hold_every_permit_until_released() throws InterruptedException {
        Permits.Counted first = new Permits.Counted(new Semaphore(1)), second = new Permits.Counted(new Semaphore(1));
        Permits permits = Permits.compound(first, second);
        permits.acquire();
        assertThat(first.semaphore().availablePermits()).isZero();
        assertThat(second.semaphore().availablePermits()).isZero();
        permits.release();
        assertThat(first.semaphore().availablePermits()).isOne();
        assertThat(second.semaphore().availablePermits()).isOne();
    }

    @Test
    public void compound_permits_acquire_in_order_and_release_in_reverse() throws InterruptedException {
        List<String> events = new ArrayList<>();
        Permits permits = Permits.compound(recording("first", events), recording("second", events));
        permits.acquire();
        permits.release();
        assertThat(events).containsExactly("acquire first", "acquire second", "release second", "release first");
    }

    @Test
    public void compound_permits_return_what_they_took_when_a_later_permit_cannot_be_taken() {
        Permits.Counted first = new Permits.Counted(new Semaphore(1));
        Permits permits = Permits.compound(first, new Permits() {
            @Override
            public void acquire() throws InterruptedException {
                throw new InterruptedException();
            }

            @Override
            public void release() {
                throw new AssertionError("A permit that was never taken is released");
            }
        });
        assertThatThrownBy(permits::acquire).isInstanceOf(InterruptedException.class);
        assertThat(first.semaphore().availablePermits())
                .as("a test run that is interrupted while waiting for a process slot gives its test slot back")
                .isOne();
    }

    @Test
    public void compound_permits_of_the_permits_given_only() {
        Permits only = Permits.of(1);
        assertThat(Permits.compound(null, only, null)).isSameAs(only);
        assertThat(Permits.compound(null, null)).as("no permit given bounds nothing").isNull();
    }

    private static Permits recording(String name, List<String> events) {
        return new Permits() {
            @Override
            public void acquire() {
                events.add("acquire " + name);
            }

            @Override
            public void release() {
                events.add("release " + name);
            }
        };
    }
}
