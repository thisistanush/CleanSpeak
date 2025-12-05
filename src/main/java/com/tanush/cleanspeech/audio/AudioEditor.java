package com.tanush.cleanspeech.audio;

import com.tanush.cleanspeech.model.EditPlan;
import com.tanush.cleanspeech.model.EditSegment;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Edits audio files by removing segments and shortening pauses.
 * Uses javax.sound.sampled for WAV manipulation and applies crossfades
 * at cut points to avoid audible clicks.
 */
public class AudioEditor {

    // Duration of crossfade in seconds (30ms = 0.03s) for smoother cuts
    private static final double CROSSFADE_DURATION_SEC = 0.03;

    // Target duration for shortened pauses (in seconds)
    private static final double TARGET_PAUSE_DURATION_SEC = 0.5;

    /**
     * Applies the edit plan to an audio file, removing segments and shortening
     * pauses.
     *
     * @param inputWav  Path to the input WAV file
     * @param editPlan  The edit plan specifying what to remove/shorten
     * @param outputDir Directory where the edited file will be created
     * @return Path to the edited WAV file
     * @throws IOException if file operations fail
     */
    public Path applyEditPlan(Path inputWav, EditPlan editPlan, Path outputDir) throws IOException {

        // Load audio data from WAV file
        AudioData audioData = loadWavFile(inputWav);

        // Combine all edit operations and sort by start time
        List<EditOperation> operations = buildEditOperations(editPlan);

        // Apply edits to audio samples
        float[] editedSamples = applyEdits(audioData.samples, audioData.sampleRate, operations);

        // Save the edited audio
        Files.createDirectories(outputDir);
        String baseName = getBaseName(inputWav.getFileName().toString());
        Path outputWav = outputDir.resolve(baseName + "_cleaned.wav");

        saveWavFile(editedSamples, audioData.sampleRate, outputWav);

        return outputWav;
    }

    /**
     * Loads a WAV file and returns the audio samples as floats.
     */
    private AudioData loadWavFile(Path wavPath) throws IOException {
        try {
            File file = wavPath.toFile();
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioStream.getFormat();

            // Ensure we have a format we can work with
            if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
                throw new IOException("Unsupported audio encoding: " + format.getEncoding());
            }

            int sampleRate = (int) format.getSampleRate();
            int bytesPerSample = format.getSampleSizeInBits() / 8;
            int channels = format.getChannels();

            // Read all bytes
            byte[] audioBytes = audioStream.readAllBytes();
            audioStream.close();

            // Convert bytes to float samples (assuming mono or taking first channel)
            int totalSamples = audioBytes.length / (bytesPerSample * channels);
            float[] samples = new float[totalSamples];

            ByteBuffer buffer = ByteBuffer.wrap(audioBytes);
            buffer.order(format.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < totalSamples; i++) {
                if (bytesPerSample == 2) {
                    // 16-bit audio
                    short sample = buffer.getShort();
                    samples[i] = sample / 32768.0f; // Normalize to -1.0 to 1.0
                    // Skip other channels if present
                    for (int c = 1; c < channels; c++) {
                        buffer.getShort();
                    }
                } else if (bytesPerSample == 1) {
                    // 8-bit audio
                    byte sample = buffer.get();
                    samples[i] = (sample - 128) / 128.0f;
                    for (int c = 1; c < channels; c++) {
                        buffer.get();
                    }
                } else {
                    throw new IOException("Unsupported sample size: " + bytesPerSample + " bytes");
                }
            }

            return new AudioData(samples, sampleRate);

        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio file format: " + wavPath, e);
        }
    }

    /**
     * Saves float samples to a WAV file.
     */
    private void saveWavFile(float[] samples, int sampleRate, Path outputPath) throws IOException {
        // Convert float samples back to 16-bit PCM
        byte[] audioBytes = new byte[samples.length * 2];
        ByteBuffer buffer = ByteBuffer.wrap(audioBytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        for (float sample : samples) {
            // Clamp to valid range
            float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
            short shortSample = (short) (clamped * 32767);
            buffer.putShort(shortSample);
        }

        // Create audio format (16-bit, mono, signed, little-endian)
        AudioFormat format = new AudioFormat(
                sampleRate, // sample rate
                16, // sample size in bits
                1, // channels (mono)
                true, // signed
                false // little-endian
        );

        // Create audio input stream from bytes
        ByteArrayInputStream byteStream = new ByteArrayInputStream(audioBytes);
        AudioInputStream audioStream = new AudioInputStream(
                byteStream,
                format,
                samples.length);

        // Write to file
        AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, outputPath.toFile());
        audioStream.close();
    }

    /**
     * Builds a sorted list of edit operations from the edit plan.
     */
    private List<EditOperation> buildEditOperations(EditPlan editPlan) {
        List<EditOperation> operations = new ArrayList<>();

        // Add removal operations
        for (EditSegment segment : editPlan.getSegmentsToRemove()) {
            operations.add(new EditOperation(
                    segment.getStartTimeSec(),
                    segment.getEndTimeSec(),
                    EditOperationType.REMOVE,
                    segment.getReason()));
        }

        // Add pause shortening operations
        for (EditSegment pause : editPlan.getPausesToShorten()) {
            operations.add(new EditOperation(
                    pause.getStartTimeSec(),
                    pause.getEndTimeSec(),
                    EditOperationType.SHORTEN_PAUSE,
                    pause.getReason()));
        }

        // Sort by start time
        operations.sort(Comparator.comparingDouble(op -> op.startSec));

        return operations;
    }

    /**
     * Applies all edit operations to the audio samples.
     */
    private float[] applyEdits(float[] originalSamples, int sampleRate, List<EditOperation> operations) {
        if (operations.isEmpty()) {
            return originalSamples.clone();
        }

        List<float[]> segments = new ArrayList<>();
        int currentSample = 0;

        for (EditOperation op : operations) {
            int opStartSample = (int) (op.startSec * sampleRate);
            int opEndSample = (int) (op.endSec * sampleRate);

            // Clamp to valid range
            opStartSample = Math.max(0, Math.min(opStartSample, originalSamples.length));
            opEndSample = Math.max(0, Math.min(opEndSample, originalSamples.length));

            // Add audio before this operation
            if (opStartSample > currentSample) {
                float[] beforeSegment = new float[opStartSample - currentSample];
                System.arraycopy(originalSamples, currentSample, beforeSegment, 0, beforeSegment.length);
                segments.add(beforeSegment);
            }

            // Handle the operation
            if (op.type == EditOperationType.REMOVE) {
                // Skip this segment entirely
                currentSample = opEndSample;
            } else if (op.type == EditOperationType.SHORTEN_PAUSE) {
                // Parse new_duration from reason (format: "LONG_PAUSE|0.5")
                double targetDuration = TARGET_PAUSE_DURATION_SEC;
                if (op.reason != null && op.reason.contains("|")) {
                    try {
                        String durationStr = op.reason.substring(op.reason.lastIndexOf("|") + 1);
                        targetDuration = Double.parseDouble(durationStr);
                    } catch (NumberFormatException e) {
                        // Use default if parsing fails
                    }
                }

                // Calculate how much to keep from start and end of the pause
                // We want to keep 'targetDuration' total.
                // So we keep targetDuration/2 from the start, and targetDuration/2 from the
                // end.
                // And we remove the middle part.

                double pauseDuration = op.endSec - op.startSec;

                if (pauseDuration > targetDuration) {
                    double keepEachSide = targetDuration / 2.0;

                    // 1. Add the start of the pause (room tone)
                    int keepStartSamples = (int) (keepEachSide * sampleRate);
                    int pauseStartSample = opStartSample;

                    if (pauseStartSample + keepStartSamples > originalSamples.length) {
                        keepStartSamples = originalSamples.length - pauseStartSample;
                    }

                    if (keepStartSamples > 0) {
                        float[] startPause = new float[keepStartSamples];
                        System.arraycopy(originalSamples, pauseStartSample, startPause, 0, keepStartSamples);
                        segments.add(startPause);
                    }

                    // 2. Skip the middle part (this is the actual shortening)
                    // We skip from (start + keep) to (end - keep)
                    // But effectively we just set currentSample to (end - keep)
                    // because we already added the start part.

                    int keepEndSamples = (int) (keepEachSide * sampleRate);
                    int pauseEndSample = opEndSample;
                    int resumeSample = pauseEndSample - keepEndSamples;

                    // Ensure we don't go backwards or overlap weirdly
                    if (resumeSample < pauseStartSample + keepStartSamples) {
                        resumeSample = pauseStartSample + keepStartSamples;
                    }

                    currentSample = resumeSample;

                } else {
                    // Pause is already shorter than target, keep it unchanged
                    int gapSamples = opEndSample - opStartSample;
                    if (gapSamples > 0) {
                        float[] gap = new float[gapSamples];
                        System.arraycopy(originalSamples, opStartSample, gap, 0, gapSamples);
                        segments.add(gap);
                    }
                    currentSample = opEndSample;
                }
            }
        }

        // Add remaining audio after last operation
        if (currentSample < originalSamples.length) {
            float[] remainder = new float[originalSamples.length - currentSample];
            System.arraycopy(originalSamples, currentSample, remainder, 0, remainder.length);
            segments.add(remainder);
        }

        // Concatenate all segments with crossfades
        float[] stitchedSamples = concatenateWithCrossfades(segments, sampleRate);

        // Apply voice leveling for consistent volume
        VoiceLeveler leveler = new VoiceLeveler();
        return leveler.levelAudio(stitchedSamples, sampleRate);
    }

    /**
     * Concatenates audio segments with crossfades at join points.
     */
    private float[] concatenateWithCrossfades(List<float[]> segments, int sampleRate) {
        if (segments.isEmpty()) {
            return new float[0];
        }

        if (segments.size() == 1) {
            return segments.get(0);
        }

        int crossfadeSamples = (int) (CROSSFADE_DURATION_SEC * sampleRate);

        // Calculate total length (accounting for crossfade overlaps)
        int totalLength = 0;
        for (int i = 0; i < segments.size(); i++) {
            totalLength = totalLength + segments.get(i).length;
            if (i > 0) {
                // Subtract overlap for crossfade
                totalLength = totalLength - Math.min(crossfadeSamples, segments.get(i).length / 2);
            }
        }

        float[] result = new float[totalLength];
        int writePos = 0;

        for (int i = 0; i < segments.size(); i++) {
            float[] segment = segments.get(i);

            if (i == 0) {
                // First segment: just copy
                System.arraycopy(segment, 0, result, writePos, segment.length);
                writePos = writePos + segment.length;
            } else {
                // Apply crossfade with previous segment
                int fadeLen = Math.min(crossfadeSamples, Math.min(segment.length, writePos));
                int fadeStart = writePos - fadeLen;

                // Apply fade-out to end of previous audio and fade-in to start of new audio
                // Using Constant Power Crossfade (Sine/Cosine) for smoother transitions
                for (int j = 0; j < fadeLen; j++) {
                    double progress = j / (double) fadeLen;

                    // Linear:
                    // float fadeOutRatio = 1.0f - progress;
                    // float fadeInRatio = progress;

                    // Constant Power (smoother):
                    float fadeOutRatio = (float) Math.cos(progress * 0.5 * Math.PI);
                    float fadeInRatio = (float) Math.sin(progress * 0.5 * Math.PI);

                    result[fadeStart + j] = result[fadeStart + j] * fadeOutRatio + segment[j] * fadeInRatio;
                }

                // Copy rest of segment after the crossfade portion
                if (segment.length > fadeLen) {
                    System.arraycopy(segment, fadeLen, result, writePos, segment.length - fadeLen);
                    writePos = writePos + segment.length - fadeLen;
                }
            }
        }

        // Trim to actual length used
        if (writePos < result.length) {
            float[] trimmed = new float[writePos];
            System.arraycopy(result, 0, trimmed, 0, writePos);
            return trimmed;
        }

        return result;
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

    // Helper class to hold audio data
    private static class AudioData {
        final float[] samples;
        final int sampleRate;

        AudioData(float[] samples, int sampleRate) {
            this.samples = samples;
            this.sampleRate = sampleRate;
        }
    }

    // Enum for edit operation types
    private enum EditOperationType {
        REMOVE,
        SHORTEN_PAUSE
    }

    // Helper class for edit operations
    private static class EditOperation {
        final double startSec;
        final double endSec;
        final EditOperationType type;
        final String reason;

        EditOperation(double startSec, double endSec, EditOperationType type, String reason) {
            this.startSec = startSec;
            this.endSec = endSec;
            this.type = type;
            this.reason = reason;
        }
    }
}
