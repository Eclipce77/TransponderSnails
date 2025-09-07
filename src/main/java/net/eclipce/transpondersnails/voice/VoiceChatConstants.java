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