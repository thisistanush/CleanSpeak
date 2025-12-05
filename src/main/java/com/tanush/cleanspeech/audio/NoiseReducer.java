package com.tanush.cleanspeech.audio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reduces background noise from audio files using ffmpeg's afftdn filter.
 * This uses FFT-based noise reduction which works well for consistent
 * background noise.
 */
public class NoiseReducer {

    /**
     * Applies noise reduction to a WAV file.
     *
     * @param inputWav  Path to the input WAV file
     * @param outputDir Directory where the denoised file will be created
     * @return Path to the denoised WAV file
     * @throws IOException if ffmpeg fails or file operations fail
     */
    public Path reduceNoise(Path inputWav, Path outputDir) throws IOException {
        // Validate input file exists
        if (!Files.exists(inputWav)) {
            throw new IOException("Input WAV file does not exist: " + inputWav);
        }

        // Create output directory if it doesn't exist
        Files.createDirectories(outputDir);

        // Generate output file path
        String baseName = getBaseName(inputWav.getFileName().toString());
        Path outputWav = outputDir.resolve(baseName + "_denoised.wav");

        // Build ffmpeg command with afftdn (FFT-based Denoiser) filter
        // afftdn is a high-quality noise reduction filter
        // nr: noise reduction amount in dB
        // nf: noise floor in dB
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", inputWav.toString(),
                "-af", "afftdn=nf=-25",
                outputWav.toString());

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Consume ffmpeg output
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
            throw new IOException("ffmpeg noise reduction failed with exit code: " + exitCode);
        }

        // Verify output file was created
        if (!Files.exists(outputWav)) {
            throw new IOException("ffmpeg did not create output file: " + outputWav);
        }

        return outputWav;
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
