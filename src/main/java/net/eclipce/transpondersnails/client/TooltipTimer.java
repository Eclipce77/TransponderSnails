package net.eclipce.transpondersnails.client;

/**
 * Rules for the config screen's tooltips: WHEN one appears (vanilla's rule: the mouse has rested on the same thing for half a
 * second, so a tooltip does not pop up the instant the mouse passes over), HOW WIDE it may be (vanilla wraps at 170 pixels; a
 * narrow window gets a narrower tooltip), and WHERE a label counts as hovered. Pure (no Minecraft classes), unit tested.
 */
public final class TooltipTimer {

    /** Vanilla's delay for widget tooltips. */
    public static final long DELAY_MS = 500L;
    /** Vanilla's tooltip width. */
    public static final int MAX_WRAP_WIDTH = 170;
    public static final int MIN_WRAP_WIDTH = 110;
    /** A label counts as hovered from this many pixels above its text... */
    public static final int LABEL_HIT_ABOVE = 2;
    /** ...to this many below its top edge (the text is about 9 high). Must stay clear of the control drawn right below the label. */
    public static final int LABEL_HIT_BELOW = 10;

    private int target = -1;
    private long since = 0L;

    /**
     * Call once per frame with the option the mouse is over (-1 = none).
     *
     * @return true if the tooltip of that option should be shown now
     */
    public boolean update(int option, long nowMs) {
        if (option != target) { // moved to another option (or off): the clock starts again
            target = option;
            since = nowMs;
        }
        return option >= 0 && nowMs - since >= DELAY_MS;
    }

    /** Tooltip wrap width for a window of this (GUI-scaled) width: a third of it, between 110 and vanilla's 170. */
    public static int wrapWidth(int screenWidth) {
        return Math.max(MIN_WRAP_WIDTH, Math.min(MAX_WRAP_WIDTH, screenWidth / 3));
    }

    /** Is the mouse over a label whose text starts at {@code y}, is centred on {@code centerX} and {@code textWidth} wide? */
    public static boolean overLabel(int mouseX, int mouseY, int centerX, int y, int textWidth) {
        return mouseX >= centerX - textWidth / 2 && mouseX <= centerX + textWidth / 2
                && mouseY >= y - LABEL_HIT_ABOVE && mouseY <= y + LABEL_HIT_BELOW;
    }
}
