package net.eclipce.transpondersnails.visual;

import net.eclipce.transpondersnails.visual.SnailGuiLayout.Rect;

/**
 * The zoom bar of the projector settings menu: the striped bar on the left of the panel (assets/transpondersnails/textures/gui/visual_snail_projector_settings_gui.png) with a small
 * red indicator beside it (textures/gui/zoom_indicator.png). Zooming in is a BIGGER screen (what the mouse wheel does: wheel up = bigger), so the top of the bar is the biggest
 * size and the bottom the smallest. Clicking the top half of the bar zooms in one size, the bottom half zooms out, and the indicator shows where the size is between the two
 * extremes of the range for the distance right now. Pure (no Minecraft classes), unit tested - and checked against the textures themselves.
 *
 * <pre>
 *   the bar is 13 dark stripes, 3 pixels thick every 6 pixels, from y 128 to y 202. The indicator is a left-pointing triangle in a 32 x 32 picture (the triangle is columns 14-16,
 *   rows 14-18 of it); it rides in the gap on the right of the bar, and its middle row is on the middle of the top stripe when zoomed all the way in and on the middle of the
 *   bottom stripe when zoomed all the way out.
 * </pre>
 */
public final class ZoomBarLayout {

    private ZoomBarLayout() {}

    public enum Direction { IN, OUT }

    /** The bar as drawn in the texture (all of its stripes). */
    public static final Rect BAR = new Rect(66, 128, 15, 75);
    /** Where a click counts: the bar and two pixels around it (kept clear of the direction pad, which starts at x 84). */
    public static final Rect ZONE = new Rect(BAR.x() - 2, BAR.y() - 2, BAR.w() + 4, BAR.h() + 4);
    /** Above this row a click zooms in, from it down it zooms out: the middle of the bar. */
    public static final int SPLIT_Y = BAR.y() + BAR.h() / 2;

    /** The size of the indicator's picture. */
    public static final int INDICATOR_SIZE = 32;
    /** Texture x of the picture's left edge: puts the triangle (its columns 14-16) at x 82-84, in the gap right of the bar, pointing at it. */
    public static final int INDICATOR_X = 68;
    /** The row of the picture that is the middle of the triangle. */
    public static final int INDICATOR_MIDDLE_ROW = 16;
    /** The texture rows the middle of the triangle is on when zoomed all the way in (middle of the top stripe) and all the way out (middle of the bottom stripe). */
    public static final int TOP_ROW = 129;
    public static final int BOTTOM_ROW = 201;

    private static boolean contains(Rect r, double x, double y) {
        return x >= r.x() && x < r.right() && y >= r.y() && y < r.bottom();
    }

    /** Which end of the bar a point of the texture is in (the top half zooms in, the bottom half out), or null if it is not on the bar. */
    public static Direction at(double x, double y) {
        if (!contains(ZONE, x, y)) return null;
        return y < SPLIT_Y ? Direction.IN : Direction.OUT;
    }

    /** The part of the bar a click would be on, for lighting it up. */
    public static Rect half(Direction direction) {
        return direction == Direction.IN
                ? new Rect(BAR.x(), BAR.y(), BAR.w(), SPLIT_Y - BAR.y())
                : new Rect(BAR.x(), SPLIT_Y, BAR.w(), BAR.bottom() - SPLIT_Y);
    }

    /** The size after one step of zoom: in = one bigger, out = one smaller, kept inside the range. */
    public static int zoomed(int size, Direction direction, int min, int max) {
        int next = size + (direction == Direction.IN ? 1 : -1);
        return Math.max(min, Math.min(Math.max(min, max), next));
    }

    /**
     * Texture y of the top edge of the indicator's picture for this size: the biggest size (all the way in) has the triangle on the top stripe, the smallest (all the way out) on the
     * bottom stripe, anything between in proportion. With no range (min = max) there is nothing to zoom either way and it sits in the middle.
     */
    public static int indicatorY(int size, int min, int max) {
        int row;
        if (max <= min) {
            row = (TOP_ROW + BOTTOM_ROW) / 2;
        } else {
            double fraction = Math.max(0.0, Math.min(1.0, (size - min) / (double) (max - min)));
            row = BOTTOM_ROW - (int) Math.round(fraction * (BOTTOM_ROW - TOP_ROW));
        }
        return row - INDICATOR_MIDDLE_ROW;
    }
}
