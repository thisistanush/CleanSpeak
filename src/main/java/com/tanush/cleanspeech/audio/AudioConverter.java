package com.tanush.cleanspeech.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Converts MP3 files to WAV format using ffmpeg.
 * The output WAV is optimized for speech recognition: 16kHz, mono, 16-bit.
 */
public class AudioConverter {

    // Sample rate for speech recognition (Whisper works best at 16kHz)
    private static final int TARGET_SAMPLE_RATE = 16000;

    // Number of audio channels (mono for speech)
    private static final int TARGET_CHANNELS = 1;

    /**
     * Converts an MP3 file to WAV format suitable for speech recognition.
     *
     * @param mp3Path   Path to the input MP3 file
     * @param outputDir Directory where the WAV file will be created
     * @return Path to the created WAV file
     * @throws IOException if ffmpeg fails or file operations fail
     */
    public Path convertMp3ToWav(Path mp3Path, Path outputDir) throws IOException {
        // Validate input file exists
        if (!Files.exists(mp3Path)) {
            throw new IOException("Input MP3 file does not exist: " + mp3Path);
        }

        // Create output directory if it doesn't exist
        Files.createDirectories(outputDir);

        // Generate output file path
        String baseName = getBaseName(mp3Path.getFileName().toString());
        Path outputWav = outputDir.resolve(baseName + "_raw.wav");

        // Build ffmpeg command
        // -y: overwrite output without asking
        // -i: input file
        // -ar: audio sample rate
        // -ac: audio channels
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", mp3Path.toString(),
                "-ar", String.valueOf(TARGET_SAMPLE_RATE),
                "-ac", String.valueOf(TARGET_CHANNELS),
                outputWav.toString());

        // Redirect error stream to standard output for easier debugging
        processBuilder.redirectErrorStream(true);

        // Execute ffmpeg
        Process process = processBuilder.start();

        // Consume ffmpeg output to prevent process blocking
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // Consume output silently
            }
        }

        // Wait for process to complete
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg process was interrupted", e);
        }

        // Check for successful completion
        if (exitCode != 0) {
            throw new IOException("ffmpeg failed with exit code: " + exitCode);
        }

        // Verify output file was created
        if (!Files.exists(outputWav)) {
            throw new IOException("ffmpeg did not create output file: " + outputWav);
        }

        return outputWav;
    }

    /**
     * Converts a WAV file back to MP3 format.
     *
     * @param wavPath   Path to the input WAV file
     * @param outputMp3 Path where the MP3 file will be created
     * @return Path to the created MP3 file
     * @throws IOException if ffmpeg fails
     */
    public Path convertWavToMp3(Path wavPath, Path outputMp3) throws IOException {
        // Validate input file exists
        if (!Files.exists(wavPath)) {
            throw new IOException("Input WAV file does not exist: " + wavPath);
        }

        // Create parent directory if needed
        if (outputMp3.getParent() != null) {
            Files.createDirectories(outputMp3.getParent());
        }

        // Build ffmpeg command for MP3 encoding
        // -b:a 192k sets audio bitrate to 192 kbps for good quality
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", wavPath.toString(),
                "-b:a", "192k",
                outputMp3.toString());

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Consume output to prevent blocking
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            while (reader.readLine() != null) {
                // Just consume the output
            }
        }

        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("ffmpeg process was interrupted", e);
        }

        if (exitCode != 0) {
            throw new IOException("ffmpeg MP3 encoding failed with exit code: " + exitCode);
        }

        return outputMp3;
    }

    /**
     * Extracts the base name from a filename (removes extension).
     */
    private String getBaseName(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0) {
            return filename.substring(0, dotIndex);
        }
        return filename;
    }
}
