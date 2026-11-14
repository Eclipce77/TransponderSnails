package net.eclipce.transpondersnails.visual.mixin;

import net.eclipce.transpondersnails.visual.client.VisualFeedManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes the camera of a snail's picture behave like the ordinary third-person camera for ONE thing: the level renderer draws the player of this client.
 *
 * LevelRenderer skips a LocalPlayer in its entity loop unless that player is the camera's entity - that is why the person standing in front of a snail was
 * invisible in its picture, and why earlier rounds drew the player by hand (a draw that came out displaced and enlarged close to the lens). With the loop
 * doing it, the player goes through exactly the code that draws every other entity, with the same matrices and buffers.
 *
 * While a feed is captured (VisualFeedManager sets the Marker as the game's camera entity and the camera type to third person) Camera#setup is finished here:
 * the camera is put back exactly at the Marker (third person would have pulled it back four blocks), and Camera#getEntity answers with the local player. The
 * game's own camera entity (Minecraft#getCameraEntity) stays the Marker, so view bobbing and the hurt shake of the player do not shake the picture.
 * Nothing changes outside a capture.
 */
@Mixin(value = Camera.class, priority = 1100)
public abstract class CameraMixin {

    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("RETURN"))
    private void transpondersnails$finishFeedCamera(BlockGetter level, Entity subject, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        if (!VisualFeedManager.isCapturing() || !(subject instanceof Marker)) return;
        this.setRotation(subject.getYRot(), subject.getXRot());
        this.setPosition(subject.getX(), subject.getY(), subject.getZ());
        Entity player = Minecraft.getInstance().player;
        if (player != null) this.entity = player;
    }
}
