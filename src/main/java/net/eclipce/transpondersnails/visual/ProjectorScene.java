package net.eclipce.transpondersnails.visual;

import java.util.ArrayList;
import java.util.List;

/**
 * The geometry of the 3D view in the projector settings: a cutaway of the world behind the snail, with the snail, the beam and the screen.
 * Pure maths (no Minecraft classes, unit tested); the client class ProjectorMiniView reads the world and draws.
 *
 * <h3>The diagram axes</h3>
 * All in blocks, measured from the snail's block (see {@link Frame}):
 * <ul>
 *   <li><b>a</b>: to the RIGHT of a viewer standing in front of the snail and looking at it - the way the "Left/Right" slider moves the screen</li>
 *   <li><b>b</b>: up; 0 is the bottom of the snail's block, and the screen grows up from there</li>
 *   <li><b>c</b>: depth, along the direction the screen is on; 0 is the middle of the snail's block, 0.5 its back face</li>
 * </ul>
 *
 * <h3>The camera</h3>
 * An orthographic camera that can be turned: {@link View}. It looks at the scene from the side (c runs roughly left to right on the
 * screen, so the projector is on the left and the screen on the right), from above, and the player can drag to turn it.
 * The picture is drawn with a depth buffer, so a larger z means nearer (see {@link Projection#point}).
 */
public final class ProjectorScene {

    private ProjectorScene() {}

    // =================================================================================================
    // The frame: world <-> diagram
    // =================================================================================================

    /**
     * @param facingX,facingZ the snail's FACING step
     * @param screenBehind    VisualCallConstants.SCREEN_BEHIND_SNAIL: the screen is on the side the snail's back is on
     */
    public record Frame(int facingX, int facingZ, boolean screenBehind) {

        /** The direction (world step) the screen is on = the c axis. */
        public int dirX() { return screenBehind ? -facingX : facingX; }
        public int dirZ() { return screenBehind ? -facingZ : facingZ; }

        /** The viewer's right (world step) = the a axis. The viewer looks AGAINST the facing: the same rule as ScreenLayout.shift. */
        public int rightX() { return facingZ; }
        public int rightZ() { return -facingX; }

        /** A point local to the snail's block (0..1 = the block, world axes) as diagram {a, b, c}. */
        public double[] toDiagram(double lx, double ly, double lz) {
            double rx = lx - 0.5;
            double rz = lz - 0.5;
            return new double[]{rx * rightX() + rz * rightZ(), ly, rx * dirX() + rz * dirZ()};
        }

        public double[] toDiagram(double[] local) {
            return toDiagram(local[0], local[1], local[2]);
        }

        /**
         * The WORLD offset {dx, dy, dz} of the block that is {@code i} blocks along c (i = 1 is the block behind the snail, i = -1 the one
         * in front of it), {@code j} blocks to the right (a) and {@code k} blocks up (b) from the snail's block.
         */
        public int[] offset(int i, int j, int k) {
            return new int[]{dirX() * i + rightX() * j, k, dirZ() * i + rightZ() * j};
        }

        /**
         * The box of a block's collision shape in diagram axes.
         *
         * @param i,j,k the block's place, see {@link #offset}
         * @param minX..maxZ the shape's bounds, local to ITS block (0..1, world axes)
         */
        public Box blockBox(int i, int j, int k, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            double[] p = toDiagram(minX, minY, minZ);
            double[] q = toDiagram(maxX, maxY, maxZ);
            return new Box(Math.min(p[0], q[0]) + j, Math.min(p[1], q[1]) + k, Math.min(p[2], q[2]) + i,
                    Math.max(p[0], q[0]) + j, Math.max(p[1], q[1]) + k, Math.max(p[2], q[2]) + i);
        }
    }

    // =================================================================================================
    // Boxes and faces
    // =================================================================================================

    public record Box(double minA, double minB, double minC, double maxA, double maxB, double maxC) {
        public double[] asBounds() {
            return new double[]{minA, minB, minC, maxA, maxB, maxC};
        }
    }

    public static final int FACE_PLUS_A = 0;
    public static final int FACE_MINUS_A = 1;
    public static final int FACE_UP = 2;
    public static final int FACE_DOWN = 3;
    public static final int FACE_PLUS_C = 4;
    public static final int FACE_MINUS_C = 5;

    /** The outward normal of a face, as diagram {a, b, c}. */
    public static double[] faceNormal(int face) {
        switch (face) {
            case FACE_PLUS_A: return new double[]{1, 0, 0};
            case FACE_MINUS_A: return new double[]{-1, 0, 0};
            case FACE_UP: return new double[]{0, 1, 0};
            case FACE_DOWN: return new double[]{0, -1, 0};
            case FACE_PLUS_C: return new double[]{0, 0, 1};
            case FACE_MINUS_C: return new double[]{0, 0, -1};
            default: throw new IllegalArgumentException("face " + face);
        }
    }

    /** The step, in block indices (i along c, j along a, k along b), to the neighbour on the other side of a face. */
    public static int[] faceIndexStep(int face) {
        switch (face) {
            case FACE_PLUS_A: return new int[]{0, 1, 0};
            case FACE_MINUS_A: return new int[]{0, -1, 0};
            case FACE_UP: return new int[]{0, 0, 1};
            case FACE_DOWN: return new int[]{0, 0, -1};
            case FACE_PLUS_C: return new int[]{1, 0, 0};
            case FACE_MINUS_C: return new int[]{-1, 0, 0};
            default: throw new IllegalArgumentException("face " + face);
        }
    }

    /** How bright a face is drawn: the top the brightest, the bottom the darkest, like the faces of a block in the game. */
    public static double shade(int face) {
        switch (face) {
            case FACE_UP: return 1.00;
            case FACE_MINUS_C: return 0.88;
            case FACE_PLUS_A:
            case FACE_MINUS_A: return 0.76;
            case FACE_PLUS_C: return 0.64;
            default: return 0.52;
        }
    }

    /** The four corners of a face as diagram {a, b, c}, in order around its edge. */
    public static double[][] faceCorners(Box b, int face) {
        switch (face) {
            case FACE_PLUS_A:
                return new double[][]{{b.maxA, b.minB, b.minC}, {b.maxA, b.maxB, b.minC}, {b.maxA, b.maxB, b.maxC}, {b.maxA, b.minB, b.maxC}};
            case FACE_MINUS_A:
                return new double[][]{{b.minA, b.minB, b.minC}, {b.minA, b.maxB, b.minC}, {b.minA, b.maxB, b.maxC}, {b.minA, b.minB, b.maxC}};
            case FACE_UP:
                return new double[][]{{b.minA, b.maxB, b.minC}, {b.maxA, b.maxB, b.minC}, {b.maxA, b.maxB, b.maxC}, {b.minA, b.maxB, b.maxC}};
            case FACE_DOWN:
                return new double[][]{{b.minA, b.minB, b.minC}, {b.maxA, b.minB, b.minC}, {b.maxA, b.minB, b.maxC}, {b.minA, b.minB, b.maxC}};
            case FACE_PLUS_C:
                return new double[][]{{b.minA, b.minB, b.maxC}, {b.maxA, b.minB, b.maxC}, {b.maxA, b.maxB, b.maxC}, {b.minA, b.maxB, b.maxC}};
            case FACE_MINUS_C:
                return new double[][]{{b.minA, b.minB, b.minC}, {b.maxA, b.minB, b.minC}, {b.maxA, b.maxB, b.minC}, {b.minA, b.maxB, b.minC}};
            default: throw new IllegalArgumentException("face " + face);
        }
    }

    // =================================================================================================
    // Which blocks to show
    // =================================================================================================

    public static final int WALL_HALF_WIDTH = 12;
    public static final int MAX_INDEX = 42;
    public static final int MAX_TOP = 30;
    private static final int MIN_BOTTOM = -10;
    /** Two ranges must overlap by more than this (blocks) to count: touching edges are not a hit. */
    private static final double OVERLAP = 0.02;

    /**
     * A block to show: its place {i, j, k} (see {@link Frame#offset}), and whether it is one of the blocks the screen lies on ({@code wall})
     * or one the light passes through on its way there.
     */
    public record Sample(int[] at, boolean wall) { }

    private static boolean overlaps(double lo1, double hi1, double lo2, double hi2) {
        return Math.min(hi1, hi2) - Math.max(lo1, lo2) > OVERLAP;
    }

    /**
     * The blocks that matter to the projection - and only those:
     * <ul>
     *   <li>everything the light passes through: the blocks that the cone from the lens to the screen cuts. If one of them is solid it is
     *       IN THE WAY of the picture.</li>
     *   <li>the blocks the screen lies on (when there is a surface): exactly those the screen covers.</li>
     * </ul>
     * The projector's own block is never in the list (it is drawn as the projector). There is no floor, no ceiling, no surroundings.
     *
     * @param lens         where the light starts, diagram {a, b, c}
     * @param screenBounds the screen's {minA, minB, minC, maxA, maxB, maxC} (it is flat: minC == maxC)
     * @param wallIndex    the block index (along c) of the surface the screen lies on, or -1 if the screen floats
     */
    public static List<Sample> projectionBlocks(double[] lens, double[] screenBounds, int wallIndex) {
        double planeC = screenBounds[5];
        double span = planeC - lens[2];
        java.util.Map<Long, Sample> out = new java.util.LinkedHashMap<>();

        int last = Math.min(MAX_INDEX, (int) Math.ceil(planeC + 0.5));
        for (int i = 0; i <= last; i++) {
            if (i - 0.5 >= planeC - 0.01) break;                    // this block starts at or behind the screen's plane
            double cHi = Math.min(planeC, i + 0.5);
            if (span <= 1e-6 || cHi <= lens[2] + 1e-6) continue;    // before the lens: no light there
            double t = (cHi - lens[2]) / span;                      // how far along the cone the far side of this block is
            double aLo = lens[0] + t * (screenBounds[0] - lens[0]);
            double aHi = lens[0] + t * (screenBounds[3] - lens[0]);
            double bLo = lens[1] + t * (screenBounds[1] - lens[1]);
            double bHi = lens[1] + t * (screenBounds[4] - lens[1]);
            for (int j = Math.max(-WALL_HALF_WIDTH, (int) Math.floor(Math.min(aLo, aHi)) - 1); j <= Math.min(WALL_HALF_WIDTH, (int) Math.ceil(Math.max(aLo, aHi)) + 1); j++) {
                for (int k = Math.max(MIN_BOTTOM, (int) Math.floor(Math.min(bLo, bHi)) - 1); k <= Math.min(MAX_TOP, (int) Math.ceil(Math.max(bLo, bHi)) + 1); k++) {
                    if (overlaps(j - 0.5, j + 0.5, Math.min(aLo, aHi), Math.max(aLo, aHi)) && overlaps(k, k + 1.0, Math.min(bLo, bHi), Math.max(bLo, bHi))) {
                        put(out, i, j, k, false);
                    }
                }
            }
        }

        if (wallIndex >= 0 && wallIndex <= MAX_INDEX) {
            for (int j = Math.max(-WALL_HALF_WIDTH, (int) Math.floor(screenBounds[0]) - 1); j <= Math.min(WALL_HALF_WIDTH, (int) Math.ceil(screenBounds[3]) + 1); j++) {
                for (int k = Math.max(MIN_BOTTOM, (int) Math.floor(screenBounds[1]) - 1); k <= Math.min(MAX_TOP, (int) Math.ceil(screenBounds[4]) + 1); k++) {
                    if (overlaps(j - 0.5, j + 0.5, screenBounds[0], screenBounds[3]) && overlaps(k, k + 1.0, screenBounds[1], screenBounds[4])) {
                        put(out, wallIndex, j, k, true);
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    private static void put(java.util.Map<Long, Sample> out, int i, int j, int k, boolean wall) {
        if (i == 0 && j == 0 && k == 0) return; // the projector itself
        long key = key(i, j, k);
        Sample old = out.get(key);
        if (old == null || (wall && !old.wall())) out.put(key, new Sample(new int[]{i, j, k}, wall || (old != null && old.wall())));
    }

    /** One number for a block place {i, j, k} (each within -64 .. 1000000), to keep them in a set or a map. */
    public static long key(int i, int j, int k) {
        return ((long) (i + 64) << 40) | ((long) (j + 64) << 20) | (long) (k + 64);
    }

    /**
     * Which faces of the block at {i, j, k} to draw, a bit per FACE_*. A face is NOT drawn when the block on the other side of it
     * {@code hides} it - and that must only be true for a block that is itself part of the drawing: a solid block that is not in the
     * cutaway hides nothing, because nothing is drawn there (it would leave a hole).
     *
     * @param hides is the block at {i, j, k} one of the blocks drawn, and a full block?
     */
    public static int exposedMask(int i, int j, int k, java.util.function.Predicate<int[]> hides) {
        int mask = 0;
        for (int face = 0; face < 6; face++) {
            int[] step = faceIndexStep(face);
            if (!hides.test(new int[]{i + step[0], j + step[1], k + step[2]})) mask |= 1 << face;
        }
        return mask;
    }

    /**
     * The block index (along c) of the surface the screen lies on: {@code surfaceDistance} blocks from the snail's back face (c = 0.5).
     */
    public static int wallIndex(double surfaceDistance) {
        return (int) Math.floor(0.5 + surfaceDistance + 0.5);
    }

    // =================================================================================================
    // The projector's own model
    // =================================================================================================

    /** The colour of a part of a texture: ARGB averaged over the uv rectangle (u0, v0)-(u1, v1) in the sprite's own 0..1; alpha 0 = nothing there. */
    @FunctionalInterface
    public interface ColorSampler {
        int sample(double u0, double v0, double u1, double v1);
    }

    /** A small piece of a face, coloured: four corners as diagram {a, b, c}, its colour, and the outward normal as a diagram unit vector. */
    public record Cell(double[][] corners, int argb, double[] normal) { }

    /** The direction the light comes from when shading a face, as diagram {a, b, c}: above, a little to the front. */
    private static final double[] LIGHT = normalize(0.35, 0.85, -0.40);

    private static double[] normalize(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        return len < 1e-12 ? new double[]{0, 1, 0} : new double[]{x / len, y / len, z / len};
    }

    /** How bright a face with this outward normal (diagram unit vector) is drawn, 0.45 .. 1: the top brightest, the underside darkest. */
    public static double shadeNormal(double[] n) {
        return 0.45 + 0.55 * Math.max(0.0, n[0] * LIGHT[0] + n[1] * LIGHT[1] + n[2] * LIGHT[2]);
    }

    /**
     * Cuts one quad of a baked block model into coloured cells, so a face keeps some of its detail: the quad is split into up to 4 x 4
     * cells (one per few texels), each coloured by averaging the part of the texture it covers. Cells that are transparent are left out.
     *
     * @param pos       the four corners {x, y, z} local to the snail's block (world axes), counter-clockwise seen from outside
     * @param uv        the four {u, v} of those corners, in the sprite's own 0..1
     * @param spriteW,spriteH the sprite's size in texels
     */
    public static List<Cell> cells(Frame frame, double[][] pos, double[][] uv, int spriteW, int spriteH, ColorSampler sampler) {
        List<Cell> out = new ArrayList<>();
        // the outward normal: e1 x e2 for corners that run counter-clockwise seen from outside
        double[] e1 = {pos[1][0] - pos[0][0], pos[1][1] - pos[0][1], pos[1][2] - pos[0][2]};
        double[] e2 = {pos[3][0] - pos[0][0], pos[3][1] - pos[0][1], pos[3][2] - pos[0][2]};
        double[] nl = normalize(e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0]);
        double[] o = frame.toDiagram(0, 0, 0);
        double[] tip = frame.toDiagram(nl[0], nl[1], nl[2]);
        double[] normal = normalize(tip[0] - o[0], tip[1] - o[1], tip[2] - o[2]);

        double texels = 1.0;
        for (int i = 1; i < 4; i++) {
            texels = Math.max(texels, Math.max(Math.abs(uv[i][0] - uv[0][0]) * spriteW, Math.abs(uv[i][1] - uv[0][1]) * spriteH));
        }
        int per = (int) Math.min(4, Math.max(1, Math.ceil(texels / 3.0)));

        for (int sx = 0; sx < per; sx++) {
            for (int sy = 0; sy < per; sy++) {
                double s0 = sx / (double) per, s1 = (sx + 1) / (double) per, t0 = sy / (double) per, t1 = (sy + 1) / (double) per;
                double[][] c = new double[4][];
                double uMin = Double.MAX_VALUE, uMax = -Double.MAX_VALUE, vMin = Double.MAX_VALUE, vMax = -Double.MAX_VALUE;
                double[][] st = {{s0, t0}, {s1, t0}, {s1, t1}, {s0, t1}};
                for (int i = 0; i < 4; i++) {
                    double[] lp = bilerp(pos, st[i][0], st[i][1], 3);
                    double[] lt = bilerp(uv, st[i][0], st[i][1], 2);
                    c[i] = frame.toDiagram(lp[0], lp[1], lp[2]);
                    uMin = Math.min(uMin, lt[0]); uMax = Math.max(uMax, lt[0]); vMin = Math.min(vMin, lt[1]); vMax = Math.max(vMax, lt[1]);
                }
                int argb = sampler.sample(uMin, vMin, uMax, vMax);
                if ((argb >>> 24) < 128) continue;
                out.add(new Cell(c, argb, normal));
            }
        }
        return out;
    }

    /** Bilinear point on a quad given by four corners (p0, p1, p2, p3 counter-clockwise); s runs p0 -> p1, t runs p0 -> p3. */
    private static double[] bilerp(double[][] q, double s, double t, int dims) {
        double[] r = new double[dims];
        for (int d = 0; d < dims; d++) {
            r[d] = (1 - s) * (1 - t) * q[0][d] + s * (1 - t) * q[1][d] + s * t * q[2][d] + (1 - s) * t * q[3][d];
        }
        return r;
    }

    // =================================================================================================
    // The camera
    // =================================================================================================

    /** The 3D view: yaw turns it around the vertical axis, pitch tilts it down onto the scene. */
    public static final class View {
        public static final double MIN_PITCH = 4.0;
        public static final double MAX_PITCH = 88.0;
        public static final double DEGREES_PER_PIXEL_YAW = 0.9;
        public static final double DEGREES_PER_PIXEL_PITCH = 0.6;

        private double yaw;
        private double pitch;

        public View(double yawDegrees, double pitchDegrees) {
            this.yaw = wrap(yawDegrees);
            this.pitch = clamp(pitchDegrees);
        }

        public double yaw() { return yaw; }
        public double pitch() { return pitch; }

        /** Turns the view by a mouse drag: right turns the scene to the right, down tilts it towards the top. */
        public void rotate(double dxPixels, double dyPixels) {
            yaw = wrap(yaw + dxPixels * DEGREES_PER_PIXEL_YAW);
            pitch = clamp(pitch + dyPixels * DEGREES_PER_PIXEL_PITCH);
        }

        private static double wrap(double degrees) {
            double d = degrees % 360.0;
            if (d > 180.0) d -= 360.0;
            if (d < -180.0) d += 360.0;
            return d;
        }

        private static double clamp(double pitch) {
            return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
        }

        /** The unit vector from the scene towards the camera, as diagram {a, b, c}. */
        public double[] towardCamera() {
            double y = Math.toRadians(yaw);
            double p = Math.toRadians(pitch);
            return new double[]{Math.cos(y) * Math.cos(p), Math.sin(p), -Math.sin(y) * Math.cos(p)};
        }

        /** Whether a face (of a box seen from outside) turns towards the camera. */
        public boolean faces(int face) {
            return faces(faceNormal(face));
        }

        /** Whether a face with this outward normal (diagram unit vector) turns towards the camera. */
        public boolean faces(double[] n) {
            double[] t = towardCamera();
            return n[0] * t[0] + n[1] * t[1] + n[2] * t[2] > 1e-9;
        }

        /** Screen position (right) of a diagram point. */
        public double u(double a, double b, double c) {
            double y = Math.toRadians(yaw);
            return c * Math.cos(y) + a * Math.sin(y);
        }

        /** Screen position (up) of a diagram point. */
        public double v(double a, double b, double c) {
            double y = Math.toRadians(yaw);
            double p = Math.toRadians(pitch);
            double w = a * Math.cos(y) - c * Math.sin(y);
            return b * Math.cos(p) - w * Math.sin(p);
        }

        /** How near the camera a diagram point is (larger = nearer). */
        public double depth(double a, double b, double c) {
            double[] t = towardCamera();
            return a * t[0] + b * t[1] + c * t[2];
        }

        /**
         * Fits a scene into a viewport: the biggest scale at which every corner of {@code bounds} is inside it, centred, with a margin.
         *
         * @param bounds {minA, minB, minC, maxA, maxB, maxC}
         */
        public Projection project(double[] bounds, double viewWidth, double viewHeight, double margin) {
            double[][] corners = new double[8][];
            int n = 0;
            for (int ia = 0; ia < 2; ia++) for (int ib = 0; ib < 2; ib++) for (int ic = 0; ic < 2; ic++) {
                corners[n++] = new double[]{bounds[ia == 0 ? 0 : 3], bounds[ib == 0 ? 1 : 4], bounds[ic == 0 ? 2 : 5]};
            }
            return project(corners, viewWidth, viewHeight, margin);
        }

        /**
         * Fits the given points (diagram {a, b, c}) into a viewport: the biggest scale at which every one of them is inside, centred,
         * with a margin. Fitting the points of what is really drawn is tighter than fitting its bounding box.
         */
        public Projection project(double[][] points, double viewWidth, double viewHeight, double margin) {
            double y = Math.toRadians(yaw);
            double p = Math.toRadians(pitch);
            double cy = Math.cos(y), sy = Math.sin(y), cp = Math.cos(p), sp = Math.sin(p);
            double uMin = Double.MAX_VALUE, uMax = -Double.MAX_VALUE;
            double vMin = Double.MAX_VALUE, vMax = -Double.MAX_VALUE;
            double dMin = Double.MAX_VALUE, dMax = -Double.MAX_VALUE;
            for (double[] pt : points) {
                double u = pt[2] * cy + pt[0] * sy;
                double w = pt[0] * cy - pt[2] * sy;
                double v = pt[1] * cp - w * sp;
                double d = w * cp + pt[1] * sp;
                uMin = Math.min(uMin, u); uMax = Math.max(uMax, u);
                vMin = Math.min(vMin, v); vMax = Math.max(vMax, v);
                dMin = Math.min(dMin, d); dMax = Math.max(dMax, d);
            }
            double spanU = Math.max(1.0, uMax - uMin);
            double spanV = Math.max(1.0, vMax - vMin);
            double scale = Math.min((viewWidth - 2 * margin) / spanU, (viewHeight - 2 * margin) / spanV);
            return new Projection(cy, sy, cp, sp, scale, (uMin + uMax) / 2.0, (vMin + vMax) / 2.0, viewWidth / 2.0, viewHeight / 2.0, dMin, dMax);
        }
    }

    /** The depth values are mapped to z in [Z_BASE, Z_BASE + Z_SPAN]: above 0, so the picture lands on the GUI, and larger = nearer. */
    public static final double Z_BASE = 1.0;
    public static final double Z_SPAN = 90.0;

    public static final class Projection {
        private final double cosYaw, sinYaw, cosPitch, sinPitch;
        private final double scale, uMid, vMid, cx, cy, dMin, dMax;

        Projection(double cosYaw, double sinYaw, double cosPitch, double sinPitch, double scale, double uMid, double vMid,
                   double cx, double cy, double dMin, double dMax) {
            this.cosYaw = cosYaw; this.sinYaw = sinYaw; this.cosPitch = cosPitch; this.sinPitch = sinPitch;
            this.scale = scale; this.uMid = uMid; this.vMid = vMid; this.cx = cx; this.cy = cy; this.dMin = dMin; this.dMax = dMax;
        }

        public double scale() { return scale; }

        /** @return {x, y, z}: x and y in pixels from the top left of the viewport, z larger = nearer (Z_BASE .. Z_BASE + Z_SPAN) */
        public double[] point(double a, double b, double c) {
            double u = c * cosYaw + a * sinYaw;
            double w = a * cosYaw - c * sinYaw;
            double v = b * cosPitch - w * sinPitch;
            double depth = w * cosPitch + b * sinPitch;
            double span = dMax - dMin;
            double z = Z_BASE + (span < 1e-9 ? 0.5 : (depth - dMin) / span) * Z_SPAN;
            return new double[]{cx + (u - uMid) * scale, cy - (v - vMid) * scale, z};
        }
    }

    // =================================================================================================
    // Drawing helpers
    // =================================================================================================

    /**
     * The GUI draws a quad only if its corners go round a certain way (its fill order: bottom left, bottom right, top right, top left, in
     * pixels with y down). Returns true if these corners go the other way and must be reversed.
     */
    public static boolean needsReverse(double[] x, double[] y) {
        double sum = 0.0;
        int n = x.length;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            sum += x[i] * y[j] - x[j] * y[i];
        }
        return sum > 0.0;
    }

    /**
     * A line from (x0, y0) to (x1, y1) as a quad {@code width} pixels wide: {{x, y} x 4}, or null if the two points are the same.
     */
    public static double[][] lineQuad(double x0, double y0, double x1, double y1, double width) {
        double dx = x1 - x0;
        double dy = y1 - y0;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-9) return null;
        double nx = -dy / len * width / 2.0;
        double ny = dx / len * width / 2.0;
        return new double[][]{{x0 + nx, y0 + ny}, {x1 + nx, y1 + ny}, {x1 - nx, y1 - ny}, {x0 - nx, y0 - ny}};
    }
}
