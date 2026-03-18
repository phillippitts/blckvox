package com.boombapcompile.blckvox.service.audio.capture;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

/**
 * Simple ring buffer for PCM bytes with lazy allocation. Thread-safe for concurrent access.
 * Designed for single-producer (capture thread) single-consumer (reader) pattern.
 *
 * <p>Starts with a small initial allocation (~10 seconds of audio at 32kB/s) and grows
 * on demand up to {@code maxCapacity}. Only drops data when the maximum capacity is reached.
 */
final class PcmRingBuffer {

    private static final Logger LOG = LogManager.getLogger(PcmRingBuffer.class);

    /** Initial allocation: ~10 seconds of 16kHz 16-bit mono audio. */
    private static final int INITIAL_CAPACITY = 320_000;

    private final int maxCapacity;
    private final Runnable overflowCallback;
    private byte[] buffer;
    private int writePos = 0;
    private int size = 0;
    private boolean dropWarned = false;

    PcmRingBuffer(int maxCapacityBytes) {
        this(maxCapacityBytes, null);
    }

    PcmRingBuffer(int maxCapacityBytes, Runnable overflowCallback) {
        this.maxCapacity = maxCapacityBytes;
        this.buffer = new byte[Math.min(INITIAL_CAPACITY, maxCapacityBytes)];
        this.overflowCallback = overflowCallback;
    }

    synchronized void write(byte[] src, int off, int len) {
        if (len <= 0) {
            return;
        }

        // Grow buffer if needed and possible before dropping data
        int needed = size + len;
        if (needed > buffer.length && buffer.length < maxCapacity) {
            int newCap = buffer.length;
            while (newCap < needed && newCap < maxCapacity) {
                newCap = (int) Math.min((long) newCap * 2, maxCapacity);
            }
            growBuffer(newCap);
        }

        // If incoming exceeds capacity, drop oldest by keeping only the last capacity bytes
        if (len >= buffer.length) {
            if (!dropWarned) {
                LOG.warn("Ring buffer overflow: incoming {} exceeds capacity {}, dropping oldest",
                        len, buffer.length);
                dropWarned = true;
                notifyOverflow();
            }
            // keep tail of src
            System.arraycopy(src, off + (len - buffer.length), buffer, 0, buffer.length);
            writePos = 0; // buffer full
            size = buffer.length;
            return;
        }
        // If size + len exceeds capacity, drop oldest
        int space = buffer.length - size;
        if (len > space) {
            int toDrop = len - space;
            if (!dropWarned) {
                LOG.warn("Ring buffer overflow: dropping {} oldest bytes (capacity={}, size={})",
                        toDrop, buffer.length, size);
                dropWarned = true;
                notifyOverflow();
            }
            size -= toDrop;
            // No need to adjust writePos - toByteArray() computes start position
            // from (writePos - size), so reducing size automatically drops oldest bytes
        }
        // Write with wrap
        int first = Math.min(len, buffer.length - writePos);
        System.arraycopy(src, off, buffer, writePos, first);
        int remaining = len - first;
        if (remaining > 0) {
            System.arraycopy(src, off + first, buffer, 0, remaining);
            writePos = remaining;
        } else {
            writePos = (writePos + first) % buffer.length;
        }
        size = Math.min(size + len, buffer.length);
    }

    synchronized byte[] toByteArray() {
        if (size == 0) {
            return new byte[0];
        }
        byte[] out = new byte[size];
        int start = (writePos - size + buffer.length) % buffer.length;
        int first = Math.min(size, buffer.length - start);
        System.arraycopy(buffer, start, out, 0, first);
        if (first < size) {
            System.arraycopy(buffer, 0, out, first, size - first);
        }
        return out;
    }

    synchronized void clear() {
        Arrays.fill(buffer, (byte)0);
        writePos = 0; size = 0;
        dropWarned = false;
    }

    /** Returns the current allocated capacity (may be less than maxCapacity). */
    int currentCapacity() {
        return buffer.length;
    }

    /**
     * Linearizes existing ring data into a larger array.
     */
    private void growBuffer(int newCapacity) {
        byte[] old = buffer;
        byte[] grown = new byte[newCapacity];
        if (size > 0) {
            int start = (writePos - size + old.length) % old.length;
            int first = Math.min(size, old.length - start);
            System.arraycopy(old, start, grown, 0, first);
            if (first < size) {
                System.arraycopy(old, 0, grown, first, size - first);
            }
        }
        buffer = grown;
        writePos = size; // data is now linearized at position 0..size-1
    }

    private void notifyOverflow() {
        if (overflowCallback != null) {
            try {
                overflowCallback.run();
            } catch (Exception e) {
                LOG.debug("Overflow callback failed: {}", e.getMessage());
            }
        }
    }
}
