package net.eclipce.transpondersnails.visual;

/**
 * Where everything of the projector settings menu goes on its texture (assets/transpondersnails/textures/gui/visual_snail_gui.png), in
 * pixels of that 256 x 256 texture, plus where the texture is put on the screen. Pure (no Minecraft classes), unit tested - and checked
 * against the texture itself (the rectangles below were measured from the texture and from its layout map).
 *
 * <pre>
 *   TITLE_ZONE   the red box above the panel: the title and the description
 *   SIZE_BAR     first slider row  (Screen size)  with its round reset button SIZE_RESET on the left
 *   SIDE_BAR     second slider row (Left/Right)   with SIDE_RESET
 *   UP_BAR       third slider row  (Down/Up)      with UP_RESET
 *   DEPTH_ZONE   the yellow box below the panel: the depth, as plain text; DEPTH_BAR is where the track of the extra-depth slider is
 *                drawn in the rare case that nothing is behind the snail (the texture has no bar there)
 *   DISPLAY      the big dark display: empty for now
 * </pre>
 */
public final class SnailGuiLayout {

    private SnailGuiLayout() {}

    /** A rectangle in texture pixels (x, y = top left corner). */
    public record Rect(int x, int y, int w, int h) {
        public int right() { return x + w; }
        public int bottom() { return y + h; }
        public int centerX() { return x + w / 2; }
        public boolean overlaps(Rect o) { return x < o.right() && o.x < right() && y < o.bottom() && o.y < bottom(); }
        public boolean inside(Rect o) { return x >= o.x && y >= o.y && right() <= o.right() && bottom() <= o.bottom(); }

        /** The smallest rectangle that contains both. */
        public Rect union(Rect o) {
            int nx = Math.min(x, o.x);
            int ny = Math.min(y, o.y);
            return new Rect(nx, ny, Math.max(right(), o.right()) - nx, Math.max(bottom(), o.bottom()) - ny);
        }
    }

    public static final int TEXTURE_SIZE = 256;

    /** The panel as drawn in the texture (its non-transparent pixels: x 53..203, y 41..214). */
    public static final Rect PANEL = new Rect(53, 41, 151, 174);
    /** The red box of the layout map: above the panel, on the transparent part of the texture. */
    public static final Rect TITLE_ZONE = new Rect(60, 12, 136, 27);
    /** The big dark display (its dark inside). Nothing is placed on it yet. */
    public static final Rect DISPLAY = new Rect(67, 55, 122, 58);

    public static final Rect SIZE_BAR = new Rect(92, 128, 100, 16);
    public static final Rect SIZE_RESET = new Rect(64, 128, 16, 16);
    public static final Rect SIDE_BAR = new Rect(92, 153, 100, 16);
    public static final Rect SIDE_RESET = new Rect(64, 153, 16, 16);
    public static final Rect UP_BAR = new Rect(92, 178, 100, 16);
    public static final Rect UP_RESET = new Rect(64, 178, 16, 16);

    /** The yellow box of the layout map: below the panel, on the transparent part of the texture. */
    public static final Rect DEPTH_ZONE = new Rect(61, 216, 134, 15);
    /** The track of the extra-depth slider, inside the depth zone (one pixel in from its outline). */
    public static final Rect DEPTH_BAR = new Rect(62, 217, 132, 13);

    /** The part of the texture that is used: from the top of the title zone to the bottom of the depth zone. */
    public static final Rect USED = TITLE_ZONE.union(PANEL).union(DEPTH_ZONE);

    /** Gap between the panel and the right edge of the screen, so the middle of the view (where the snail and its screen are) stays free. */
    private static final int MARGIN_RIGHT = 20;
    private static final int MARGIN = 4;

    /** Screen x of the texture's left edge: the panel on the right of the screen, never cut off on the left. */
    public static int originX(int screenWidth) {
        return Math.max(8 - PANEL.x(), screenWidth - MARGIN_RIGHT - PANEL.right());
    }

    /** Screen y of the texture's top edge: the used part centred vertically, never above the top of the screen. */
    public static int originY(int screenHeight) {
        int centred = screenHeight / 2 - (USED.y() + USED.h() / 2);
        int lowest = screenHeight - MARGIN - USED.bottom();
        return Math.max(MARGIN - USED.y(), Math.min(centred, lowest));
    }
}
