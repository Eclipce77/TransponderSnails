package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Exposes a RenderTarget's colour attachment as a registered texture so a normal RenderType can sample it.
 * The render target owns the GL texture; this wrapper never allocates or deletes anything.
 */
@OnlyIn(Dist.CLIENT)
final class VisualFeedTexture extends AbstractTexture {

    private final RenderTarget target;

    VisualFeedTexture(RenderTarget target) {
        this.target = target;
    }

    @Override
    public void load(ResourceManager resourceManager) {
        // nothing to load - the render target provides the pixels
    }

    @Override
    public int getId() {
        return target.getColorTextureId();
    }

    @Override
    public void releaseId() {
        // owned by the render target
    }

    @Override
    public void close() {
        // owned by the render target
    }
}
