package com.boombapcompile.blckvox.service.stt.util;

import com.boombapcompile.blckvox.exception.TranscriptionException;
import org.junit.jupiter.api.Test;
import java.util.concurrent.Semaphore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConcurrencyGuardTest {

    @Test
    void acquireSucceedsWhenPermitAvailable() {
        Semaphore semaphore = new Semaphore(1);
        ConcurrencyGuard guard = new ConcurrencyGuard(semaphore, 1000, "test", null);

        guard.acquire();
        assertThat(semaphore.availablePermits()).isZero();
    }

    @Test
    void releaseRestoresPermit() {
        Semaphore semaphore = new Semaphore(1);
        ConcurrencyGuard guard = new ConcurrencyGuard(semaphore, 1000, "test", null);

        guard.acquire();
        guard.release();
        assertThat(semaphore.availablePermits()).isEqualTo(1);
    }

    @Test
    void acquireThrowsOnTimeout() {
        Semaphore semaphore = new Semaphore(0); // No permits available
        ConcurrencyGuard guard = new ConcurrencyGuard(semaphore, 1, "vosk", null);

        assertThatThrownBy(guard::acquire)
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("concurrency limit reached");
    }

    @Test
    void acquireThrowsOnTimeoutWithNullPublisher() {
        Semaphore semaphore = new Semaphore(0);
        ConcurrencyGuard guard = new ConcurrencyGuard(semaphore, 1, "vosk", null);

        assertThatThrownBy(guard::acquire)
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("concurrency limit reached");
    }

    @Test
    void acquireThrowsOnInterrupt() {
        Semaphore semaphore = new Semaphore(0);
        ConcurrencyGuard guard = new ConcurrencyGuard(semaphore, 60_000, "vosk", null);

        Thread.currentThread().interrupt();
        assertThatThrownBy(guard::acquire)
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("interrupted");
        // Clear interrupt flag
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void multipleAcquireReleaseCycles() {
        Semaphore semaphore = new Semaphore(1);
        ConcurrencyGuard guard = new ConcurrencyGuard(semaphore, 1000, "test", null);

        for (int i = 0; i < 5; i++) {
            guard.acquire();
            assertThat(semaphore.availablePermits()).isZero();
            guard.release();
            assertThat(semaphore.availablePermits()).isEqualTo(1);
        }
    }
}
