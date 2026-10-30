package net.eclipce.transpondersnails.visual;

import java.util.Locale;

/**
 * Texts of the projector settings menu that depend on a value. Pure (no Minecraft classes) so they are unit tested.
 */
public final class VisualMenuText {

    private VisualMenuText() {}

    /** 2 -> "2", 1.25 -> "1.25", -1.5 -> "1.5": the size of a value without its sign, and without trailing zeros. */
    public static String number(double value) {
        double a = Math.abs(value);
        return a == Math.rint(a) ? Integer.toString((int) a) : Double.toString(a);
    }

    /**
     * Label of an offset slider. While the screen is not moved along that axis it names the axis ("Left/Right"); once it is
     * moved it names ONLY the direction it was moved in, with the amount ("Right: 2") - the direction is not repeated.
     *
     * @param axisName the label for "no adjustment", e.g. "Left/Right"
     * @param negative the direction of negative values, e.g. "Left"
     * @param positive the direction of positive values, e.g. "Right"
     */
    public static String offsetLabel(double value, String axisName, String negative, String positive) {
        if (Math.abs(value) < 0.001) return axisName;
        return (value > 0 ? positive : negative) + ": " + number(value);
    }

    /**
     * The screen's size and how far away it is, as the menu writes it: "4 x 4 blocks, 1.5 away" (the distance to one decimal, always with a point, whatever the language of the
     * game is).
     */
    public static String sizeAndDistance(int size, double distance) {
        return size + " x " + size + " blocks, " + String.format(Locale.ROOT, "%.1f", distance) + " away";
    }
}
