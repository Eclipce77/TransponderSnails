package net.eclipce.transpondersnails.visual;

import net.eclipce.transpondersnails.visual.SnailGuiLayout.Rect;

/**
 * The direction pad of the projector settings menu: four arrows (up, down, left, right) and a button in the middle that puts the projector back where it was. Where each one
 * is on the texture (assets/transpondersnails/textures/gui/visual_snail_projector_settings_gui.png, 256 x 256), which part of the texture counts as which button, and what pressing
 * one does. Pure (no Minecraft classes), unit tested - and checked against the texture itself: the five button textures (textures/gui/buttons/arrow_up.png, arrow_down.png, arrow_left.png,
 * arrow_right.png, center_button.png) are drawn into the GUI texture at exactly the positions below (they match it pixel for pixel), and they are what the menu draws over a button to
 * light it up, so the highlight has the button's own shape.
 *
 * <pre>
 *   each button is a 32 x 32 picture; the arrow or disc inside it is much smaller (the arrows are 3 pixels thick), so the buttons are NOT clicked on their drawn pixels but in
 *   zones: the middle button is the circle of radius 16 around the centre of the pad, and each arrow is the 32 x 32 square of its picture - together they leave no gap on either axis.
 * </pre>
 */
public final class ProjectorPadLayout {

    private ProjectorPadLayout() {}

    /** The buttons, in the order the menu numbers them (its tooltips use the ordinal). */
    public enum Button { UP, DOWN, LEFT, RIGHT, CENTER }

    /** The size of every button picture, in pixels. */
    public static final int SPRITE_SIZE = 32;

    // Where the pictures are in the GUI texture (measured: the picture matches the texture at that place pixel for pixel).
    public static final Rect UP_RECT = new Rect(112, 122, SPRITE_SIZE, SPRITE_SIZE);
    public static final Rect DOWN_RECT = new Rect(111, 176, SPRITE_SIZE, SPRITE_SIZE);
    public static final Rect LEFT_RECT = new Rect(84, 149, SPRITE_SIZE, SPRITE_SIZE);
    public static final Rect RIGHT_RECT = new Rect(139, 149, SPRITE_SIZE, SPRITE_SIZE);
    public static final Rect CENTER_RECT = new Rect(112, 149, SPRITE_SIZE, SPRITE_SIZE);

    /** The middle of the pad: the middle of the centre button's picture. */
    public static final double CENTER_X = CENTER_RECT.x() + SPRITE_SIZE / 2.0;
    public static final double CENTER_Y = CENTER_RECT.y() + SPRITE_SIZE / 2.0;
    /** The middle button is a circle of this radius (half its picture): inside it nothing else can be clicked. */
    public static final double CENTER_RADIUS = SPRITE_SIZE / 2.0;

    /** Everything the pad can be clicked in: the smallest rectangle around the five pictures. */
    public static final Rect ZONE = UP_RECT.union(DOWN_RECT).union(LEFT_RECT).union(RIGHT_RECT).union(CENTER_RECT);

    /** The rectangle of a button's picture in the texture. */
    public static Rect rect(Button button) {
        return switch (button) {
            case UP -> UP_RECT;
            case DOWN -> DOWN_RECT;
            case LEFT -> LEFT_RECT;
            case RIGHT -> RIGHT_RECT;
            case CENTER -> CENTER_RECT;
        };
    }

    /** The file name (without .png, in textures/gui/buttons/) of a button's picture. */
    public static String pictureName(Button button) {
        return switch (button) {
            case UP -> "arrow_up";
            case DOWN -> "arrow_down";
            case LEFT -> "arrow_left";
            case RIGHT -> "arrow_right";
            case CENTER -> "center_button";
        };
    }

    private static boolean contains(Rect r, double x, double y) {
        return x >= r.x() && x < r.right() && y >= r.y() && y < r.bottom();
    }

    /**
     * Which button is at this point of the texture (pass the mouse position minus where the texture is on the screen), or null if none.
     * The middle button wins inside its circle. Outside it a point belongs to the arrow whose square it is in; near the middle the squares overlap a little, and there the arrow
     * the point lies furthest towards (along the axis it is furthest from the middle on) gets it, so every point has exactly one answer.
     */
    public static Button at(double x, double y) {
        double dx = x - CENTER_X;
        double dy = y - CENTER_Y;
        if (dx * dx + dy * dy <= CENTER_RADIUS * CENTER_RADIUS) {
            return Button.CENTER;
        }
        boolean up = contains(UP_RECT, x, y);
        boolean down = contains(DOWN_RECT, x, y);
        boolean left = contains(LEFT_RECT, x, y);
        boolean right = contains(RIGHT_RECT, x, y);
        int candidates = (up ? 1 : 0) + (down ? 1 : 0) + (left ? 1 : 0) + (right ? 1 : 0);
        if (candidates == 0) return null;
        if (candidates == 1) return up ? Button.UP : down ? Button.DOWN : left ? Button.LEFT : Button.RIGHT;
        // overlap: the dominant direction from the middle (ties go to the vertical arrows)
        if (Math.abs(dy) >= Math.abs(dx)) {
            Button vertical = dy < 0 ? Button.UP : Button.DOWN;
            if (vertical == Button.UP ? up : down) return vertical;
        }
        Button horizontal = dx < 0 ? Button.LEFT : Button.RIGHT;
        if (horizontal == Button.LEFT ? left : right) return horizontal;
        return up ? Button.UP : down ? Button.DOWN : left ? Button.LEFT : Button.RIGHT;
    }

    /**
     * The zoom (the screen size) the middle button goes back to: the default size, kept inside what the menu allows (the largest screen of the settings). This is the size that is
     * PREFERRED: what is shown is it held inside the zoom range of the distance right now (see ProjectorPlacement), so far from a wall, where the smallest screen is bigger than the
     * default, "the default" is the smallest one there is.
     */
    public static int defaultSize(int largestScreen) {
        return VisualScreenConfig.clampSize(VisualCallConstants.SCREEN_DEFAULT_SIZE, largestScreen);
    }

    /** What the pad moves: how far the screen is slid sideways and up (blocks, 0 = in the middle) and the extra depth (blocks, 0 = original). */
    public record Offsets(float side, float up, float back) { }

    /**
     * What pressing a button does. An arrow slides the screen one step (SCREEN_OFFSET_STEP blocks) that way - left is a negative side offset and down a negative up offset, the same as
     * the left ends of the old sliders - and stops at the edge (SCREEN_OFFSET_MAX), always on a whole step. The middle button puts all three back: the projector is in its original place (and the zoom goes back to the default too: {@link #defaultSize}; the size is not part of the offsets).
     */
    public static Offsets apply(Button button, Offsets now) {
        float step = (float) VisualCallConstants.SCREEN_OFFSET_STEP;
        return switch (button) {
            case UP -> new Offsets(now.side(), VisualScreenConfig.snapOffset(now.up() + step), now.back());
            case DOWN -> new Offsets(now.side(), VisualScreenConfig.snapOffset(now.up() - step), now.back());
            case LEFT -> new Offsets(VisualScreenConfig.snapOffset(now.side() - step), now.up(), now.back());
            case RIGHT -> new Offsets(VisualScreenConfig.snapOffset(now.side() + step), now.up(), now.back());
            case CENTER -> new Offsets(0.0F, 0.0F, 0.0F);
        };
    }
}
