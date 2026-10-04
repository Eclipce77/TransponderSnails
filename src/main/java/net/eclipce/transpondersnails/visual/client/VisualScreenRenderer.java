package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.ScreenLayout;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
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

    private VisualScreenRenderer() {}

    static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (VisualFeedManager.isCapturing()) return; // no screen-in-screen while a feed is being rendered

        java.util.Collection<VisualFeedManager.ScreenLink> links = VisualFeedManager.activeLinks();
        if (links.isEmpty()) return;

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
    }

    private static void drawScreen(Minecraft mc, ClientLevel level, VisualFeedManager.ScreenLink link,
                                   Vec3 cam, PoseStack pose, MultiBufferSource.BufferSource buffers, double maxDistSq) {
        BlockPos pos = link.screenPos();
        if (!level.isLoaded(pos)) return;
        if (pos.distToCenterSqr(cam.x, cam.y, cam.z) > maxDistSq) return;

        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TransponderSnailBlock) || !state.hasProperty(TransponderSnailBlock.FACING)) return;
        Direction facing = state.getValue(TransponderSnailBlock.FACING);

        int size = VisualCallConstants.SCREEN_DEFAULT_SIZE;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof TransponderSnailBlockEntity snail) {
            size = snail.getScreenSize();
        }

        // which side of the snail the screen is on
        Direction screenDir = VisualCallConstants.SCREEN_BEHIND_SNAIL ? facing.getOpposite() : facing;
        // Nominal distance, a hair closer so the screen never shares a plane with a block face behind it (z-fighting).
        double distance = VisualCallConstants.SCREEN_FLOAT_DISTANCE - VisualCallConstants.SCREEN_SURFACE_OFFSET;
        // A solid block touching the snail on that side (snail against a wall): the screen would end up inside it and be
        // invisible, so lay it flat on that block's face instead.
        BlockPos adjacent = pos.relative(screenDir);
        boolean wallAdjacent = !level.getBlockState(adjacent).getCollisionShape(level, adjacent).isEmpty();
        if (wallAdjacent) {
            distance = -VisualCallConstants.SCREEN_SURFACE_OFFSET;
        }
        ScreenLayout.Quad quad = VisualCallConstants.SCREEN_BEHIND_SNAIL
                ? ScreenLayout.computeBehind(facing.getStepX(), facing.getStepZ(), size, distance)
                : ScreenLayout.computeFloating(facing.getStepX(), facing.getStepZ(), size, distance);
        double[][] c = quad.corners();

        // only spend time on screens the camera can see
        AABB bounds = new AABB(
                pos.getX() + Math.min(Math.min(c[0][0], c[1][0]), Math.min(c[2][0], c[3][0])) - 0.1,
                pos.getY() - 0.1,
                pos.getZ() + Math.min(Math.min(c[0][2], c[1][2]), Math.min(c[2][2], c[3][2])) - 0.1,
                pos.getX() + Math.max(Math.max(c[0][0], c[1][0]), Math.max(c[2][0], c[3][0])) + 0.1,
                pos.getY() + size + 0.1,
                pos.getZ() + Math.max(Math.max(c[0][2], c[1][2]), Math.max(c[2][2], c[3][2])) + 0.1);
        if (!mc.levelRenderer.getFrustum().isVisible(bounds)) return;

        VisualFeed feed = VisualFeedManager.touchFeed(link); // marks the feed as wanted
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
        int worldLight = level.getMaxLocalRawBrightness(wallAdjacent ? pos : adjacent);
        int light = Math.max(worldLight, VisualCallConstants.PROJECTOR_IMAGE_LIGHT);
        float lt = Math.min(15, Math.max(0, light)) / 15.0F;
        float bright = Math.min(1.0F, lt / (4.0F - 3.0F * lt));

        int r = Math.round(VisualCallConstants.FEED_TINT_R * bright);
        int g = Math.round(VisualCallConstants.FEED_TINT_G * bright);
        int b = Math.round(VisualCallConstants.FEED_TINT_B * bright);
        int a = Math.round(Math.min(1.0F, alpha) * 255.0F);

        if (!link.drawLogged) {
            link.drawLogged = true;
            LOGGER.info("VisualScreenRenderer: drawing the feed of the snail at {} on the screen at {} ({}x{}, {} block(s) {} the snail{}, light {}, opacity {})",
                    link.cameraPos(), pos, size, size, VisualCallConstants.SCREEN_FLOAT_DISTANCE,
                    VisualCallConstants.SCREEN_BEHIND_SNAIL ? "behind" : "in front of",
                    wallAdjacent ? ", laid flat on the adjacent wall" : "", light, VisualCallConstants.FEED_OPACITY);
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
}
