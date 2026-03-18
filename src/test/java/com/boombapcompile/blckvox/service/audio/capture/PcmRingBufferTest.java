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

    // --- Lazy allocation tests ---

    @Test
    void startsWithSmallAllocation() {
        // maxCapacity = 1MB, but initial should be INITIAL_CAPACITY (320_000)
        PcmRingBuffer buf = new PcmRingBuffer(1_000_000);
        assertThat(buf.currentCapacity()).isEqualTo(320_000);
    }

    @Test
    void growsOnDemand() {
        PcmRingBuffer buf = new PcmRingBuffer(1_000_000);
        int initial = buf.currentCapacity();

        // Write more than initial capacity to force growth
        byte[] data = new byte[initial + 1];
        buf.write(data, 0, data.length);

        assertThat(buf.currentCapacity()).isGreaterThan(initial);
        // Data should be fully preserved (no overflow since maxCapacity is large)
        assertThat(buf.toByteArray()).hasSize(initial + 1);
    }

    @Test
    void stopsGrowingAtMaxCapacity() {
        int maxCap = 500_000;
        PcmRingBuffer buf = new PcmRingBuffer(maxCap);

        // Write enough data to exceed maxCapacity
        byte[] data = new byte[maxCap + 100];
        buf.write(data, 0, data.length);

        assertThat(buf.currentCapacity()).isEqualTo(maxCap);
        // Only the last maxCap bytes should be kept
        assertThat(buf.toByteArray()).hasSize(maxCap);
    }

    @Test
    void growBufferLinearizesWrappedData() {
        // Use a maxCapacity large enough to trigger growth.
        // INITIAL_CAPACITY = 320_000, so we need maxCapacity > 320_000.
        int maxCap = 640_000;
        PcmRingBuffer buf = new PcmRingBuffer(maxCap);

        // Fill the initial buffer to capacity
        byte[] fill = new byte[320_000];
        java.util.Arrays.fill(fill, (byte) 1);
        buf.write(fill, 0, fill.length);
        assertThat(buf.currentCapacity()).isEqualTo(320_000);

        // Read all data and overwrite to position writePos near end, then wrap around
        buf.clear();
        // Write 310_000 bytes to advance writePos to 310_000
        byte[] advance = new byte[310_000];
        java.util.Arrays.fill(advance, (byte) 2);
        buf.write(advance, 0, advance.length);
        // Clear but keep buffer (doesn't shrink)
        buf.clear();
        // Now writePos is at 0, size is 0

        // Write 310_000 bytes to advance writePos near end
        buf.write(advance, 0, advance.length);
        // writePos is at 310_000, size is 310_000

        // Now write more data that wraps around: 20_000 bytes fits (capacity=320_000, used=310_000, space=10_000)
        // 20_000 > 10_000 remaining, so part wraps to beginning
        byte[] wrap = new byte[20_000];
        java.util.Arrays.fill(wrap, (byte) 3);
        buf.write(wrap, 0, wrap.length);
        // Buffer is 320_000, used=320_000 (full, with some old data dropped)

        // Now trigger growth by writing more data that exceeds current capacity
        // but fits in maxCapacity — this triggers growBuffer() with size > 0
        // and wrapped data (writePos < size means first < size in growBuffer)
        byte[] triggerGrow = new byte[100_000];
        java.util.Arrays.fill(triggerGrow, (byte) 4);
        buf.write(triggerGrow, 0, triggerGrow.length);

        // Buffer should have grown
        assertThat(buf.currentCapacity()).isGreaterThan(320_000);
        // Data should be linearized and readable
        byte[] result = buf.toByteArray();
        assertThat(result.length).isGreaterThan(0);
        // The last 100_000 bytes should be 4s
        byte[] tail = java.util.Arrays.copyOfRange(result, result.length - 100_000, result.length);
        assertThat(tail).containsOnly((byte) 4);
    }

    @Test
    void clearDoesNotShrinkBuffer() {
        PcmRingBuffer buf = new PcmRingBuffer(1_000_000);
        int initial = buf.currentCapacity();

        // Force growth
        byte[] data = new byte[initial + 1];
        buf.write(data, 0, data.length);
        int grown = buf.currentCapacity();
        assertThat(grown).isGreaterThan(initial);

        buf.clear();

        // Buffer capacity should be retained after clear
        assertThat(buf.currentCapacity()).isEqualTo(grown);
        assertThat(buf.toByteArray()).isEmpty();
    }
}
