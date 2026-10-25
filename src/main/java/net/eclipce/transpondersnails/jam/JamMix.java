package net.eclipce.transpondersnails.jam;

import java.util.Random;

/**
 * Turns real audio into static, a little or entirely. At 0 nothing changes, at 1 there is only static, and in between the voice is faded down and
 * static faded up along a CURVE, not a straight line: a straight line leaves the voice louder than the static until about 40% (half way it is only
 * 6 dB quieter), so words keep cutting through and a partly jammed call is only sometimes jammed. With the curve the voice is already well under the
 * static at 40% and drowned at half. Pure (no Minecraft classes), unit tested.
 */
public final class JamMix {

    private JamMix() {}

    /** The loudness of the static (a 16-bit sample): about a fifth of full scale. */
    public static final double NOISE_AMPLITUDE = 7000.0;

    /** How much of the voice is left at this jam: (1 - jam) squared. */
    public static double voiceGain(double jam) {
        double keep = 1.0 - Math.max(0.0, Math.min(1.0, jam));
        return keep * keep;
    }

    /** How much static is mixed in at this jam: whatever the voice has given up, so the two always add up to a full signal. */
    public static double noiseGain(double jam) {
        return 1.0 - voiceGain(jam);
    }

    /**
     * Mixes static into 16-bit samples, in place.
     *
     * @param jam   0 = untouched, 1 = only static
     * @param noise the source of the static (a thread's own Random: the audio is processed on the voice chat's threads)
     */
    public static void apply(short[] pcm, double jam, Random noise) {
        if (pcm == null || !(jam > 0.0)) return;
        double keep = voiceGain(jam);
        double mix = 1.0 - keep;
        for (int i = 0; i < pcm.length; i++) {
            double staticNoise = (noise.nextDouble() * 2.0 - 1.0) * NOISE_AMPLITUDE;
            double mixed = pcm[i] * keep + staticNoise * mix;
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(mixed)));
        }
    }
}
