package net.eclipce.transpondersnails.visual;

/**
 * Adaptive video quality, pure maths (no Minecraft classes, unit tested).
 *
 * The cost of a video feed grows with its resolution, but a screen that is far away - or small - covers only a few
 * pixels of the viewer's display, so rendering it at full resolution is wasted work. These helpers pick the smallest
 * resolution "tier" that still gives one feed pixel per screen pixel, and lower frame rates / view radii for
 * feeds that are far away or small.
 */
public final class VisualQuality {

    private VisualQuality() {}

    /**
     * Resolutions a feed is rendered at (square): the powers of two and a step in between each (x1.5). The cost of a feed grows with the
     * square of its resolution, so with only powers of two a screen that needs a little more than 1024 pixels is rendered at 2048 - four
     * times the work for a few pixels more. The halfway tiers (384, 768, 1536) make that 2.25 times.
     */
    public static final int[] TIERS = {256, 384, 512, 768, 1024, 1536, 2048};

    /**
     * Feed pixels per screen pixel that we aim for. 1.0 = exactly as sharp as the viewer's display can show. A little under that is not
     * visible (the picture is filtered, has a soft edge and a vignette) but is a lot cheaper when close to a big screen on a large display:
     * 0.85 is 28% fewer pixels. Put it back to 1.0 for the sharpest possible picture.
     */
    public static final double SUPERSAMPLE = 0.85;

    /** Lowest frame rate a distant feed is throttled to. */
    public static final int MIN_FPS = 5;

    /**
     * How many pixels of the viewer's display (vertically) a screen covers.
     *
     * @param screenBlocks   height of the screen in blocks
     * @param distanceBlocks distance from the camera to the screen
     * @param windowHeightPx height of the viewer's window in pixels
     * @param fovDegrees     the viewer's vertical field of view (Minecraft's FOV option)
     */
    public static double projectedPixels(double screenBlocks, double distanceBlocks, int windowHeightPx, double fovDegrees) {
        double d = Math.max(distanceBlocks, 0.25);
        double fov = Math.toRadians(Math.max(10.0, Math.min(170.0, fovDegrees)));
        return screenBlocks * windowHeightPx / (2.0 * d * Math.tan(fov / 2.0));
    }

    /** The largest tier that is <= res (the smallest tier if res is below all of them). */
    public static int floorTier(int res) {
        int best = TIERS[0];
        for (int t : TIERS) {
            if (t <= res) best = t;
        }
        return best;
    }

    /** The smallest tier that is >= pixels (the largest tier if pixels exceeds all of them). */
    public static int ceilTier(double pixels) {
        for (int t : TIERS) {
            if (t >= pixels) return t;
        }
        return TIERS[TIERS.length - 1];
    }

    /** The tier a screen covering {@code pixels} display pixels should be fed at, within [minRes, maxRes]. */
    public static int pickResolution(double pixels, int minRes, int maxRes) {
        int lo = floorTier(minRes);
        int hi = Math.max(lo, floorTier(maxRes));
        int want = ceilTier(pixels * SUPERSAMPLE);
        return Math.max(lo, Math.min(hi, want));
    }

    /** Frame rate for a feed whose screen is {@code distanceBlocks} away: full rate up close, throttled further out. */
    public static int pickFps(double distanceBlocks, int maxFps) {
        double factor;
        if (distanceBlocks <= 16.0) factor = 1.0;
        else if (distanceBlocks <= 32.0) factor = 0.67;
        else if (distanceBlocks <= 48.0) factor = 0.5;
        else factor = 0.34;
        int fps = (int) Math.round(maxFps * factor);
        return Math.min(maxFps, Math.max(Math.min(MIN_FPS, maxFps), fps));
    }

    /**
     * A low resolution cannot show distant detail anyway, so it does not need the full view distance either - this is
     * what makes small / far feeds cheap to render, not just cheap to store.
     */
    public static int viewRadiusFor(int resolution, int maxRadiusChunks) {
        int cap;
        if (resolution <= 256) cap = 8;
        else if (resolution <= 512) cap = 12;
        else if (resolution <= 768) cap = 14;
        else cap = Integer.MAX_VALUE;
        return Math.max(2, Math.min(maxRadiusChunks, cap));
    }
}
