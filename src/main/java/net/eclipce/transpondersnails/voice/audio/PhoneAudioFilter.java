package net.eclipce.transpondersnails.voice.audio;

/**
 * Improved phone audio filter that simulates phone call quality.
 *
 * Combines elements of analog and digital phone systems:
 * - Frequency limiting (300Hz - 3400Hz) for telephone bandwidth
 * - Pre-emphasis and de-emphasis for that characteristic "tinny" sound
 * - Gentle filtering to avoid ringing artifacts
 * - Soft saturation for analog warmth
 * - Dynamic range management to prevent audio glitches
 *
 * This version uses cascaded biquad filters for smoother frequency response
 * and better numerical stability compared to high-order IIR filters.
 */
public class PhoneAudioFilter {

    // Sample rate - Simple Voice Chat uses 48kHz
    private static final int SAMPLE_RATE = 48000;

    // Phone bandwidth (300Hz - 3400Hz)
    private static final double LOW_CUTOFF = 300.0;
    private static final double HIGH_CUTOFF = 3400.0;

    // Filter stages for smooth rolloff (2 stages = 24dB/octave)
    private final BiquadFilter[] highpassStages;
    private final BiquadFilter[] lowpassStages;

    // Pre-emphasis filter (boosts highs slightly for "phone" character)
    private final BiquadFilter preEmphasis;

    // De-emphasis filter (reduces boosted highs)
    private final BiquadFilter deEmphasis;

    // Dynamics processing
    private static final double NOISE_GATE_THRESHOLD = 0.001; // -60dB
    private static final double SOFT_CLIP_THRESHOLD = 0.85;   // Gentle saturation
    private static final double OUTPUT_GAIN = 1.1;             // Slight output boost

    /**
     * Simple biquad filter implementation for stable IIR filtering
     */
    private static class BiquadFilter {
        // Filter coefficients
        private final double b0, b1, b2, a1, a2;

        // Filter state
        private double x1 = 0, x2 = 0;
        private double y1 = 0, y2 = 0;

        public BiquadFilter(double b0, double b1, double b2, double a1, double a2) {
            this.b0 = b0;
            this.b1 = b1;
            this.b2 = b2;
            this.a1 = a1;
            this.a2 = a2;
        }

        public double process(double input) {
            // Direct Form II implementation (more stable)
            double output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;

            // Update state
            x2 = x1;
            x1 = input;
            y2 = y1;
            y1 = output;

            return output;
        }

        public void reset() {
            x1 = x2 = 0;
            y1 = y2 = 0;
        }

        /**
         * Create a Butterworth highpass filter
         */
        public static BiquadFilter createHighpass(double frequency, double sampleRate) {
            double w0 = 2.0 * Math.PI * frequency / sampleRate;
            double cosW0 = Math.cos(w0);
            double sinW0 = Math.sin(w0);
            double alpha = sinW0 / (2.0 * 0.5); // Q = 0.5 for gentle slope

            double b0 = (1.0 + cosW0) / 2.0;
            double b1 = -(1.0 + cosW0);
            double b2 = (1.0 + cosW0) / 2.0;
            double a0 = 1.0 + alpha;
            double a1 = -2.0 * cosW0;
            double a2 = 1.0 - alpha;

            return new BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0);
        }

        /**
         * Create a Butterworth lowpass filter
         */
        public static BiquadFilter createLowpass(double frequency, double sampleRate) {
            double w0 = 2.0 * Math.PI * frequency / sampleRate;
            double cosW0 = Math.cos(w0);
            double sinW0 = Math.sin(w0);
            double alpha = sinW0 / (2.0 * 0.5); // Q = 0.5 for gentle slope

            double b0 = (1.0 - cosW0) / 2.0;
            double b1 = 1.0 - cosW0;
            double b2 = (1.0 - cosW0) / 2.0;
            double a0 = 1.0 + alpha;
            double a1 = -2.0 * cosW0;
            double a2 = 1.0 - alpha;

            return new BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0);
        }

        /**
         * Create a shelving filter for pre-emphasis/de-emphasis
         */
        public static BiquadFilter createHighShelf(double frequency, double sampleRate, double gainDb) {
            double A = Math.pow(10, gainDb / 40.0);
            double w0 = 2.0 * Math.PI * frequency / sampleRate;
            double cosW0 = Math.cos(w0);
            double sinW0 = Math.sin(w0);
            double alpha = sinW0 / 2.0;

            double b0 = A * ((A + 1) + (A - 1) * cosW0 + 2 * Math.sqrt(A) * alpha);
            double b1 = -2 * A * ((A - 1) + (A + 1) * cosW0);
            double b2 = A * ((A + 1) + (A - 1) * cosW0 - 2 * Math.sqrt(A) * alpha);
            double a0 = (A + 1) - (A - 1) * cosW0 + 2 * Math.sqrt(A) * alpha;
            double a1 = 2 * ((A - 1) - (A + 1) * cosW0);
            double a2 = (A + 1) - (A - 1) * cosW0 - 2 * Math.sqrt(A) * alpha;

            return new BiquadFilter(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0);
        }
    }

    /**
     * Creates a new phone audio filter with improved characteristics
     */
    public PhoneAudioFilter() {
        // Create 2 cascaded highpass stages for 24dB/octave rolloff (smoother)
        highpassStages = new BiquadFilter[2];
        highpassStages[0] = BiquadFilter.createHighpass(LOW_CUTOFF, SAMPLE_RATE);
        highpassStages[1] = BiquadFilter.createHighpass(LOW_CUTOFF, SAMPLE_RATE);

        // Create 2 cascaded lowpass stages for 24dB/octave rolloff (smoother)
        lowpassStages = new BiquadFilter[2];
        lowpassStages[0] = BiquadFilter.createLowpass(HIGH_CUTOFF, SAMPLE_RATE);
        lowpassStages[1] = BiquadFilter.createLowpass(HIGH_CUTOFF, SAMPLE_RATE);

        // Pre-emphasis: Boost highs at 2kHz by 3dB for "phone" character
        preEmphasis = BiquadFilter.createHighShelf(2000.0, SAMPLE_RATE, 3.0);

        // De-emphasis: Reduce highs at 3kHz by 2dB for smoothness
        deEmphasis = BiquadFilter.createHighShelf(3000.0, SAMPLE_RATE, -2.0);

    }

    /**
     * Processes audio samples through the phone filter.
     *
     * Processing chain:
     * 1. Convert to normalized float
     * 2. Noise gate (remove ultra-quiet noise)
     * 3. Pre-emphasis (boost highs)
     * 4. Bandpass filtering (300-3400Hz)
     * 5. De-emphasis (smooth highs)
     * 6. Soft saturation (analog warmth)
     * 7. Output gain and conversion
     *
     * @param samples The audio samples to filter (16-bit PCM at 48kHz)
     * @return The filtered audio samples (same array, modified in-place)
     */
    public short[] process(short[] samples) {
        if (samples == null || samples.length == 0) {
            return samples;
        }

        for (int i = 0; i < samples.length; i++) {
            // Convert to normalized double (-1.0 to 1.0)
            double sample = samples[i] / 32768.0;

            // 1. Noise gate - remove ultra-quiet noise to prevent artifacts
            if (Math.abs(sample) < NOISE_GATE_THRESHOLD) {
                sample = 0.0;
            }

            // 2. Pre-emphasis - boost high frequencies for phone character
            sample = preEmphasis.process(sample);

            // 3. Highpass filtering - remove frequencies below 300Hz (2 stages)
            sample = highpassStages[0].process(sample);
            sample = highpassStages[1].process(sample);

            // 4. Lowpass filtering - remove frequencies above 3400Hz (2 stages)
            sample = lowpassStages[0].process(sample);
            sample = lowpassStages[1].process(sample);

            // 5. De-emphasis - smooth out harsh highs
            sample = deEmphasis.process(sample);

            // 6. Soft saturation - adds analog warmth and prevents harsh clipping
            sample = softClip(sample);

            // 7. Output gain
            sample *= OUTPUT_GAIN;

            // 8. Hard clamp to prevent overflow
            sample = Math.max(-1.0, Math.min(1.0, sample));

            // Convert back to 16-bit
            samples[i] = (short) (sample * 32767.0);
        }

        return samples;
    }

    /**
     * Soft clipping function for analog-style saturation
     * Uses a cubic polynomial for smooth saturation
     */
    private double softClip(double input) {
        double absInput = Math.abs(input);

        if (absInput < SOFT_CLIP_THRESHOLD) {
            // Linear region - no distortion
            return input;
        } else if (absInput < 1.0) {
            // Soft saturation region
            double sign = Math.signum(input);
            double normalized = (absInput - SOFT_CLIP_THRESHOLD) / (1.0 - SOFT_CLIP_THRESHOLD);
            // Cubic soft knee
            double saturated = SOFT_CLIP_THRESHOLD + (1.0 - SOFT_CLIP_THRESHOLD) *
                    (normalized - normalized * normalized * normalized / 3.0);
            return sign * saturated;
        } else {
            // Hard limit at ±1.0
            return Math.signum(input);
        }
    }

    /**
     * Resets the filter state.
     * Call this when starting a new audio stream to prevent artifacts.
     */
    public void reset() {
        for (BiquadFilter filter : highpassStages) {
            filter.reset();
        }
        for (BiquadFilter filter : lowpassStages) {
            filter.reset();
        }
        preEmphasis.reset();
        deEmphasis.reset();
    }

    /**
     * Creates a copy of this filter with the same coefficients but fresh state.
     * Useful for creating per-call or per-player filter instances.
     *
     * @return A new filter instance with reset state
     */
    public PhoneAudioFilter createFreshInstance() {
        return new PhoneAudioFilter();
    }

    /**
     * Gets a description of the filter settings.
     *
     * @return Human-readable description of the filter
     */
    public String getDescription() {
        return String.format("Phone Audio Filter v2: %.0fHz - %.0fHz bandpass, " +
                        "24dB/oct rolloff, pre/de-emphasis, soft saturation",
                LOW_CUTOFF, HIGH_CUTOFF);
    }
}