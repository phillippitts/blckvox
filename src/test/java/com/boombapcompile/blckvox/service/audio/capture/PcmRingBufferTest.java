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
    void growBufferPreservesDataAndTriggersOverflowAfterGrowth() {
        // Growth always fires BEFORE overflow in write(), so growBuffer()
        // never encounters wrapped data (linearization branch is a defensive guard).
        // This test verifies growth + post-growth overflow in a single write.
        // maxCap barely larger than initial → growth leaves limited headroom.
        int maxCap = 325_000; // INITIAL_CAPACITY=320_000, just 5K headroom
        PcmRingBuffer buf = new PcmRingBuffer(maxCap);
        assertThat(buf.currentCapacity()).isEqualTo(320_000);

        // Fill to capacity via massive write path (writePos=0, size=320_000)
        byte[] fill = new byte[320_000];
        java.util.Arrays.fill(fill, (byte) 0x0A);
        buf.write(fill, 0, fill.length);

        // Write 10K: triggers growth (320K+10K > 320K, 320K < 325K) → grows to 325K
        // Then overflow: space=325K-320K=5K, 10K>5K → drops 5K oldest, wraps at end
        byte[] extra = new byte[10_000];
        java.util.Arrays.fill(extra, (byte) 0x0B);
        buf.write(extra, 0, extra.length);

        assertThat(buf.currentCapacity()).isEqualTo(325_000);

        byte[] result = buf.toByteArray();
        assertThat(result).hasSize(325_000);
        // Last 10K should be 0x0B
        byte[] tail = java.util.Arrays.copyOfRange(result, result.length - 10_000, result.length);
        assertThat(tail).containsOnly((byte) 0x0B);
        // First 315K should be 0x0A (5K oldest dropped)
        assertThat(result[0]).isEqualTo((byte) 0x0A);
        assertThat(result[314_999]).isEqualTo((byte) 0x0A);
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

    // --- Mutation-killing boundary tests ---

    @Test
    void writeWrapsAroundCorrectly() {
        // Cap=8, write 6 then 4 → wraps around boundary
        // After first write: writePos=6, size=6, data=[1,2,3,4,5,6,_,_]
        // Second write: first=Math.min(4,8-6)=2, copies [7,8] to positions 6,7
        //   remaining=4-2=2, copies [9,10] to positions 0,1
        //   writePos=2, size=min(6+4,8)=8
        // Expected content: [3,4,5,6,7,8,9,10] (oldest 2 dropped)
        PcmRingBuffer buf = new PcmRingBuffer(8);
        buf.write(new byte[]{1,2,3,4,5,6}, 0, 6);
        buf.write(new byte[]{7,8,9,10}, 0, 4);
        assertThat(buf.toByteArray()).containsExactly(3,4,5,6,7,8,9,10);
    }

    @Test
    void writeExactlyCapacityEntersMassiveWritePathWithOverflow() {
        // Cap=4, write exactly 4 bytes → len >= buffer.length (4>=4) → massive write path
        // Kills >= to > on L55
        int[] overflowCount = {0};
        PcmRingBuffer buf = new PcmRingBuffer(4, () -> overflowCount[0]++);
        buf.write(new byte[]{1,2,3,4}, 0, 4);
        assertThat(buf.toByteArray()).containsExactly(1,2,3,4);
        assertThat(overflowCount[0]).isEqualTo(1); // overflow fires for massive write
    }

    @Test
    void writeOneLargerThanCapacityKeepsTail() {
        // Cap=4, write 5 → keeps last 4
        PcmRingBuffer buf = new PcmRingBuffer(4);
        buf.write(new byte[]{1,2,3,4,5}, 0, 5);
        assertThat(buf.toByteArray()).containsExactly(2,3,4,5);
    }

    @Test
    void writePastCapacityDropsOldestSizeIsCorrect() {
        // Cap=8, fill with 8, then write 3 more → drops oldest 3
        // Kills subtraction mutant on L78 (size -= toDrop)
        PcmRingBuffer buf = new PcmRingBuffer(8);
        buf.write(new byte[]{1,2,3,4,5,6,7,8}, 0, 8);
        buf.write(new byte[]{9,10,11}, 0, 3);
        byte[] result = buf.toByteArray();
        assertThat(result).hasSize(8);
        assertThat(result).containsExactly(4,5,6,7,8,9,10,11);
    }

    @Test
    void overflowCallbackInvokedExactlyOnOverflow() {
        // Normal write (no callback), then overflow write (callback fires)
        int[] count = {0};
        PcmRingBuffer buf = new PcmRingBuffer(8, () -> count[0]++);
        buf.write(new byte[]{1,2,3}, 0, 3); // no overflow
        assertThat(count[0]).isEqualTo(0);
        buf.write(new byte[]{4,5,6,7,8,9,10}, 0, 7); // 3+7=10 > 8 → overflow
        assertThat(count[0]).isEqualTo(1);
    }

    @Test
    void writeFillsToCapacityMinusOneNoOverflow() {
        // Cap=8, write 7 → no overflow (7 < 8)
        // Boundary for L55 (len >= buffer.length): 7 >= 8 is false
        int[] count = {0};
        PcmRingBuffer buf = new PcmRingBuffer(8, () -> count[0]++);
        buf.write(new byte[]{1,2,3,4,5,6,7}, 0, 7);
        assertThat(count[0]).isEqualTo(0);
        assertThat(buf.toByteArray()).containsExactly(1,2,3,4,5,6,7);
    }

    @Test
    void modularWritePosWrapsCorrectlyOnExactBoundary() {
        // Write exactly cap, clear, write again → verifies writePos modulo
        // After first write (massive path): writePos=0
        // After clear: writePos=0, size=0
        // After second write: data correct
        PcmRingBuffer buf = new PcmRingBuffer(4);
        buf.write(new byte[]{1,2,3,4}, 0, 4);
        buf.clear();
        buf.write(new byte[]{5,6}, 0, 2);
        assertThat(buf.toByteArray()).containsExactly(5,6);
    }

    @Test
    void toByteArrayReturnsEmptyWhenSizeZero() {
        // Verifies L96 size==0 check
        PcmRingBuffer buf = new PcmRingBuffer(8);
        assertThat(buf.toByteArray()).isEmpty();
        assertThat(buf.toByteArray()).hasSize(0);
    }

    @Test
    void clearThenWriteProducesCorrectData() {
        // Write, clear, write shorter data → only new data returned
        PcmRingBuffer buf = new PcmRingBuffer(8);
        buf.write(new byte[]{1,2,3,4,5,6,7,8}, 0, 8);
        buf.clear();
        buf.write(new byte[]{10,20}, 0, 2);
        assertThat(buf.toByteArray()).containsExactly(10,20);
    }

    @Test
    void growBufferPreservesWrappedData() {
        // Test growth with wrapped data (data crosses buffer boundary)
        // Small max to keep test fast — use initial cap of 8, max of 32
        // PcmRingBuffer uses INITIAL_CAPACITY=320000, so to get a small buffer for testing,
        // set maxCapacity to a small value (buffer = Math.min(INITIAL_CAPACITY, maxCap))
        PcmRingBuffer buf = new PcmRingBuffer(32);
        // buffer.length = min(320000, 32) = 32
        // Write 30 bytes to advance writePos to 30
        byte[] first = new byte[30];
        for (int i = 0; i < 30; i++) {
            first[i] = (byte) (i + 1);
        }
        buf.write(first, 0, 30);
        // writePos=30, size=30

        // Write 10 more bytes → wraps around (30+10=40 > 32, drops 8 oldest)
        // first part: min(10, 32-30)=2 bytes at positions 30,31
        // remaining: 10-2=8 bytes at positions 0-7
        // size = min(30+10, 32) = 32
        byte[] second = new byte[]{41,42,43,44,45,46,47,48,49,50};
        buf.write(second, 0, 10);

        byte[] result = buf.toByteArray();
        assertThat(result).hasSize(32);
        // Should contain bytes 9..30 (from first write, not dropped) + 41..50 (second write)
        // First write: bytes 1-30. After overflow of 8: oldest 8 dropped → remaining: 9-30 (22 bytes)
        // Plus second write: 41-50 (10 bytes) = 32 total
        assertThat(result[0]).isEqualTo((byte)9);
        assertThat(result[21]).isEqualTo((byte)30);
        assertThat(result[22]).isEqualTo((byte)41);
        assertThat(result[31]).isEqualTo((byte)50);
    }
}
