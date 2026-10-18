package net.eclipce.transpondersnails.visual;

/**
 * The picture shown on the screen in the settings' 3D view: a small test scene (sky, sun, hills) put through the projector's REAL optics -
 * the fade at the edges, the vignette in the corners, and the dimming of a long throw and a big picture (see {@link ProjectorEffects}) - so
 * the preview shows how a projection on that screen will look. Pure (no Minecraft classes), unit tested.
 */
public final class ProjectorPicture {

    private ProjectorPicture() {}

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * Math.max(0.0, Math.min(1.0, t)));
    }

    private static int mix(int rgbA, int rgbB, double t) {
        return (lerp((rgbA >> 16) & 255, (rgbB >> 16) & 255, t) << 16) | (lerp((rgbA >> 8) & 255, (rgbB >> 8) & 255, t) << 8) | lerp(rgbA & 255, rgbB & 255, t);
    }

    /** The test scene at a point of the picture: u across (0 left .. 1 right), v up (0 bottom .. 1 top). RGB. */
    public static int color(double u, double v) {
        double hill = 0.27 + 0.07 * Math.sin(u * 6.0) + 0.04 * Math.sin(u * 13.0 + 1.0);
        if (v < hill) {
            return mix(0x4FA03C, 0x2A6428, (hill - v) / Math.max(0.05, hill)); // the grass, darker at the bottom
        }
        double dx = u - 0.74;
        double dy = v - 0.74;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 0.11) return 0xFFE46A;                                         // the sun
        int sky = mix(0xD2EBFF, 0x3F7FD8, (v - hill) / (1.0 - hill));          // pale at the horizon, blue above
        return d < 0.17 ? mix(sky, 0xFFF3B0, (0.17 - d) / 0.06 * 0.6) : sky;    // a halo round the sun
    }

    /**
     * One point of the picture as the projector shows it: the scene's colour darkened by the vignette and the throw, and an alpha that
     * fades to nothing at the very edge (so the wall behind shows through there).
     *
     * @return ARGB
     */
    public static int argb(double u, double v, int size, double depth, int largestScreen) {
        double alpha = ProjectorEffects.edgeAlpha(u, v, ProjectorEffects.edgeFeather(size));
        double bright = Math.max(0.0, ProjectorEffects.vignette(u, v) * ProjectorEffects.throwBrightness(depth, size, largestScreen));
        int rgb = color(u, v);
        int r = (int) Math.min(255, Math.round(((rgb >> 16) & 255) * bright));
        int g = (int) Math.min(255, Math.round(((rgb >> 8) & 255) * bright));
        int b = (int) Math.min(255, Math.round((rgb & 255) * bright));
        return ((int) Math.round(Math.max(0.0, Math.min(1.0, alpha)) * 255) << 24) | (r << 16) | (g << 8) | b;
    }
}
