package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.ScreenLayout;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
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

/**
 * Draws the video screens: a size x size square floating in the air {@link VisualCallConstants#SCREEN_FLOAT_DISTANCE}
 * block(s) in front of every Visual Snail that is part of a running call. No wall is needed.
 *
 * Drawn from a level render event (not a block entity renderer) so it does not depend on the snail's BlockEntityType
 * or on chunk compilation. Visible from both sides (the back side is mirrored, like a hologram).
 * While the screen has no picture yet a dark "no signal" panel is drawn instead.
 */
@OnlyIn(Dist.CLIENT)
public final class VisualScreenRenderer {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static RenderType noSignalType;
    private static boolean errorLogged = false;

    private VisualScreenRenderer() {}

    private static RenderType noSignalType() {
        if (noSignalType == null) {
            noSignalType = RenderType.create(
                    "transpondersnails_visual_no_signal",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    256,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                            .createCompositeState(false));
        }
        return noSignalType;
    }

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
                drawScreen(mc, level, link, event, cam, pose, buffers, maxDistSq);
            } catch (Exception e) {
                if (!errorLogged) {
                    errorLogged = true;
                    LOGGER.error("VisualScreenRenderer: drawing the screen at {} failed (further errors are not logged)", link.screenPos(), e);
                }
            }
        }
    }

    private static void drawScreen(Minecraft mc, ClientLevel level, VisualFeedManager.ScreenLink link, RenderLevelStageEvent event,
                                   Vec3 cam, PoseStack pose, MultiBufferSource.BufferSource buffers, double maxDistSq) {
        {
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

            ScreenLayout.Quad quad = ScreenLayout.computeFloating(
                    facing.getStepX(), facing.getStepZ(), size, VisualCallConstants.SCREEN_FLOAT_DISTANCE);
            double[][] c = quad.corners();

            // only spend time on screens the camera can see
            AABB bounds = new AABB(
                    pos.getX() + Math.min(Math.min(c[0][0], c[1][0]), Math.min(c[2][0], c[3][0])) - 0.1,
                    pos.getY() + 0.0 - 0.1,
                    pos.getZ() + Math.min(Math.min(c[0][2], c[1][2]), Math.min(c[2][2], c[3][2])) - 0.1,
                    pos.getX() + Math.max(Math.max(c[0][0], c[1][0]), Math.max(c[2][0], c[3][0])) + 0.1,
                    pos.getY() + size + 0.1,
                    pos.getZ() + Math.max(Math.max(c[0][2], c[1][2]), Math.max(c[2][2], c[3][2])) + 0.1);
            if (!mc.levelRenderer.getFrustum().isVisible(bounds)) return;

            VisualFeed feed = VisualFeedManager.touchFeed(link); // marks the feed as wanted
            boolean hasPicture = feed != null && feed.hasFrame();

            if (hasPicture) {
                if (!link.drawLogged) {
                    link.drawLogged = true;
                    LOGGER.info("VisualScreenRenderer: drawing the feed of the snail at {} on the screen at {} ({}x{}, {} block(s) in front)",
                            link.cameraPos(), pos, size, size, VisualCallConstants.SCREEN_FLOAT_DISTANCE);
                }
            } else if (!link.noSignalLogged) {
                link.noSignalLogged = true;
                LOGGER.info("VisualScreenRenderer: screen at {} has no picture yet - drawing the 'no signal' panel", pos);
            }

            RenderType type = hasPicture ? feed.renderType() : noSignalType();
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
                    consumer.vertex(matrix, (float) c[i][0], (float) c[i][1], (float) c[i][2]);
                    if (hasPicture) {
                        consumer.color(VisualCallConstants.FEED_TINT_R, VisualCallConstants.FEED_TINT_G, VisualCallConstants.FEED_TINT_B, 255)
                                .uv(ScreenLayout.Quad.UVS[i][0], ScreenLayout.Quad.UVS[i][1]);
                    } else {
                        consumer.color(18, 38, 44, 255);
                    }
                    consumer.endVertex();
                }
            }
            } finally {
                pose.popPose();
            }
            buffers.endBatch(type);
        }
    }
}
