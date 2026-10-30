package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.visual.ProjectorEffects;
import net.eclipce.transpondersnails.visual.ProjectorHeadAim;
import net.eclipce.transpondersnails.visual.ProjectorPicture;
import net.eclipce.transpondersnails.visual.ProjectorPlacement;
import net.eclipce.transpondersnails.visual.ProjectorScene;
import net.eclipce.transpondersnails.visual.SnailGuiLayout.Rect;
import net.eclipce.transpondersnails.visual.VisualSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The 3D view in the projector settings (in the big display of the texture). It shows only what matters to the projection:
 * <ul>
 *   <li><b>the projector</b>: the snail's own model, as the game bakes it for this block (its shell colour, its body tint), coloured from
 *       its textures;</li>
 *   <li><b>anything in the way</b>: the solid blocks the light passes through between the lens and the screen - nothing else of the
 *       surroundings is drawn;</li>
 *   <li><b>the screen</b>: at its real size, offset and depth, with the blocks it lies on, and with a test picture put through the
 *       projector's real optics (the edge fade, the vignette, the dimming of a long throw and a big picture), a line for every block;</li>
 *   <li><b>the light</b>: a cone from the lens to the corners of the screen, and a bright ray from the lens to the middle of the screen
 *       with an arrowhead every block, so it is clear where it goes.</li>
 * </ul>
 * Drag to turn it. It is drawn by projecting the geometry itself ({@link ProjectorScene}) and sending flat coloured quads to the GUI with
 * a z for each, so the GUI's depth buffer hides what is behind what; the translucent parts come last. The world is read at most every
 * REBUILD_TICKS ticks, and at once when the settings change.
 */
@OnlyIn(Dist.CLIENT)
final class ProjectorMiniView {

    private static final int REBUILD_TICKS = 10;
    private static final int FALLBACK_COLOR = 0xE8A33C;

    private static final int RAY_COLOR = 0xF0FFE070;
    private static final int RAY_EDGE_COLOR = 0xC8FFF3B0;
    private static final int CONE_COLOR = 0x30E8F8FF;

    private final ProjectorScene.View view = new ProjectorScene.View(32.0, 28.0);

    /** A solid block of the scene: its box, colour, which faces can be seen (bit per ProjectorScene.FACE_*), and a checker flag. */
    private record Cube(ProjectorScene.Box box, int rgb, int exposed, boolean odd) { }

    /** A block read from the world (pass 1). */
    private record Found(int[] at, BlockPos pos, BlockState state, AABB bounds, boolean full) { }

    /** A cell of the picture on the screen: four corners (diagram) and its colour. */
    private record PicCell(double[][] corners, int argb) { }

    /** A translucent piece to draw after the solid ones: four corners {x, y, z} and a colour. */
    private record Piece(double depth, double[][] corners, int argb) { }

    private final List<Cube> cubes = new ArrayList<>();
    private List<ProjectorScene.Cell> model;           // the snail's model; null if it could not be read: a plain block is drawn instead
    private final List<PicCell> picture = new ArrayList<>();
    private final List<double[][]> gridLines = new ArrayList<>();
    private double[][] screen;                          // the screen's corners as diagram {a, b, c}: bottom left, bottom right, top right, top left
    private double[] lens;                              // where the light starts
    private double[] centre;                            // the middle of the screen
    private double[][] fitPoints;                       // the corners of everything that is drawn: the view is fitted to them
    private double[] builtFor;                          // what the scene was built for (see keyOf)
    private boolean dirty = true;
    private int ticksSinceBuild = 0;

    ProjectorMiniView() {
        SPRITE_COLORS.clear(); // a resource pack may have changed since the last time
    }

    void markDirty() {
        dirty = true;
    }

    /** Turns the view by a mouse drag (pixels). */
    void drag(double dx, double dy) {
        view.rotate(dx, dy);
    }

    // ------------------------------------------------------------------------------------------------
    // Reading the world
    // ------------------------------------------------------------------------------------------------

    private static double[] keyOf(ProjectorPlacement.Result res, double surfaceDistance, float side, float up) {
        double[] c = res.center();
        return new double[]{res.size(), res.depth(), res.surfaceFound() ? 1.0 : 0.0, c[0], c[1], c[2], Double.isNaN(surfaceDistance) ? -1.0 : surfaceDistance, side, up};
    }

    private static boolean same(double[] x, double[] y) {
        if (x == null || y == null || x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) if (Math.abs(x[i] - y[i]) > 1e-6) return false;
        return true;
    }

    /**
     * Call every tick: rebuilds the scene when the settings or the surface changed, and now and then for changes in the world.
     * {@code side} and {@code up} are the screen's left/right and up/down offsets: they turn the projector's head (the size of the screen does not).
     */
    void tick(ClientLevel level, BlockPos pos, ProjectorPlacement.Result res, double surfaceDistance, float side, float up) {
        if (level == null || res == null || !level.isLoaded(pos)) return;
        ticksSinceBuild++;
        double[] key = keyOf(res, surfaceDistance, side, up);
        if (!dirty && ticksSinceBuild < REBUILD_TICKS && same(key, builtFor)) return;
        if (rebuild(level, pos, res, surfaceDistance, side, up)) {
            builtFor = key;
            dirty = false;
            ticksSinceBuild = 0;
        }
    }

    private boolean rebuild(ClientLevel level, BlockPos pos, ProjectorPlacement.Result res, double surfaceDistance, float side, float up) {
        BlockState snail = level.getBlockState(pos);
        if (!(snail.getBlock() instanceof TransponderSnailBlock) || !snail.hasProperty(TransponderSnailBlock.FACING)) return false;
        Direction facing = snail.getValue(TransponderSnailBlock.FACING);
        ProjectorScene.Frame frame = new ProjectorScene.Frame(facing.getStepX(), facing.getStepZ(), net.eclipce.transpondersnails.visual.VisualCallConstants.SCREEN_BEHIND_SNAIL);

        // the screen, where the light starts, and the middle of the screen
        double[][] corners = res.quad().corners();
        double[][] diagram = new double[4][];
        double[] sb = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        double[] mid = new double[3];
        for (int i = 0; i < 4; i++) {
            diagram[i] = frame.toDiagram(corners[i]);
            for (int k = 0; k < 3; k++) {
                sb[k] = Math.min(sb[k], diagram[i][k]);
                sb[k + 3] = Math.max(sb[k + 3], diagram[i][k]);
                mid[k] += diagram[i][k] / 4.0;
            }
        }
        double[] lensPoint = frame.toDiagram(ProjectorEffects.lensPosition(facing.getStepX(), facing.getStepZ()));
        int wallIndex = res.surfaceFound() ? ProjectorScene.wallIndex(surfaceDistance) : -1;

        // Pass 1: read the blocks that matter - the ones the light passes through, and the ones the screen lies on.
        Map<Long, Found> found = new LinkedHashMap<>();
        for (ProjectorScene.Sample sample : ProjectorScene.projectionBlocks(lensPoint, sb, wallIndex)) {
            int[] o = sample.at();
            int[] w = frame.offset(o[0], o[1], o[2]);
            BlockPos p = pos.offset(w[0], w[1], w[2]);
            if (!level.isLoaded(p)) continue;
            BlockState state = level.getBlockState(p);
            VoxelShape shape = state.getCollisionShape(level, p);
            if (shape.isEmpty()) continue;   // the same rule as the surface ray: only what has a collision shape counts
            found.put(ProjectorScene.key(o[0], o[1], o[2]), new Found(o, p, state, shape.bounds(), state.isCollisionShapeFullBlock(level, p)));
        }

        // Pass 2: which faces to draw. A face is hidden only by a full block that is ALSO drawn (one that is not drawn hides nothing:
        // it would leave a hole).
        List<Cube> built = new ArrayList<>();
        List<double[]> points = new ArrayList<>();
        for (Found f : found.values()) {
            int[] o = f.at();
            int exposed = ProjectorScene.exposedMask(o[0], o[1], o[2], at -> {
                Found neighbour = found.get(ProjectorScene.key(at[0], at[1], at[2]));
                return neighbour != null && neighbour.full();
            });
            if (exposed == 0) continue;
            AABB bb = f.bounds();
            ProjectorScene.Box box = frame.blockBox(o[0], o[1], o[2], bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
            int rgb = f.state().getMapColor(level, f.pos()).col;
            if (rgb == 0) rgb = 0x808080;
            built.add(new Cube(box, rgb, exposed, ((o[0] + o[1] + o[2]) & 1) != 0));
            addCorners(points, box);
        }

        // The projector: the game's own model for this block
        List<ProjectorScene.Cell> baked = bakeModel(level, pos, snail, frame, ProjectorHeadAim.poseFor(side, up));
        double[] mb = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        if (baked != null) {
            for (ProjectorScene.Cell c : baked) for (double[] p : c.corners()) for (int k = 0; k < 3; k++) { mb[k] = Math.min(mb[k], p[k]); mb[k + 3] = Math.max(mb[k + 3], p[k]); }
        } else {
            mb = new double[]{-0.5, 0.0, -0.5, 0.5, 1.0, 0.5};
        }
        addCorners(points, new ProjectorScene.Box(mb[0], mb[1], mb[2], mb[3], mb[4], mb[5]));
        for (double[] corner : diagram) points.add(corner);
        points.add(lensPoint);

        // The picture on the screen: the real mesh (dense at the edges, where it fades), put through the real optics
        List<PicCell> cells = new ArrayList<>();
        int size = res.size();
        int largest = VisualSettings.maxScreenSize();
        float[] grid = ProjectorEffects.GRID;
        for (int ix = 0; ix < grid.length - 1; ix++) {
            for (int iy = 0; iy < grid.length - 1; iy++) {
                double u0 = grid[ix], u1 = grid[ix + 1], v0 = grid[iy], v1 = grid[iy + 1];
                cells.add(new PicCell(new double[][]{onScreen(diagram, u0, v0), onScreen(diagram, u1, v0), onScreen(diagram, u1, v1), onScreen(diagram, u0, v1)},
                        ProjectorPicture.argb((u0 + u1) / 2.0, (v0 + v1) / 2.0, size, res.depth(), largest)));
            }
        }
        List<double[][]> lines = new ArrayList<>();
        for (int k = 1; k < size; k++) {               // a line for every block of the screen: so its size can be counted
            double t = k / (double) size;
            lines.add(new double[][]{onScreen(diagram, t, 0.0), onScreen(diagram, t, 1.0)});
            lines.add(new double[][]{onScreen(diagram, 0.0, t), onScreen(diagram, 1.0, t)});
        }

        cubes.clear();
        cubes.addAll(built);
        model = baked;
        picture.clear();
        picture.addAll(cells);
        gridLines.clear();
        gridLines.addAll(lines);
        screen = diagram;
        lens = lensPoint;
        centre = mid;
        fitPoints = points.toArray(new double[0][]);
        return true;
    }

    /** The point (u across, v up, both 0..1) on the screen, as diagram {a, b, c}; the corners are bottom left, bottom right, top right, top left. */
    private static double[] onScreen(double[][] s, double u, double v) {
        double[] p = new double[3];
        for (int k = 0; k < 3; k++) p[k] = s[0][k] + u * (s[1][k] - s[0][k]) + v * (s[3][k] - s[0][k]);
        return p;
    }

    private static void addCorners(List<double[]> out, ProjectorScene.Box b) {
        for (int ia = 0; ia < 2; ia++) for (int ib = 0; ib < 2; ib++) for (int ic = 0; ic < 2; ic++) {
            out.add(new double[]{ia == 0 ? b.minA() : b.maxA(), ib == 0 ? b.minB() : b.maxB(), ic == 0 ? b.minC() : b.maxC()});
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The snail's own model
    // ------------------------------------------------------------------------------------------------

    /** A texture's pixels (ARGB), for working out the colour of a part of it. */
    private record SpriteColors(int w, int h, int[] argb) {
        /** The average colour of the opaque texels inside the rectangle (u0, v0)-(u1, v1) of the sprite, or 0 if there are none. */
        int average(double u0, double v0, double u1, double v1) {
            int x0 = Math.max(0, Math.min(w - 1, (int) Math.floor(Math.min(u0, u1) * w)));
            int x1 = Math.max(x0, Math.min(w - 1, (int) Math.ceil(Math.max(u0, u1) * w) - 1));
            int y0 = Math.max(0, Math.min(h - 1, (int) Math.floor(Math.min(v0, v1) * h)));
            int y1 = Math.max(y0, Math.min(h - 1, (int) Math.ceil(Math.max(v0, v1) * h) - 1));
            long r = 0, g = 0, b = 0;
            int n = 0;
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    int p = argb[y * w + x];
                    if ((p >>> 24) < 128) continue;
                    r += (p >> 16) & 255;
                    g += (p >> 8) & 255;
                    b += p & 255;
                    n++;
                }
            }
            return n == 0 ? 0 : 0xFF000000 | ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
        }
    }

    private static final Map<ResourceLocation, SpriteColors> SPRITE_COLORS = new HashMap<>();

    private static SpriteColors spriteColors(TextureAtlasSprite sprite) {
        return SPRITE_COLORS.computeIfAbsent(sprite.contents().name(), ProjectorMiniView::loadSprite);
    }

    private static SpriteColors loadSprite(ResourceLocation name) {
        try {
            ResourceLocation file = new ResourceLocation(name.getNamespace(), "textures/" + name.getPath() + ".png");
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(file);
            if (resource.isPresent()) {
                try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                    int w = image.getWidth();
                    int h = image.getHeight();
                    int[] px = new int[w * h];
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            int abgr = image.getPixelRGBA(x, y); // the image stores the channels as ABGR
                            px[y * w + x] = (abgr & 0xFF000000) | ((abgr & 0xFF) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 0xFF);
                        }
                    }
                    return new SpriteColors(w, h, px);
                }
            }
        } catch (Exception ignored) {
            // fall through to the grey below
        }
        return new SpriteColors(1, 1, new int[]{0xFFB0B0B0});
    }

    private static int tinted(int argb, int tint) {
        if (argb == 0 || tint == -1) return argb;
        int r = ((argb >> 16) & 255) * ((tint >> 16) & 255) / 255;
        int g = ((argb >> 8) & 255) * ((tint >> 8) & 255) / 255;
        int b = (argb & 255) * (tint & 255) / 255;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /**
     * The snail as the game draws it: the baked model of this block state (so its shell colour, its turn and its call state are right),
     * every quad cut into coloured cells (ProjectorScene.cells). Null if anything goes wrong: a plain block is drawn then.
     */
    private static List<ProjectorScene.Cell> bakeModel(ClientLevel level, BlockPos pos, BlockState state, ProjectorScene.Frame frame, ProjectorHeadAim.Pose head) {
        try {
            Minecraft mc = Minecraft.getInstance();
            BakedModel baked = mc.getBlockRenderer().getBlockModel(state);
            RandomSource random = RandomSource.create(42L);
            List<ProjectorScene.Cell> out = new ArrayList<>();
            Direction[] sides = {null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
            for (Direction side : sides) {
                for (BakedQuad quad : baked.getQuads(state, side, random)) {
                    addQuad(out, mc, level, pos, state, quad, frame, p -> p);
                }
            }
            // The head of the projector is not in the snail's baked model (it is drawn turned, in the world: VisualProjectorHeadRenderer). It is added here, turned the same way:
            // each corner is put through the head's pose (pixels, for a snail facing north) and then turned with the snail's facing - so the preview shows what the world will.
            BakedModel headModel = state.hasProperty(TransponderSnailBlock.FACING) && head != null
                    ? VisualGlowModels.get(VisualGlowModels.projectorHead(VisualGlowModels.projectorSnail())) : null;
            if (headModel != null) {
                Direction facing = state.getValue(TransponderSnailBlock.FACING);
                UnaryOperator<double[]> turned = p -> {
                    double[] pixels = ProjectorHeadAim.apply(head, new double[]{p[0] * 16.0, p[1] * 16.0, p[2] * 16.0});
                    return ProjectorHeadAim.toWorld(new double[]{pixels[0] / 16.0, pixels[1] / 16.0, pixels[2] / 16.0}, facing.getStepX(), facing.getStepZ());
                };
                for (Direction side : sides) {
                    for (BakedQuad quad : headModel.getQuads(null, side, random)) {
                        addQuad(out, mc, level, pos, state, quad, frame, turned);
                    }
                }
            }
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    /** One baked quad, cut into coloured cells (ProjectorScene.cells); {@code mapPoint} moves each of its corners first (block-local 0..1), or leaves them. */
    private static void addQuad(List<ProjectorScene.Cell> out, Minecraft mc, ClientLevel level, BlockPos pos, BlockState state, BakedQuad quad,
                                ProjectorScene.Frame frame, UnaryOperator<double[]> mapPoint) {
        int[] v = quad.getVertices();   // 8 ints a vertex: x y z, colour, u v, light, normal
        TextureAtlasSprite sprite = quad.getSprite();
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        double[][] pos4 = new double[4][3];
        double[][] uv4 = new double[4][2];
        for (int i = 0; i < 4; i++) {
            pos4[i][0] = Float.intBitsToFloat(v[i * 8]);
            pos4[i][1] = Float.intBitsToFloat(v[i * 8 + 1]);
            pos4[i][2] = Float.intBitsToFloat(v[i * 8 + 2]);
            pos4[i] = mapPoint.apply(pos4[i]);
            uv4[i][0] = (Float.intBitsToFloat(v[i * 8 + 4]) - u0) / (u1 - u0);   // atlas uv -> the sprite's own 0..1
            uv4[i][1] = (Float.intBitsToFloat(v[i * 8 + 5]) - v0) / (v1 - v0);
        }
        int tint = quad.isTinted() ? mc.getBlockColors().getColor(state, level, pos, quad.getTintIndex()) : -1;
        SpriteColors colors = spriteColors(sprite);
        out.addAll(ProjectorScene.cells(frame, pos4, uv4, sprite.contents().width(), sprite.contents().height(),
                (a, b, c, d) -> tinted(colors.average(a, b, c, d), tint)));
    }

    // ------------------------------------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------------------------------------

    /** Draws the view into {@code vp} (screen pixels of the GUI). Call after the texture, before the widgets. */
    void render(GuiGraphics g, Rect vp) {
        // the backdrop: a dark sky
        g.fillGradient(vp.x(), vp.y(), vp.right(), vp.bottom(), 0xFF223044, 0xFF0B0F15);
        if (fitPoints == null || screen == null) return;

        ProjectorScene.Projection proj = view.project(fitPoints, vp.w(), vp.h(), 4.0);
        g.enableScissor(vp.x(), vp.y(), vp.right(), vp.bottom());
        try {
            VertexConsumer vc = g.bufferSource().getBuffer(RenderType.gui());
            Matrix4f m = g.pose().last().pose();
            float ox = vp.x();
            float oy = vp.y();

            // which of the six directions turn towards the camera: the same for every block, so worked out once
            boolean[] visible = new boolean[6];
            for (int face = 0; face < 6; face++) visible[face] = view.faces(face);

            // 1. everything solid: the blocks in the way and under the screen, and the projector. The depth buffer sorts them.
            for (Cube c : cubes) {
                for (int face = 0; face < 6; face++) {
                    if ((c.exposed() & (1 << face)) == 0 || !visible[face]) continue;
                    double shade = ProjectorScene.shade(face) * (c.odd() ? 1.05 : 0.95);
                    emit(vc, m, ox, oy, projectFace(proj, c.box(), face), shaded(c.rgb(), shade));
                }
            }
            if (model != null) {
                for (ProjectorScene.Cell cell : model) {
                    if (!view.faces(cell.normal())) continue;
                    emit(vc, m, ox, oy, projectCorners(proj, cell.corners()), shaded(cell.argb() & 0xFFFFFF, ProjectorScene.shadeNormal(cell.normal())));
                }
            } else {
                ProjectorScene.Box block = new ProjectorScene.Box(-0.5, 0.0, -0.5, 0.5, 1.0, 0.5);
                for (int face = 0; face < 6; face++) {
                    if (visible[face]) emit(vc, m, ox, oy, projectFace(proj, block, face), shaded(FALLBACK_COLOR, ProjectorScene.shade(face)));
                }
            }

            // 2. the picture on the screen, over the blocks it lies on: the edges fade, so the wall shows through there
            for (PicCell cell : picture) {
                emit(vc, m, ox, oy, projectCorners(proj, cell.corners()), cell.argb());
            }

            // 3. the light and the lines, translucent, farthest first
            List<Piece> pieces = new ArrayList<>();
            for (double[][] line : gridLines) line(pieces, proj, line[0], line[1], 0.9, 0x55FFFFFF, 0.06);   // the blocks of the screen
            for (int i = 0; i < 4; i++) {
                double[] p0 = screen[i];
                double[] p1 = screen[(i + 1) % 4];
                line(pieces, proj, p0, p1, 1.7, 0xF0FFFFFF, 0.08);                                           // the screen's frame
                line(pieces, proj, lens, p0, 1.4, RAY_EDGE_COLOR, 0.0);                                      // the edges of the cone
                double[][] face = {proj.point(lens[0], lens[1], lens[2]), proj.point(p0[0], p0[1], p0[2]), proj.point(p1[0], p1[1], p1[2])};
                pieces.add(new Piece((face[0][2] + face[1][2] + face[2][2]) / 3.0, new double[][]{face[0], face[1], face[2], face[2]}, CONE_COLOR)); // a side of the cone
            }
            line(pieces, proj, lens, centre, 2.4, RAY_COLOR, 0.0);                                           // the ray: lens to the middle of the screen
            arrowheads(pieces, proj);
            cross(pieces, proj);
            dot(pieces, proj, lens, 1.5, 0xFFFFFFFF);                                                         // where the light starts (a small dot: on a long throw the projector itself is a speck)

            pieces.sort(Comparator.comparingDouble(Piece::depth)); // smaller z = farther: drawn first
            for (Piece piece : pieces) {
                emit(vc, m, ox, oy, piece.corners(), piece.argb());
            }

            g.flush();
        } finally {
            g.disableScissor();
        }
    }

    /** An arrowhead every block along the ray, pointing the way the light goes. */
    private void arrowheads(List<Piece> out, ProjectorScene.Projection proj) {
        double[] d = {centre[0] - lens[0], centre[1] - lens[1], centre[2] - lens[2]};
        double len = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (len < 1e-6) return;
        double[] u = {d[0] / len, d[1] / len, d[2] / len};
        double[] helper = Math.abs(u[1]) > 0.95 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
        double[] p1 = normalize(u[1] * helper[2] - u[2] * helper[1], u[2] * helper[0] - u[0] * helper[2], u[0] * helper[1] - u[1] * helper[0]);
        double[] p2 = normalize(u[1] * p1[2] - u[2] * p1[1], u[2] * p1[0] - u[0] * p1[2], u[0] * p1[1] - u[1] * p1[0]);
        int count = (int) Math.max(2, Math.min(10, Math.round(len)));
        for (int i = 0; i < count; i++) {
            double t = (i + 0.5) / count;
            double[] c = {lens[0] + d[0] * t, lens[1] + d[1] * t, lens[2] + d[2] * t};
            double[] tip = {c[0] + u[0] * 0.10, c[1] + u[1] * 0.10, c[2] + u[2] * 0.10};
            for (double[] side : new double[][]{p1, p2}) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    double[] tail = {c[0] - u[0] * 0.14 + sign * side[0] * 0.12, c[1] - u[1] * 0.14 + sign * side[1] * 0.12, c[2] - u[2] * 0.14 + sign * side[2] * 0.12};
                    line(out, proj, tail, tip, 1.5, 0xFFFFF0A0, 0.10);
                }
            }
        }
    }

    /** A cross on the middle of the screen, where the ray ends. */
    private void cross(List<Piece> out, ProjectorScene.Projection proj) {
        double[] across = normalize(screen[1][0] - screen[0][0], screen[1][1] - screen[0][1], screen[1][2] - screen[0][2]);
        double[] up = normalize(screen[3][0] - screen[0][0], screen[3][1] - screen[0][1], screen[3][2] - screen[0][2]);
        for (double[] dir : new double[][]{across, up}) {
            double[] a = {centre[0] - dir[0] * 0.3, centre[1] - dir[1] * 0.3, centre[2] - dir[2] * 0.3};
            double[] b = {centre[0] + dir[0] * 0.3, centre[1] + dir[1] * 0.3, centre[2] + dir[2] * 0.3};
            line(out, proj, a, b, 1.8, 0xFFFFE070, 0.10);
        }
    }

    private static double[] normalize(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        return len < 1e-12 ? new double[]{0, 1, 0} : new double[]{x / len, y / len, z / len};
    }

    /** A face's corners projected: {x, y, z} each. */
    private static double[][] projectFace(ProjectorScene.Projection proj, ProjectorScene.Box box, int face) {
        return projectCorners(proj, ProjectorScene.faceCorners(box, face));
    }

    private static double[][] projectCorners(ProjectorScene.Projection proj, double[][] c) {
        double[][] out = new double[4][];
        for (int i = 0; i < 4; i++) out[i] = proj.point(c[i][0], c[i][1], c[i][2]);
        return out;
    }

    /** A thin translucent line from p0 to p1 (diagram points), {@code width} pixels wide; {@code zBias} pulls it towards the camera. */
    private static void line(List<Piece> out, ProjectorScene.Projection proj, double[] p0, double[] p1, double width, int argb, double zBias) {
        double[] s0 = proj.point(p0[0], p0[1], p0[2]);
        double[] s1 = proj.point(p1[0], p1[1], p1[2]);
        double[][] q = ProjectorScene.lineQuad(s0[0], s0[1], s1[0], s1[1], width);
        if (q == null) return;
        double z0 = s0[2] + zBias;
        double z1 = s1[2] + zBias;
        out.add(new Piece((z0 + z1) / 2.0, new double[][]{{q[0][0], q[0][1], z0}, {q[1][0], q[1][1], z1}, {q[2][0], q[2][1], z1}, {q[3][0], q[3][1], z0}}, argb));
    }

    /** A small square facing the viewer at a point (diagram), {@code radius} pixels. */
    private static void dot(List<Piece> out, ProjectorScene.Projection proj, double[] p, double radius, int argb) {
        double[] s = proj.point(p[0], p[1], p[2]);
        double z = s[2] + 0.5;
        out.add(new Piece(z, new double[][]{{s[0] - radius, s[1] - radius, z}, {s[0] + radius, s[1] - radius, z}, {s[0] + radius, s[1] + radius, z}, {s[0] - radius, s[1] + radius, z}}, argb));
    }

    private static int shaded(int rgb, double shade) {
        int r = clamp(((rgb >> 16) & 255) * shade);
        int g = clamp(((rgb >> 8) & 255) * shade);
        int b = clamp((rgb & 255) * shade);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    /** Sends one quad (four corners {x, y, z}) to the GUI, in the order the GUI draws (see ProjectorScene.needsReverse). */
    private static void emit(VertexConsumer vc, Matrix4f m, float ox, float oy, double[][] p, int argb) {
        double[] xs = {p[0][0], p[1][0], p[2][0], p[3][0]};
        double[] ys = {p[0][1], p[1][1], p[2][1], p[3][1]};
        boolean reverse = ProjectorScene.needsReverse(xs, ys);
        int a = (argb >>> 24) & 255;
        int r = (argb >> 16) & 255;
        int g = (argb >> 8) & 255;
        int b = argb & 255;
        for (int n = 0; n < 4; n++) {
            double[] v = p[reverse ? 3 - n : n];
            vc.vertex(m, ox + (float) v[0], oy + (float) v[1], (float) v[2]).color(r, g, b, a).endVertex();
        }
    }
}
