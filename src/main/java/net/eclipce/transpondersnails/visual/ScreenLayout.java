package net.eclipce.transpondersnails.visual;

import java.util.function.IntPredicate;

/**
 * Pure geometry for the projected video screen and the snail's camera eye. No Minecraft classes on purpose so it can
 * be unit tested. All coordinates are LOCAL to the snail block (0..1 is the snail's own block).
 *
 * Conventions (same as Minecraft):
 *   facing step (fx, fz): NORTH = (0,-1), SOUTH = (0,1), EAST = (1,0), WEST = (-1,0)
 *   "right" as seen by someone looking along the facing = Direction#getClockWise() = (-fz, fx)
 */
public final class ScreenLayout {

    private ScreenLayout() {}

    /** Distance the screen is pushed off the wall towards the viewer, to avoid z-fighting. */
    public static final double WALL_OFFSET = 0.002;

    /**
     * Eye position of the snail, local to the block. The model's eyes span x 5..11, y 5..9 (16ths), front face at
     * z = 3 (north facing). The camera sits at the centre of the eyes, a hair in front of them.
     */
    public static double[] eyeOffset(int fx, int fz) {
        final double forward = 0.5 - (2.9 / 16.0); // distance from block centre to just in front of the eye front face
        return new double[]{0.5 + fx * forward, 7.0 / 16.0, 0.5 + fz * forward};
    }

    /**
     * Eye position for a camera that films what is BEHIND the snail, local to the block: same eye height, but at the
     * very back of the block (just behind the model, which ends 14/16 from the front), so the camera does not sit
     * inside the snail's body and sees nothing of it.
     */
    public static double[] eyeOffsetBehind(int fx, int fz) {
        final double back = 0.45; // block centre -> 0.95 behind the front edge of the block
        return new double[]{0.5 - fx * back, 7.0 / 16.0, 0.5 - fz * back};
    }

    /**
     * Finds the first solid block in front of the snail.
     *
     * @param maxRange       furthest distance (in blocks) to look
     * @param solidAtDistance distance d >= 1 -> is the block d steps in front of the snail solid?
     * @return distance in [1, maxRange], or -1 if there is no wall
     */
    public static int findWallDistance(int maxRange, IntPredicate solidAtDistance) {
        for (int d = 1; d <= maxRange; d++) {
            if (solidAtDistance.test(d)) {
                return d;
            }
        }
        return -1;
    }

    /** The screen quad: four corners (bottom-left, bottom-right, top-right, top-left) as seen by a viewer in front. */
    public record Quad(double[] bottomLeft, double[] bottomRight, double[] topRight, double[] topLeft) {
        /** UVs matching corner order (the render target's v=0 is the BOTTOM of the image). */
        public static final float[][] UVS = {{0f, 0f}, {1f, 0f}, {1f, 1f}, {0f, 1f}};

        public double[][] corners() {
            return new double[][]{bottomLeft, bottomRight, topRight, topLeft};
        }
    }

    /**
     * Builds the size x size screen on the wall in front of the snail. The screen is centred (for odd sizes) on the
     * snail's column and grows UPWARDS from the snail's own block row.
     *
     * @param wallDistance result of {@link #findWallDistance}, >= 1
     */
    public static Quad compute(int fx, int fz, int size, int wallDistance) {
        if (wallDistance < 1) throw new IllegalArgumentException("wallDistance must be >= 1");
        // coordinate of the wall's face that looks at the snail, along the facing axis
        int step = fx != 0 ? fx : fz;
        double plane = step > 0 ? wallDistance - WALL_OFFSET : 1 - wallDistance + WALL_OFFSET;
        return computeAt(fx, fz, size, plane);
    }

    /**
     * A screen floating in the air in front of the snail (no wall involved). The picture faces away from the snail,
     * towards whoever stands in front of it; it is centred on the snail's column and grows upwards from its block row.
     *
     * @param fx,fz    the snail's FACING step
     * @param distance blocks between the snail block's front face and the screen
     */
    public static Quad computeFloating(int fx, int fz, int size, double distance) {
        if ((fx == 0) == (fz == 0)) throw new IllegalArgumentException("facing must be a horizontal axis step");
        int step = fx != 0 ? fx : fz;
        double plane = step > 0 ? 1.0 + distance : -distance;
        // the viewer looks TOWARDS the snail, i.e. against its facing
        return computeAt(-fx, -fz, size, plane);
    }

    /**
     * A screen floating in the air BEHIND the snail. The picture still faces the way the snail faces (towards the room in
     * front of it); it is centred on the snail's column and grows upwards from its block row.
     *
     * @param fx,fz    the snail's FACING step
     * @param distance blocks between the snail block's BACK face and the screen (may be slightly negative to lay the
     *                 screen just inside the block, flat against a wall behind it)
     */
    public static Quad computeBehind(int fx, int fz, int size, double distance) {
        if ((fx == 0) == (fz == 0)) throw new IllegalArgumentException("facing must be a horizontal axis step");
        int step = fx != 0 ? fx : fz;
        // the block's back face is at local 0 when the snail faces +axis, at 1 when it faces -axis
        double plane = step > 0 ? -distance : 1.0 + distance;
        // the viewer (in front of the snail) looks TOWARDS the snail, i.e. against its facing
        return computeAt(-fx, -fz, size, plane);
    }

    /**
     * Builds a size x size quad on the plane at local coordinate {@code plane} (along the axis of (fx, fz)).
     * (fx, fz) is the direction the viewer looks along; the picture's normal points against it.
     */
    public static Quad computeAt(int fx, int fz, int size, double plane) {
        if (size < 1) throw new IllegalArgumentException("size must be >= 1");
        if ((fx == 0) == (fz == 0)) throw new IllegalArgumentException("facing must be a horizontal axis step");

        int rx = -fz;
        int rz = fx;

        // lateral block indices covered, relative to the snail's column
        int first = -((size - 1) / 2);
        int last = first + size - 1;
        double left = first - 0.5;
        double right = last + 0.5;

        double[] bl = point(fx, rx, rz, left, 0, plane);
        double[] br = point(fx, rx, rz, right, 0, plane);
        double[] tr = point(fx, rx, rz, right, size, plane);
        double[] tl = point(fx, rx, rz, left, size, plane);
        return new Quad(bl, br, tr, tl);
    }

    private static double[] point(int fx, int rx, int rz, double lateral, double y, double plane) {
        double x = 0.5 + rx * lateral;
        double z = 0.5 + rz * lateral;
        if (fx != 0) {
            x = plane;
        } else {
            z = plane;
        }
        return new double[]{x, y, z};
    }
}
