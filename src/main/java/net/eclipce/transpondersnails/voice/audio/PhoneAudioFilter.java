package net.eclipce.transpondersnails.voice.audio;

/**
 * Audio filter that simulates modern phone call quality by limiting frequency range.
 * Applies a bandpass filter (300Hz - 3400Hz) to make voice chat sound like a phone call.
 *
 * This filter uses second-order IIR (Infinite Impulse Response) Butterworth filters
 * for computational efficiency and good phase response.
 */
public class PhoneAudioFilter {

    // Sample rate - Simple Voice Chat uses 48kHz
    private static final int SAMPLE_RATE = 48000;

    // Filter cutoff frequencies (in Hz)
    private static final double LOW_CUTOFF = 300.0;   // High-pass cutoff
    private static final double HIGH_CUTOFF = 3400.0; // Low-pass cutoff

    // High-pass filter coefficients (removes frequencies below 300Hz)
    private final double hp_a0, hp_a1, hp_a2;
    private final double hp_b1, hp_b2;
    private double hp_x1 = 0, hp_x2 = 0; // Previous input samples
    private double hp_y1 = 0, hp_y2 = 0; // Previous output samples

    // Low-pass filter coefficients (removes frequencies above 3400Hz)
    private final double lp_a0, lp_a1, lp_a2;
    private final double lp_b1, lp_b2;
    private double lp_x1 = 0, lp_x2 = 0; // Previous input samples
    private double lp_y1 = 0, lp_y2 = 0; // Previous output samples

    // Normalization factor to prevent volume loss
    private static final double GAIN_COMPENSATION = 1.2;

    /**
     * Creates a new phone audio filter with default settings.
     * Initializes both high-pass (300Hz) and low-pass (3400Hz) filters.
     */
    public PhoneAudioFilter() {
        // Calculate high-pass filter coefficients (300Hz cutoff)
        double omega_hp = 2.0 * Math.PI * LOW_CUTOFF / SAMPLE_RATE;
        double sin_hp = Math.sin(omega_hp);
        double cos_hp = Math.cos(omega_hp);
        double alpha_hp = sin_hp / (2.0 * 0.707); // Q = 0.707 for Butterworth

        double hp_norm = 1.0 + alpha_hp;
        hp_a0 = ((1.0 + cos_hp) / 2.0) / hp_norm;
        hp_a1 = (-(1.0 + cos_hp)) / hp_norm;
        hp_a2 = ((1.0 + cos_hp) / 2.0) / hp_norm;
        hp_b1 = (-2.0 * cos_hp) / hp_norm;
        hp_b2 = (1.0 - alpha_hp) / hp_norm;

        // Calculate low-pass filter coefficients (3400Hz cutoff)
        double omega_lp = 2.0 * Math.PI * HIGH_CUTOFF / SAMPLE_RATE;
        double sin_lp = Math.sin(omega_lp);
        double cos_lp = Math.cos(omega_lp);
        double alpha_lp = sin_lp / (2.0 * 0.707); // Q = 0.707 for Butterworth

        double lp_norm = 1.0 + alpha_lp;
        lp_a0 = ((1.0 - cos_lp) / 2.0) / lp_norm;
        lp_a1 = (1.0 - cos_lp) / lp_norm;
        lp_a2 = ((1.0 - cos_lp) / 2.0) / lp_norm;
        lp_b1 = (-2.0 * cos_lp) / lp_norm;
        lp_b2 = (1.0 - alpha_lp) / lp_norm;

        System.out.println("PhoneAudioFilter initialized: " + LOW_CUTOFF + "Hz - " + HIGH_CUTOFF + "Hz bandpass");
    }

    /**
     * Processes audio samples through the phone filter.
     * Applies bandpass filtering to simulate phone call audio quality.
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
            double input = samples[i] / 32768.0;

            // Apply high-pass filter (removes low frequencies)
            double hp_output = (hp_a0 * input) + (hp_a1 * hp_x1) + (hp_a2 * hp_x2)
                    - (hp_b1 * hp_y1) - (hp_b2 * hp_y2);

            // Update high-pass filter state
            hp_x2 = hp_x1;
            hp_x1 = input;
            hp_y2 = hp_y1;
            hp_y1 = hp_output;

            // Apply low-pass filter (removes high frequencies)
            double lp_output = (lp_a0 * hp_output) + (lp_a1 * lp_x1) + (lp_a2 * lp_x2)
                    - (lp_b1 * lp_y1) - (lp_b2 * lp_y2);

            // Update low-pass filter state
            lp_x2 = lp_x1;
            lp_x1 = hp_output;
            lp_y2 = lp_y1;
            lp_y1 = lp_output;

            // Apply gain compensation and convert back to 16-bit
            double filtered = lp_output * GAIN_COMPENSATION;

            // Clamp to prevent overflow
            filtered = Math.max(-1.0, Math.min(1.0, filtered));

            // Convert back to short
            samples[i] = (short) (filtered * 32767.0);
        }

        return samples;
    }

    /**
     * Resets the filter state.
     * Call this when starting a new audio stream to prevent artifacts.
     */
    public void reset() {
        hp_x1 = hp_x2 = 0;
        hp_y1 = hp_y2 = 0;
        lp_x1 = lp_x2 = 0;
        lp_y1 = lp_y2 = 0;
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
        return String.format("Phone Audio Filter: %.0fHz - %.0fHz bandpass, Gain: %.1fx",
                LOW_CUTOFF, HIGH_CUTOFF, GAIN_COMPENSATION);
    }
}