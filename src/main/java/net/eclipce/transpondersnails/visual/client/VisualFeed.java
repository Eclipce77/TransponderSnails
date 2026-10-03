package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/** The offscreen view from one Visual Snail's eyes. Keyed by the CAMERA snail's position. */
@OnlyIn(Dist.CLIENT)
final class VisualFeed {

    final BlockPos cameraPos;

    RenderTarget target;
    private ResourceLocation textureId;
    private RenderType renderType;

    long lastCaptureNs = 0L;
    volatile long lastTouchedMs = 0L;
    int okFrames = 0;
    boolean failed = false;

    /** Sections rendered for this camera (vanilla renderer path only). Rebuilt periodically. */
    List<LevelRenderer.RenderChunkInfo> sections = new ArrayList<>();
    int capturesSinceSectionRefresh = VisualCallConstants.FEED_SECTION_REFRESH_CAPTURES;
    int lastViewDistance = -1;
    boolean sectionsLogged = false;
    boolean emptySectionsWarned = false;

    VisualFeed(BlockPos cameraPos) {
        this.cameraPos = cameraPos.immutable();
    }

    boolean isAllocated() {
        return target != null;
    }

    void allocate(int uniqueId) {
        if (target != null) return;
        int res = VisualCallConstants.FEED_RESOLUTION;
        target = new TextureTarget(res, res, true, Minecraft.ON_OSX);
        target.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);

        textureId = new ResourceLocation(TransponderSnails.MOD_ID, "visual_feed/" + uniqueId);
        Minecraft.getInstance().getTextureManager().register(textureId, new VisualFeedTexture(target));

        renderType = RenderType.create(
                "transpondersnails_visual_feed_" + uniqueId,
                DefaultVertexFormat.POSITION_COLOR_TEX,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(textureId, true, false))
                        .createCompositeState(false));
    }

    boolean hasFrame() {
        return target != null && okFrames > 0 && !failed;
    }

    RenderType renderType() {
        return renderType;
    }

    void release() {
        sections = new ArrayList<>();
        if (textureId != null) {
            Minecraft.getInstance().getTextureManager().release(textureId);
            textureId = null;
        }
        renderType = null;
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
    }
}
