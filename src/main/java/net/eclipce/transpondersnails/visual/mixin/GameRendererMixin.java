package net.eclipce.transpondersnails.visual.mixin;

import net.eclipce.transpondersnails.visual.VisualCallConstants;
import net.eclipce.transpondersnails.visual.client.VisualFeedManager;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = GameRenderer.class, priority = 1100)
public class GameRendererMixin {

    /**
     * The near plane of the projection (vanilla 0.05 blocks): while a snail's view is captured it is {@link VisualCallConstants#FEED_NEAR_PLANE} - the nearest distance
     * the camera can focus on. The camera sits right at the snail's eyes, so the snail's own head, eyes and glow (and anything held against the lens) are inside that
     * distance and are not drawn, instead of filling the picture. Outside a capture the value is vanilla's. require = 0: if another mod changed the constant this just
     * does nothing (the other guards still apply) instead of stopping the game.
     */
    @ModifyConstant(method = "getProjectionMatrix", constant = @Constant(floatValue = 0.05F), require = 0)
    private float transpondersnails$feedNearPlane(float vanillaNearPlane) {
        return VisualFeedManager.isCapturing() ? VisualCallConstants.FEED_NEAR_PLANE : vanillaNearPlane;
    }
}
