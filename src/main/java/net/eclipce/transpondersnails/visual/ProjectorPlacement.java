package net.eclipce.transpondersnails.visual;

/**
 * Where a projector's screen is and how big it is, worked out from the settings and from how far the surface behind the snail
 * is. Pure maths (no Minecraft classes, unit tested); the one world-dependent input, the distance to the surface, comes from
 * {@link ProjectorSurface}. The client renderer, the settings menu (live preview) and the server (who is close enough to watch)
 * all use this, so they cannot disagree.
 *
 * <ul>
 *   <li>A surface is found straight behind the snail: the screen is laid on it (a hair in front, so it never z-fights).
 *       The depth is automatic; the "extra depth" setting is not used.</li>
 *   <li>No surface within reach: the screen floats SCREEN_FLOAT_DISTANCE behind the snail, plus the "extra depth"
 *       (0 .. SCREEN_BACK_MAX blocks further back - never towards the snail).</li>
 *   <li>The size is the preferred size held inside the zoom range for that distance ({@link ProjectorZoom}).</li>
 *   <li>Left/right and up/down offsets slide the screen along the surface; the surface is always looked for in line with the snail.</li>
 * </ul>
 */
public final class ProjectorPlacement {

    private ProjectorPlacement() {}

    /**
     * @param quad          the screen in the snail block's local coordinates
     * @param surfaceFound  true if it lies on a surface, false if it floats
     * @param depth         blocks between the snail's back face and the screen (before the tiny z-fight offset)
     * @param size          the size actually shown (preferred size inside [minSize, maxSize])
     * @param lightSteps    how many blocks from the snail the screen's own block is (0 = the snail's block); the light level is read there
     */
    public record Result(ScreenLayout.Quad quad, boolean surfaceFound, double depth, int size, int minSize, int maxSize, int lightSteps) {
        /** The middle of the screen, in the snail block's local coordinates. */
        public double[] center() {
            double[][] c = quad.corners();
            return new double[]{
                    (c[0][0] + c[1][0] + c[2][0] + c[3][0]) / 4.0,
                    (c[0][1] + c[1][1] + c[2][1] + c[3][1]) / 4.0,
                    (c[0][2] + c[1][2] + c[2][2] + c[3][2]) / 4.0};
        }
    }

    /**
     * @param fx,fz           the snail's FACING step
     * @param surfaceDistance blocks from the snail's back face to the surface straight behind it, or NaN if there is none in reach
     * @param largestScreen   the configured largest screen size
     */
    public static Result compute(int fx, int fz, int preferredSize, float side, float up, float back,
                                 int largestScreen, double surfaceDistance) {
        boolean found = !Double.isNaN(surfaceDistance);
        double depth = found
                ? Math.max(0.0, surfaceDistance)
                : VisualCallConstants.SCREEN_FLOAT_DISTANCE + VisualScreenConfig.snapBack(back);

        int min = ProjectorZoom.minSize(depth, largestScreen);
        int max = ProjectorZoom.maxSize(depth, largestScreen);
        int size = Math.max(min, Math.min(max, preferredSize));

        // a hair closer to the snail than the surface / nominal depth (z-fighting); may end up 0.03 inside the snail's own block
        double plane = depth - VisualCallConstants.SCREEN_SURFACE_OFFSET;
        ScreenLayout.Quad quad = VisualCallConstants.SCREEN_BEHIND_SNAIL
                ? ScreenLayout.computeBehind(fx, fz, size, plane)
                : ScreenLayout.computeFloating(fx, fz, size, plane);
        // slide along the surface (never in depth: that is automatic, or the "extra depth" above)
        quad = ScreenLayout.shift(quad, fx, fz, VisualScreenConfig.snapOffset(side), VisualScreenConfig.snapOffset(up), 0.0);

        int lightSteps = Math.max(0, (int) Math.floor(plane) + 1);
        return new Result(quad, found, depth, size, min, max, lightSteps);
    }
}
