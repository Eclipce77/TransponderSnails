package net.eclipce.transpondersnails.voice.audio;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import java.util.Random;

/**
 * Generates static/white noise audio to replace protected participants' audio
 * when their calls are being intercepted.
 *
 * The static is designed to sound like radio interference or poor signal,
 * making it clear that the audio is being blocked rather than just silent.
 *
 * Note: This generates procedural static. A custom looping sound can be
 * substituted later by encoding a sound file to Opus format.
 */
public class StaticAudioGenerator {

    // Simple Voice Chat uses 48kHz sample rate
    private static final int SAMPLE_RATE = 48000;

    // 20ms frame size at 48kHz = 960 samples
    private static final int FRAME_SIZE = 960;

    // Volume of static (0.0 to 1.0) - keep it moderate so it's not too harsh
    private static final float STATIC_VOLUME = 0.15f;

    // Opus encoder for converting PCM to Opus
    private final OpusEncoder opusEncoder;

    // Random for generating static
    private final Random random;

    // Pre-generated static frames for efficiency
    private final byte[][] staticFrameCache;
    private static final int CACHE_SIZE = 20; // 20 frames = 400ms of unique static
    private int cacheIndex = 0;

    // Variation parameters for more realistic static
    private float currentVolume = STATIC_VOLUME;
    private long lastVolumeChangeTime = 0;
    private static final long VOLUME_CHANGE_INTERVAL_MS = 100; // Vary volume every 100ms

    public StaticAudioGenerator(VoicechatServerApi voiceChatApi) {
        this.opusEncoder = voiceChatApi.createEncoder();
        this.random = new Random();
        this.staticFrameCache = new byte[CACHE_SIZE][];

        // Pre-generate static frames
        preGenerateStaticFrames();

        System.out.println("StaticAudioGenerator: Initialized with " + CACHE_SIZE + " cached frames");
    }

    /**
     * Pre-generate cached static frames for efficient retrieval
     */
    private void preGenerateStaticFrames() {
        for (int i = 0; i < CACHE_SIZE; i++) {
            short[] pcmSamples = generateStaticPCM(STATIC_VOLUME);
            staticFrameCache[i] = opusEncoder.encode(pcmSamples);
        }
    }

    /**
     * Get the next static audio frame (Opus encoded).
     * Uses cached frames for efficiency with slight volume variations for realism.
     *
     * @return Opus-encoded static audio frame
     */
    public byte[] getStaticFrame() {
        // Occasionally regenerate a frame with volume variation for realism
        long now = System.currentTimeMillis();
        if (now - lastVolumeChangeTime > VOLUME_CHANGE_INTERVAL_MS) {
            lastVolumeChangeTime = now;

            // Vary volume between 50% and 150% of base volume
            currentVolume = STATIC_VOLUME * (0.5f + random.nextFloat());

            // Regenerate current frame with new volume
            short[] pcmSamples = generateStaticPCM(currentVolume);
            staticFrameCache[cacheIndex] = opusEncoder.encode(pcmSamples);
        }

        // Get cached frame and advance index
        byte[] frame = staticFrameCache[cacheIndex];
        cacheIndex = (cacheIndex + 1) % CACHE_SIZE;

        return frame;
    }

    /**
     * Generate a fresh static frame with custom volume.
     * Use this when you need unique static (not from cache).
     *
     * @param volume Volume level (0.0 to 1.0)
     * @return Opus-encoded static audio frame
     */
    public byte[] generateFreshStaticFrame(float volume) {
        short[] pcmSamples = generateStaticPCM(volume);
        return opusEncoder.encode(pcmSamples);
    }

    /**
     * Generate PCM samples of static/white noise.
     *
     * @param volume Volume level (0.0 to 1.0)
     * @return Array of 16-bit PCM samples
     */
    private short[] generateStaticPCM(float volume) {
        short[] samples = new short[FRAME_SIZE];

        // Clamp volume
        volume = Math.max(0f, Math.min(1f, volume));

        // Maximum amplitude for given volume
        int maxAmplitude = (int) (Short.MAX_VALUE * volume);

        for (int i = 0; i < FRAME_SIZE; i++) {
            // Generate white noise: random values between -maxAmplitude and +maxAmplitude
            int sample = random.nextInt(maxAmplitude * 2 + 1) - maxAmplitude;

            // Apply a slight low-pass feel by averaging with previous sample
            // This makes the static sound less harsh
            if (i > 0) {
                sample = (sample + samples[i - 1]) / 2;
            }

            samples[i] = (short) sample;
        }

        return samples;
    }

    /**
     * Generate "crackling" static that sounds like intermittent interference.
     * Has periods of near-silence punctuated by bursts of static.
     *
     * @return Opus-encoded crackling static frame
     */
    public byte[] getCracklingStaticFrame() {
        short[] samples = new short[FRAME_SIZE];

        // Determine if this frame should have a "burst" of static
        boolean hasBurst = random.nextFloat() < 0.3f; // 30% chance of burst

        int maxAmplitude = (int) (Short.MAX_VALUE * (hasBurst ? STATIC_VOLUME : STATIC_VOLUME * 0.1f));

        for (int i = 0; i < FRAME_SIZE; i++) {
            // During burst, add short crackles
            if (hasBurst && random.nextFloat() < 0.2f) {
                int sample = random.nextInt(maxAmplitude * 4 + 1) - maxAmplitude * 2;
                samples[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
            } else {
                // Low-level background noise
                samples[i] = (short) (random.nextInt(maxAmplitude * 2 + 1) - maxAmplitude);
            }
        }

        return opusEncoder.encode(samples);
    }

    /**
     * Generate silence frame.
     * Useful for testing or when you want complete audio blocking.
     *
     * @return Opus-encoded silence
     */
    public byte[] getSilenceFrame() {
        short[] samples = new short[FRAME_SIZE];
        // All zeros = silence
        return opusEncoder.encode(samples);
    }

    /**
     * Check if the generator is healthy and working
     */
    public boolean isHealthy() {
        return opusEncoder != null && staticFrameCache[0] != null;
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        try {
            if (opusEncoder != null) {
                opusEncoder.close();
            }
        } catch (Exception e) {
            System.err.println("StaticAudioGenerator: Error during cleanup: " + e.getMessage());
        }
    }

    /**
     * Get description of the generator
     */
    public String getDescription() {
        return String.format("StaticAudioGenerator: %d cached frames, base volume: %.0f%%",
                CACHE_SIZE, STATIC_VOLUME * 100);
    }
}