package net.eclipce.transpondersnails.voice;

/**
 * Shared constants for voice chat functionality
 */
public class VoiceChatConstants {

    // Volume category ID for Transponder Snail calls
    public static final String SNAIL_VOLUME_CATEGORY = "snail_volume";

    // Plugin ID for consistency
    public static final String PLUGIN_ID = "transpondersnails";

    // Snail Specific settings
    public static final double SNAIL_INTERACTION_RANGE = 10.0; // Range to find snails

    // Call settings
    public static final long RING_INTERVAL_MS = 2000; // Ring every 2 seconds
    public static final long RING_TIMEOUT_MS = 30000; // 30 seconds

    // Audio channel ranges
    public static final double LOCATIONAL_SNAIL_RANGE = 10.0;  // Range for placed snails
    public static final double HANDHELD_SNAIL_RANGE = 3.0;     // Range for handheld snails

    // Audio settings
    public static final int AUDIO_SAMPLE_RATE = 48000; // 48kHz sample rate
    public static final int AUDIO_FRAME_SIZE = 960;    // 20ms frame size at 48kHz
    public static final int AUDIO_BUFFER_SIZE = 10;    // Keep last 10 audio frames

    private VoiceChatConstants() {
        // Utility class - no instantiation
    }
}