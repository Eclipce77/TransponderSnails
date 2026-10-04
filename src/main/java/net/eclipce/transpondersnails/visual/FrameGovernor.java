package net.eclipce.transpondersnails.visual;

/**
 * Protects the viewer's frame rate. Fed with the frame times of the client while video feeds are being rendered, it
 * steps the video quality down (lower resolution cap, lower frame rate, fewer simultaneous feeds, shorter view radius)
 * when the game gets slow, and back up again - slowly - once it is comfortable. Pure logic, unit tested.
 *
 * Only slowness that happens WHILE a feed was rendered counts: if the game is slow for some other reason there is
 * nothing to gain from degrading the video.
 */
public final class FrameGovernor {

    public static final int MAX_LEVEL = 3;

    /** Smoothed frame time above this (ms) for DOWN_AFTER_MS -> one level worse. (28 ms ~ 36 fps) */
    public static final double SLOW_MS = 28.0;
    /** Smoothed frame time below this (ms) for UP_AFTER_MS -> one level better. (20 ms = 50 fps) */
    public static final double FAST_MS = 20.0;
    public static final long DOWN_AFTER_MS = 1500L;
    public static final long UP_AFTER_MS = 6000L;
    /** After stepping down, do not step back up for at least this long (stops it flapping). */
    public static final long HOLD_AFTER_DOWN_MS = 10000L;

    private static final double ALPHA = 0.1;

    private double emaMs = 0.0;
    private boolean haveEma = false;
    private int level = 0;
    private long slowSinceMs = -1L;
    private long fastSinceMs = -1L;
    private long holdUntilMs = 0L;

    /**
     * @param nowMs         current time
     * @param frameMs       duration of the last frame
     * @param captureActive true if a feed was rendered during the last second
     */
    public void onFrame(long nowMs, double frameMs, boolean captureActive) {
        emaMs = haveEma ? emaMs + ALPHA * (frameMs - emaMs) : frameMs;
        haveEma = true;

        if (captureActive && emaMs > SLOW_MS) {
            fastSinceMs = -1L;
            if (slowSinceMs < 0L) {
                slowSinceMs = nowMs;
            } else if (nowMs - slowSinceMs >= DOWN_AFTER_MS && level < MAX_LEVEL) {
                level++;
                slowSinceMs = nowMs;               // wait another full window before the next step
                holdUntilMs = nowMs + HOLD_AFTER_DOWN_MS;
            }
        } else if (emaMs < FAST_MS) {
            slowSinceMs = -1L;
            if (fastSinceMs < 0L) {
                fastSinceMs = nowMs;
            } else if (nowMs - fastSinceMs >= UP_AFTER_MS && nowMs >= holdUntilMs && level > 0) {
                level--;
                fastSinceMs = nowMs;
            }
        } else {
            slowSinceMs = -1L;                      // in between: hold the current level
            fastSinceMs = -1L;
        }
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
