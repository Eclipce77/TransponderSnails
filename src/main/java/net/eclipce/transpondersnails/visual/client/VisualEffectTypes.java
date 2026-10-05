package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Render types of the projector's light effects (the glow around the picture and the light ray). */
@OnlyIn(Dist.CLIENT)
final class VisualEffectTypes {

    private VisualEffectTypes() {}

    /** Light adds to what is behind it: where several layers overlap it gets brighter, like real light. */
    private static final RenderStateShard.TransparencyStateShard ADDITIVE =
            new RenderStateShard.TransparencyStateShard(
                    "transpondersnails_additive_light",
                    () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    },
                    () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    });

    private static RenderType additive;

    /**
     * Colored quads that are added to the picture of the world, are hidden by solid blocks, but do not hide anything themselves
     * (they do not write depth), so the overlapping layers of the glow and of the ray never cut each other off.
     */
    static RenderType additive() {
        if (additive == null) {
            additive = RenderType.create(
                    "transpondersnails_projector_light",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    256,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                            .setTransparencyState(ADDITIVE)
                            .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, false))
                            .createCompositeState(false));
        }
        return additive;
    }
}
