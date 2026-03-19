package com.boombapcompile.blckvox.service.audio.capture;

import java.util.UUID;

/**
 * Published after each PCM chunk is captured from the microphone.
 *
 * <p>Contains a defensive copy of the raw PCM data for downstream consumers
 * (e.g., live waveform display, streaming STT).
 *
 * @param pcmData defensive copy of captured PCM bytes
 * @param length number of valid bytes in pcmData
 * @param sessionId capture session that produced this chunk
 */
public record PcmChunkCapturedEvent(byte[] pcmData, int length, UUID sessionId) {

    /** Compact constructor performs defensive copy to prevent caller mutation. */
    public PcmChunkCapturedEvent {
        pcmData = pcmData.clone();
    }
}
