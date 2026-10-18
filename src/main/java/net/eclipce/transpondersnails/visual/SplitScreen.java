package net.eclipce.transpondersnails.visual;

/**
 * Two pictures on one screen (an intercepted call shows both ends side by side). The screen is split down the middle with a thin gap; each
 * half shows the middle part of its camera's square picture, cropped to fill the half (no black bars). Pure (no Minecraft classes), unit tested.
 */
public final class SplitScreen {

    private SplitScreen() {}

    /** The gap between the halves, as a fraction of the screen's width. */
    public static final double GAP = 0.012;

    /** A rectangle in the unit square: u from {@code u0} to {@code u1}, v from {@code v0} to {@code v1}. */
    public record Rect(double u0, double v0, double u1, double v1) {
        public double width() { return u1 - u0; }
        public double height() { return v1 - v0; }
    }

    /** Where on the screen (0..1 across and down) a half goes: half 0 on the left, half 1 on the right. */
    public static Rect screenHalf(int half) {
        double mid = GAP / 2.0;
        return half == 0 ? new Rect(0.0, 0.0, 0.5 - mid, 1.0) : new Rect(0.5 + mid, 0.0, 1.0, 1.0);
    }

    /**
     * Which part of a square picture fills a rectangle of the given shape: all of the picture's height or width, whichever fits, from the
     * middle outwards. {@code aspect} is the rectangle's width divided by its height.
     */
    public static Rect cropToFill(double aspect) {
        if (!(aspect > 0.0)) return new Rect(0.0, 0.0, 1.0, 1.0);
        if (aspect < 1.0) {                       // narrower than the picture: keep the middle strip
            double half = aspect / 2.0;
            return new Rect(0.5 - half, 0.0, 0.5 + half, 1.0);
        }
        double half = 1.0 / aspect / 2.0;         // wider: keep the middle band
        return new Rect(0.0, 0.5 - half, 1.0, 0.5 + half);
    }

    /** The part of a square camera picture shown in {@code half} of a screen that is {@code screenWidth} x {@code screenHeight}. */
    public static Rect pictureFor(int half, double screenWidth, double screenHeight) {
        Rect r = screenHalf(half);
        return cropToFill(r.width() * screenWidth / (r.height() * screenHeight));
    }
}
