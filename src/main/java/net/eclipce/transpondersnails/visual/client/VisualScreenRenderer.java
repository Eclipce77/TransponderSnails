package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.ProjectorEffects;
import net.eclipce.transpondersnails.visual.ProjectorPlacement;
import net.eclipce.transpondersnails.visual.ProjectorTransition;
import net.eclipce.transpondersnails.visual.ProjectorSurface;
import net.eclipce.transpondersnails.visual.ScreenLayout;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.VisualGlow;
import net.eclipce.transpondersnails.visual.VisualQuality;
import net.eclipce.transpondersnails.visual.VisualRoles;
import net.eclipce.transpondersnails.visual.VisualSettings;
import net.eclipce.transpondersnails.visual.VisualSnailRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

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
    private static boolean glowErrorLogged = false;
    private static final Set<String> MISSING_GLOW_MODELS = new HashSet<>();

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

        // The glowing eyes / projector parts of the snails first: they are solid, the translucent picture and light come after them.
        try {
            drawGlows(mc, level, links, cam, pose, buffers, maxDistSq);
        } catch (Exception e) {
            if (!glowErrorLogged) {
                glowErrorLogged = true;
                LOGGER.error("VisualScreenRenderer: drawing the glow of the snails failed (further errors are not logged)", e);
            }
        }

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
        double screenDistSq = (scx - cam.x) * (scx - cam.x) + (scy - cam.y) * (scy - cam.y) + (scz - cam.z) * (scz - cam.z);
        boolean screenInRange = screenDistSq <= maxDistSq;
        if (!screenInRange && pos.distToCenterSqr(cam.x, cam.y, cam.z) > maxDistSq) return; // neither the screen nor the snail is near

        // only spend time on what the camera can see: the picture, and the light ray from the snail to it
        AABB bounds = new AABB(
                pos.getX() + Math.min(Math.min(c[0][0], c[1][0]), Math.min(c[2][0], c[3][0])) - 0.1,
                pos.getY() + Math.min(c[0][1], c[1][1]) - 0.1,
                pos.getZ() + Math.min(Math.min(c[0][2], c[1][2]), Math.min(c[2][2], c[3][2])) - 0.1,
                pos.getX() + Math.max(Math.max(c[0][0], c[1][0]), Math.max(c[2][0], c[3][0])) + 0.1,
                pos.getY() + Math.max(c[2][1], c[3][1]) + 0.1,
                pos.getZ() + Math.max(Math.max(c[0][2], c[1][2]), Math.max(c[2][2], c[3][2])) + 0.1);
        Frustum frustum = mc.levelRenderer.getFrustum();
        boolean live = link.pictureStartMs >= 0L; // the first fresh picture of this call exists: the projector is on
        boolean ending = link.isEnding();         // the call is over: the screen fades out on its last picture
        boolean screenVisible = screenInRange && frustum.isVisible(bounds);
        boolean beamVisible = VisualCallConstants.PROJECTOR_BEAM_ENABLED && live && frustum.isVisible(bounds.minmax(new AABB(pos)));
        if (!screenVisible && !beamVisible) return;

        VisualFeed feed = null;
        if (screenVisible) {
            // a screen that is fading out must not make its feed capture again: the last picture stays frozen
            feed = ending ? VisualFeedManager.peekFeed(link) : VisualFeedManager.touchFeed(link); // touching marks the feed as wanted
            if (feed != null && !ending) {
                // How many pixels of this display the screen covers and how far away it is: adaptive quality picks the resolution,
                // frame rate and view radius of the feed from it (a far or small screen does not need a sharp feed).
                double dist = Math.sqrt(screenDistSq);
                double pixels = VisualQuality.projectedPixels(size, dist, mc.getWindow().getHeight(), mc.options.fov().get());
                VisualFeedManager.reportView(link, pixels, dist);
            }
        }
        // projector still off: no picture, no light and no effects until the first fresh picture of this call exists
        if (!live) return;
        boolean pictureReady = screenVisible && feed != null && feed.hasFrame();

        // ---- the projector turning on and off, like an old CRT television: fade, static and a pop of light (see ProjectorTransition) ----
        long now = System.currentTimeMillis();
        long sinceStart = now - link.pictureStartMs;
        long sinceEnd = ending ? now - link.endMs : -1L;
        float ease = (float) ProjectorTransition.visibility(sinceStart, sinceEnd);          // fades in, and out again when the call ends
        double staticAmount = ProjectorTransition.staticAmount(sinceStart, sinceEnd);       // TV snow at both ends
        double pop = ProjectorTransition.pop(sinceStart, sinceEnd);                         // a flash of light at both ends
        float noise = 0.5F + 0.5F * (float) (Math.sin(now * 0.045) * Math.sin(now * 0.113 + 1.3)); // 0..1
        float flicker = (float) ProjectorTransition.flicker(sinceStart, sinceEnd, noise);   // warm-up / dying flicker, none in between
        float alpha = VisualCallConstants.FEED_OPACITY * ease * flicker;
        if (alpha <= 0.0F && staticAmount <= 0.0 && pop <= 0.0) return; // nothing to see (yet / any more)

        // ---- light: the picture is at least as bright as the projector's own light, or as the surroundings if those are brighter ----
        Direction screenDir = VisualCallConstants.SCREEN_BEHIND_SNAIL ? facing.getOpposite() : facing;
        int worldLight = level.getMaxLocalRawBrightness(pos.relative(screenDir, placement.lightSteps())); // the light that is there anyway
        int light = Math.max(worldLight, VisualCallConstants.PROJECTOR_IMAGE_LIGHT);
        float bright = (float) ProjectorEffects.pictureBrightness(worldLight);

        // ---- the optics of a projected image ----
        // a longer throw and a bigger picture spread the same light thinner; in daylight the picture is washed out; the glow and the
        // ray show much better in the dark than in the sun
        float optics = (float) ProjectorEffects.throwBrightness(placement.depth(), size, VisualSettings.maxScreenSize());
        float washout = (float) ProjectorEffects.ambientWashout(worldLight);
        double baseGlow = ProjectorEffects.glowStrength(worldLight);
        double glow = baseGlow * ease;       // the glow and the ray follow the fade
        double popLight = baseGlow * pop;    // the pop of light does not: it is strongest when the picture is not there (yet / any more)

        int r = Math.round(VisualCallConstants.FEED_TINT_R * bright * optics);
        int g = Math.round(VisualCallConstants.FEED_TINT_G * bright * optics);
        int b = Math.round(VisualCallConstants.FEED_TINT_B * bright * optics);
        int a = Math.round(Math.min(1.0F, alpha * washout) * 255.0F);

        if (!link.drawLogged) {
            link.drawLogged = true;
            LOGGER.info("VisualScreenRenderer: drawing the feed of the snail at {} on the screen at {} ({}x{}, zoom range {}..{}, {} blocks {} the snail, {}, light {}, opacity {})",
                    link.cameraPos(), pos, size, size, placement.minSize(), placement.maxSize(),
                    String.format(java.util.Locale.ROOT, "%.1f", placement.depth()),
                    VisualCallConstants.SCREEN_BEHIND_SNAIL ? "behind" : "in front of",
                    placement.surfaceFound() ? "on a surface" : "floating, no surface in reach", light, VisualCallConstants.FEED_OPACITY);
        }

        RenderType additive = VisualEffectTypes.additive();
        pose.pushPose();
        try {
            pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            Matrix4f matrix = pose.last().pose();

            if (pictureReady) {
                if (alpha > 0.0F) {
                    RenderType type = feed.renderType();
                    drawPicture(buffers.getBuffer(type), matrix, c, size, r, g, b, a, 0.0F, 0.0F, 1.0F);
                    buffers.endBatch(type); // the picture first, then the static over it, then the light on top of both
                }
                if (staticAmount > 0.0) {
                    float[] window = VisualStaticTexture.window(now); // fresh noise every few hundredths of a second
                    int staticAlpha = Math.round(Math.min(1.0F, (float) (VisualCallConstants.FEED_OPACITY * staticAmount * washout)) * 255.0F);
                    RenderType staticType = VisualStaticTexture.renderType();
                    drawPicture(buffers.getBuffer(staticType), matrix, c, size, r, g, b, staticAlpha, window[0], window[1], VisualStaticTexture.WINDOW);
                    buffers.endBatch(staticType);
                }
            }
            if (screenVisible) {
                double bloom = glow + VisualCallConstants.PROJECTOR_POP_BLOOM_BOOST * popLight; // the glow flares up with the pop...
                drawBloom(buffers.getBuffer(additive), matrix, c, size, bloom, 1.0 + 0.8 * pop);  // ...and spreads wider
                if (pop > 0.0) {
                    drawFlash(buffers.getBuffer(additive), matrix, c, size, pop * VisualCallConstants.PROJECTOR_POP_FLASH_ALPHA);
                    drawCrtLine(buffers.getBuffer(additive), matrix, c, size, pop * VisualCallConstants.PROJECTOR_POP_LINE_ALPHA);
                }
            }
            if (beamVisible) {
                double[] lens = ProjectorEffects.lensPosition(facing.getStepX(), facing.getStepZ());
                drawBeam(buffers.getBuffer(additive), matrix, c, middle, lens, glow + VisualCallConstants.PROJECTOR_POP_BEAM_BOOST * popLight);
            }
            buffers.endBatch(additive);
        } finally {
            pose.popPose();
        }
    }

    // ------------------------------------------------------------------------------------------------
    // The picture: a mesh whose brightness and opacity fall off towards the edges, like a projected image
    // ------------------------------------------------------------------------------------------------

    private static int to255(double v) {
        return (int) Math.max(0L, Math.min(255L, Math.round(v)));
    }

    /** A point of the screen quad (corners: bottom left, bottom right, top right, top left), u across and v up, both 0..1. */
    private static double bilinear(double[][] c, int axis, double u, double v) {
        return (1.0 - v) * ((1.0 - u) * c[0][axis] + u * c[1][axis]) + v * ((1.0 - u) * c[3][axis] + u * c[2][axis]);
    }

    /**
     * One vertex of the picture mesh. (u, v) is where it is on the picture, 0..1; the texture coordinate is
     * (uOff + u * uvScale, vOff + v * uvScale): the whole feed for the picture (0, 0, 1), a random window of the noise for the static.
     */
    private static void pictureVertex(VertexConsumer vc, Matrix4f m, double[][] c, float u, float v,
                                      int r, int g, int b, int a, double feather, float uOff, float vOff, float uvScale) {
        double shade = ProjectorEffects.vignette(u, v);          // brightest in the middle
        double fade = ProjectorEffects.edgeAlpha(u, v, feather);  // fading out at the edge
        vc.vertex(m, (float) bilinear(c, 0, u, v), (float) bilinear(c, 1, u, v), (float) bilinear(c, 2, u, v))
                .color(to255(r * shade), to255(g * shade), to255(b * shade), to255(a * fade))
                .uv(uOff + u * uvScale, vOff + v * uvScale) // the render target's v = 0 is the BOTTOM of the image, like the corners' order
                .endVertex();
    }

    private static void drawPicture(VertexConsumer vc, Matrix4f m, double[][] c, int size, int r, int g, int b, int a,
                                    float uOff, float vOff, float uvScale) {
        float[] grid = ProjectorEffects.GRID;
        double feather = ProjectorEffects.edgeFeather(size);
        for (int iy = 0; iy < grid.length - 1; iy++) {
            float v0 = grid[iy];
            float v1 = grid[iy + 1];
            for (int ix = 0; ix < grid.length - 1; ix++) {
                float u0 = grid[ix];
                float u1 = grid[ix + 1];
                // front (counter clockwise for a viewer in front of the snail)...
                pictureVertex(vc, m, c, u0, v0, r, g, b, a, feather, uOff, vOff, uvScale);
                pictureVertex(vc, m, c, u1, v0, r, g, b, a, feather, uOff, vOff, uvScale);
                pictureVertex(vc, m, c, u1, v1, r, g, b, a, feather, uOff, vOff, uvScale);
                pictureVertex(vc, m, c, u0, v1, r, g, b, a, feather, uOff, vOff, uvScale);
                // ...and back (reversed, so it is visible from behind too)
                pictureVertex(vc, m, c, u0, v0, r, g, b, a, feather, uOff, vOff, uvScale);
                pictureVertex(vc, m, c, u0, v1, r, g, b, a, feather, uOff, vOff, uvScale);
                pictureVertex(vc, m, c, u1, v1, r, g, b, a, feather, uOff, vOff, uvScale);
                pictureVertex(vc, m, c, u1, v0, r, g, b, a, feather, uOff, vOff, uvScale);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Light: the bloom around the picture and the ray from the projector to it (both added to the world, never hiding anything)
    // ------------------------------------------------------------------------------------------------

    private static void lightVertex(VertexConsumer vc, Matrix4f m, double[] p, int alpha) {
        vc.vertex(m, (float) p[0], (float) p[1], (float) p[2])
                .color(VisualCallConstants.PROJECTOR_LIGHT_R, VisualCallConstants.PROJECTOR_LIGHT_G, VisualCallConstants.PROJECTOR_LIGHT_B, alpha)
                .endVertex();
    }

    /** One quad of light, each corner with its own opacity (they blend smoothly), drawn from both sides. */
    private static void lightQuad(VertexConsumer vc, Matrix4f m, double[] p0, int a0, double[] p1, int a1,
                                  double[] p2, int a2, double[] p3, int a3) {
        lightVertex(vc, m, p0, a0);
        lightVertex(vc, m, p1, a1);
        lightVertex(vc, m, p2, a2);
        lightVertex(vc, m, p3, a3);
        lightVertex(vc, m, p0, a0);
        lightVertex(vc, m, p3, a3);
        lightVertex(vc, m, p2, a2);
        lightVertex(vc, m, p1, a1);
    }

    /** The soft glow around the picture: light spilling over the wall, strongest at the picture's edge and gone a little way out. */
    private static void drawBloom(VertexConsumer vc, Matrix4f m, double[][] c, int size, double strength, double widthScale) {
        double bx = c[1][0] - c[0][0];
        double bz = c[1][2] - c[0][2];
        double len = Math.sqrt(bx * bx + bz * bz);
        if (len < 1.0E-6) return;
        double rx = bx / len; // the picture's own "right" (a unit vector)
        double rz = bz / len;
        double width = ProjectorEffects.bloomWidth(size) * widthScale;
        // two layers: a bright narrow one and a faint wide one - together they fall off fast near the picture and slowly further out
        bloomLayer(vc, m, c, rx, rz, width * 0.45, VisualCallConstants.PROJECTOR_BLOOM_NEAR_ALPHA * strength);
        bloomLayer(vc, m, c, rx, rz, width, VisualCallConstants.PROJECTOR_BLOOM_FAR_ALPHA * strength);
    }

    private static void bloomLayer(VertexConsumer vc, Matrix4f m, double[][] c, double rx, double rz, double w, double alpha) {
        int inner = to255(alpha * 255.0);
        if (inner <= 0) return;
        double[] oBL = {c[0][0] - rx * w, c[0][1] - w, c[0][2] - rz * w};
        double[] oBR = {c[1][0] + rx * w, c[1][1] - w, c[1][2] + rz * w};
        double[] oTR = {c[2][0] + rx * w, c[2][1] + w, c[2][2] + rz * w};
        double[] oTL = {c[3][0] - rx * w, c[3][1] + w, c[3][2] - rz * w};
        // four trapezoids around the picture: opaque at the picture's edge, transparent at the outer edge
        lightQuad(vc, m, oBL, 0, oBR, 0, c[1], inner, c[0], inner); // below
        lightQuad(vc, m, c[3], inner, c[2], inner, oTR, 0, oTL, 0); // above
        lightQuad(vc, m, oBL, 0, c[0], inner, c[3], inner, oTL, 0); // left
        lightQuad(vc, m, c[1], inner, oBR, 0, oTR, 0, c[2], inner); // right
    }

    private static void lightPoint(VertexConsumer vc, Matrix4f m, double[][] c, double u, double v, int alpha) {
        vc.vertex(m, (float) bilinear(c, 0, u, v), (float) bilinear(c, 1, u, v), (float) bilinear(c, 2, u, v))
                .color(VisualCallConstants.PROJECTOR_LIGHT_R, VisualCallConstants.PROJECTOR_LIGHT_G, VisualCallConstants.PROJECTOR_LIGHT_B, alpha)
                .endVertex();
    }

    /** The flash of the pop: the whole picture area lit up, soft at its edges (the same mesh as the picture), both sides. */
    private static void drawFlash(VertexConsumer vc, Matrix4f m, double[][] c, int size, double alpha) {
        double peak = alpha * 255.0;
        if (peak < 1.0) return;
        float[] grid = ProjectorEffects.GRID;
        double feather = ProjectorEffects.edgeFeather(size);
        for (int iy = 0; iy < grid.length - 1; iy++) {
            float v0 = grid[iy];
            float v1 = grid[iy + 1];
            for (int ix = 0; ix < grid.length - 1; ix++) {
                float u0 = grid[ix];
                float u1 = grid[ix + 1];
                int a00 = to255(peak * ProjectorEffects.edgeAlpha(u0, v0, feather));
                int a10 = to255(peak * ProjectorEffects.edgeAlpha(u1, v0, feather));
                int a11 = to255(peak * ProjectorEffects.edgeAlpha(u1, v1, feather));
                int a01 = to255(peak * ProjectorEffects.edgeAlpha(u0, v1, feather));
                lightPoint(vc, m, c, u0, v0, a00);
                lightPoint(vc, m, c, u1, v0, a10);
                lightPoint(vc, m, c, u1, v1, a11);
                lightPoint(vc, m, c, u0, v1, a01);
                lightPoint(vc, m, c, u0, v0, a00);
                lightPoint(vc, m, c, u0, v1, a01);
                lightPoint(vc, m, c, u1, v1, a11);
                lightPoint(vc, m, c, u1, v0, a10);
            }
        }
    }

    /**
     * The bright horizontal line of a CRT turning on or off: a thin band across the middle of the picture, brightest in its centre and fading
     * out above, below and at both ends.
     */
    private static void drawCrtLine(VertexConsumer vc, Matrix4f m, double[][] c, int size, double alpha) {
        int peak = to255(alpha * 255.0);
        if (peak <= 0) return;
        double half = Math.min(0.1, (0.10 + 0.012 * size) / size / 2.0); // half the band's height, as a fraction of the picture's
        double[] us = {0.0, 0.05, 0.95, 1.0};
        int[] alphas = {0, peak, peak, 0};
        for (int k = 0; k < us.length - 1; k++) {
            double u0 = us[k];
            double u1 = us[k + 1];
            int a0 = alphas[k];
            int a1 = alphas[k + 1];
            for (int side = 0; side < 2; side++) { // the upper and the lower half of the band
                double outer = side == 0 ? 0.5 + half : 0.5 - half;
                if (side == 0) {
                    lightPoint(vc, m, c, u0, 0.5, a0);
                    lightPoint(vc, m, c, u1, 0.5, a1);
                    lightPoint(vc, m, c, u1, outer, 0);
                    lightPoint(vc, m, c, u0, outer, 0);
                    lightPoint(vc, m, c, u0, 0.5, a0);
                    lightPoint(vc, m, c, u0, outer, 0);
                    lightPoint(vc, m, c, u1, outer, 0);
                    lightPoint(vc, m, c, u1, 0.5, a1);
                } else {
                    lightPoint(vc, m, c, u0, outer, 0);
                    lightPoint(vc, m, c, u1, outer, 0);
                    lightPoint(vc, m, c, u1, 0.5, a1);
                    lightPoint(vc, m, c, u0, 0.5, a0);
                    lightPoint(vc, m, c, u0, outer, 0);
                    lightPoint(vc, m, c, u0, 0.5, a0);
                    lightPoint(vc, m, c, u1, 0.5, a1);
                    lightPoint(vc, m, c, u1, outer, 0);
                }
            }
        }
    }

    /** How wide the layers of the ray are (1 = as wide as the picture) and how strong each is. The middle of the ray is brightest. */
    private static final double[] BEAM_SHELL_SCALE = {1.0, 0.66, 0.33};
    private static final double[] BEAM_SHELL_STRENGTH = {0.5, 0.7, 1.0};

    /**
     * The light ray: a cone from the top of the projector to the edges of the picture, made of three nested layers so that it is brighter
     * in the middle and a little hazy at its edges. It is bright at the projector and fades to almost nothing at the picture, so it
     * never hides the screen: looking at the screen along the ray you see through it, while from the side - or looking straight into the
     * lens from the screen's side, where all the layers overlap at the tip - it shows.
     */
    private static void drawBeam(VertexConsumer vc, Matrix4f m, double[][] c, double[] center, double[] lens, double strength) {
        for (int k = 0; k < BEAM_SHELL_SCALE.length; k++) {
            int apex = to255(VisualCallConstants.PROJECTOR_BEAM_APEX_ALPHA * BEAM_SHELL_STRENGTH[k] * strength * 255.0);
            int base = to255(VisualCallConstants.PROJECTOR_BEAM_BASE_ALPHA * BEAM_SHELL_STRENGTH[k] * strength * 255.0);
            if (apex <= 0) continue;
            double[][] rim = new double[4][3]; // the picture's corners, pulled in towards its middle for the inner layers
            for (int j = 0; j < 4; j++) {
                for (int axis = 0; axis < 3; axis++) {
                    rim[j][axis] = center[axis] + BEAM_SHELL_SCALE[k] * (c[j][axis] - center[axis]);
                }
            }
            for (int j = 0; j < 4; j++) {
                lightQuad(vc, m, lens, apex, lens, apex, rim[j], base, rim[(j + 1) % 4], base); // one side of the cone
            }
        }
    }

    // ------------------------------------------------------------------------------------------------
    // Glowing parts of the snails: the eyes while capturing, the projector while projecting
    // ------------------------------------------------------------------------------------------------

    /** Per snail: how far it is projecting / capturing, worked out from the live links (see VisualGlow). Reused every frame. */
    private static final Map<BlockPos, float[]> GLOW_FLAGS = new HashMap<>();

    private static void drawGlows(Minecraft mc, ClientLevel level, Collection<VisualFeedManager.ScreenLink> links,
                                  Vec3 cam, PoseStack pose, MultiBufferSource.BufferSource buffers, double maxDistSq) {
        GLOW_FLAGS.clear();
        long now = System.currentTimeMillis();
        for (VisualFeedManager.ScreenLink link : links) {
            if (link.pictureStartMs < 0L) continue; // not projecting yet
            BlockPos screen = link.screenPos();
            BlockPos camera = link.cameraPos();
            if (!level.isLoaded(screen) || !level.isLoaded(camera)) continue;
            VisualSnailRole screenRole = VisualRoles.of(level.getBlockState(screen).getBlock());
            VisualSnailRole cameraRole = VisualRoles.of(level.getBlockState(camera).getBlock());
            if (screenRole == null || cameraRole == null) continue;

            long sinceEnd = link.isEnding() ? now - link.endMs : -1L;
            float ease = (float) ProjectorTransition.visibility(now - link.pictureStartMs, sinceEnd); // the glow comes on and goes off with the picture
            if (ease <= 0.0F) continue;
            VisualGlow.apply(screenRole, cameraRole, ease,
                    GLOW_FLAGS.computeIfAbsent(screen.immutable(), k -> new float[2]),
                    GLOW_FLAGS.computeIfAbsent(camera.immutable(), k -> new float[2]));
        }
        if (GLOW_FLAGS.isEmpty()) return;

        Frustum frustum = mc.levelRenderer.getFrustum();
        for (Map.Entry<BlockPos, float[]> entry : GLOW_FLAGS.entrySet()) {
            BlockPos pos = entry.getKey();
            float[] flags = entry.getValue();
            if (pos.distToCenterSqr(cam.x, cam.y, cam.z) > maxDistSq) continue;
            if (!frustum.isVisible(new AABB(pos))) continue;

            BlockState state = level.getBlockState(pos);
            VisualSnailRole role = VisualRoles.of(state.getBlock());
            if (role == null || !state.hasProperty(TransponderSnailBlock.FACING)) continue;
            Direction facing = state.getValue(TransponderSnailBlock.FACING);
            // The eyes must look like the model that is showing: idle / sound / call / active, as the blockstate says. While the picture fades
            // out the snail is already back to idle, and the glowing eyes must be the idle ones then, or they would change when they go.
            boolean hasSound = state.hasProperty(TransponderSnailBlock.HAS_SOUND) && state.getValue(TransponderSnailBlock.HAS_SOUND);
            boolean inCall = state.hasProperty(TransponderSnailBlock.IN_CALL) && state.getValue(TransponderSnailBlock.IN_CALL);
            String snail = role == VisualSnailRole.DUPLEX ? VisualGlowModels.projectorSnail() : VisualGlowModels.cameraSnail();
            int ambient = LevelRenderer.getLightColor(level, pos);

            drawGlowPart(level, pose, buffers, cam, pos, facing, ambient, VisualGlowModels.eyes(snail, hasSound, inCall),
                    VisualGlow.eyes(role, flags), VisualCallConstants.GLOW_EYES_LIGHT);
            if (role == VisualSnailRole.DUPLEX) {
                float projecting = VisualGlow.projector(role, flags);
                drawGlowPart(level, pose, buffers, cam, pos, facing, ambient, VisualGlowModels.projector(snail),
                        projecting, VisualCallConstants.GLOW_PROJECTOR_LIGHT);
                drawGlowPart(level, pose, buffers, cam, pos, facing, ambient, VisualGlowModels.projectorTop(snail),
                        projecting, VisualCallConstants.GLOW_PROJECTOR_TOP_LIGHT);
            }
        }
        buffers.endBatch(RenderType.cutout());
    }

    /**
     * One quad of a glowing part. At full glow it is flat bright; as it fades it takes on the face shading of the real model again (top faces
     * bright, sides darker), so that it blends into the snail instead of jumping when it appears or goes.
     */
    private static void putGlowQuad(VertexConsumer vc, PoseStack.Pose pose, BakedQuad quad, ClientLevel level, float ease, int packedLight) {
        float shade = level.getShade(quad.getDirection(), quad.isShade());
        float k = shade + (1.0F - shade) * ease;
        vc.putBulkData(pose, quad, k, k, k, packedLight, OverlayTexture.NO_OVERLAY);
    }

    /** The blockstate turns the model by 0 / 90 / 180 / 270 degrees (clockwise from above) for north / east / south / west. */
    private static int yRotation(Direction facing) {
        switch (facing) {
            case EAST: return 90;
            case SOUTH: return 180;
            case WEST: return 270;
            default: return 0;
        }
    }

    /** Draws one glow model over its snail, full-bright up to {@code fullLevel} (scaled by how far the picture has faded in). */
    private static void drawGlowPart(ClientLevel level, PoseStack pose, MultiBufferSource.BufferSource buffers, Vec3 cam, BlockPos pos,
                                     Direction facing, int ambient, String modelName, float ease, int fullLevel) {
        if (ease <= 0.0F) return;
        BakedModel model = VisualGlowModels.get(modelName);
        if (model == null) {
            if (MISSING_GLOW_MODELS.add(modelName)) {
                LOGGER.warn("VisualScreenRenderer: the glow model '{}' is missing (assets/transpondersnails/models/block/{}.json) - that part will not glow",
                        modelName, modelName);
            }
            return;
        }
        // never darker than the world around it: only the block light is raised, like a vanilla glowing block
        int glowLevel = ProjectorEffects.glowLight(fullLevel, ease);
        int packedLight = LightTexture.pack(Math.max(LightTexture.block(ambient), glowLevel), LightTexture.sky(ambient));

        pose.pushPose();
        try {
            pose.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-yRotation(facing))); // the same turn the blockstate gives the snail
            pose.translate(-0.5, -0.5, -0.5);

            VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
            PoseStack.Pose last = pose.last();
            RandomSource random = RandomSource.create(42L);
            for (Direction side : Direction.values()) {
                for (BakedQuad quad : model.getQuads(null, side, random)) {
                    putGlowQuad(vc, last, quad, level, ease, packedLight);
                }
            }
            for (BakedQuad quad : model.getQuads(null, null, random)) {
                putGlowQuad(vc, last, quad, level, ease, packedLight);
            }
        } finally {
            pose.popPose();
        }
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
