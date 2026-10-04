package net.eclipce.transpondersnails.visual;

/**
 * Limits of the projector settings (screen size and offsets). Pure logic, shared by the menu (client) and the server, which
 * never trusts what the client sends: every value is clamped and snapped here before it is stored.
 */
public final class VisualScreenConfig {

    private VisualScreenConfig() {}

    /** Clamps a size to 1 .. largestScreen (the configured largest screen). */
    public static int clampSize(int size, int largestScreen) {
        return Math.max(VisualCallConstants.SCREEN_MIN_SIZE,
                Math.min(Math.max(VisualCallConstants.SCREEN_MIN_SIZE, largestScreen), size));
    }

    public static int clampSize(int size) {
        return clampSize(size, VisualCallConstants.SCREEN_MAX_SIZE);
    }

    /**
     * The "extra depth" (blocks the screen is pushed further BACK, away from the snail): 0 = the original position, never
     * negative (never towards the snail), at most SCREEN_BACK_MAX, in quarter steps. NaN becomes 0.
     */
    public static double snapBack(double value) {
        if (Double.isNaN(value) || value <= 0.0) return 0.0;
        double step = VisualCallConstants.SCREEN_OFFSET_STEP;
        double snapped = Math.round(value / step) * step;
        return Math.max(0.0, Math.min(VisualCallConstants.SCREEN_BACK_MAX, snapped));
    }

    public static float snapBack(float value) {
        return (float) snapBack((double) value);
    }

    /** Clamps an offset to +-SCREEN_OFFSET_MAX blocks and snaps it to a multiple of SCREEN_OFFSET_STEP. NaN becomes 0. */
    public static double snapOffset(double value) {
        if (Double.isNaN(value)) return 0.0;
        double step = VisualCallConstants.SCREEN_OFFSET_STEP;
        double snapped = Math.round(value / step) * step;
        double max = VisualCallConstants.SCREEN_OFFSET_MAX;
        return Math.max(-max, Math.min(max, snapped));
    }

    public static float snapOffset(float value) {
        return (float) snapOffset((double) value);
    }
}
