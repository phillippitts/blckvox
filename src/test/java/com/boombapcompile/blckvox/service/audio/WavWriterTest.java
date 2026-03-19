package com.boombapcompile.blckvox.service.audio;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_BITS_PER_SAMPLE;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_BLOCK_ALIGN;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_BYTE_RATE;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_CHANNELS;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_SAMPLE_RATE;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.WAV_BITS_PER_SAMPLE_OFFSET;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.WAV_BLOCK_ALIGN_OFFSET;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.WAV_BYTE_RATE_OFFSET;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.WAV_CHANNELS_OFFSET;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.WAV_HEADER_SIZE;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.WAV_SAMPLE_RATE_OFFSET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WavWriterTest {

    @Test
    void shouldThrowIllegalStateExceptionWhenWriteFails(@org.junit.jupiter.api.io.TempDir Path tempDir) {
        byte[] pcm = new byte[32_000];
        // Non-existent parent directory → IOException → IllegalStateException
        Path badPath = tempDir.resolve("nonexistent").resolve("file.wav");

        assertThatThrownBy(() -> WavWriter.writePcm16LeMono16kHz(pcm, badPath))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to write WAV file")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void shouldWriteValidWavHeaderAndPayload() throws IOException {
        // 1 second of silence at 16kHz mono 16-bit = 32,000 bytes
        byte[] pcm = new byte[REQUIRED_BYTE_RATE];
        Path wav = Files.createTempFile("wav-writer-", ".wav");
        try {
            WavWriter.writePcm16LeMono16kHz(pcm, wav);
            byte[] all = Files.readAllBytes(wav);

            // Overall size
            assertThat(all.length).isEqualTo(WAV_HEADER_SIZE + pcm.length);

            // RIFF/WAVE markers
            assertThat(new String(all, 0, 4)).isEqualTo("RIFF");
            assertThat(new String(all, 8, 4)).isEqualTo("WAVE");

            // fmt chunk size (little-endian 16) at bytes 16-19
            int fmtSize = ((all[16] & 0xFF)) | ((all[17] & 0xFF) << 8)
                    | ((all[18] & 0xFF) << 16) | ((all[19] & 0xFF) << 24);
            assertThat(fmtSize).isEqualTo(16);

            // Channels, sample rate, byte rate, block align, bits/sample
            int channels = (all[WAV_CHANNELS_OFFSET] & 0xFF) | ((all[WAV_CHANNELS_OFFSET + 1] & 0xFF) << 8);
            int sampleRate = (all[WAV_SAMPLE_RATE_OFFSET] & 0xFF)
                    | ((all[WAV_SAMPLE_RATE_OFFSET + 1] & 0xFF) << 8)
                    | ((all[WAV_SAMPLE_RATE_OFFSET + 2] & 0xFF) << 16)
                    | ((all[WAV_SAMPLE_RATE_OFFSET + 3] & 0xFF) << 24);
            int byteRate = (all[WAV_BYTE_RATE_OFFSET] & 0xFF)
                    | ((all[WAV_BYTE_RATE_OFFSET + 1] & 0xFF) << 8)
                    | ((all[WAV_BYTE_RATE_OFFSET + 2] & 0xFF) << 16)
                    | ((all[WAV_BYTE_RATE_OFFSET + 3] & 0xFF) << 24);
            int blockAlign = (all[WAV_BLOCK_ALIGN_OFFSET] & 0xFF)
                    | ((all[WAV_BLOCK_ALIGN_OFFSET + 1] & 0xFF) << 8);
            int bitsPerSample = (all[WAV_BITS_PER_SAMPLE_OFFSET] & 0xFF)
                    | ((all[WAV_BITS_PER_SAMPLE_OFFSET + 1] & 0xFF) << 8);

            assertThat(channels).isEqualTo(REQUIRED_CHANNELS);
            assertThat(sampleRate).isEqualTo(REQUIRED_SAMPLE_RATE);
            assertThat(byteRate).isEqualTo(REQUIRED_BYTE_RATE);
            assertThat(blockAlign).isEqualTo(REQUIRED_BLOCK_ALIGN);
            assertThat(bitsPerSample).isEqualTo(REQUIRED_BITS_PER_SAMPLE);
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    @Test
    void shouldRejectOddLengthPcm() throws IOException {
        byte[] pcm = new byte[3]; // Odd length - invalid for 16-bit samples
        Path tmp = Files.createTempFile("bad", ".wav");
        try {
            assertThatThrownBy(() -> WavWriter.writePcm16LeMono16kHz(pcm, tmp))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PCM data length must be even");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void headerChunkSizeIsCorrect() throws IOException {
        // Verify bytes 4-7 = 36 + pcm.length (LE int)
        // Kills mutant on L45 (36 + subchunk2Size)
        byte[] pcm = new byte[100]; // simple size for easy verification
        Path wav = Files.createTempFile("wav-chunk-", ".wav");
        try {
            WavWriter.writePcm16LeMono16kHz(pcm, wav);
            byte[] all = Files.readAllBytes(wav);

            int chunkSize = (all[4] & 0xFF) | ((all[5] & 0xFF) << 8)
                    | ((all[6] & 0xFF) << 16) | ((all[7] & 0xFF) << 24);
            assertThat(chunkSize).isEqualTo(36 + pcm.length);
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    @Test
    void headerDataSubchunkSizeIsCorrect() throws IOException {
        // Verify bytes 40-43 = pcm.length (LE int)
        // Kills mutant on L71 (writeLEInt(os, subchunk2Size))
        byte[] pcm = new byte[200];
        Path wav = Files.createTempFile("wav-data-", ".wav");
        try {
            WavWriter.writePcm16LeMono16kHz(pcm, wav);
            byte[] all = Files.readAllBytes(wav);

            int dataSize = (all[40] & 0xFF) | ((all[41] & 0xFF) << 8)
                    | ((all[42] & 0xFF) << 16) | ((all[43] & 0xFF) << 24);
            assertThat(dataSize).isEqualTo(pcm.length);
        } finally {
            Files.deleteIfExists(wav);
        }
    }

    @Test
    void headerByteOrderIsLittleEndian() throws IOException {
        // Write known value 16000 (0x3E80) as sample rate at offset 24
        // Verify individual bytes are in little-endian order
        // Kills shift mutants on L87-90
        byte[] pcm = new byte[32_000];
        Path wav = Files.createTempFile("wav-le-", ".wav");
        try {
            WavWriter.writePcm16LeMono16kHz(pcm, wav);
            byte[] all = Files.readAllBytes(wav);

            // Sample rate at offset 24: 16000 = 0x00003E80
            // LE: [0x80, 0x3E, 0x00, 0x00]
            assertThat(all[24] & 0xFF).isEqualTo(0x80);
            assertThat(all[25] & 0xFF).isEqualTo(0x3E);
            assertThat(all[26] & 0xFF).isEqualTo(0x00);
            assertThat(all[27] & 0xFF).isEqualTo(0x00);

            // Byte rate at offset 28: 32000 = 0x00007D00
            // LE: [0x00, 0x7D, 0x00, 0x00]
            assertThat(all[28] & 0xFF).isEqualTo(0x00);
            assertThat(all[29] & 0xFF).isEqualTo(0x7D);
            assertThat(all[30] & 0xFF).isEqualTo(0x00);
            assertThat(all[31] & 0xFF).isEqualTo(0x00);
        } finally {
            Files.deleteIfExists(wav);
        }
    }
}
