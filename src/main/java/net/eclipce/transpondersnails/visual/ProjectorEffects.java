package net.eclipce.transpondersnails.visual;

/**
 * The optics of the projected picture, as plain maths (no Minecraft classes, unit tested): how the picture fades at its edges, how
 * bright it is, how wide its glow is, where the light ray starts, and how bright the glowing parts of the snail are.
 */
public final class ProjectorEffects {

    private ProjectorEffects() {}

    /**
     * Where the picture mesh is cut, 0..1 across the picture (the same for both directions, symmetric). Dense near the edges, where the
     * fade-out happens, so it is smooth there; sparse in the middle, where the picture is evenly bright.
     */
    public static final float[] GRID = {
            0.00F, 0.02F, 0.04F, 0.06F, 0.08F, 0.10F, 0.15F, 0.25F, 0.35F, 0.50F,
            0.65F, 0.75F, 0.85F, 0.90F, 0.92F, 0.94F, 0.96F, 0.98F, 1.00F};

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }

    private static double smoothstep(double t) {
        double x = clamp01(t);
        return x * x * (3.0 - 2.0 * x);
    }

    // ---------------- the picture ----------------

    /** Width of the edge fade-out as a fraction of the picture: about 0.3 blocks, so a big screen has a proportionally thinner edge. */
    public static double edgeFeather(int size) {
        double f = VisualCallConstants.PROJECTOR_EDGE_FEATHER_BLOCKS / Math.max(1, size);
        return Math.max(VisualCallConstants.PROJECTOR_EDGE_FEATHER_MIN, Math.min(VisualCallConstants.PROJECTOR_EDGE_FEATHER_MAX, f));
    }

    /** Opacity factor 0..1 at a point of the picture (u, v in 0..1): 0 at the very edge, 1 from {@code feather} inwards. */
    public static double edgeAlpha(double u, double v, double feather) {
        double d = Math.min(Math.min(u, 1.0 - u), Math.min(v, 1.0 - v));
        return smoothstep(d / Math.max(1.0E-6, feather));
    }

    /** Brightness factor at a point of the picture: 1 in the middle, 1 - PROJECTOR_VIGNETTE in the corners. */
    public static double vignette(double u, double v) {
        double dx = 2.0 * u - 1.0;
        double dy = 2.0 * v - 1.0;
        double r2 = (dx * dx + dy * dy) / 2.0; // 0 in the middle, 1 in a corner
        return 1.0 - VisualCallConstants.PROJECTOR_VIGNETTE * clamp01(r2);
    }

    /** Overall brightness factor: a longer throw and a bigger picture spread the same light thinner. */
    public static double throwBrightness(double depth, int size, int largestScreen) {
        double throwPart = clamp01(depth / VisualCallConstants.PROJECTOR_MAX_THROW);
        double sizePart = largestScreen <= 1 ? 0.0 : clamp01((size - 1.0) / (largestScreen - 1.0));
        return 1.0 - VisualCallConstants.PROJECTOR_THROW_DIMMING * throwPart - VisualCallConstants.PROJECTOR_SIZE_DIMMING * sizePart;
    }

    /** Opacity factor for the ambient light (0-15) at the screen: 1 in the dark, 1 - PROJECTOR_DAYLIGHT_WASHOUT in full daylight. */
    public static double ambientWashout(int ambientLight) {
        return 1.0 - VisualCallConstants.PROJECTOR_DAYLIGHT_WASHOUT * clamp01(ambientLight / 15.0);
    }

    // ---------------- the glow and the ray ----------------

    /**
     * How strongly the glow and the ray show: PROJECTOR_GLOW_NIGHT in the dark, PROJECTOR_GLOW_DAY in full daylight (light only shows against
     * darkness, but a ray that is too strong in the dark would haze over the picture - the thing the viewer is there to see).
     */
    public static double glowStrength(int ambientLight) {
        double dark = 1.0 - clamp01(ambientLight / 15.0);
        return VisualCallConstants.PROJECTOR_GLOW_DAY + (VisualCallConstants.PROJECTOR_GLOW_NIGHT - VisualCallConstants.PROJECTOR_GLOW_DAY) * dark;
    }

    /**
     * Brightness factor 0..1 of the picture for the light (0-15) around the screen: the picture has a light level of its own
     * (PROJECTOR_IMAGE_LIGHT) and is never darker than that, or than the surroundings if those are brighter - then the vanilla light curve.
     */
    public static double pictureBrightness(int worldLight) {
        int light = Math.max(worldLight, VisualCallConstants.PROJECTOR_IMAGE_LIGHT);
        double lt = Math.min(15, Math.max(0, light)) / 15.0;
        return Math.min(1.0, lt / (4.0 - 3.0 * lt));
    }

    /**
     * The Brightness setting (0-1) the world is lit with while a feed is captured: the camera's exposure, but never lower than the
     * player's own setting. NaN (a broken setting) counts as the camera's exposure.
     */
    public static double exposureFor(double playerBrightness) {
        double camera = clamp01(VisualCallConstants.FEED_CAPTURE_BRIGHTNESS);
        if (Double.isNaN(playerBrightness)) return camera;
        return Math.max(playerBrightness, camera);
    }

    /** Width of the glow around the picture, in blocks. */
    public static double bloomWidth(int size) {
        double w = VisualCallConstants.PROJECTOR_BLOOM_WIDTH_BASE + VisualCallConstants.PROJECTOR_BLOOM_WIDTH_PER_SIZE * size;
        return Math.max(VisualCallConstants.PROJECTOR_BLOOM_WIDTH_MIN, Math.min(VisualCallConstants.PROJECTOR_BLOOM_WIDTH_MAX, w));
    }

    /**
     * Where the light ray starts, in the snail block's local coordinates (0..1): the center of the top part of the projector, for a snail
     * with the given FACING (the model is drawn for north and turned like the blockstate turns it: east 90 degrees, south 180, west 270).
     */
    public static double[] lensPosition(int fx, int fz) {
        double x = VisualCallConstants.PROJECTOR_LENS_X / 16.0 - 0.5;
        double z = VisualCallConstants.PROJECTOR_LENS_Z / 16.0 - 0.5;
        // clockwise turn seen from above, by 0 / 90 / 180 / 270 degrees: north / east / south / west
        int turns = fz < 0 ? 0 : fx > 0 ? 1 : fz > 0 ? 2 : 3;
        double rx = x;
        double rz = z;
        for (int i = 0; i < turns; i++) {  // one quarter turn clockwise: (x, z) -> (-z, x)
            double nx = -rz;
            double nz = rx;
            rx = nx;
            rz = nz;
        }
        return new double[]{0.5 + rx, VisualCallConstants.PROJECTOR_LENS_Y / 16.0, 0.5 + rz};
    }

    /** Light level (0-15) of a glowing part: its full level scaled by how far the picture has faded in (0..1). */
    public static int glowLight(int fullLevel, double ease) {
        return (int) Math.round(Math.max(0, Math.min(15, fullLevel)) * clamp01(ease));
    }
}
