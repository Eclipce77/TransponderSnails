package net.eclipce.transpondersnails.voice.audio;

/**
 * Megaphone / bullhorn effect for the Amplified Transponder Snail.
 *
 * Processing chain (48kHz mono, 16-bit PCM, stateful across frames):
 *  1. Smooth noise gate       - stops mic hiss from being amplified
 *  2. Highpass  150Hz         - removes rumble but leaves the "boom" region
 *  3. Peak     +6dB @ 230Hz   - BOOM: chesty low-mid body
 *  4. Peak     +7dB @ 1.8kHz  - the horn's nasal "honk" resonance
 *  5. Lowpass  4.8kHz (x2)    - horn bandwidth limit
 *  6. Drive (tanh)            - horn/driver distortion + perceived loudness
 *  7. Echo    170ms           - damped feedback delay (slap-back off buildings)
 *  8. Reverb                  - small Schroeder/Freeverb-style room
 *  9. Makeup gain + limiter   - louder overall, never clips
 *
 * One instance per audio stream - it keeps filter, delay and reverb state between frames.
 * Because echo/reverb ring on after the speaker stops, call {@link #process} with silent frames
 * to render the tail (see AmplifiedSnailManager's tail pumping).
 */
public class MegaphoneAudioFilter {

    private static final double SAMPLE_RATE = 48000.0;

    // ---- Tone ----
    private static final double HIGHPASS_HZ = 150.0;
    private static final double BOOM_HZ = 230.0, BOOM_DB = 6.0, BOOM_Q = 0.9;
    private static final double HONK_HZ = 1800.0, HONK_DB = 7.0, HONK_Q = 1.3;
    private static final double LOWPASS_HZ = 4800.0;

    // ---- Drive / level ----
    private static final double INPUT_GAIN = 1.0;
    private static final double DRIVE = 2.0;          // higher = grittier
    private static final double OUTPUT_GAIN = 0.60;   // makeup gain after effects (~+5dB louder than the raw voice overall)
    private static final double LIMIT_CEILING = 0.95;

    // ---- Echo ----
    private static final double ECHO_MS = 170.0;
    private static final double ECHO_FEEDBACK = 0.34;
    private static final double ECHO_MIX = 0.32;
    private static final double ECHO_DAMPING = 0.35;  // lowpass in the feedback loop (0 = bright, 1 = dark)

    // ---- Reverb ----
    private static final int[] COMB_DELAYS = {1214, 1293, 1390, 1476};  // Freeverb tunings scaled to 48kHz
    private static final int[] ALLPASS_DELAYS = {605, 480};
    private static final double REVERB_FEEDBACK = 0.72;
    private static final double REVERB_DAMPING = 0.30;
    private static final double REVERB_MIX = 0.18;

    // ---- Gate ----
    private static final double GATE_THRESHOLD = 0.004;   // ~ -48dB
    private static final double GATE_ATTACK = 0.02;       // per-sample smoothing
    private static final double GATE_RELEASE = 0.0008;

    private final Biquad highpass = Biquad.highpass(HIGHPASS_HZ, 0.707);
    private final Biquad boom = Biquad.peaking(BOOM_HZ, BOOM_Q, BOOM_DB);
    private final Biquad honk = Biquad.peaking(HONK_HZ, HONK_Q, HONK_DB);
    private final Biquad lowpass1 = Biquad.lowpass(LOWPASS_HZ, 0.707);
    private final Biquad lowpass2 = Biquad.lowpass(LOWPASS_HZ, 0.707);

    private final double driveNorm = Math.tanh(DRIVE);

    private final double[] echoBuffer = new double[(int) (SAMPLE_RATE * ECHO_MS / 1000.0)];
    private int echoIndex = 0;
    private double echoDampState = 0.0;

    private final Comb[] combs = new Comb[COMB_DELAYS.length];
    private final Allpass[] allpasses = new Allpass[ALLPASS_DELAYS.length];

    private double gateGain = 0.0;

    public MegaphoneAudioFilter() {
        for (int i = 0; i < combs.length; i++) {
            combs[i] = new Comb(COMB_DELAYS[i]);
        }
        for (int i = 0; i < allpasses.length; i++) {
            allpasses[i] = new Allpass(ALLPASS_DELAYS[i]);
        }
    }

    /**
     * Processes a frame in place.
     *
     * @param samples 16-bit PCM at 48kHz (modified in place)
     * @return the same array
     */
    public short[] process(short[] samples) {
        if (samples == null) {
            return null;
        }

        for (int i = 0; i < samples.length; i++) {
            double x = samples[i] / 32768.0 * INPUT_GAIN;

            // 1. Smooth noise gate (no clicks)
            double target = Math.abs(x) > GATE_THRESHOLD ? 1.0 : 0.0;
            gateGain += (target - gateGain) * (target > gateGain ? GATE_ATTACK : GATE_RELEASE);
            x *= gateGain;

            // 2-5. Horn tone
            x = highpass.process(x);
            x = boom.process(x);
            x = honk.process(x);
            x = lowpass1.process(x);
            x = lowpass2.process(x);

            // 6. Drive
            double dry = Math.tanh(x * DRIVE) / driveNorm;

            // 7. Echo (damped feedback delay)
            double delayed = echoBuffer[echoIndex];
            echoDampState += (delayed - echoDampState) * (1.0 - ECHO_DAMPING);
            echoBuffer[echoIndex] = flushDenormal(dry + echoDampState * ECHO_FEEDBACK);
            echoIndex = (echoIndex + 1) % echoBuffer.length;
            double withEcho = dry + echoDampState * ECHO_MIX;

            // 8. Reverb (parallel combs -> series allpasses)
            double reverbIn = withEcho * 0.25;
            double wet = 0.0;
            for (Comb comb : combs) {
                wet += comb.process(reverbIn);
            }
            for (Allpass allpass : allpasses) {
                wet = allpass.process(wet);
            }
            double y = withEcho + wet * REVERB_MIX;

            // 9. Makeup gain + soft limiter
            y *= OUTPUT_GAIN;
            y = softLimit(y);

            samples[i] = (short) Math.round(y * 32767.0);
        }
        return samples;
    }

    /** Resets all state (filters, echo, reverb). */
    public void reset() {
        highpass.reset();
        boom.reset();
        honk.reset();
        lowpass1.reset();
        lowpass2.reset();
        java.util.Arrays.fill(echoBuffer, 0.0);
        echoIndex = 0;
        echoDampState = 0.0;
        for (Comb comb : combs) {
            comb.reset();
        }
        for (Allpass allpass : allpasses) {
            allpass.reset();
        }
        gateGain = 0.0;
    }

    /** Linear below the knee, smoothly approaches the ceiling above it. */
    private static double softLimit(double x) {
        double knee = 0.7;
        double abs = Math.abs(x);
        if (abs <= knee) {
            return x;
        }
        double range = LIMIT_CEILING - knee;
        double over = (abs - knee) / range;
        return Math.signum(x) * (knee + range * Math.tanh(over));
    }

    private static double flushDenormal(double v) {
        return Math.abs(v) < 1e-15 ? 0.0 : v;
    }

    // =================== DSP BUILDING BLOCKS ===================

    /** RBJ cookbook biquad, Direct Form I. */
    private static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0;
            this.b1 = b1 / a0;
            this.b2 = b2 / a0;
            this.a1 = a1 / a0;
            this.a2 = a2 / a0;
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = flushDenormal(y);
            return y;
        }

        void reset() {
            x1 = x2 = y1 = y2 = 0.0;
        }

        static Biquad highpass(double freq, double q) {
            double w0 = 2 * Math.PI * freq / SAMPLE_RATE, cos = Math.cos(w0), alpha = Math.sin(w0) / (2 * q);
            return new Biquad((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad lowpass(double freq, double q) {
            double w0 = 2 * Math.PI * freq / SAMPLE_RATE, cos = Math.cos(w0), alpha = Math.sin(w0) / (2 * q);
            return new Biquad((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad peaking(double freq, double q, double gainDb) {
            double a = Math.pow(10, gainDb / 40.0);
            double w0 = 2 * Math.PI * freq / SAMPLE_RATE, cos = Math.cos(w0), alpha = Math.sin(w0) / (2 * q);
            return new Biquad(1 + alpha * a, -2 * cos, 1 - alpha * a, 1 + alpha / a, -2 * cos, 1 - alpha / a);
        }
    }

    /** Lowpass-feedback comb filter (Freeverb style). */
    private static final class Comb {
        private final double[] buffer;
        private int index;
        private double store;

        Comb(int size) {
            buffer = new double[size];
        }

        double process(double input) {
            double output = buffer[index];
            store = flushDenormal(output * (1 - REVERB_DAMPING) + store * REVERB_DAMPING);
            buffer[index] = flushDenormal(input + store * REVERB_FEEDBACK);
            index = (index + 1) % buffer.length;
            return output;
        }

        void reset() {
            java.util.Arrays.fill(buffer, 0.0);
            index = 0;
            store = 0.0;
        }
    }

    /** Schroeder allpass (Freeverb style). */
    private static final class Allpass {
        private static final double FEEDBACK = 0.5;
        private final double[] buffer;
        private int index;

        Allpass(int size) {
            buffer = new double[size];
        }

        double process(double input) {
            double buffered = buffer[index];
            double output = buffered - input;
            buffer[index] = flushDenormal(input + buffered * FEEDBACK);
            index = (index + 1) % buffer.length;
            return output;
        }

        void reset() {
            java.util.Arrays.fill(buffer, 0.0);
            index = 0;
        }
    }
}
