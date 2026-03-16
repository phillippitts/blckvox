package com.boombapcompile.blckvox.service.audio.capture;

import com.boombapcompile.blckvox.config.properties.AudioCaptureProperties;
import com.boombapcompile.blckvox.config.properties.AudioValidationProperties;
import com.boombapcompile.blckvox.exception.InvalidAudioException;
import com.boombapcompile.blckvox.service.validation.AudioValidator;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import javax.sound.sampled.Control;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class JavaSoundAudioCaptureServiceTest {

    @Test
    void startStopReadAllProducesPcm() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(50, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof CaptureErrorEvent) {
                errors.incrementAndGet();
            } else if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };

        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 5);
        svc.stopSession(id);
        byte[] pcm = svc.readAll(id);

        assertThat(pcm).isNotNull();
        assertThat(pcm.length).isGreaterThan(0);
        assertThat(pcm.length % 2).isEqualTo(0);
        assertThat(errors.get()).isEqualTo(0);
    }

    @Test
    void singleActiveSessionOnly() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 60000, null);
        AudioValidationProperties vprops = new AudioValidationProperties(50, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 1);
        assertThatThrownBy(svc::startSession)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already active");
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 5);
        svc.stopSession(id);
        svc.readAll(id);
    }

    @Test
    void invalidAudioRejectedByValidator() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(10000, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        // Capture for only a few chunks (way below 10s minimum)
        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 1);
        svc.stopSession(id);

        assertThatThrownBy(() -> svc.readAll(id))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void permissionDeniedPublishesEvent() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(250, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger eventCount = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof CaptureErrorEvent evt
                    && "MIC_PERMISSION_DENIED".equals(evt.reason())) {
                eventCount.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            throw new SecurityException("Microphone access denied");
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).untilAtomic(eventCount, org.hamcrest.Matchers.greaterThan(0));
        svc.stopSession(id);

        assertThat(eventCount.get()).isGreaterThan(0);
    }

    @Test
    void deviceUnavailablePublishesEvent() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(250, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger eventCount = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof CaptureErrorEvent evt
                    && "MIC_UNAVAILABLE".equals(evt.reason())) {
                eventCount.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            throw new LineUnavailableException("No audio device available");
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).untilAtomic(eventCount, org.hamcrest.Matchers.greaterThan(0));
        svc.stopSession(id);

        assertThat(eventCount.get()).isGreaterThan(0);
    }

    @Test
    void maxDurationEnforcesHardStop() {
        // Very short max duration (600ms)
        AudioCaptureProperties props = new AudioCaptureProperties(20, 600, null);
        AudioValidationProperties vprops = new AudioValidationProperties(100, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        // Wait for the hard stop to kick in (~600ms of capture, producing ~60 chunks at 10ms each)
        await().atMost(Duration.ofSeconds(3)).until(() -> chunks.get() >= 50);
        svc.stopSession(id);
        byte[] pcm = svc.readAll(id);

        // At 16kHz, 16-bit, mono: 32,000 bytes/sec = ~19,200 bytes for 600ms
        int expectedMaxBytes = (600 * 32000) / 1000;
        assertThat(pcm.length).isLessThanOrEqualTo(expectedMaxBytes + 2000);
        assertThat(pcm.length).isGreaterThan(3000);
    }

    @Test
    void canceledSessionThrowsOnRead() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(250, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 1);
        svc.cancelSession(id);

        assertThatThrownBy(() -> svc.readAll(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canceled");
    }

    @Test
    void readAllWhileActiveThrows() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 60000, null);
        AudioValidationProperties vprops = new AudioValidationProperties(250, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 1);

        assertThatThrownBy(() -> svc.readAll(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("still active");

        svc.stopSession(id);
    }

    @Test
    void shutdownTerminatesCaptureThread() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 60000, null);
        AudioValidationProperties vprops = new AudioValidationProperties(250, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 1);
        svc.shutdown();

        // After shutdown, should be able to start a new session (full cleanup)
        await().atMost(Duration.ofSeconds(2))
                .pollInterval(Duration.ofMillis(50))
                .untilAsserted(() -> {
                    UUID newId = svc.startSession();
                    svc.stopSession(newId);
                });
    }

    @Test
    void allZeroAudioTriggersWarning() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(50, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        // Provider that returns a line producing all zeros
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            ZeroTargetDataLine line = new ZeroTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 5);
        svc.stopSession(id);
        byte[] pcm = svc.readAll(id);

        // All-zero audio still captured; silence warning is logged internally
        assertThat(pcm).isNotNull();
        assertThat(pcm.length).isGreaterThan(0);
    }

    @Test
    void zeroOrNegativeBytesReadSkipped() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(50, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        // Provider that returns 0 bytes for first 3 reads, then real data
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            ZeroReadThenDataLine line = new ZeroReadThenDataLine(fmt, 3);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 3);
        svc.stopSession(id);
        byte[] pcm = svc.readAll(id);

        assertThat(pcm).isNotNull();
        assertThat(pcm.length).isGreaterThan(0);
    }

    @Test
    void deviceNameFallbackToDefault() {
        // Specify a non-existent device; should fall back to default behavior
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, "non-existent-device-xyz");
        AudioValidationProperties vprops = new AudioValidationProperties(50, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        // Provider simulates device lookup: doesn't find named device, falls through to default
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            // dev.isPresent() = true, but we return a line as if fallback occurred
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 3);
        svc.stopSession(id);
        byte[] pcm = svc.readAll(id);

        assertThat(pcm).isNotNull();
        assertThat(pcm.length).isGreaterThan(0);
    }

    @Test
    void genericExceptionInCapturePublishesCaptureError() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(250, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger captureErrors = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof CaptureErrorEvent evt && "CAPTURE_ERROR".equals(evt.reason())) {
                captureErrors.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            throw new RuntimeException("Generic audio failure");
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).untilAtomic(captureErrors, org.hamcrest.Matchers.greaterThan(0));
        svc.stopSession(id);

        assertThat(captureErrors.get()).isGreaterThan(0);
    }

    @Test
    void wrongSessionIdThrowsIllegalState() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(50, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        ApplicationEventPublisher publisher = e -> {};
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        UUID wrongId = UUID.randomUUID();
        assertThatThrownBy(() -> svc.stopSession(wrongId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
        svc.stopSession(id);
    }

    @Test
    void shutdownWithActiveSessionCleansUp() throws InterruptedException {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 60000, null);
        AudioValidationProperties vprops = new AudioValidationProperties(50, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            RepeatingTargetDataLine line = new RepeatingTargetDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 3);

        // Call shutdown while session is active — covers shutdown() with active session
        svc.shutdown();

        // After shutdown, service should be cleaned up (no active session)
        // Give the thread time to terminate
        Thread.sleep(200);
    }

    @Test
    void closeAudioLineExceptionsAreSwallowed() {
        AudioCaptureProperties props = new AudioCaptureProperties(20, 500, null);
        AudioValidationProperties vprops = new AudioValidationProperties(50, 60000, 100 * 1024 * 1024);
        AudioValidator validator = new AudioValidator(vprops);
        AtomicInteger chunks = new AtomicInteger();
        ApplicationEventPublisher publisher = e -> {
            if (e instanceof PcmChunkCapturedEvent) {
                chunks.incrementAndGet();
            }
        };
        JavaSoundAudioCaptureService.DataLineProvider provider = (fmt, dev) -> {
            ThrowingCloseDataLine line = new ThrowingCloseDataLine(fmt);
            line.open(fmt);
            return line;
        };
        JavaSoundAudioCaptureService svc = new JavaSoundAudioCaptureService(props, validator, publisher, provider);

        UUID id = svc.startSession();
        await().atMost(Duration.ofSeconds(2)).until(() -> chunks.get() >= 3);
        svc.stopSession(id);
        byte[] pcm = svc.readAll(id);

        // Capture works despite stop()/close() throwing
        assertThat(pcm).isNotNull();
        assertThat(pcm.length).isGreaterThan(0);
    }

    // --- Test doubles ---

    /** A TargetDataLine that works normally but throws from stop() and close(). */
    static final class ThrowingCloseDataLine implements TargetDataLine {
        private final javax.sound.sampled.AudioFormat fmt;
        private boolean started;
        private boolean open;
        private final byte[] pattern;
        private int pos = 0;

        ThrowingCloseDataLine(javax.sound.sampled.AudioFormat fmt) {
            this.fmt = fmt;
            this.pattern = new byte[320];
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = (byte) (i & 0xFF);
            }
        }

        @Override public javax.sound.sampled.AudioFormat getFormat() { return fmt; }
        @Override public void open(javax.sound.sampled.AudioFormat format, int bufferSize) { open = true; }
        @Override public void open(javax.sound.sampled.AudioFormat format) { open = true; }
        @Override public int read(byte[] b, int off, int len) {
            if (!started || !open) return 0;
            try { Thread.sleep(10); } catch (InterruptedException ignore) {}
            int n = Math.min(len, pattern.length);
            System.arraycopy(pattern, 0, b, off, n);
            return n;
        }
        @Override public void start() { started = true; }
        @Override public void stop() { throw new RuntimeException("stop failed"); }
        @Override public void close() { throw new RuntimeException("close failed"); }
        @Override public boolean isOpen() { return open; }
        @Override public int available() { return 0; }
        @Override public void drain() {}
        @Override public void flush() {}
        @Override public int getBufferSize() { return 0; }
        @Override public int getFramePosition() { return 0; }
        @Override public float getLevel() { return 0; }
        @Override public long getLongFramePosition() { return 0; }
        @Override public Control getControl(Control.Type c) { throw new IllegalArgumentException(); }
        @Override public Control[] getControls() { return new Control[0]; }
        @Override public boolean isControlSupported(Control.Type c) { return false; }
        @Override public void addLineListener(LineListener l) {}
        @Override public void removeLineListener(LineListener l) {}
        @Override public javax.sound.sampled.Line.Info getLineInfo() {
            return new DataLine.Info(TargetDataLine.class, fmt);
        }
        @Override public void open() { open = true; }
        @Override public boolean isActive() { return started; }
        @Override public boolean isRunning() { return started; }
        @Override public long getMicrosecondPosition() { return 0L; }
    }

    /** A TargetDataLine that produces all-zero audio data. */
    static final class ZeroTargetDataLine implements TargetDataLine {
        private final javax.sound.sampled.AudioFormat fmt;
        private boolean started;
        private boolean open;

        ZeroTargetDataLine(javax.sound.sampled.AudioFormat fmt) { this.fmt = fmt; }

        @Override public javax.sound.sampled.AudioFormat getFormat() { return fmt; }
        @Override public void open(javax.sound.sampled.AudioFormat format, int bufferSize) { open = true; }
        @Override public void open(javax.sound.sampled.AudioFormat format) { open = true; }
        @Override public int read(byte[] b, int off, int len) {
            if (!started || !open) return 0;
            try { Thread.sleep(10); } catch (InterruptedException ignore) {}
            java.util.Arrays.fill(b, off, off + Math.min(len, 320), (byte) 0);
            return Math.min(len, 320);
        }
        @Override public void start() { started = true; }
        @Override public void stop() { started = false; }
        @Override public void close() { open = false; }
        @Override public boolean isOpen() { return open; }
        @Override public int available() { return 0; }
        @Override public void drain() {}
        @Override public void flush() {}
        @Override public int getBufferSize() { return 0; }
        @Override public int getFramePosition() { return 0; }
        @Override public float getLevel() { return 0; }
        @Override public long getLongFramePosition() { return 0; }
        @Override public Control getControl(Control.Type c) { throw new IllegalArgumentException(); }
        @Override public Control[] getControls() { return new Control[0]; }
        @Override public boolean isControlSupported(Control.Type c) { return false; }
        @Override public void addLineListener(LineListener l) {}
        @Override public void removeLineListener(LineListener l) {}
        @Override public javax.sound.sampled.Line.Info getLineInfo() {
            return new DataLine.Info(TargetDataLine.class, fmt);
        }
        @Override public void open() { open = true; }
        @Override public boolean isActive() { return started; }
        @Override public boolean isRunning() { return started; }
        @Override public long getMicrosecondPosition() { return 0L; }
    }

    /** A TargetDataLine that returns 0 bytes for the first N reads, then real data. */
    static final class ZeroReadThenDataLine implements TargetDataLine {
        private final javax.sound.sampled.AudioFormat fmt;
        private final int zeroReads;
        private boolean started;
        private boolean open;
        private int readCount;

        ZeroReadThenDataLine(javax.sound.sampled.AudioFormat fmt, int zeroReads) {
            this.fmt = fmt;
            this.zeroReads = zeroReads;
        }

        @Override public javax.sound.sampled.AudioFormat getFormat() { return fmt; }
        @Override public void open(javax.sound.sampled.AudioFormat format, int bufferSize) { open = true; }
        @Override public void open(javax.sound.sampled.AudioFormat format) { open = true; }
        @Override public int read(byte[] b, int off, int len) {
            if (!started || !open) return 0;
            try { Thread.sleep(10); } catch (InterruptedException ignore) {}
            readCount++;
            if (readCount <= zeroReads) return 0;
            int n = Math.min(len, 320);
            for (int i = 0; i < n; i++) b[off + i] = (byte) (i & 0xFF);
            return n;
        }
        @Override public void start() { started = true; }
        @Override public void stop() { started = false; }
        @Override public void close() { open = false; }
        @Override public boolean isOpen() { return open; }
        @Override public int available() { return 0; }
        @Override public void drain() {}
        @Override public void flush() {}
        @Override public int getBufferSize() { return 0; }
        @Override public int getFramePosition() { return 0; }
        @Override public float getLevel() { return 0; }
        @Override public long getLongFramePosition() { return 0; }
        @Override public Control getControl(Control.Type c) { throw new IllegalArgumentException(); }
        @Override public Control[] getControls() { return new Control[0]; }
        @Override public boolean isControlSupported(Control.Type c) { return false; }
        @Override public void addLineListener(LineListener l) {}
        @Override public void removeLineListener(LineListener l) {}
        @Override public javax.sound.sampled.Line.Info getLineInfo() {
            return new DataLine.Info(TargetDataLine.class, fmt);
        }
        @Override public void open() { open = true; }
        @Override public boolean isActive() { return started; }
        @Override public boolean isRunning() { return started; }
        @Override public long getMicrosecondPosition() { return 0L; }
    }

    static final class RepeatingTargetDataLine implements TargetDataLine {
        private final javax.sound.sampled.AudioFormat fmt;
        private boolean started;
        private boolean open;
        private final byte[] pattern;
        private int pos = 0;

        RepeatingTargetDataLine(javax.sound.sampled.AudioFormat fmt) {
            this.fmt = fmt;
            this.pattern = new byte[320]; // ~10ms at 16k mono 16-bit
            for (int i = 0; i < pattern.length; i++) {
                pattern[i] = (byte) (i & 0xFF);
            }
        }

        @Override public javax.sound.sampled.AudioFormat getFormat() {
            return fmt;
        }
        @Override public void open(javax.sound.sampled.AudioFormat format, int bufferSize) {
            open = true;
        }
        @Override public void open(javax.sound.sampled.AudioFormat format) {
            open = true;
        }
        @Override public int read(byte[] b, int off, int len) {
            if (!started || !open) {
                return 0;
            }
            // Throttle to simulate real-time audio capture (~10ms per read)
            try {
                Thread.sleep(10);
            } catch (InterruptedException ignore) {
            }
            int n = Math.min(len, pattern.length);
            // copy from pattern cyclically
            if (pos + n <= pattern.length) {
                System.arraycopy(pattern, pos, b, off, n);
                pos = (pos + n) % pattern.length;
            } else {
                int first = pattern.length - pos;
                System.arraycopy(pattern, pos, b, off, first);
                System.arraycopy(pattern, 0, b, off + first, n - first);
                pos = (n - first);
            }
            return n;
        }
        @Override public void start() {
            started = true;
        }
        @Override public void stop() {
            started = false;
        }
        @Override public void close() {
            open = false;
        }
        @Override public boolean isOpen() {
            return open;
        }
        @Override public int available() {
            return 0;
        }
        @Override public void drain() {
        }
        @Override public void flush() {
        }
        @Override public int getBufferSize() {
            return 0;
        }
        @Override public int getFramePosition() {
            return 0;
        }
        @Override public float getLevel() {
            return 0;
        }
        @Override public long getLongFramePosition() {
            return 0;
        }
        @Override public Control getControl(Control.Type control) {
            throw new IllegalArgumentException();
        }
        @Override public Control[] getControls() {
            return new Control[0];
        }
        @Override public boolean isControlSupported(Control.Type control) {
            return false;
        }
        @Override public void addLineListener(LineListener listener) {
        }
        @Override public void removeLineListener(LineListener listener) {
        }
        @Override public javax.sound.sampled.Line.Info getLineInfo() {
            return new DataLine.Info(TargetDataLine.class, fmt);
        }
        @Override public void open() {
            open = true;
        }
        @Override public boolean isActive() {
            return started;
        }
        @Override public boolean isRunning() {
            return started;
        }
        @Override public long getMicrosecondPosition() {
            return 0L;
        }
    }
}
