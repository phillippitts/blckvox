package com.boombapcompile.blckvox.service.audio.capture;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PcmRingBufferTest {

    @Test
    void writesAndReadsInOrderWithinCapacity() {
        PcmRingBuffer buf = new PcmRingBuffer(8);
        buf.write(new byte[]{1,2,3,4}, 0, 4);
        assertThat(buf.toByteArray()).containsExactly(1,2,3,4);
        buf.write(new byte[]{5,6,7,8}, 0, 4);
        assertThat(buf.toByteArray()).containsExactly(1,2,3,4,5,6,7,8);
    }

    @Test
    void dropsOldestWhenOverflow() {
        PcmRingBuffer buf = new PcmRingBuffer(8);
        buf.write(new byte[]{1,2,3,4,5,6,7,8}, 0, 8);
        buf.write(new byte[]{9,10,11}, 0, 3);
        // Expect last 8 numbers [4..11]
        assertThat(buf.toByteArray()).containsExactly(4,5,6,7,8,9,10,11);
    }

    @Test
    void clearResetsState() {
        PcmRingBuffer buf = new PcmRingBuffer(4);
        buf.write(new byte[]{1,2,3,4}, 0, 4);
        buf.clear();
        assertThat(buf.toByteArray()).isEmpty();
    }

    @Test
    void wrapAndOverflowDropsOldestCorrectly() {
        // Capacity 10: test wrap+overflow in single write
        PcmRingBuffer buf = new PcmRingBuffer(10);

        // Write 7 bytes, positioning writePos near end
        buf.write(new byte[]{1,2,3,4,5,6,7}, 0, 7);
        assertThat(buf.toByteArray()).containsExactly(1,2,3,4,5,6,7);

        // Write 8 bytes: wraps (3 remaining + 5 wrapped) AND overflows (8 > 3, drops 5 oldest)
        // After this: oldest 5 bytes [1,2,3,4,5] are dropped, buffer contains [6,7,11,12,13,14,15,16,17,18]
        buf.write(new byte[]{11,12,13,14,15,16,17,18}, 0, 8);

        // Expect last 10 bytes: [6,7] from first write + [11..18] from second write
        assertThat(buf.toByteArray()).containsExactly(6,7,11,12,13,14,15,16,17,18);
    }

    @Test
    void writeLenZeroIsNoOp() {
        PcmRingBuffer buf = new PcmRingBuffer(8);
        buf.write(new byte[]{1, 2}, 0, 2);
        buf.write(new byte[]{99}, 0, 0);
        assertThat(buf.toByteArray()).containsExactly(1, 2);
    }

    @Test
    void writeLenNegativeIsNoOp() {
        PcmRingBuffer buf = new PcmRingBuffer(8);
        buf.write(new byte[]{1}, 0, -1);
        assertThat(buf.toByteArray()).isEmpty();
    }

    @Test
    void toByteArrayReturnsEmptyWhenNeverWritten() {
        PcmRingBuffer buf = new PcmRingBuffer(4);
        assertThat(buf.toByteArray()).isEmpty();
    }

    @Test
    void massiveWriteExceedingCapacityKeepsTail() {
        PcmRingBuffer buf = new PcmRingBuffer(4);
        // Write 10 bytes into a 4-byte buffer — should keep last 4
        buf.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, 0, 10);
        assertThat(buf.toByteArray()).containsExactly(7, 8, 9, 10);
    }

    @Test
    void overflowCallbackInvokedOnce() {
        int[] count = {0};
        PcmRingBuffer buf = new PcmRingBuffer(4, () -> count[0]++);
        // First overflow triggers callback
        buf.write(new byte[]{1, 2, 3, 4, 5, 6}, 0, 6);
        assertThat(count[0]).isEqualTo(1);
        // Second overflow doesn't trigger again (dropWarned=true)
        buf.write(new byte[]{7, 8, 9, 10}, 0, 4);
        assertThat(count[0]).isEqualTo(1);
    }

    @Test
    void overflowCallbackNullDoesNotThrow() {
        PcmRingBuffer buf = new PcmRingBuffer(4, null);
        // Should not throw even though callback is null
        buf.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8}, 0, 8);
        assertThat(buf.toByteArray()).containsExactly(5, 6, 7, 8);
    }

    @Test
    void overflowCallbackExceptionIsCaught() {
        PcmRingBuffer buf = new PcmRingBuffer(4, () -> {
            throw new RuntimeException("callback boom");
        });
        // Should not throw — exception is caught internally
        buf.write(new byte[]{1, 2, 3, 4, 5, 6}, 0, 6);
        assertThat(buf.toByteArray()).containsExactly(3, 4, 5, 6);
    }

    @Test
    void clearAfterOverflowResetsDropWarned() {
        int[] count = {0};
        PcmRingBuffer buf = new PcmRingBuffer(4, () -> count[0]++);
        buf.write(new byte[]{1, 2, 3, 4, 5}, 0, 5); // triggers callback
        assertThat(count[0]).isEqualTo(1);

        buf.clear();

        // After clear, dropWarned is reset — next overflow triggers callback again
        buf.write(new byte[]{1, 2, 3, 4, 5}, 0, 5);
        assertThat(count[0]).isEqualTo(2);
    }

    @Test
    void writeWithNonZeroOffset() {
        PcmRingBuffer buf = new PcmRingBuffer(8);
        byte[] src = {99, 99, 1, 2, 3, 99};
        buf.write(src, 2, 3); // write bytes at offset 2, len 3
        assertThat(buf.toByteArray()).containsExactly(1, 2, 3);
    }
}
