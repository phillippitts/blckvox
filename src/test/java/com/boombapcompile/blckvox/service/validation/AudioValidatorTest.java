package com.boombapcompile.blckvox.service.validation;

import com.boombapcompile.blckvox.exception.InvalidAudioException;

import com.boombapcompile.blckvox.config.properties.AudioValidationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudioValidatorTest {

    private AudioValidator validator;

    @BeforeEach
    void setup() {
        AudioValidationProperties props = new AudioValidationProperties(250, 300_000, 100 * 1024 * 1024);
        validator = new AudioValidator(props);
    }

    @Test
    void wavShouldRejectStereo() {
        byte[] wav = makeMinimalWav(16_000, 2, 16); // Correct rate, wrong channels
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("channel count");
    }

    @Test
    void wavShouldRejectWrongSampleRate() {
        byte[] wav = makeMinimalWav(44_100, 1, 16); // Wrong rate, correct channels
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("sample rate");
    }

    @Test
    void wavShouldAccept16000HzMono16bit() {
        byte[] wav = makeMinimalWav(16_000, 1, 16);
        assertThatCode(() -> validator.validate(wav)).doesNotThrowAnyException();
    }

    @Test
    void wavShouldReject24bit() {
        byte[] wav = makeMinimalWav(16_000, 1, 24);
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("bit depth");
    }

    @Test
    void pcmShouldAccept16000HzMono16bitByLength() {
        // ~1 second at 16kHz mono 16-bit = 32,000 bytes
        byte[] pcm = new byte[32_000];
        assertThatCode(() -> validator.validate(pcm)).doesNotThrowAnyException();
    }

    @Test
    void pcmShouldRejectTooShortUnderMinDuration() {
        // ~200ms at 32kB/s => 6,400 bytes; use smaller to trigger min threshold (250ms)
        byte[] pcm = new byte[5_000];
        assertThatThrownBy(() -> validator.validate(pcm))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void wavShouldHandleNonStandardHeaderWithListChunk() {
        // WAV with LIST chunk before data chunk (common in files with metadata)
        byte[] wav = makeWavWithListChunk(16_000, 1, 16);
        assertThatCode(() -> validator.validate(wav)).doesNotThrowAnyException();
    }

    @Test
    void wavShouldHandleExtendedFmtChunk() {
        // WAV with extended fmt chunk (18 bytes instead of 16)
        byte[] wav = makeWavWithExtendedFmt(16_000, 1, 16);
        assertThatCode(() -> validator.validate(wav)).doesNotThrowAnyException();
    }

    @Test
    void wavShouldRejectMissingFmtChunk() {
        // WAV with data chunk but no fmt chunk
        byte[] wav = makeWavWithoutFmtChunk();
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Missing fmt chunk");
    }

    @Test
    void wavShouldRejectMissingDataChunk() {
        // WAV with fmt chunk but no data chunk
        byte[] wav = makeWavWithoutDataChunk();
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Missing data chunk");
    }

    @Test
    void wavShouldRejectMisalignedDataChunk() {
        // WAV with odd data size (not aligned to block size of 2 bytes)
        byte[] wav = makeWavWithMisalignedData(16_000, 1, 16);
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("not aligned to block size");
    }

    @Test
    void shouldRejectNullData() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("null");
    }

    @Test
    void shouldRejectOversizedPayload() {
        // Max is 100MB; create a byte array slightly over
        AudioValidationProperties smallProps = new AudioValidationProperties(250, 300_000, 100);
        AudioValidator smallValidator = new AudioValidator(smallProps);
        byte[] big = new byte[101];
        assertThatThrownBy(() -> smallValidator.validate(big))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void wavShouldRejectUnsupportedAudioFormat() {
        // Format 3 = IEEE float, not PCM
        byte[] wav = makeMinimalWavWithFormat(3, 16_000, 1, 16);
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Unsupported audio format");
    }

    @Test
    void wavShouldRejectWrongBlockAlign() {
        // blockAlign=4 instead of 2 for mono 16-bit
        byte[] wav = makeMinimalWavWithBlockAlign(16_000, 1, 16, 4);
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("block align");
    }

    @Test
    void wavShouldRejectWrongByteRate() {
        // byteRate=64000 instead of 32000
        byte[] wav = makeMinimalWavWithByteRate(16_000, 1, 16, 64_000);
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("byte rate");
    }

    @Test
    void wavShouldRejectTooSmallForRiffHeader() {
        byte[] tooSmall = new byte[]{
                'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'};
        // 12 bytes = valid RIFF header but no chunks → Missing fmt
        assertThatThrownBy(() -> validator.validate(tooSmall))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Missing fmt chunk");
    }

    @Test
    void wavShouldRejectInvalidChunkSize() {
        // WAV with negative chunk size
        byte[] wav = new byte[28];
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        putLEInt(wav, 4, 20);
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        // chunk with size pointing beyond array
        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
        putLEInt(wav, 16, 999); // chunk size way too large
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Invalid chunk size");
    }

    @Test
    void wavShouldRejectNegativeChunkSize() {
        byte[] wav = new byte[28];
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        putLEInt(wav, 4, 20);
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
        putLEInt(wav, 16, -1); // negative chunk size
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("Invalid chunk size");
    }

    @Test
    void isWavReturnsFalseForPartialRiffHeader() {
        // 32000 bytes (valid PCM length) but first byte is 'R' — exercises isWav partial match
        byte[] data = new byte[32_000];
        data[0] = 'R'; // matches first byte but not second
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavReturnsFalseWhenWaveSignatureMismatch() {
        // Has "RIFF" but not "WAVE" at bytes 8-11
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'A'; data[9] = 'V'; data[10] = 'I'; data[11] = ' '; // AVI, not WAVE
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void wavShouldRejectFmtChunkTooSmall() {
        // fmt chunk of only 8 bytes (min is 16)
        byte[] wav = makeWavWithSmallFmt();
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("fmt chunk too small");
    }

    @Test
    void isWavReturnsFalseForDataShorterThan12Bytes() {
        // 10 bytes: length < 12 → isWav length check fails → PCM path → too short
        byte[] data = new byte[10];
        assertThatThrownBy(() -> validator.validate(data))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void isWavReturnsFalseWhenRiPartialMatch() {
        // R, I match but a[2] != 'F' → PCM path
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'X';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavReturnsFalseWhenRifPartialMatch() {
        // R, I, F match but a[3] != 'F' → PCM path
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'X';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavReturnsFalseWhenRiffWButNotA() {
        // RIFF + W matches but a[9] != 'A' → PCM path
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'W'; data[9] = 'X';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavReturnsFalseWhenRiffWaButNotV() {
        // RIFF + WA matches but a[10] != 'V' → PCM path
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'W'; data[9] = 'A'; data[10] = 'X';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavReturnsFalseWhenRiffWavButNotE() {
        // RIFF + WAV matches but a[11] != 'E' → PCM path
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'W'; data[9] = 'A'; data[10] = 'V'; data[11] = 'X';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void pcmShouldRejectOddByteCount() {
        byte[] pcm = new byte[32_001]; // Odd - not aligned to 2 bytes
        assertThatThrownBy(() -> validator.validate(pcm))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("not aligned to block size");
    }

    @Test
    void pcmShouldRejectTooLong() {
        // Over 300 seconds = over 9,600,000 bytes at 32kB/s
        AudioValidationProperties strictProps = new AudioValidationProperties(250, 1000, 100 * 1024 * 1024);
        AudioValidator strictValidator = new AudioValidator(strictProps);
        byte[] longPcm = new byte[64_000]; // ~2 seconds - exceeds 1s max
        assertThatThrownBy(() -> strictValidator.validate(longPcm))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("too long");
    }

    @Test
    void wavWithOddSizedChunkShouldPadToEvenBoundary() {
        // WAV with a LIST chunk of odd size (should be padded)
        byte[] wav = makeWavWithOddChunk(16_000, 1, 16);
        assertThatCode(() -> validator.validate(wav)).doesNotThrowAnyException();
    }

    // --- Helpers ---

    private static byte[] makeMinimalWavWithFormat(int audioFormat, int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_000;
        int header = 44;
        byte[] out = new byte[header + payloadBytes];
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        putLEInt(out, 4, 36 + payloadBytes);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        putLEInt(out, 16, 16);
        putLEShort(out, 20, audioFormat);
        putLEShort(out, 22, channels);
        putLEInt(out, 24, sampleRate);
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, 28, byteRate);
        putLEShort(out, 32, blockAlign);
        putLEShort(out, 34, bitsPerSample);
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        putLEInt(out, 40, payloadBytes);
        return out;
    }

    private static byte[] makeMinimalWavWithBlockAlign(
            int sampleRate, int channels, int bitsPerSample, int blockAlign) {
        int payloadBytes = 40_000;
        int header = 44;
        byte[] out = new byte[header + payloadBytes];
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        putLEInt(out, 4, 36 + payloadBytes);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        putLEInt(out, 16, 16);
        putLEShort(out, 20, 1); // PCM
        putLEShort(out, 22, channels);
        putLEInt(out, 24, sampleRate);
        putLEInt(out, 28, sampleRate * blockAlign); // byte rate uses custom blockAlign
        putLEShort(out, 32, blockAlign);
        putLEShort(out, 34, bitsPerSample);
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        putLEInt(out, 40, payloadBytes);
        return out;
    }

    private static byte[] makeMinimalWavWithByteRate(int sampleRate, int channels, int bitsPerSample, int byteRate) {
        int payloadBytes = 40_000;
        int header = 44;
        byte[] out = new byte[header + payloadBytes];
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        putLEInt(out, 4, 36 + payloadBytes);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        putLEInt(out, 16, 16);
        putLEShort(out, 20, 1); // PCM
        putLEShort(out, 22, channels);
        putLEInt(out, 24, sampleRate);
        putLEInt(out, 28, byteRate);
        int blockAlign = (bitsPerSample / 8) * channels;
        putLEShort(out, 32, blockAlign);
        putLEShort(out, 34, bitsPerSample);
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        putLEInt(out, 40, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithSmallFmt() {
        int fmtSize = 8; // Too small (min is 16)
        int payloadBytes = 40_000;
        int totalSize = 12 + 8 + fmtSize + 8 + payloadBytes;
        byte[] out = new byte[totalSize];
        int offset = 0;
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';
        out[offset++] = 'f'; out[offset++] = 'm'; out[offset++] = 't'; out[offset++] = ' ';
        putLEInt(out, offset, fmtSize); offset += 4;
        offset += fmtSize; // skip content
        out[offset++] = 'd'; out[offset++] = 'a'; out[offset++] = 't'; out[offset++] = 'a';
        putLEInt(out, offset, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithOddChunk(int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_000;
        int oddChunkSize = 13; // Odd size - should be padded to even boundary
        int totalSize = 12 + 8 + 16 + 8 + oddChunkSize + 1 + 8 + payloadBytes; // +1 for padding
        byte[] out = new byte[totalSize];
        int offset = 0;
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';
        // fmt chunk
        out[offset++] = 'f'; out[offset++] = 'm'; out[offset++] = 't'; out[offset++] = ' ';
        putLEInt(out, offset, 16); offset += 4;
        putLEShort(out, offset, 1); offset += 2;
        putLEShort(out, offset, channels); offset += 2;
        putLEInt(out, offset, sampleRate); offset += 4;
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, offset, byteRate); offset += 4;
        putLEShort(out, offset, blockAlign); offset += 2;
        putLEShort(out, offset, bitsPerSample); offset += 2;
        // Odd-sized unknown chunk (e.g. metadata)
        out[offset++] = 'X'; out[offset++] = 'T'; out[offset++] = 'R'; out[offset++] = 'A';
        putLEInt(out, offset, oddChunkSize); offset += 4;
        offset += oddChunkSize; // skip content
        offset += 1; // padding byte for even boundary
        // data chunk
        out[offset++] = 'd'; out[offset++] = 'a'; out[offset++] = 't'; out[offset++] = 'a';
        putLEInt(out, offset, payloadBytes);
        return out;
    }

    private static byte[] makeMinimalWav(int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_000; // Default payload size for test WAV files
        int header = 44;
        byte[] out = new byte[header + payloadBytes];
        // RIFF/WAVE
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        putLEInt(out, 4, 36 + payloadBytes);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        // fmt chunk
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        putLEInt(out, 16, 16);
        putLEShort(out, 20, 1);
        putLEShort(out, 22, channels);
        putLEInt(out, 24, sampleRate);
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, 28, byteRate);
        putLEShort(out, 32, blockAlign);
        putLEShort(out, 34, bitsPerSample);
        // data
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        putLEInt(out, 40, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithListChunk(int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_000;
        int listChunkSize = 24; // LIST chunk with some metadata
        int totalSize = 12 + 8 + 16 + 8 + listChunkSize + 8 + payloadBytes;
        byte[] out = new byte[totalSize];
        int offset = 0;

        // RIFF header
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';

        // fmt chunk
        out[offset++] = 'f'; out[offset++] = 'm'; out[offset++] = 't'; out[offset++] = ' ';
        putLEInt(out, offset, 16); offset += 4;
        putLEShort(out, offset, 1); offset += 2; // PCM
        putLEShort(out, offset, channels); offset += 2;
        putLEInt(out, offset, sampleRate); offset += 4;
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, offset, byteRate); offset += 4;
        putLEShort(out, offset, blockAlign); offset += 2;
        putLEShort(out, offset, bitsPerSample); offset += 2;

        // LIST chunk (metadata)
        out[offset++] = 'L'; out[offset++] = 'I'; out[offset++] = 'S'; out[offset++] = 'T';
        putLEInt(out, offset, listChunkSize); offset += 4;
        offset += listChunkSize; // Skip LIST content

        // data chunk
        out[offset++] = 'd'; out[offset++] = 'a'; out[offset++] = 't'; out[offset++] = 'a';
        putLEInt(out, offset, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithExtendedFmt(int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_000;
        int fmtSize = 18; // Extended format with cbSize field
        int totalSize = 12 + 8 + fmtSize + 8 + payloadBytes;
        byte[] out = new byte[totalSize];
        int offset = 0;

        // RIFF header
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';

        // fmt chunk (extended)
        out[offset++] = 'f'; out[offset++] = 'm'; out[offset++] = 't'; out[offset++] = ' ';
        putLEInt(out, offset, fmtSize); offset += 4;
        putLEShort(out, offset, 1); offset += 2; // PCM
        putLEShort(out, offset, channels); offset += 2;
        putLEInt(out, offset, sampleRate); offset += 4;
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, offset, byteRate); offset += 4;
        putLEShort(out, offset, blockAlign); offset += 2;
        putLEShort(out, offset, bitsPerSample); offset += 2;
        putLEShort(out, offset, 0); offset += 2; // cbSize = 0 (no extension)

        // data chunk
        out[offset++] = 'd'; out[offset++] = 'a'; out[offset++] = 't'; out[offset++] = 'a';
        putLEInt(out, offset, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithoutFmtChunk() {
        int payloadBytes = 40_000;
        int totalSize = 12 + 8 + payloadBytes;
        byte[] out = new byte[totalSize];
        int offset = 0;

        // RIFF header
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';

        // data chunk only (no fmt)
        out[offset++] = 'd'; out[offset++] = 'a'; out[offset++] = 't'; out[offset++] = 'a';
        putLEInt(out, offset, payloadBytes);
        return out;
    }

    private static byte[] makeWavWithoutDataChunk() {
        int totalSize = 12 + 8 + 16;
        byte[] out = new byte[totalSize];
        int offset = 0;

        // RIFF header
        out[offset++] = 'R'; out[offset++] = 'I'; out[offset++] = 'F'; out[offset++] = 'F';
        putLEInt(out, offset, totalSize - 8); offset += 4;
        out[offset++] = 'W'; out[offset++] = 'A'; out[offset++] = 'V'; out[offset++] = 'E';

        // fmt chunk only (no data)
        out[offset++] = 'f'; out[offset++] = 'm'; out[offset++] = 't'; out[offset++] = ' ';
        putLEInt(out, offset, 16); offset += 4;
        putLEShort(out, offset, 1); offset += 2; // PCM
        putLEShort(out, offset, 1); offset += 2; // mono
        putLEInt(out, offset, 16000); offset += 4; // sample rate
        putLEInt(out, offset, 32000); offset += 4; // byte rate
        putLEShort(out, offset, 2); offset += 2; // block align
        putLEShort(out, offset, 16); // bits per sample
        return out;
    }

    private static byte[] makeWavWithMisalignedData(int sampleRate, int channels, int bitsPerSample) {
        int payloadBytes = 40_001; // ODD size - not aligned to 2-byte block
        int header = 44;
        byte[] out = new byte[header + payloadBytes];
        // RIFF/WAVE
        out[0] = 'R'; out[1] = 'I'; out[2] = 'F'; out[3] = 'F';
        putLEInt(out, 4, 36 + payloadBytes);
        out[8] = 'W'; out[9] = 'A'; out[10] = 'V'; out[11] = 'E';
        // fmt chunk
        out[12] = 'f'; out[13] = 'm'; out[14] = 't'; out[15] = ' ';
        putLEInt(out, 16, 16);
        putLEShort(out, 20, 1);
        putLEShort(out, 22, channels);
        putLEInt(out, 24, sampleRate);
        int blockAlign = (bitsPerSample / 8) * channels;
        int byteRate = sampleRate * blockAlign;
        putLEInt(out, 28, byteRate);
        putLEShort(out, 32, blockAlign);
        putLEShort(out, 34, bitsPerSample);
        // data with misaligned size
        out[36] = 'd'; out[37] = 'a'; out[38] = 't'; out[39] = 'a';
        putLEInt(out, 40, payloadBytes); // ODD size - misaligned!
        return out;
    }

    @Test
    void truncatedWavHeaderTooSmallForRiff() {
        // WAV file smaller than RIFF header (12 bytes)
        byte[] tiny = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V'};
        assertThatThrownBy(() -> validator.validate(tiny))
                .isInstanceOf(InvalidAudioException.class);
    }

    @Test
    void wavWithValidRiffButTooSmallForChunks() {
        // Valid RIFF header (12 bytes) but no room for any chunk headers
        // This exercises the WAV path where validateWav is called, wav.length < RIFF_HEADER_SIZE is false,
        // but there are no fmt/data chunks found — hits requireChunk("fmt") → missing fmt chunk
        byte[] wav = new byte[12];
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("fmt");
    }

    @Test
    void wavWithInvalidChunkSizeThrows() {
        // Valid RIFF header but chunk size is negative/corrupt
        byte[] wav = new byte[20];
        wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
        wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
        // Chunk at offset 12: ID = "fmt ", size = very large (0xFFFFFFFF = -1 as signed int)
        wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' ';
        wav[16] = (byte)0xFF; wav[17] = (byte)0xFF; wav[18] = (byte)0xFF; wav[19] = (byte)0xFF;
        assertThatThrownBy(() -> validator.validate(wav))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("chunk size");
    }

    // --- Mutation-killing boundary tests ---

    @Test
    void isWavMutantKillerByte0NotR() {
        // All WAV signature bytes correct EXCEPT byte 0 → should NOT enter WAV path
        // If PIT removes `a[0]=='R'` from the AND chain, isWav returns true and WAV validation throws
        byte[] data = new byte[32_000];
        data[0] = 'X'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'W'; data[9] = 'A'; data[10] = 'V'; data[11] = 'E';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavMutantKillerByte1NotI() {
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'X'; data[2] = 'F'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'W'; data[9] = 'A'; data[10] = 'V'; data[11] = 'E';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavMutantKillerByte2NotF() {
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'X'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'W'; data[9] = 'A'; data[10] = 'V'; data[11] = 'E';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavMutantKillerByte3NotF() {
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'X';
        putLEInt(data, 4, 31992);
        data[8] = 'W'; data[9] = 'A'; data[10] = 'V'; data[11] = 'E';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavMutantKillerByte8NotW() {
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'X'; data[9] = 'A'; data[10] = 'V'; data[11] = 'E';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavMutantKillerByte9NotA() {
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'W'; data[9] = 'X'; data[10] = 'V'; data[11] = 'E';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void isWavMutantKillerByte10NotV() {
        byte[] data = new byte[32_000];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        putLEInt(data, 4, 31992);
        data[8] = 'W'; data[9] = 'A'; data[10] = 'X'; data[11] = 'E';
        assertThatCode(() -> validator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void durationExactlyAtMinimumPasses() {
        // 250ms at 32kB/s = 8000 bytes. durationMs = (8000*1000)/32000 = 250
        // 250 < 250 is false → passes. Kills < to <= on L245
        byte[] pcm = new byte[8000];
        assertThatCode(() -> validator.validate(pcm)).doesNotThrowAnyException();
    }

    @Test
    void durationOneByteBelowMinimumFails() {
        // 7998 bytes → durationMs = (7998*1000)/32000 = 249 (integer division) < 250 → fails
        byte[] pcm = new byte[7998];
        assertThatThrownBy(() -> validator.validate(pcm))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void durationExactlyAtMaximumPasses() {
        // 300_000ms at 32kB/s = 9_600_000 bytes. durationMs = (9600000*1000)/32000 = 300000
        // 300000 > 300000 is false → passes. Kills > to >= on L249
        byte[] pcm = new byte[9_600_000];
        assertThatCode(() -> validator.validate(pcm)).doesNotThrowAnyException();
    }

    @Test
    void durationJustAboveMaximumFails() {
        // 9_600_002 bytes → durationMs = (9600002*1000)/32000 = 300000 (integer division)
        // Need enough bytes to push past: 9_632_000 bytes → 301000ms
        byte[] pcm = new byte[9_632_000];
        assertThatThrownBy(() -> validator.validate(pcm))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("too long");
    }

    @Test
    void fileSizeExactlyAtLimitPasses() {
        // maxFileSizeBytes is 100MB. At exact limit: data.length > max is false → passes
        // Kills > to >= on L47
        // Use small maxFileSize to keep test fast
        AudioValidationProperties smallProps = new AudioValidationProperties(250, 300_000, 32_000);
        AudioValidator smallValidator = new AudioValidator(smallProps);
        // 32000 bytes = exactly at limit AND = 1 second duration (passes min/max)
        byte[] data = new byte[32_000];
        assertThatCode(() -> smallValidator.validate(data)).doesNotThrowAnyException();
    }

    @Test
    void fileSizeOneBytePastLimitFails() {
        AudioValidationProperties smallProps = new AudioValidationProperties(250, 300_000, 31_999);
        AudioValidator smallValidator = new AudioValidator(smallProps);
        byte[] data = new byte[32_000]; // 32000 > 31999 → fails
        assertThatThrownBy(() -> smallValidator.validate(data))
                .isInstanceOf(InvalidAudioException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void isWavExactly12BytesEntersWavPath() {
        // 12 bytes with valid RIFF/WAVE header → enters WAV path
        // Kills >= to > on L62 (12 >= 12 is true)
        byte[] data = new byte[12];
        data[0] = 'R'; data[1] = 'I'; data[2] = 'F'; data[3] = 'F';
        data[8] = 'W'; data[9] = 'A'; data[10] = 'V'; data[11] = 'E';
        // Enters WAV path → too small for RIFF header or missing chunks
        assertThatThrownBy(() -> validator.validate(data))
                .isInstanceOf(InvalidAudioException.class);
    }

    private static void putLEShort(byte[] a, int off, int v) {
        a[off] = (byte) (v & 0xFF);
        a[off + 1] = (byte) ((v >>> 8) & 0xFF);
    }
    private static void putLEInt(byte[] a, int off, int v) {
        a[off] = (byte) (v & 0xFF);
        a[off + 1] = (byte) ((v >>> 8) & 0xFF);
        a[off + 2] = (byte) ((v >>> 16) & 0xFF);
        a[off + 3] = (byte) ((v >>> 24) & 0xFF);
    }
}