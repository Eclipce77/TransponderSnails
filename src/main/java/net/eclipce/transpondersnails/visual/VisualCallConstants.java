package net.eclipce.transpondersnails.visual;

/**
 * Tunables for the Visual Transponder Snail video call system.
 * (Kept as constants for the demo - move them into ModConfig when you want them configurable.)
 */
public final class VisualCallConstants {

    private VisualCallConstants() {}

    // ---------------- Matching ----------------
    /** Two Visual Snails auto-connect when they are within this many blocks (centre to centre, same dimension). */
    public static final double CALL_RANGE = 20.0;

    // ---------------- Screen ("projector") ----------------
    public static final int SCREEN_MIN_SIZE = 1;
    public static final int SCREEN_MAX_SIZE = 6;
    public static final int SCREEN_DEFAULT_SIZE = 2;
    /** The screen floats in the air this many blocks in front of the snail's front face (no wall needed). */
    public static final double SCREEN_FLOAT_DISTANCE = 1.0;
    /** Screens further away than this (blocks) are neither drawn nor fed. */
    public static final double SCREEN_RENDER_DISTANCE = 64.0;
    /** Legacy: only used for the block entity's render bounding box in TransponderSnailBlockEntity. */
    public static final int SCREEN_MAX_WALL_DISTANCE = 12;
    /** Colour multiplier of the projected picture (0-255 per channel). 255,255,255 = untouched. A slight green-blue
     *  monitor tint keeps the screen distinguishable from the wall it is drawn on. */
    public static final int FEED_TINT_R = 255;
    public static final int FEED_TINT_G = 255;
    public static final int FEED_TINT_B = 255;

    // ---------------- Video feed (client rendering) ----------------
    /** Square resolution of the offscreen render target. */
    public static final int FEED_RESOLUTION = 256;
    /** Max captures per second per feed. */
    public static final int FEED_FPS = 15;
    /** Radius (chunks) rendered around the remote snail's eyes. Clamped to the player's own render distance. */
    public static final int FEED_VIEW_CHUNKS = 6;
    /** Sections above/below the camera that are considered when building the visible section list. */
    public static final int FEED_VIEW_SECTIONS_VERTICAL = 4;
    /** Captured frames are refreshed into the visible-section list every N captures. */
    public static final int FEED_SECTION_REFRESH_CAPTURES = 20;
    /** A feed is only re-rendered while a screen asked for it within this window (ms). */
    public static final long FEED_WANTED_WINDOW_MS = 1500L;
    /** Feeds that nobody asked for during this long (ms) and have no link are freed. */
    public static final long FEED_IDLE_RELEASE_MS = 5000L;

    // ---------------- Call handshake / failure policy ----------------
    /** Players within this many blocks of a screen snail are sent the video feed control packets. */
    public static final double VIDEO_TRACK_RANGE = 32.0;
    /** Successful captured frames a viewer must produce before it reports "video ready". */
    public static final int HANDSHAKE_OK_FRAMES = 3;
    /** Video must come up on BOTH ends within this many server ticks after the call is answered. */
    public static final int VIDEO_START_TIMEOUT_TICKS = 200; // 10 s
    /** Consecutive failed audio sends before the call is failed. */
    public static final int MAX_AUDIO_SEND_ERRORS = 25;
    /** If true, any viewer reporting a video error ends the call (the requested "video failure fails the call" rule). */
    public static final boolean FAIL_CALL_ON_ANY_VIEWER_ERROR = true;

    // ---------------- Audio activity (visual "active" state) ----------------
    public static final long AUDIO_ACTIVITY_WINDOW_MS = 500L;
}
