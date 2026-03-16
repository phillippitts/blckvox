package com.boombapcompile.blckvox.service.validation;

import com.boombapcompile.blckvox.config.properties.AudioValidationProperties;

import com.boombapcompile.blckvox.exception.InvalidAudioException;
import com.boombapcompile.blckvox.service.audio.WavFormat;
import org.springframework.stereotype.Component;

import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_BITS_PER_SAMPLE;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_BLOCK_ALIGN;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_BYTE_RATE;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_CHANNELS;
import static com.boombapcompile.blckvox.service.audio.AudioFormat.REQUIRED_SAMPLE_RATE;

/**
 * Validates input audio as either WAV (RIFF/WAVE) or raw PCM for STT ingestion.
 *
 * <p>WAV: Parses chunk structure to locate fmt and data chunks, validates format fields.
 * Handles non-standard WAV files with additional chunks or extended fmt chunks.
 *
 * <p>PCM: Checks length-derived duration and block alignment under known capture settings.
 */
@Component
public class AudioValidator {
    private final AudioValidationProperties props;

    /**
     * Constructs an AudioValidator with the specified validation configuration.
     *
     * @param props audio validation properties (min/max duration, max file size)
     */
    public AudioValidator(AudioValidationProperties props) {
        this.props = props;
    }

    /**
     * Validate a complete audio clip (final buffer) before STT.
     * @param data audio bytes, either WAV (RIFF/WAVE) or raw PCM16LE mono at 16kHz
     * @throws InvalidAudioException when format or duration constraints are violated
     */
    public void validate(byte[] data) {
        if (data == null) {
            throw new InvalidAudioException("Audio data is null");
        }

        // Guard against oversized payloads (security: prevent memory exhaustion)
        if (data.length > props.getMaxFileSizeBytes()) {
            throw new InvalidAudioException(data.length,
                    "Audio payload too large: " + data.length + " bytes. Max: "
                    + props.getMaxFileSizeBytes() + " bytes ("
                    + (props.getMaxFileSizeBytes() / (1024 * 1024)) + " MB)");
        }

        if (isWav(data)) {
            validateWav(data);
        } else {
            validatePcm(data);
        }
    }

    private boolean isWav(byte[] a) {
        return a.length >= 12
            && a[0] == 'R' && a[1] == 'I' && a[2] == 'F' && a[3] == 'F'
            && a[8] == 'W' && a[9] == 'A' && a[10] == 'V' && a[11] == 'E';
    }

    /**
     * Validates WAV file by parsing chunk structure to find fmt and data chunks.
     *
     * <p>WAV file structure:
     * <ul>
     *   <li>RIFF header (12 bytes): "RIFF" + fileSize + "WAVE"</li>
     *   <li>Chunks: chunkID (4 bytes) + chunkSize (4 bytes) + chunkData (chunkSize bytes)</li>
     *   <li>fmt chunk: audio format parameters</li>
     *   <li>data chunk: actual PCM audio data</li>
     * </ul>
     *
     * @param wav WAV file bytes
     * @throws InvalidAudioException if WAV structure is invalid or format doesn't match requirements
     */
    private void validateWav(byte[] wav) {
        if (wav.length < WavFormat.RIFF_HEADER_SIZE) {
            throw new InvalidAudioException("Audio too small for RIFF header ("
                    + WavFormat.RIFF_HEADER_SIZE + " bytes)");
        }

        // Parse chunks to find fmt and data
        WavChunks chunks = parseWavChunks(wav);

        // Validate fmt chunk
        requireChunk(chunks.fmtOffset, "fmt");
        validateFmtChunk(wav, chunks.fmtOffset, chunks.fmtSize);

        // Validate data chunk
        requireChunk(chunks.dataOffset, "data");
        // Ensure data size is aligned to block size (2 bytes for 16-bit mono)
        if (chunks.dataSize % REQUIRED_BLOCK_ALIGN != 0) {
            throw new InvalidAudioException("WAV data not aligned to block size (" + REQUIRED_BLOCK_ALIGN
                    + " bytes). Size: " + chunks.dataSize);
        }
        validateDurationByBytes(chunks.dataSize);
    }

    /**
     * Ensures a required WAV chunk was found during parsing.
     *
     * @param offset chunk offset (-1 if not found)
     * @param chunkName name of the chunk for error message
     * @throws InvalidAudioException if chunk was not found
     */
    private void requireChunk(int offset, String chunkName) {
        if (offset == -1) {
            throw new InvalidAudioException("Missing " + chunkName + " chunk in WAV file");
        }
    }

    /**
     * Parses WAV file to locate fmt and data chunks.
     *
     * @param wav WAV file bytes
     * @return WavChunks containing offsets and sizes
     */
    private WavChunks parseWavChunks(byte[] wav) {
        int offset = WavFormat.RIFF_HEADER_SIZE; // Skip RIFF header
        int fmtOffset = -1;
        int fmtSize = 0;
        int dataOffset = -1;
        int dataSize = 0;

        while (offset + WavFormat.CHUNK_HEADER_SIZE <= wav.length) {
            // Read chunk ID (4 bytes) and size (4 bytes)
            String chunkId = readChunkId(wav, offset);
            int chunkSize = readLEInt(wav, offset + 4);

            if (chunkSize < 0 || offset + WavFormat.CHUNK_HEADER_SIZE + chunkSize > wav.length) {
                throw new InvalidAudioException("Invalid chunk size: " + chunkSize + " at offset " + offset);
            }

            if ("fmt ".equals(chunkId)) {
                fmtOffset = offset + WavFormat.CHUNK_HEADER_SIZE;
                fmtSize = chunkSize;
            } else if ("data".equals(chunkId)) {
                dataOffset = offset + WavFormat.CHUNK_HEADER_SIZE;
                dataSize = chunkSize;
                break; // data chunk is usually last, stop parsing
            }

            // Move to next chunk (pad to even boundary)
            offset += WavFormat.CHUNK_HEADER_SIZE + chunkSize;
            if (chunkSize % 2 == 1) {
                offset++; // WAV chunks are padded to even byte boundaries
            }
        }

        return new WavChunks(fmtOffset, fmtSize, dataOffset, dataSize);
    }

    /**
     * Validates fmt chunk contains required audio format.
     *
     * @param wav WAV file bytes
     * @param offset offset to fmt chunk data (after chunk header)
     * @param size size of fmt chunk
     */
    private void validateFmtChunk(byte[] wav, int offset, int size) {
        // Standard PCM fmt chunk is 16 bytes, but can be larger with extensions
        if (size < WavFormat.FMT_CHUNK_MIN_SIZE) {
            throw new InvalidAudioException("fmt chunk too small: " + size + " bytes (expected at least "
                    + WavFormat.FMT_CHUNK_MIN_SIZE + ")");
        }

        int audioFormat = readLEShort(wav, offset);      // 2 bytes: audio format (1 = PCM)
        int channels = readLEShort(wav, offset + 2);     // 2 bytes: number of channels
        int sampleRate = readLEInt(wav, offset + 4);     // 4 bytes: sample rate
        int byteRate = readLEInt(wav, offset + 8);       // 4 bytes: byte rate
        int blockAlign = readLEShort(wav, offset + 12);  // 2 bytes: block align
        int bitsPerSample = readLEShort(wav, offset + 14); // 2 bytes: bits per sample

        if (audioFormat != WavFormat.AUDIO_FORMAT_PCM) {
            throw new InvalidAudioException("Unsupported audio format: " + audioFormat
                    + " (expected " + WavFormat.AUDIO_FORMAT_PCM + " for PCM)");
        }
        if (channels != REQUIRED_CHANNELS) {
            throw new InvalidAudioException("Invalid channel count: " + channels + ". Expected: " + REQUIRED_CHANNELS);
        }
        if (sampleRate != REQUIRED_SAMPLE_RATE) {
            throw new InvalidAudioException("Invalid sample rate: " + sampleRate + " Hz. Expected: "
                    + REQUIRED_SAMPLE_RATE + " Hz");
        }
        if (bitsPerSample != REQUIRED_BITS_PER_SAMPLE) {
            throw new InvalidAudioException("Invalid bit depth: " + bitsPerSample + "-bit. Expected: "
                    + REQUIRED_BITS_PER_SAMPLE + "-bit");
        }
        if (blockAlign != REQUIRED_BLOCK_ALIGN) {
            throw new InvalidAudioException("Invalid block align: " + blockAlign + ". Expected: "
                    + REQUIRED_BLOCK_ALIGN);
        }
        if (byteRate != REQUIRED_BYTE_RATE) {
            throw new InvalidAudioException("Invalid byte rate: " + byteRate + ". Expected: " + REQUIRED_BYTE_RATE);
        }
    }

    /**
     * Reads 4-character chunk ID from WAV file.
     *
     * @param wav WAV file bytes
     * @param offset offset to chunk ID
     * @return chunk ID as string
     */
    private String readChunkId(byte[] wav, int offset) {
        if (offset + 4 > wav.length) {
            return "";
        }
        return new String(new byte[]{wav[offset], wav[offset + 1], wav[offset + 2], wav[offset + 3]}, java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * Container for WAV chunk locations.
     */
    private static class WavChunks {
        final int fmtOffset;
        final int fmtSize;
        final int dataOffset;
        final int dataSize;

        WavChunks(int fmtOffset, int fmtSize, int dataOffset, int dataSize) {
            this.fmtOffset = fmtOffset;
            this.fmtSize = fmtSize;
            this.dataOffset = dataOffset;
            this.dataSize = dataSize;
        }
    }

    private void validatePcm(byte[] pcm) {
        if (pcm.length % REQUIRED_BLOCK_ALIGN != 0) {
            throw new InvalidAudioException("PCM not aligned to block size (" + REQUIRED_BLOCK_ALIGN + " bytes)");
        }
        validateDurationByBytes(pcm.length);
    }

    private void validateDurationByBytes(int bytes) {
        long durationMs = (bytes * 1000L) / REQUIRED_BYTE_RATE; // ms at 16kHz mono 16-bit
        if (durationMs < props.getMinDurationMs()) {
            throw new InvalidAudioException(bytes, "Audio too short: ~" + durationMs
                    + " ms. Min: " + props.getMinDurationMs() + " ms");
        }
        if (durationMs > props.getMaxDurationMs()) {
            throw new InvalidAudioException(bytes, "Audio too long: ~" + durationMs
                    + " ms. Max: " + props.getMaxDurationMs() + " ms");
        }
    }

    private static int readLEShort(byte[] a, int off) {
        return (a[off] & 0xFF) | ((a[off + 1] & 0xFF) << 8);
    }

    private static int readLEInt(byte[] a, int off) {
        return (a[off] & 0xFF)
             | ((a[off + 1] & 0xFF) << 8)
             | ((a[off + 2] & 0xFF) << 16)
             | ((a[off + 3] & 0xFF) << 24);
    }
}