package com.tanush.cleanspeech.audio;

/**
 * Automatic voice leveling and normalization.
 * Analyzes audio loudness and applies gentle gain adjustments to create
 * a more consistent, podcast-like audio level. Quiet parts are boosted
 * and loud parts are softened, without clipping or pumping artifacts.
 */
public class VoiceLeveler {

    // Target loudness in dBFS (decibels relative to full scale)
    // -13 dBFS is a common target for podcasts and voice recordings
    private static final double TARGET_LOUDNESS_DBFS = -13.0;

    // Maximum gain adjustment per segment (in dB)
    // Limits: -4 dB to +4 dB to avoid aggressive leveling
    private static final double MAX_GAIN_DB = 4.0;

    // Window size for RMS analysis (in seconds)
    private static final double WINDOW_SIZE_SEC = 0.5;

    // Minimum RMS to consider (avoids boosting silence to infinity)
    private static final double MIN_RMS_THRESHOLD = 0.001;

    // Headroom to prevent clipping (in linear scale)
    private static final float HEADROOM = 0.95f;

    /**
     * Applies voice leveling to the audio samples.
     * Analyzes loudness over short windows and applies gentle gain adjustments.
     *
     * @param samples    Audio samples in range [-1.0, 1.0]
     * @param sampleRate Sample rate in Hz
     * @return Leveled audio samples
     */
    public float[] levelAudio(float[] samples, int sampleRate) {
        if (samples == null || samples.length == 0) {
            return samples;
        }

        int windowSamples = (int) (WINDOW_SIZE_SEC * sampleRate);
        int numWindows = (samples.length + windowSamples - 1) / windowSamples;

        // Calculate RMS and target gain for each window
        double[] windowGains = new double[numWindows];

        for (int w = 0; w < numWindows; w++) {
            int start = w * windowSamples;
            int end = Math.min(start + windowSamples, samples.length);

            double rms = calculateRms(samples, start, end);
            windowGains[w] = calculateGain(rms);
        }

        // Smooth the gain curve to avoid sudden jumps
        double[] smoothedGains = smoothGainCurve(windowGains);

        // Apply gains to samples with interpolation
        float[] leveled = new float[samples.length];

        for (int i = 0; i < samples.length; i++) {
            // Find which window this sample is in
            int windowIndex = i / windowSamples;
            int nextWindowIndex = Math.min(windowIndex + 1, numWindows - 1);

            // Position within current window (0.0 to 1.0)
            double positionInWindow = (i % windowSamples) / (double) windowSamples;

            // Interpolate gain between current and next window
            double gain = smoothedGains[windowIndex] * (1.0 - positionInWindow)
                    + smoothedGains[nextWindowIndex] * positionInWindow;

            // Apply gain
            leveled[i] = (float) (samples[i] * gain);
        }

        // Apply limiter to prevent clipping
        applyLimiter(leveled);

        return leveled;
    }

    /**
     * Calculates RMS (Root Mean Square) for a range of samples.
     * RMS is a measure of audio loudness.
     */
    private double calculateRms(float[] samples, int start, int end) {
        double sumSquares = 0.0;
        int count = end - start;

        for (int i = start; i < end; i++) {
            sumSquares = sumSquares + (samples[i] * samples[i]);
        }

        if (count > 0) {
            return Math.sqrt(sumSquares / count);
        }
        return 0.0;
    }

    /**
     * Calculates the gain needed to reach target loudness.
     * Gain is limited to ±MAX_GAIN_DB to avoid aggressive adjustments.
     */
    private double calculateGain(double rms) {
        // Don't boost very quiet segments (likely silence)
        if (rms < MIN_RMS_THRESHOLD) {
            return 1.0;
        }

        // Convert RMS to dBFS
        double rmsDbfs = linearToDb(rms);

        // Calculate needed gain
        double neededGainDb = TARGET_LOUDNESS_DBFS - rmsDbfs;

        // Clamp to maximum allowed adjustment
        if (neededGainDb > MAX_GAIN_DB) {
            neededGainDb = MAX_GAIN_DB;
        }
        if (neededGainDb < -MAX_GAIN_DB) {
            neededGainDb = -MAX_GAIN_DB;
        }

        // Convert back to linear gain
        return dbToLinear(neededGainDb);
    }

    /**
     * Smooths the gain curve to avoid sudden jumps between windows.
     * Uses a simple moving average filter.
     */
    private double[] smoothGainCurve(double[] gains) {
        if (gains.length <= 2) {
            return gains.clone();
        }

        double[] smoothed = new double[gains.length];

        // First and last use smaller window
        smoothed[0] = (gains[0] + gains[1]) / 2.0;
        smoothed[gains.length - 1] = (gains[gains.length - 2] + gains[gains.length - 1]) / 2.0;

        // Middle values use 3-point average
        for (int i = 1; i < gains.length - 1; i++) {
            smoothed[i] = (gains[i - 1] + gains[i] + gains[i + 1]) / 3.0;
        }

        return smoothed;
    }

    /**
     * Applies a soft limiter to prevent clipping.
     * Any samples that would exceed headroom are gently compressed.
     */
    private void applyLimiter(float[] samples) {
        for (int i = 0; i < samples.length; i++) {
            if (samples[i] > HEADROOM) {
                // Soft clip using tanh-like curve
                samples[i] = HEADROOM + (1.0f - HEADROOM) * softClip(samples[i] - HEADROOM);
            } else if (samples[i] < -HEADROOM) {
                samples[i] = -HEADROOM - (1.0f - HEADROOM) * softClip(-samples[i] - HEADROOM);
            }
        }
    }

    /**
     * Soft clipping function for gentle limiting.
     */
    private float softClip(float x) {
        // Simple tanh-like curve that approaches 1.0 asymptotically
        return (float) (x / (1.0 + Math.abs(x)));
    }

    /**
     * Converts linear amplitude to decibels.
     */
    private double linearToDb(double linear) {
        if (linear <= 0) {
            return -100.0; // Very quiet
        }
        return 20.0 * Math.log10(linear);
    }

    /**
     * Converts decibels to linear amplitude.
     */
    private double dbToLinear(double db) {
        return Math.pow(10.0, db / 20.0);
    }
}
