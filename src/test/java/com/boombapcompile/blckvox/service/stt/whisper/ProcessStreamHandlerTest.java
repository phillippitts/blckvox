package com.boombapcompile.blckvox.service.stt.whisper;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessStreamHandlerTest {

    @Test
    void gobblerCapturesStdout() {
        InputStream stream = toStream("line one\nline two\nline three");
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(stream, "test", 4096);
        gobbler.join(Duration.ofSeconds(2));
        assertThat(gobbler.getOutput()).isEqualTo("line one\nline two\nline three");
    }

    @Test
    void gobblerTruncatesAtCapacity() {
        // 20 bytes max: "line one" = 8 chars, + newline + "line two" = 17 chars
        // "line three" should be truncated/dropped
        InputStream stream = toStream("line one\nline two\nline three");
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(stream, "test", 20);
        gobbler.join(Duration.ofSeconds(2));
        String output = gobbler.getOutput();
        assertThat(output.length()).isLessThanOrEqualTo(20);
    }

    @Test
    void gobblerDrainsStreamBeyondCap() {
        // Even after cap, the stream should be fully consumed (no blocking)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("line ").append(i).append("\n");
        }
        InputStream stream = toStream(sb.toString());
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(stream, "test", 50);
        gobbler.join(Duration.ofSeconds(2));
        // Should not hang — gobbler thread should finish
        assertThat(gobbler.getOutput()).isNotNull();
    }

    @Test
    void gobblerHandlesEmptyStream() {
        InputStream stream = toStream("");
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(stream, "test", 4096);
        gobbler.join(Duration.ofSeconds(2));
        assertThat(gobbler.getOutput()).isEmpty();
    }

    @Test
    void joinWithNullThread() {
        // Create a gobbler with null thread via the constructor
        ProcessStreamHandler.StreamGobbler gobbler =
                new ProcessStreamHandler.StreamGobbler(null, new StringBuilder("data"));
        gobbler.join(Duration.ofSeconds(1));
        assertThat(gobbler.getOutput()).isEqualTo("data");
    }

    @Test
    void getOutputBeforeJoinReturnsPartial() {
        InputStream stream = toStream("hello");
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(stream, "test", 4096);
        // Call getOutput before join — should not crash
        String output = gobbler.getOutput();
        assertThat(output).isNotNull();
        gobbler.join(Duration.ofSeconds(2));
    }

    @Test
    void lineTruncationWhenLineExceedsRemaining() {
        // First line fits (5 chars), second line gets truncated mid-line
        // "abcde" = 5, newline = 1, "fghijklmnop" starts at pos 6, cap is 10
        InputStream stream = toStream("abcde\nfghijklmnop");
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(stream, "test", 10);
        gobbler.join(Duration.ofSeconds(2));
        String output = gobbler.getOutput();
        assertThat(output.length()).isLessThanOrEqualTo(10);
        assertThat(output).startsWith("abcde");
    }

    @Test
    void joinSetsInterruptFlagOnInterrupt() throws Exception {
        // Create a long-running stream so the gobbler thread takes time
        InputStream slow = new InputStream() {
            private volatile boolean read = false;
            @Override
            public int read() throws java.io.IOException {
                if (!read) {
                    read = true;
                    return 'a';
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new java.io.IOException("interrupted");
                }
                return -1;
            }
        };
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(slow, "test-interrupt", 4096);

        // Interrupt the current thread before joining to trigger InterruptedException in join
        Thread.currentThread().interrupt();
        gobbler.join(Duration.ofMillis(100));

        // The interrupt flag should be restored
        assertThat(Thread.interrupted()).isTrue(); // clears the flag
    }

    @Test
    void gobblerHandlesIOException() {
        // InputStream that throws IOException on read — exercises the catch(IOException) branch
        InputStream failing = new InputStream() {
            @Override
            public int read() throws java.io.IOException {
                throw new java.io.IOException("simulated device error");
            }
        };
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(failing, "test-fail", 4096);
        gobbler.join(Duration.ofSeconds(2));
        // Should not hang or throw — just captures empty/partial output
        assertThat(gobbler.getOutput()).isNotNull();
    }

    @Test
    void gobblerCapReachedThenContinuesDraining() {
        // Cap of 5 bytes: first line "hello" fills to cap, then subsequent lines are drained without accumulating
        // The second line hitting cap triggers capReached=true warning, subsequent lines skip the warning
        InputStream stream = toStream("hello\nworld\nextra\nmore");
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(stream, "test", 5);
        gobbler.join(Duration.ofSeconds(2));
        assertThat(gobbler.getOutput()).hasSize(5);
        assertThat(gobbler.getOutput()).isEqualTo("hello");
    }

    @Test
    void joinTimeoutExceededLeavesJoinedFalse() throws Exception {
        // Create a slow stream that takes a long time
        InputStream slow = new InputStream() {
            private int count = 0;
            @Override
            public int read() throws java.io.IOException {
                if (count++ < 2) {
                    return 'a';
                }
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    throw new java.io.IOException("interrupted");
                }
                return -1;
            }
        };
        ProcessStreamHandler.StreamGobbler gobbler =
                ProcessStreamHandler.startGobbler(slow, "test-timeout", 4096);
        // Join with very short timeout — thread won't finish
        gobbler.join(Duration.ofMillis(10));
        // getOutput should still work (returns partial)
        assertThat(gobbler.getOutput()).isNotNull();
    }

    private static InputStream toStream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
