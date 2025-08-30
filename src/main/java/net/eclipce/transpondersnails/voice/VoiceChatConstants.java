package net.eclipce.transpondersnails.voice;

/**
 * Shared constants for voice chat functionality
 */
public class VoiceChatConstants {

    // Volume category ID for Transponder Snail calls
    public static final String SNAIL_VOLUME_CATEGORY = "snail_volume";

    // Plugin ID for consistency
    public static final String PLUGIN_ID = "transpondersnails";

    // Audio channel ranges
    public static final double LOCATIONAL_SNAIL_RANGE = 10.0;  // Range for placed snails
    public static final double HANDHELD_SNAIL_RANGE = 3.0;     // Range for handheld snails

    // Call timeout settings
    public static final long CALL_TIMEOUT_MS = 30000; // 30 seconds

    // Audio settings
    public static final int AUDIO_SAMPLE_RATE = 48000; // 48kHz sample rate
    public static final int AUDIO_FRAME_SIZE = 960;    // 20ms frame size at 48kHz
    public static final int AUDIO_BUFFER_SIZE = 10;    // Keep last 10 audio frames

    // Channel settings
    public static final float CALL_VOLUME = 1.0f;
    public static final boolean CALL_3D_AUDIO = true;
    public static final boolean DEFAULT_DISABLED = false;

    // Sound event intervals
    public static final long RING_TONE_INTERVAL_MS = 2000; // Ring every 2 seconds

    private VoiceChatConstants() {
        // Utility class - no instantiation
    }
}