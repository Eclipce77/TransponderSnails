package net.eclipce.transpondersnails.voice;

import net.eclipce.transpondersnails.config.ModConfig;

/**
 * Shared constants for voice chat functionality
 * Now uses configuration values instead of hardcoded constants
 */
public class VoiceChatConstants {

    // Volume category ID for Transponder Snail calls (still constant)
    public static final String SNAIL_VOLUME_CATEGORY = "snail_volume";

    // Plugin ID for consistency (still constant)
    public static final String PLUGIN_ID = "transpondersnails";

    // Audio settings (still constant - these are technical limitations)
    public static final int AUDIO_SAMPLE_RATE = 48000; // 48kHz sample rate
    public static final int AUDIO_FRAME_SIZE = 960;    // 20ms frame size at 48kHz
    public static final int AUDIO_BUFFER_SIZE = 10;    // Keep last 10 audio frames

    // =================== AMPLIFIED TRANSPONDER SNAIL (MEGAPHONE) ===================

    /** How far amplified audio carries IN FRONT of the megaphone, in blocks. */
    public static final float AMPLIFIED_SNAIL_BROADCAST_RANGE = 96.0f;

    /** How far amplified audio carries BEHIND / to the sides of the megaphone, in blocks. */
    public static final float AMPLIFIED_SNAIL_REAR_RANGE = 24.0f;

    /** Half-angle of the megaphone's front cone, in degrees (70 = a 140 degree wide cone). */
    public static final double AMPLIFIED_SNAIL_FRONT_CONE_HALF_ANGLE = 70.0;

    /** Placed snail: pickup radius from the block center, in blocks (1.5 = the 1 block ring incl. diagonals). */
    public static final double AMPLIFIED_SNAIL_PICKUP_RADIUS = 1.5;

    /** Placed snail: volume multiplier at the edge of the pickup ring (1.0 right next to the snail). */
    public static final double AMPLIFIED_SNAIL_PICKUP_EDGE_GAIN = 0.6;

    /** Placed snail: max height difference between the speaker's eyes and the snail's center, in blocks. */
    public static final double AMPLIFIED_SNAIL_PICKUP_VERTICAL = 3.0;

    private VoiceChatConstants() {
        // Utility class - no instantiation
    }

    // =================== CONFIGURABLE VALUES ===================
    // These now delegate to the configuration system

    /**
     * Range to find snails for interaction
     * @return The configured interaction range in blocks
     */
    public static double getSnailInteractionRange() {
        return ModConfig.getSnailInteractionRange();
    }

    /**
     * Timeout for ring duration before giving up
     * @return The configured ring timeout in milliseconds
     */
    public static long getRingTimeoutMs() {
        return ModConfig.getRingTimeoutMs();
    }

    /**
     * Range for placed snail voice chat
     * @return The configured locational snail range in blocks
     */
    public static double getLocationalSnailRange() {
        return ModConfig.getLocationalSnailRange();
    }

    /**
     * Range for handheld snail voice chat
     * @return The configured handheld snail range in blocks
     */
    public static double getHandheldSnailRange() {
        return ModConfig.getHandheldSnailRange();
    }
}