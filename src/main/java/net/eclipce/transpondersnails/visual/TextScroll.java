package net.eclipce.transpondersnails.visual;

/**
 * Text that is too wide for its box scrolls: it waits at the start, slides to its end, waits, and slides back, over and over.
 * Pure (no Minecraft classes), unit tested; the GUI draws the text shifted left by {@link #offset} inside a clipped box.
 */
public final class TextScroll {

    private TextScroll() {}

    /** How long the text rests at either end. */
    public static final long PAUSE_MS = 900L;
    /** How fast it slides, in pixels (GUI units) a second. */
    public static final double SPEED = 22.0;

    /**
     * How many pixels to shift the text to the left right now: 0 if it fits, otherwise between 0 and (textWidth - boxWidth).
     * The same clock gives the same answer, so every text in a list moves together.
     */
    public static int offset(int textWidth, int boxWidth, long nowMs) {
        int travel = textWidth - boxWidth;
        if (travel <= 0) return 0;
        long moveMs = Math.max(1L, Math.round(travel * 1000.0 / SPEED));
        long cycle = 2L * (PAUSE_MS + moveMs);
        long t = Math.floorMod(nowMs, cycle);
        if (t < PAUSE_MS) return 0;                                   // resting at the start
        t -= PAUSE_MS;
        if (t < moveMs) return (int) Math.round(travel * (t / (double) moveMs));       // sliding to the end
        t -= moveMs;
        if (t < PAUSE_MS) return travel;                              // resting at the end
        t -= PAUSE_MS;
        return (int) Math.round(travel * (1.0 - t / (double) moveMs));                 // sliding back
    }
}
