package net.eclipce.transpondersnails.visual;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the projector's HEAD (its top part: the lens that throws the picture) points. When the screen is moved left / right or up / down in the projector settings the head turns
 * towards where it went, the way a real projector is aimed. It follows ONLY those two offsets - never the zoom (the size of the screen) - so the head does not move when the
 * picture is made bigger or smaller. Pure (no Minecraft classes), unit tested - and checked against the real model boxes, so the head never goes into the rest of the model.
 *
 * <pre>
 *   the head is a cube (models/block/visual_transponder_snail_projector_head.json) standing on the neck. It turns about the middle of its bottom face, where it sits on the neck:
 *     yaw    about the vertical axis  - the lens turns sideways: it follows the left / right offset
 *     pitch  about the head's own left-right axis - the lens tilts: it follows the up / down offset (the pitch axis turns with the yaw, like a pan-tilt head)
 *   Tilting a cube about the middle of its bottom face pushes one half of that face down, into the neck. So the head is also lifted by exactly as much as it would dip in
 *   (see liftFor) - a fraction of a pixel at the most - and then it touches the neck at the most, never goes into it.
 *   All numbers here are in MODEL PIXELS (0-16 per block), for a snail facing north; the renderer turns the result with the snail, like the blockstate does.
 * </pre>
 */
public final class ProjectorHeadAim {

    private ProjectorHeadAim() {}

    // The boxes this works with, copied from the model files (the unit tests check them against the files).
    /** Half the width (x) and half the depth (z) of the head: a 1.5 cube. */
    public static final double HEAD_HALF_X = 0.75;
    public static final double HEAD_HALF_Z = 0.75;
    /** The neck the head stands on (element 10 of the visual_transponder_snail models): the height of its top face and the rectangle of that face. */
    public static final double NECK_TOP = 11.0;
    public static final double NECK_MIN_X = 7.37;
    public static final double NECK_MAX_X = 8.62;
    public static final double NECK_MIN_Z = 9.9;
    public static final double NECK_MAX_Z = 11.15;

    /**
     * How the head is turned: {@code yaw} in degrees about the vertical axis (positive turns the lens from +z towards +x, as Axis.YP does), {@code pitch} in degrees about the
     * head's own left-right axis (positive tips the lens DOWN, as Minecraft's pitch does), and {@code lift} in pixels straight up (what keeps the head out of the neck).
     */
    public record Pose(double yaw, double pitch, double lift) {
        public static final Pose NEUTRAL = new Pose(0.0, 0.0, 0.0);
    }

    /** The pose for these two angles; the lift is worked out here, so it always goes with them. */
    public static Pose pose(double yaw, double pitch) {
        return new Pose(yaw + 0.0, pitch + 0.0, liftFor(yaw, pitch)); // "+ 0.0": no negative zero
    }

    /**
     * The pose for a screen moved {@code side} blocks sideways (positive = to the viewer's right, as in the settings menu) and {@code up} blocks up (negative = down). Nothing
     * else goes in - not the size of the screen. Offsets beyond the settings' range count as the end of it; NaN counts as none.
     */
    public static Pose poseFor(double side, double up) {
        double yaw = 0.0 - unit(side) * VisualCallConstants.PROJECTOR_HEAD_MAX_YAW_DEGREES;   // to the right = the lens towards -x (the viewer looks along +z, their right is -x)
        double pitch = 0.0 - unit(up) * VisualCallConstants.PROJECTOR_HEAD_MAX_PITCH_DEGREES;  // up = the lens tips up = a negative pitch
        return pose(yaw, pitch);
    }

    /** An offset as a fraction of the settings' range: -1 .. +1. */
    private static double unit(double offset) {
        if (Double.isNaN(offset)) return 0.0;
        return Math.max(-1.0, Math.min(1.0, offset / VisualCallConstants.SCREEN_OFFSET_MAX));
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    /** The head's own turn applied to a vector: first the pitch (about x), then the yaw (about y). The same order the renderer applies its two rotations in. */
    private static double[] turn(double yawDeg, double pitchDeg, double x, double y, double z) {
        double b = Math.toRadians(pitchDeg);
        double cb = Math.cos(b);
        double sb = Math.sin(b);
        double y1 = y * cb - z * sb;   // Axis.XP.rotationDegrees: y' = y cos - z sin, z' = y sin + z cos
        double z1 = y * sb + z * cb;
        double a = Math.toRadians(yawDeg);
        double ca = Math.cos(a);
        double sa = Math.sin(a);
        return new double[]{x * ca + z1 * sa, y1, -x * sa + z1 * ca};   // Axis.YP.rotationDegrees: x' = x cos + z sin, z' = -x sin + z cos
    }

    /** Where a point of the head goes (model pixels): turned about the pivot, then lifted. */
    public static double[] apply(Pose pose, double[] point) {
        double[] r = turn(pose.yaw(), pose.pitch(), point[0] - VisualCallConstants.PROJECTOR_HEAD_PIVOT_X,
                point[1] - VisualCallConstants.PROJECTOR_HEAD_PIVOT_Y, point[2] - VisualCallConstants.PROJECTOR_HEAD_PIVOT_Z);
        return new double[]{VisualCallConstants.PROJECTOR_HEAD_PIVOT_X + r[0], VisualCallConstants.PROJECTOR_HEAD_PIVOT_Y + pose.lift() + r[1],
                VisualCallConstants.PROJECTOR_HEAD_PIVOT_Z + r[2]};
    }

    /** The direction the lens looks (a unit vector, model space, snail facing north): along +z when the head is not turned - the screen is behind the snail. */
    public static double[] forward(Pose pose) {
        return turn(pose.yaw(), pose.pitch(), 0.0, 0.0, 1.0);
    }

    /** A point of a snail facing north, block-local 0..1, as it is for a snail facing (fx, fz): turned about the block's middle like the blockstate turns the model. */
    public static double[] toWorld(double[] p, int fx, int fz) {
        int turns = fz < 0 ? 0 : fx > 0 ? 1 : fz > 0 ? 2 : 3;   // clockwise quarter turns seen from above: north / east / south / west
        double x = p[0] - 0.5;
        double z = p[2] - 0.5;
        for (int i = 0; i < turns; i++) {
            double nx = -z;
            double nz = x;
            x = nx;
            z = nz;
        }
        return new double[]{0.5 + x, p[1], 0.5 + z};
    }

    // ---------------------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * How far (pixels) the head must be lifted so that it does not go into the neck when it is tilted. The bottom face of the head is a plane through the pivot; tilted, part of
     * it is BELOW the top of the neck, and the part of it that is over the neck is inside the neck. That is worked out exactly: the part of the neck's top face that the tilted
     * bottom face covers (a polygon), and how far below the top of the neck the face is at each of its corners - the plane is straight, so the most is at a corner. Zero when the
     * head is not tilted: turning about the vertical axis alone never takes it below the neck.
     */
    public static double liftFor(double yawDeg, double pitchDeg) {
        if (Math.abs(pitchDeg) < 1e-9) return 0.0;
        double[] e1 = turn(yawDeg, pitchDeg, HEAD_HALF_X, 0.0, 0.0);
        double[] e3 = turn(yawDeg, pitchDeg, 0.0, 0.0, HEAD_HALF_Z);
        double[] up = turn(yawDeg, pitchDeg, 0.0, 1.0, 0.0);   // the normal of the bottom face
        if (up[1] <= 1e-6) return 0.0;                          // more than a right angle: not a pose this is meant for
        // the bottom face seen from above, relative to the pivot (x, z)
        List<double[]> face = List.of(
                new double[]{-e1[0] - e3[0], -e1[2] - e3[2]}, new double[]{e1[0] - e3[0], e1[2] - e3[2]},
                new double[]{e1[0] + e3[0], e1[2] + e3[2]}, new double[]{-e1[0] + e3[0], -e1[2] + e3[2]});
        double px = VisualCallConstants.PROJECTOR_HEAD_PIVOT_X;
        double pz = VisualCallConstants.PROJECTOR_HEAD_PIVOT_Z;
        List<double[]> over = List.of(
                new double[]{NECK_MIN_X - px, NECK_MIN_Z - pz}, new double[]{NECK_MAX_X - px, NECK_MIN_Z - pz},
                new double[]{NECK_MAX_X - px, NECK_MAX_Z - pz}, new double[]{NECK_MIN_X - px, NECK_MAX_Z - pz});
        double lift = 0.0;
        for (double[] q : clip(over, face)) {
            double faceHeight = VisualCallConstants.PROJECTOR_HEAD_PIVOT_Y - (up[0] * q[0] + up[2] * q[1]) / up[1];   // the bottom face's height over this point
            lift = Math.max(lift, NECK_TOP - faceHeight);
        }
        return lift;
    }

    /** The part of {@code subject} that is inside the convex polygon {@code clipper} (Sutherland-Hodgman; either winding order). */
    private static List<double[]> clip(List<double[]> subject, List<double[]> clipper) {
        double area = 0.0;
        for (int i = 0; i < clipper.size(); i++) {
            double[] a = clipper.get(i);
            double[] b = clipper.get((i + 1) % clipper.size());
            area += a[0] * b[1] - b[0] * a[1];
        }
        double winding = area >= 0.0 ? 1.0 : -1.0;
        List<double[]> out = new ArrayList<>(subject);
        for (int i = 0; i < clipper.size() && !out.isEmpty(); i++) {
            double[] a = clipper.get(i);
            double[] b = clipper.get((i + 1) % clipper.size());
            List<double[]> in = out;
            out = new ArrayList<>();
            for (int j = 0; j < in.size(); j++) {
                double[] p = in.get(j);
                double[] q = in.get((j + 1) % in.size());
                double sp = winding * ((b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]));
                double sq = winding * ((b[0] - a[0]) * (q[1] - a[1]) - (b[1] - a[1]) * (q[0] - a[0]));
                if (sp >= 0.0) out.add(p);
                if ((sp >= 0.0) != (sq >= 0.0)) {
                    double t = sp / (sp - sq);
                    out.add(new double[]{p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])});
                }
            }
        }
        return out;
    }
}
