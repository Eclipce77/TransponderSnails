package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.ProjectorPlacement;
import net.eclipce.transpondersnails.visual.ProjectorSurface;
import net.eclipce.transpondersnails.visual.ScreenLayout;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.VisualQuality;
import net.eclipce.transpondersnails.visual.VisualSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Draws the video screens: a size x size square floating in the air {@link VisualCallConstants#SCREEN_FLOAT_DISTANCE}
 * block(s) in front of every Visual Snail that is part of a running call. No wall is needed.
 *
 * Projector look:
 *  - the picture fades in after the call is answered ({@link VisualCallConstants#FEED_FADE_IN_MS}, with a little flicker
 *    while it warms up),
 *  - it is slightly see-through ({@link VisualCallConstants#FEED_OPACITY}), so the blocks behind it show through and
 *    tint it,
 *  - it has a light level ({@link VisualCallConstants#PROJECTOR_IMAGE_LIGHT}): never darker than that, whatever the room
 *    lighting, but as bright as its surroundings when they are brighter.
 *
 * Drawn from a level render event (not a block entity renderer). Visible from both sides (the back is mirrored, like a
 * hologram). Nothing is drawn until the first picture of the call has arrived.
 */
@OnlyIn(Dist.CLIENT)
public final class VisualScreenRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean errorLogged = false;
    private static boolean previewErrorLogged = false;

    private VisualScreenRenderer() {}

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (VisualFeedManager.isCapturing()) return; // no screen-in-screen while a feed is being rendered

        java.util.Collection<VisualFeedManager.ScreenLink> links = VisualFeedManager.activeLinks();
        VisualConfigScreen editing = VisualConfigScreen.active(); // the projector settings menu, if it is open
        if (links.isEmpty() && editing == null) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        double maxDistSq = VisualCallConstants.SCREEN_RENDER_DISTANCE * VisualCallConstants.SCREEN_RENDER_DISTANCE;

        for (VisualFeedManager.ScreenLink link : links) {
            try {
                drawScreen(mc, level, link, cam, pose, buffers, maxDistSq);
            } catch (Exception e) {
                if (!errorLogged) {
                    errorLogged = true;
                    LOGGER.error("VisualScreenRenderer: drawing the screen at {} failed (further errors are not logged)", link.screenPos(), e);
                }
            }
        }

        if (editing != null) {
            try {
                drawPreview(level, editing, cam, pose, buffers);
            } catch (Exception e) {
                if (!previewErrorLogged) {
                    previewErrorLogged = true;
                    LOGGER.error("VisualScreenRenderer: drawing the settings preview failed (further errors are not logged)", e);
                }
            }
        }
    }

    private static void drawScreen(Minecraft mc, ClientLevel level, VisualFeedManager.ScreenLink link,
                                   Vec3 cam, PoseStack pose, MultiBufferSource.BufferSource buffers, double maxDistSq) {
        BlockPos pos = link.screenPos();
        if (!level.isLoaded(pos)) return;
        // coarse check from the snail; the exact check below is from the screen itself (which can be far from the snail)
        double reach = VisualCallConstants.SCREEN_RENDER_DISTANCE + VisualCallConstants.PROJECTOR_MAX_THROW;
        if (pos.distToCenterSqr(cam.x, cam.y, cam.z) > reach * reach) return;

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TransponderSnailBlock) || !state.hasProperty(TransponderSnailBlock.FACING)) return;
        Direction facing = state.getValue(TransponderSnailBlock.FACING);

        int size = VisualCallConstants.SCREEN_DEFAULT_SIZE;
        float offSide = 0.0F;
        float offUp = 0.0F;
        float offBack = 0.0F;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransponderSnailBlockEntity snail) {
            size = snail.getScreenSize();
            offSide = snail.getScreenOffsetSide();
            offUp = snail.getScreenOffsetUp();
            offBack = snail.getScreenOffsetBack();
        }
        // While the settings menu of THIS snail is open its values apply at once, before the server has confirmed them.
        VisualConfigScreen editing = VisualConfigScreen.active();
        if (editing != null && editing.pos().equals(pos)) {
            size = editing.size();
            offSide = editing.side();
            offUp = editing.up();
            offBack = editing.back();
        }

        // depth is found automatically (the surface straight behind the snail), the size follows the projector's zoom
        ProjectorPlacement.Result placement = place(level, pos, facing, size, offSide, offUp, offBack);
        size = placement.size(); // what is shown: the preferred size held inside the zoom range for this distance
        double[][] c = placement.quad().corners();
        double[] middle = placement.center();
        double scx = pos.getX() + middle[0];
        double scy = pos.getY() + middle[1];
        double scz = pos.getZ() + middle[2];
        if ((scx - cam.x) * (scx - cam.x) + (scy - cam.y) * (scy - cam.y) + (scz - cam.z) * (scz - cam.z) > maxDistSq) return;

        // only spend time on screens the camera can see
        AABB bounds = new AABB(
                pos.getX() + Math.min(Math.min(c[0][0], c[1][0]), Math.min(c[2][0], c[3][0])) - 0.1,
                pos.getY() + Math.min(c[0][1], c[1][1]) - 0.1,
                pos.getZ() + Math.min(Math.min(c[0][2], c[1][2]), Math.min(c[2][2], c[3][2])) - 0.1,
                pos.getX() + Math.max(Math.max(c[0][0], c[1][0]), Math.max(c[2][0], c[3][0])) + 0.1,
                pos.getY() + Math.max(c[2][1], c[3][1]) + 0.1,
                pos.getZ() + Math.max(Math.max(c[0][2], c[1][2]), Math.max(c[2][2], c[3][2])) + 0.1);
        if (!mc.levelRenderer.getFrustum().isVisible(bounds)) return;

        VisualFeed feed = VisualFeedManager.touchFeed(link); // marks the feed as wanted
        if (feed != null) {
            // How many pixels of this display the screen covers and how far away it is: adaptive quality picks the resolution,
            // frame rate and view radius of the feed from it (a far or small screen does not need a sharp feed).
            double dist = Math.sqrt((scx - cam.x) * (scx - cam.x) + (scy - cam.y) * (scy - cam.y) + (scz - cam.z) * (scz - cam.z));
            double pixels = VisualQuality.projectedPixels(size, dist, mc.getWindow().getHeight(), mc.options.fov().get());
            VisualFeedManager.reportView(link, pixels, dist);
        }
        // projector still off: nothing until the first fresh picture of this call exists
        if (feed == null || !feed.hasFrame() || link.pictureStartMs < 0L) return;

        // ---- fade in (smoothstep) with a little warm-up flicker ----
        long now = System.currentTimeMillis();
        float t = Math.min(1.0F, Math.max(0.0F, (now - link.pictureStartMs) / (float) VisualCallConstants.FEED_FADE_IN_MS));
        float ease = t * t * (3.0F - 2.0F * t);
        float noise = 0.5F + 0.5F * (float) (Math.sin(now * 0.045) * Math.sin(now * 0.113 + 1.3)); // 0..1
        float flicker = 1.0F - (1.0F - t) * 0.35F * noise;                                          // gone once fully faded in
        float alpha = VisualCallConstants.FEED_OPACITY * ease * flicker;
        if (alpha <= 0.0F) return;

        // ---- light level: at least the projector's, or the surroundings' if brighter (vanilla light curve) ----
        Direction screenDir = VisualCallConstants.SCREEN_BEHIND_SNAIL ? facing.getOpposite() : facing;
        int worldLight = level.getMaxLocalRawBrightness(pos.relative(screenDir, placement.lightSteps()));
        int light = Math.max(worldLight, VisualCallConstants.PROJECTOR_IMAGE_LIGHT);
        float lt = Math.min(15, Math.max(0, light)) / 15.0F;
        float bright = Math.min(1.0F, lt / (4.0F - 3.0F * lt));

        int r = Math.round(VisualCallConstants.FEED_TINT_R * bright);
        int g = Math.round(VisualCallConstants.FEED_TINT_G * bright);
        int b = Math.round(VisualCallConstants.FEED_TINT_B * bright);
        int a = Math.round(Math.min(1.0F, alpha) * 255.0F);

        if (!link.drawLogged) {
            link.drawLogged = true;
            LOGGER.info("VisualScreenRenderer: drawing the feed of the snail at {} on the screen at {} ({}x{}, zoom range {}..{}, {} blocks {} the snail, {}, light {}, opacity {})",
                    link.cameraPos(), pos, size, size, placement.minSize(), placement.maxSize(),
                    String.format(java.util.Locale.ROOT, "%.1f", placement.depth()),
                    VisualCallConstants.SCREEN_BEHIND_SNAIL ? "behind" : "in front of",
                    placement.surfaceFound() ? "on a surface" : "floating, no surface in reach", light, VisualCallConstants.FEED_OPACITY);
        }

        RenderType type = feed.renderType();
        pose.pushPose();
        try {
            pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            Matrix4f matrix = pose.last().pose();
            VertexConsumer consumer = buffers.getBuffer(type);

            // front (counter clockwise for a viewer in front of the snail), then back (reversed, so it is visible from behind too)
            int[] front = {0, 1, 2, 3};
            int[] back = {0, 3, 2, 1};
            for (int[] order : new int[][]{front, back}) {
                for (int i : order) {
                    consumer.vertex(matrix, (float) c[i][0], (float) c[i][1], (float) c[i][2])
                            .color(r, g, b, a)
                            .uv(ScreenLayout.Quad.UVS[i][0], ScreenLayout.Quad.UVS[i][1])
                            .endVertex();
                }
            }
        } finally {
            pose.popPose();
        }
        buffers.endBatch(type);
    }

    // ------------------------------------------------------------------------------------------------
    // Placement (shared by the real screen and the settings preview, so they can never disagree)
    // ------------------------------------------------------------------------------------------------

    /** How long a found surface distance is reused (ms). Short: a wall that is placed or broken is noticed within a few ticks. */
    private static final long SURFACE_CACHE_MS = 200L;

    private record SurfaceEntry(long timeMs, Direction dir, double distance) {}

    private static final Map<BlockPos, SurfaceEntry> SURFACE_CACHE = new HashMap<>();
    private static long lastSurfacePruneMs = 0L;

    /** Blocks from the snail's back to the surface straight behind it (NaN = none in reach). One ray per snail per 200 ms, not per frame. */
    private static double surfaceDistance(ClientLevel level, BlockPos pos, Direction facing) {
        long now = System.currentTimeMillis();
        Direction dir = VisualCallConstants.SCREEN_BEHIND_SNAIL ? facing.getOpposite() : facing;

        SurfaceEntry cached = SURFACE_CACHE.get(pos);
        if (cached != null && cached.dir() == dir && now - cached.timeMs() < SURFACE_CACHE_MS) {
            return cached.distance();
        }
        double distance = ProjectorSurface.find(level, pos, dir);
        SURFACE_CACHE.put(pos.immutable(), new SurfaceEntry(now, dir, distance));

        if (now - lastSurfacePruneMs > 10_000L) { // forget snails nobody has looked at for a while
            lastSurfacePruneMs = now;
            Iterator<SurfaceEntry> it = SURFACE_CACHE.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().timeMs() > 5_000L) it.remove();
            }
        }
        return distance;
    }

    private static ProjectorPlacement.Result place(ClientLevel level, BlockPos pos, Direction facing, int size,
                                                   float side, float up, float back) {
        return ProjectorPlacement.compute(facing.getStepX(), facing.getStepZ(), size, side, up, back,
                VisualSettings.maxScreenSize(), surfaceDistance(level, pos, facing));
    }

    /** Distance (blocks) found for the settings menu, same cache and same ray as the real screen uses. */
    static double surfaceDistanceFor(ClientLevel level, BlockPos pos, Direction facing) {
        return surfaceDistance(level, pos, facing);
    }

    // ------------------------------------------------------------------------------------------------
    // Settings preview: a faint fill with a frame around it, shown while the settings menu is open
    // ------------------------------------------------------------------------------------------------

    /** Thickness (blocks) of the preview frame. It is drawn OUTSIDE the screen's edge, so it never overlaps the picture. */
    private static final double PREVIEW_FRAME_WIDTH = 0.06;

    private static RenderType previewType;

    private static RenderType previewType() {
        if (previewType == null) {
            previewType = RenderType.create(
                    "transpondersnails_projector_preview",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    256,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                            .setTransparencyState(VisualFeed.PROJECTOR_TRANSPARENCY)
                            .createCompositeState(false));
        }
        return previewType;
    }

    private static void drawPreview(ClientLevel level, VisualConfigScreen editing, Vec3 cam, PoseStack pose,
                                    MultiBufferSource.BufferSource buffers) {
        BlockPos pos = editing.pos();
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TransponderSnailBlock) || !state.hasProperty(TransponderSnailBlock.FACING)) return;
        Direction facing = state.getValue(TransponderSnailBlock.FACING);

        double[][] c = place(level, pos, facing, editing.size(), editing.side(), editing.up(), editing.back()).quad().corners();
        double bx = c[1][0] - c[0][0];
        double bz = c[1][2] - c[0][2];
        double len = Math.sqrt(bx * bx + bz * bz);
        if (len < 1.0E-6) return;
        double w = PREVIEW_FRAME_WIDTH;
        double rx = bx / len * w; // the screen's own "right", as long as the frame is thick
        double rz = bz / len * w;

        // the outer corners of the frame
        double[] oBL = offset(c[0], -rx, -w, -rz);
        double[] oBR = offset(c[1], rx, -w, rz);
        double[] oTR = offset(c[2], rx, w, rz);
        double[] oTL = offset(c[3], -rx, w, -rz);

        RenderType type = previewType();
        pose.pushPose();
        try {
            pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            Matrix4f matrix = pose.last().pose();
            VertexConsumer vc = buffers.getBuffer(type);

            previewQuad(vc, matrix, c[0], c[1], c[2], c[3], 80, 200, 255, 34);          // faint fill: the area
            previewQuad(vc, matrix, oBL, oBR, c[1], c[0], 140, 230, 255, 235);          // frame: bottom
            previewQuad(vc, matrix, c[3], c[2], oTR, oTL, 140, 230, 255, 235);          //        top
            previewQuad(vc, matrix, oBL, c[0], c[3], oTL, 140, 230, 255, 235);          //        left
            previewQuad(vc, matrix, c[1], oBR, oTR, c[2], 140, 230, 255, 235);          //        right
        } finally {
            pose.popPose();
        }
        buffers.endBatch(type);
    }

    private static double[] offset(double[] p, double dx, double dy, double dz) {
        return new double[]{p[0] + dx, p[1] + dy, p[2] + dz};
    }

    /** One coloured quad, drawn from both sides. */
    private static void previewQuad(VertexConsumer vc, Matrix4f matrix, double[] p0, double[] p1, double[] p2, double[] p3,
                                    int r, int g, int b, int a) {
        double[][] p = {p0, p1, p2, p3};
        int[] front = {0, 1, 2, 3};
        int[] back = {0, 3, 2, 1};
        for (int[] order : new int[][]{front, back}) {
            for (int i : order) {
                vc.vertex(matrix, (float) p[i][0], (float) p[i][1], (float) p[i][2]).color(r, g, b, a).endVertex();
            }
        }
    }
}
