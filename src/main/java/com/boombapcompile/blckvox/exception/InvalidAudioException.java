package com.boombapcompile.blckvox.exception;

/**
 * Thrown when audio data is invalid or does not match the required format
 * (16kHz, 16-bit signed PCM, mono, little-endian) or violates duration bounds.
 */
public class InvalidAudioException extends BlckvoxException {

    public InvalidAudioException(String reason) {
        super("Invalid audio data: " + reason);
    }

    public InvalidAudioException(int audioSize, String reason) {
        super("Invalid audio data (" + audioSize + " bytes): " + reason);
    }
}
