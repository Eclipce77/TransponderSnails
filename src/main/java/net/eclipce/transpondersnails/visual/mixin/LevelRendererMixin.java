package net.eclipce.transpondersnails.visual.mixin;

import net.eclipce.transpondersnails.visual.client.VisualFeedManager;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 1100)
public class LevelRendererMixin {

    /**
     * While a snail feed is captured the visible sections are supplied by VisualFeedManager. Vanilla's setupRender
     * would recompute them from the remote camera (async full update, clobbering the section data the main view uses
     * next frame), so it is skipped. With Embeddium / Rubidium / Sodium their own chunk culling is used instead and
     * this method is left alone.
     */
    @Inject(method = "setupRender", at = @At("HEAD"), cancellable = true)
    private void transpondersnails$skipSetupRenderWhileCapturing(Camera camera, Frustum frustum, boolean hasCapturedFrustum,
                                                                 boolean isSpectator, CallbackInfo ci) {
        if (VisualFeedManager.isCapturing() && !VisualFeedManager.isSodiumLike()) {
            ci.cancel();
        }
    }
}
