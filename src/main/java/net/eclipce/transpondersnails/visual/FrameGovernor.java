package net.eclipce.transpondersnails.visual;

/**
 * Protects the viewer's frame rate. Fed with the frame times of the client while video feeds are being rendered, it
 * steps the video quality down (lower resolution cap, lower frame rate, fewer simultaneous feeds, shorter view radius)
 * when the game gets slow, and back up again - slowly - once it is comfortable. Pure logic, unit tested.
 *
 * Only slowness that happens WHILE a feed was rendered counts: if the game is slow for some other reason there is
 * nothing to gain from degrading the video.
 *
 * Two things make it step down:
 * <ul>
 *   <li><b>The game is slow</b> in absolute terms: the smoothed frame time is above SLOW_MS (36 fps) - a slow computer.</li>
 *   <li><b>The video costs too much of the player's time</b>, whatever the frame rate: a player at 144 fps who drops to 60 fps while
 *       standing at a screen is in trouble, but never gets near 36 fps. So the governor measures what a capture costs: the frames that
 *       follow a capture (they contain its cost) against the frames that do not, times how many captures there are per second. That is
 *       the share of the time the video takes (the "overhead"); above OVERHEAD_HIGH it steps down, and sooner than for plain slowness.</li>
 * </ul>
 * Stepping back up is slow, and slower each time it turns out to have been too early (it would otherwise flap between two levels).
 */
public final class FrameGovernor {

    public static final int MAX_LEVEL = 3;

    /** Smoothed frame time above this (ms) for DOWN_AFTER_MS -> one level worse. (28 ms ~ 36 fps) */
    public static final double SLOW_MS = 28.0;
    /** Smoothed frame time below this (ms) for UP_AFTER_MS -> one level better. (20 ms = 50 fps) */
    public static final double FAST_MS = 20.0;
    public static final long DOWN_AFTER_MS = 1500L;
    /** Same, when it is the measured cost of the captures that is too high: react sooner. */
    public static final long DOWN_AFTER_OVERHEAD_MS = 750L;
    public static final long UP_AFTER_MS = 6000L;
    /** After stepping down, do not step back up for at least this long (stops it flapping). */
    public static final long HOLD_AFTER_DOWN_MS = 10000L;
    /** The video may take this share of the player's time (0..1) before it is turned down: 12%. */
    public static final double OVERHEAD_HIGH = 0.12;
    /** Below this share (6%) it is comfortable and the quality may go back up. */
    public static final double OVERHEAD_LOW = 0.06;

    private static final double ALPHA = 0.1;
    private static final double ALPHA_BASELINE = 0.05;
    private static final double ALPHA_CAPTURE = 0.2;
    private static final long RATE_WINDOW_MS = 1000L;
    /** A step down this soon after a step up means the step up was too early. */
    private static final long FLAP_WINDOW_MS = 30_000L;
    private static final long MAX_UP_DELAY_MS = 120_000L;
    /** This long without a step down: forgive, and try going back up at the normal pace again. */
    private static final long CALM_MS = 300_000L;

    private double emaMs = 0.0;
    private boolean haveEma = false;
    private int level = 0;
    private long slowSinceMs = -1L;
    private long fastSinceMs = -1L;
    private long holdUntilMs = 0L;

    // what a capture costs the player
    private double baselineMs = 0.0;      // frames that do not contain a capture
    private boolean haveBaseline = false;
    private double captureMs = 0.0;       // frames that do
    private boolean haveCapture = false;
    private double captureRate = 0.0;     // captures per second
    private int windowCaptures = 0;
    private long windowStartMs = -1L;

    private long upDelayMs = UP_AFTER_MS;
    private long lastUpMs = -1L;
    private long lastDownMs = -1L;

    /** For callers that cannot tell which frames contain a capture: only the absolute rule applies. */
    public void onFrame(long nowMs, double frameMs, boolean captureActive) {
        onFrame(nowMs, frameMs, false, captureActive);
    }

    /**
     * @param nowMs         current time
     * @param frameMs       duration of the last frame
     * @param afterCapture  true if a feed was captured at the END of the frame before this one: this frame's time contains its cost
     * @param captureActive true if a feed was rendered during the last second
     */
    public void onFrame(long nowMs, double frameMs, boolean afterCapture, boolean captureActive) {
        emaMs = haveEma ? emaMs + ALPHA * (frameMs - emaMs) : frameMs;
        haveEma = true;

        // what the captures cost: the frames that contain one, against the frames that do not
        if (windowStartMs < 0L) windowStartMs = nowMs;
        if (afterCapture) {
            captureMs = haveCapture ? captureMs + ALPHA_CAPTURE * (frameMs - captureMs) : frameMs;
            haveCapture = true;
            windowCaptures++;
        } else {
            baselineMs = haveBaseline ? baselineMs + ALPHA_BASELINE * (frameMs - baselineMs) : frameMs;
            haveBaseline = true;
        }
        if (nowMs - windowStartMs >= RATE_WINDOW_MS) {
            captureRate = windowCaptures * 1000.0 / (nowMs - windowStartMs);
            windowCaptures = 0;
            windowStartMs = nowMs;
        }
        double overhead = overhead();

        boolean overheadSlow = captureActive && overhead > OVERHEAD_HIGH;
        if (overheadSlow || (captureActive && emaMs > SLOW_MS)) {
            fastSinceMs = -1L;
            if (slowSinceMs < 0L) {
                slowSinceMs = nowMs;
            } else if (nowMs - slowSinceMs >= (overheadSlow ? DOWN_AFTER_OVERHEAD_MS : DOWN_AFTER_MS) && level < MAX_LEVEL) {
                level++;
                slowSinceMs = nowMs;               // wait another full window before the next step
                holdUntilMs = nowMs + HOLD_AFTER_DOWN_MS;
                if (lastUpMs >= 0L && nowMs - lastUpMs < FLAP_WINDOW_MS) {
                    upDelayMs = Math.min(MAX_UP_DELAY_MS, upDelayMs * 2L); // that step up was too early: wait longer next time
                }
                lastDownMs = nowMs;
            }
        } else if (emaMs < FAST_MS && overhead < OVERHEAD_LOW) {
            slowSinceMs = -1L;
            if (lastDownMs >= 0L && nowMs - lastDownMs > CALM_MS) upDelayMs = UP_AFTER_MS;
            if (fastSinceMs < 0L) {
                fastSinceMs = nowMs;
            } else if (nowMs - fastSinceMs >= upDelayMs && nowMs >= holdUntilMs && level > 0) {
                level--;
                fastSinceMs = nowMs;
                lastUpMs = nowMs;
            }
        } else {
            slowSinceMs = -1L;                      // in between: hold the current level
            fastSinceMs = -1L;
        }
    }

    /**
     * The share of the player's time that rendering the video takes, 0..1: what one capture adds to a frame, times the captures per
     * second. 0 while it is not known (no capture seen yet, or none lately).
     */
    public double overhead() {
        if (!haveBaseline || !haveCapture) return 0.0;
        return Math.max(0.0, captureMs - baselineMs) * captureRate / 1000.0;
    }

    /** What one capture adds to the frame it lands in (ms), as measured. */
    public double captureCostMs() {
        return (haveBaseline && haveCapture) ? Math.max(0.0, captureMs - baselineMs) : 0.0;
    }

    public int level() { return level; }
    public double smoothedFrameMs() { return emaMs; }

    /** Highest feed resolution allowed at the current level (never above {@code configuredMax}). */
    public int maxResolution(int configuredMax) {
        int cap;
        switch (level) {
            case 0: cap = Integer.MAX_VALUE; break;
            case 1: cap = 512; break;
            default: cap = 256; break;
        }
        return Math.min(configuredMax, cap);
    }

    /** Multiplier applied to every feed's frame rate. */
    public double fpsScale() {
        switch (level) {
            case 0: return 1.0;
            case 1: return 0.75;
            case 2: return 0.5;
            default: return 0.34;
        }
    }

    /** How many feeds may be rendered at once (never fewer than 1, never more than {@code configured}). */
    public int maxActiveFeeds(int configured) {
        int n;
        switch (level) {
            case 0: n = configured; break;
            case 1: n = configured - 1; break;
            case 2: n = configured / 2; break;
            default: n = 1; break;
        }
        return Math.max(1, Math.min(configured, n));
    }

    /** Highest view radius (chunks) allowed at the current level. */
    public int maxViewChunks(int configured) {
        int cap;
        switch (level) {
            case 0: cap = Integer.MAX_VALUE; break;
            case 1: cap = 12; break;
            case 2: cap = 8; break;
            default: cap = 6; break;
        }
        return Math.max(2, Math.min(configured, cap));
    }
}
