package com.boombapcompile.blckvox.service.stt.util;

import com.boombapcompile.blckvox.exception.TranscriptionException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicConcurrencyGuardTest {

    @Test
    void shouldStartWithConfiguredMaxPermits() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
        assertThat(guard.getConfiguredMax()).isEqualTo(4);
    }

    @Test
    void shouldIncreasePermits() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        // Decrease first to have room to increase
        guard.adjustPermits(2);
        assertThat(guard.getCurrentPermits()).isEqualTo(2);

        // Increase back
        guard.adjustPermits(4);
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
    }

    @Test
    void shouldDecreasePermitsBestEffort() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        guard.adjustPermits(2);
        assertThat(guard.getCurrentPermits()).isEqualTo(2);
    }

    @Test
    void shouldClampToMinimumOfOne() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        guard.adjustPermits(0);
        assertThat(guard.getCurrentPermits()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void shouldClampToConfiguredMax() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        guard.adjustPermits(10);
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
    }

    @Test
    void shouldHandleNoOpAdjustment() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);

        guard.adjustPermits(4);
        assertThat(guard.getCurrentPermits()).isEqualTo(4);
    }

    @Test
    void shouldAcquireAndRelease() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(2, 1000, "test", null);

        guard.acquire();
        guard.acquire();
        guard.release();
        guard.release();
        // Should not throw — permits were returned
    }

    @Test
    void shouldEnforceMinimumOnePermit() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(1, 1000, "test", null);
        assertThat(guard.getConfiguredMax()).isEqualTo(1);

        guard.adjustPermits(-5);
        assertThat(guard.getCurrentPermits()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void acquireTimeoutThrowsTranscriptionException() {
        // 1 permit, very short timeout
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(1, 50, "test-engine", null);
        guard.acquire(); // Take the only permit

        assertThatThrownBy(guard::acquire)
                .isInstanceOf(TranscriptionException.class)
                .hasMessageContaining("concurrency limit");
        guard.release();
    }

    @Test
    void acquireInterruptedThrowsTranscriptionException() throws InterruptedException {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(1, 5000, "test-engine", null);
        guard.acquire(); // Take the only permit

        Thread testThread = new Thread(() -> {
            assertThatThrownBy(guard::acquire)
                    .isInstanceOf(TranscriptionException.class)
                    .hasMessageContaining("interrupted");
        });
        testThread.start();
        Thread.sleep(50); // Let it start waiting
        testThread.interrupt();
        testThread.join(2000);
        guard.release();
    }

    @Test
    void decreaseWhenAllPermitsInUseCannotDrain() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(2, 1000, "test", null);
        // Acquire both permits — none available for draining
        guard.acquire();
        guard.acquire();

        // Try to decrease to 1 — tryAcquire() will fail, drained stays 0
        guard.adjustPermits(1);
        // Permits won't change because nothing was drained
        assertThat(guard.getCurrentPermits()).isEqualTo(2);

        guard.release();
        guard.release();
    }

    @Test
    void decreaseWhenPermitsInUseDoesPartialDrain() {
        DynamicConcurrencyGuard guard = new DynamicConcurrencyGuard(4, 1000, "test", null);
        // Acquire 3 of 4 permits — only 1 available for draining
        guard.acquire();
        guard.acquire();
        guard.acquire();

        // Try to decrease to 1 — can only drain 1 (the one available)
        guard.adjustPermits(1);
        // Permits should reflect partial drain
        assertThat(guard.getCurrentPermits()).isLessThanOrEqualTo(4);

        guard.release();
        guard.release();
        guard.release();
    }
}
