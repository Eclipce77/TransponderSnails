package net.eclipce.transpondersnails.visual.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.eclipce.transpondersnails.visual.client.VisualFeedManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityRenderDispatcher.class, priority = 1100)
public class EntityRenderDispatcherMixin {

    /**
     * While a snail's view is captured, only entities where the picture has terrain are drawn (VisualFeedManager#feedMayShowEntity): entities
     * are tracked much further out than the picture's terrain reaches, and were drawn floating over the empty sky. Outside a capture this does nothing.
     */
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void transpondersnails$feedShowsOnlyWhatItCanSee(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                                             CallbackInfoReturnable<Boolean> cir) {
        if (VisualFeedManager.isCapturing() && !VisualFeedManager.feedMayShowEntity(entity)) {
            cir.setReturnValue(false);
        }
    }

    /** Lets the feed know whether the level renderer itself drew the player of this client (then the player is not drawn a second time by hand). */
    @Inject(method = "render", at = @At("HEAD"))
    private void transpondersnails$noteRenderedEntity(Entity entity, double x, double y, double z, float yaw, float partialTick, PoseStack poseStack,
                                                      MultiBufferSource buffers, int light, CallbackInfo ci) {
        VisualFeedManager.noteEntityRendered(entity);
    }
}
