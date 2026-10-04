package net.eclipce.transpondersnails.visual;

/**
 * Tunables for the Visual Transponder Snail video call system.
 * (Kept as constants for the demo - move them into ModConfig when you want them configurable.)
 */
public final class VisualCallConstants {

    private VisualCallConstants() {}

    // ---------------- Snail variants (registry paths, namespace = the mod id) ----------------
    /** Visual Transponder Snail: the projector snail. Films AND shows a picture, picks up AND plays audio. */
    public static final String DUPLEX_SNAIL_ID = "visual_transponder_snail";
    /** Visual Transmitter Transponder Snail: a camera. Films and picks up sound, shows nothing and plays nothing. */
    public static final String CAMERA_SNAIL_ID = "visual_transmitter_transponder_snail";

    // ---------------- Matching ----------------
    /** Two Visual Snails auto-connect when they are within this many blocks (centre to centre, same dimension). */
    public static final double CALL_RANGE = 20.0;

    // ---------------- Screen ("projector") ----------------
    public static final int SCREEN_MIN_SIZE = 1;
    /**
     * Largest screen (blocks per side) by default. The server config option visual_snails.max_screen_size overrides it, so it can
     * be raised later without touching the code.
     */
    public static final int SCREEN_MAX_SIZE = 20;
    public static final int SCREEN_DEFAULT_SIZE = 2;
    /**
     * true  = the screen floats BEHIND the snail (like a monitor with a webcam in front of it): the picture faces the way
     *         the snail faces, so people standing in front of the snail see it past the snail.
     * false = the screen floats in front of the snail.
     * If a solid block touches the snail on that side (snail against a wall) the screen lies flat on that block's face.
     */
    public static final boolean SCREEN_BEHIND_SNAIL = true;
    /** The projector settings menu can move the screen at most this many blocks in any direction from its default place. */
    public static final double SCREEN_OFFSET_MAX = 3.0;
    /** ...in steps of this many blocks. */
    public static final double SCREEN_OFFSET_STEP = 0.25;
    /** The screen floats in the air this many blocks from the snail block (no wall needed). */
    public static final double SCREEN_FLOAT_DISTANCE = 1.0;
    /**
     * The screen sits this much CLOSER to the snail than SCREEN_FLOAT_DISTANCE says. At exactly a whole number of blocks
     * the screen's plane would be the same plane as the face of a block placed behind it (a backdrop) and the two would
     * z-fight, giving a flickering hatch pattern. Keep this above ~0.01 and do not make it a whole number of blocks off.
     */
    public static final double SCREEN_SURFACE_OFFSET = 0.03;
    /** Screens further away than this (blocks) are neither drawn nor fed. */
    public static final double SCREEN_RENDER_DISTANCE = 64.0;

    // ---------------- Projector: automatic depth and zoom ----------------
    /**
     * The snail looks for a surface straight behind it (in line with it) up to this many blocks away and projects onto it.
     * With no surface in reach the screen floats in the air instead (SCREEN_FLOAT_DISTANCE, plus the "extra depth" setting).
     */
    public static final double PROJECTOR_MAX_THROW = 40.0;
    /** Height above the block's bottom of the projector lens: the ray that looks for a surface starts there. */
    public static final double PROJECTOR_LENS_HEIGHT = 7.0 / 16.0;
    /** Zoom: the minimum screen size goes up by 1 every this many blocks of distance to the screen. */
    public static final double PROJECTOR_BLOCKS_PER_SIZE_STEP = 2.0;
    /** Zoom: the maximum screen size is this many times the minimum (min 1x1 -> max 4x4, min 3x3 -> max 12x12, ...). */
    public static final int PROJECTOR_ZOOM_RANGE = 4;
    /** With no surface behind the snail the screen can be pushed this many blocks further back - never towards the snail. */
    public static final double SCREEN_BACK_MAX = 3.0;
    /** Colour multiplier of the projected picture (0-255 per channel). 255,255,255 = untouched. A slight green-blue
     *  monitor tint keeps the screen distinguishable from the world around it. */
    public static final int FEED_TINT_R = 215;
    public static final int FEED_TINT_G = 255;
    public static final int FEED_TINT_B = 235;

    // ---------------- Camera ----------------
    /**
     * false = the snail films what is IN FRONT of it, from its eyes (snail eye level).
     * true  = the snail films what is behind it (camera at the back of the block, still at eye height).
     */
    public static final boolean FEED_LOOKS_BEHIND = false;

    // ---------------- Projector look ----------------
    /** Opacity of the projected picture (0-1). Below 1 the world behind the screen (blocks, textures) shows through
     *  and tints the picture, like light on a surface. */
    public static final float FEED_OPACITY = 0.8F;
    /** The picture fades in over this long after the call is answered (a projector warming up, only faster). */
    public static final long FEED_FADE_IN_MS = 800L;
    /** The picture is drawn at least this bright (Minecraft light level 0-15) even in a dark room, like a projector;
     *  in a brighter spot it is drawn at that spot's brightness. */
    public static final int PROJECTOR_IMAGE_LIGHT = 14;
    /** Light level (0-15) the snail BLOCK itself gives off while it is in a call. Only used by the optional
     *  ModBlocks patch; 0 = no light. */
    public static final int PROJECTOR_EMIT_LIGHT = 8;

    // ---------------- Video feed (client rendering) ----------------
    /**
     * Highest resolution of a standard (projector) feed. Raised from 256: with adaptive quality (see VisualQuality) the full
     * resolution is only used while a screen really covers that many pixels of the viewer's display.
     */
    public static final int FEED_RESOLUTION = 1024;
    /** Lowest resolution a feed is ever rendered at (screens that are far away, small, or not seen yet). */
    public static final int FEED_MIN_RESOLUTION = 256;
    // ---- adaptive quality: how quickly a feed changes resolution (a resize re-creates the render target) ----
    /** A higher resolution is only applied after it has been wanted this long (ms)... */
    public static final long LOD_UP_DELAY_MS = 600L;
    /** ...a lower one only after this long, so walking back and forth does not cause flapping. */
    public static final long LOD_DOWN_DELAY_MS = 4000L;
    /** Never resize the same feed more often than this (ms). */
    public static final long LOD_MIN_RESIZE_INTERVAL_MS = 1500L;

    /** Max captures per second per feed. */
    public static final int FEED_FPS = 15;
    /** Radius (chunks) rendered around the remote snail's eyes. Clamped to the player's own render distance. */
    public static final int FEED_VIEW_CHUNKS = 6;
    /** Sections above/below the camera that are considered when building the visible section list. */
    public static final int FEED_VIEW_SECTIONS_VERTICAL = 4;

    // ---------------- Camera snail feed (Visual Transmitter Transponder Snail): better than a standard call ----------------
    /** Square resolution of the camera's render target (standard calls: {@link #FEED_RESOLUTION}). 512 is a lighter alternative. */
    public static final int CAMERA_FEED_RESOLUTION = 1024;
    /** Max captures per second of a camera feed. Lower this first if the high resolution costs too much frame rate. */
    public static final int CAMERA_FEED_FPS = 15;
    /** View radius (chunks) of a camera. Still clamped to the VIEWER's render distance: the viewer's client must have the chunks. */
    public static final int CAMERA_FEED_VIEW_CHUNKS = 16;
    /** Sections above/below a camera that are drawn (standard: {@link #FEED_VIEW_SECTIONS_VERTICAL}). */
    public static final int CAMERA_FEED_VIEW_SECTIONS_VERTICAL = 8;
    /** Captured frames are refreshed into the visible-section list every N captures. */
    public static final int FEED_SECTION_REFRESH_CAPTURES = 60; // also refreshed at once when the viewer changes section
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

    // ---------------- Server protection ----------------
    /** A player's clicks on a Visual snail closer together than this (ticks) are ignored (each one would start / end a call). */
    public static final int INTERACTION_COOLDOWN_TICKS = 10;
    /** How often (ticks) a RUNNING call looks for players that walked into range of its screen. */
    public static final int VIEWER_REFRESH_TICKS = 20;

    // ---------------- Audio activity (visual "active" state) ----------------
    public static final long AUDIO_ACTIVITY_WINDOW_MS = 500L;
}
