package net.eclipce.transpondersnails.visual;

/**
 * The zoom of a projector: like a real one, the further away the screen is, the bigger the picture has to be.
 *
 * <pre>
 *   minimum size = 1 + one for every PROJECTOR_BLOCKS_PER_SIZE_STEP (2) blocks of distance
 *   maximum size = PROJECTOR_ZOOM_RANGE (4) x the minimum
 *   both never above the configured largest screen
 * </pre>
 *
 * Examples (distance -> min..max): 0-2 blocks 1..4, 2-4 blocks 2..8, 4-6 blocks 3..12, 8-10 blocks 5..20, further 6..20 and up.
 * Pure maths, unit tested.
 */
public final class ProjectorZoom {

    private ProjectorZoom() {}

    /** The minimum before the configured cap: 1 + floor(distance / blocks per step). */
    private static long rawMin(double distance) {
        if (Double.isNaN(distance) || distance < 0.0) distance = 0.0;
        double steps = Math.floor(distance / VisualCallConstants.PROJECTOR_BLOCKS_PER_SIZE_STEP);
        return 1L + (long) Math.min(steps, 1.0E6); // infinity-safe
    }

    public static int minSize(double distance, int largestScreen) {
        int cap = Math.max(VisualCallConstants.SCREEN_MIN_SIZE, largestScreen);
        return (int) Math.max(VisualCallConstants.SCREEN_MIN_SIZE, Math.min(cap, rawMin(distance)));
    }

    public static int maxSize(double distance, int largestScreen) {
        int cap = Math.max(VisualCallConstants.SCREEN_MIN_SIZE, largestScreen);
        long raw = rawMin(distance) * VisualCallConstants.PROJECTOR_ZOOM_RANGE;
        return (int) Math.max(minSize(distance, cap), Math.min(cap, raw));
    }

    /** The size actually shown for a preferred size: the preference, held inside [min, max] for this distance. */
    public static int clamp(int preferred, double distance, int largestScreen) {
        return Math.max(minSize(distance, largestScreen), Math.min(maxSize(distance, largestScreen), preferred));
    }
}
