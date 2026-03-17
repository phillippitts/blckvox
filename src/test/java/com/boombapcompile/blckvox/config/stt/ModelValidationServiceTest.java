package com.boombapcompile.blckvox.config.stt;

import com.boombapcompile.blckvox.exception.ModelNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelValidationServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldFailWhenWhisperModelMissing() {
        WhisperConfig whisper = new WhisperConfig("/bin/echo", "/tmp/does-not-exist.bin", 10, "en", 2, 1048576, 0.85);
        VoskConfig vosk = new VoskConfig("models/vosk-model-en-us-0.22", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("Whisper model not found");
    }

    @Test
    void shouldFailWhenWhisperModelTooSmall() throws Exception {
        Path tmpModel = tempDir.resolve("ggml-tiny.bin");
        Files.write(tmpModel, new byte[1024]); // Only 1KB - below 100MB threshold

        Path tmpBin = tempDir.resolve("whisper");
        Files.createFile(tmpBin);
        tmpBin.toFile().setExecutable(true);

        WhisperConfig whisper = new WhisperConfig(tmpBin.toString(), tmpModel.toString(), 10, "en", 2, 1048576, 0.85);
        VoskConfig vosk = new VoskConfig("models/vosk-model-en-us-0.22", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("too small");
    }

    @Test
    void shouldFailWhenWhisperBinaryNotExecutable() throws Exception {
        Path tmpModel = tempDir.resolve("ggml-base.en.bin");
        Files.write(tmpModel, new byte[101 * 1024 * 1024]); // 101MB

        Path tmpBin = tempDir.resolve("whisper");
        Files.createFile(tmpBin);
        tmpBin.toFile().setExecutable(false);

        WhisperConfig whisper = new WhisperConfig(tmpBin.toString(), tmpModel.toString(), 10, "en", 2, 1048576, 0.85);
        VoskConfig vosk = new VoskConfig("models/vosk-model-en-us-0.22", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("not executable");
    }

    @Test
    void shouldFailWhenWhisperBinaryIsDirectory() throws Exception {
        Path tmpModel = tempDir.resolve("ggml-base.en.bin");
        Files.write(tmpModel, new byte[101 * 1024 * 1024]); // 101MB

        Path tmpDir = tempDir.resolve("whisper-dir");
        Files.createDirectory(tmpDir);

        WhisperConfig whisper = new WhisperConfig(tmpDir.toString(), tmpModel.toString(), 10, "en", 2, 1048576, 0.85);
        VoskConfig vosk = new VoskConfig("models/vosk-model-en-us-0.22", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("not a regular file");
    }

    @Test
    void shouldFailWhenVoskModelDirectoryMissing() {
        Path nonExistentPath = Path.of("/tmp/does-not-exist-vosk");
        VoskConfig vosk = new VoskConfig(nonExistentPath.toString(), 16000, 1);
        WhisperConfig whisper = new WhisperConfig();
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(() -> svc.validateVoskDirectoryStructure(nonExistentPath))
            .isInstanceOf(ModelNotFoundException.class);
    }

    @Test
    void shouldFailWhenVoskModelMissingSubdirectories() throws Exception {
        Path voskDir = tempDir.resolve("vosk-model");
        Files.createDirectory(voskDir);
        // Missing 'am' and 'conf' subdirectories

        VoskConfig vosk = new VoskConfig(voskDir.toString(), 16000, 1);
        WhisperConfig whisper = new WhisperConfig();
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(() -> svc.validateVoskDirectoryStructure(voskDir))
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("Missing expected Vosk model subdirectories");
    }

    @Test
    void shouldPassWhisperValidationWithValidModelAndBinary() throws Exception {
        // Create a sparse model file > 100MB
        Path model = tempDir.resolve("ggml-base.en.bin");
        try (RandomAccessFile raf = new RandomAccessFile(model.toFile(), "rw")) {
            raf.setLength(SttModelConstants.MIN_WHISPER_MODEL_SIZE_BYTES + 1024);
        }

        Path binary = tempDir.resolve("whisper");
        Files.createFile(binary);
        binary.toFile().setExecutable(true);

        WhisperConfig whisper = new WhisperConfig(
                binary.toString(), model.toString(), 10, "en", 2, 1048576, 0.85);
        VoskConfig vosk = new VoskConfig("models/vosk", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatCode(svc::validateWhisper).doesNotThrowAnyException();
    }

    @Test
    void shouldFailWhenWhisperBinaryMissing() throws Exception {
        Path model = tempDir.resolve("ggml-base.en.bin");
        try (RandomAccessFile raf = new RandomAccessFile(model.toFile(), "rw")) {
            raf.setLength(SttModelConstants.MIN_WHISPER_MODEL_SIZE_BYTES + 1024);
        }

        WhisperConfig whisper = new WhisperConfig(
                "/tmp/nonexistent-binary-xyz", model.toString(), 10, "en", 2, 1048576, 0.85);
        VoskConfig vosk = new VoskConfig("models/vosk", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void shouldFailWhenWhisperModelIsDirectory() throws Exception {
        Path modelDir = tempDir.resolve("model-dir");
        Files.createDirectory(modelDir);

        Path binary = tempDir.resolve("whisper");
        Files.createFile(binary);
        binary.toFile().setExecutable(true);

        WhisperConfig whisper = new WhisperConfig(
                binary.toString(), modelDir.toString(), 10, "en", 2, 1048576, 0.85);
        VoskConfig vosk = new VoskConfig("models/vosk", 16000, 1);
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatThrownBy(svc::validateWhisper)
            .isInstanceOf(ModelNotFoundException.class)
            .hasMessageContaining("not a regular file");
    }

    @Test
    void shouldPassVoskDirectoryStructureWithValidDirs() throws Exception {
        Path voskDir = tempDir.resolve("vosk-model-valid");
        Files.createDirectories(voskDir.resolve("am"));
        Files.createDirectories(voskDir.resolve("conf"));

        VoskConfig vosk = new VoskConfig(voskDir.toString(), 16000, 1);
        WhisperConfig whisper = new WhisperConfig();
        ModelValidationService svc = new ModelValidationService(vosk, whisper);

        assertThatCode(() -> svc.validateVoskDirectoryStructure(voskDir))
            .doesNotThrowAnyException();
    }
}
