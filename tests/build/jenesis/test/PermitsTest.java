package build.jenesis.test;

import module java.base;
import module org.junit.jupiter.api;
import build.jenesis.Permits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PermitsTest {

    @Test
    public void sequenced_permits_hold_both_until_released() throws InterruptedException {
        Permits.Counted first = new Permits.Counted(new Semaphore(1)), second = new Permits.Counted(new Semaphore(1));
        Permits permits = new Permits.Sequenced(first, second);
        permits.acquire();
        assertThat(first.semaphore().availablePermits()).isZero();
        assertThat(second.semaphore().availablePermits()).isZero();
        permits.release();
        assertThat(first.semaphore().availablePermits()).isOne();
        assertThat(second.semaphore().availablePermits()).isOne();
    }

    @Test
    public void sequenced_permits_return_the_first_when_the_second_cannot_be_taken() {
        Permits.Counted first = new Permits.Counted(new Semaphore(1));
        Permits permits = new Permits.Sequenced(first, new Permits() {
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
}
