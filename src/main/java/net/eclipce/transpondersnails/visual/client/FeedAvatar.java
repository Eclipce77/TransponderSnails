package net.eclipce.transpondersnails.visual.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * A stand-in for the player of this client, used only to draw him into a snail's picture (VisualScreenRenderer#drawLocalPlayerInFeed). It is never added to the
 * world and never ticks: before each draw it takes over what the picture shows of the real player - place, turn, pose, skin parts, worn and held items, the swing
 * of the arms and legs.
 *
 * It exists because other mods hook the drawing of the real LocalPlayer (Mine Mine no Mi did: inside a feed the real player came out rolled, upside down and several
 * times too big). A fresh remote player has none of their state, and is drawn the way every other player of the world is.
 */
@OnlyIn(Dist.CLIENT)
final class FeedAvatar extends RemotePlayer {

    FeedAvatar(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    void copyFrom(LocalPlayer src, float partial) {
        setPos(src.getX(), src.getY(), src.getZ());
        this.xo = src.xo;
        this.yo = src.yo;
        this.zo = src.zo;
        this.xOld = src.xOld;
        this.yOld = src.yOld;
        this.zOld = src.zOld;
        setYRot(src.getYRot());
        setXRot(src.getXRot());
        this.yRotO = src.yRotO;
        this.xRotO = src.xRotO;
        this.yBodyRot = src.yBodyRot;
        this.yBodyRotO = src.yBodyRotO;
        this.yHeadRot = src.yHeadRot;
        this.yHeadRotO = src.yHeadRotO;
        this.tickCount = src.tickCount;

        setPose(src.getPose());
        setShiftKeyDown(src.isShiftKeyDown());
        setSprinting(src.isSprinting());
        setSwimming(src.isSwimming());
        setInvisible(src.isInvisible());
        setRemainingFireTicks(src.getRemainingFireTicks());
        setSharedFlag(7, src.isFallFlying()); // the elytra pose

        // the legs' swing: the animator can only be moved forward, so push it by exactly what is missing, then give it the real speed
        this.walkAnimation.update(src.walkAnimation.position(partial) - this.walkAnimation.position(), 1.0F);
        this.walkAnimation.setSpeed(src.walkAnimation.speed(partial));
        this.swinging = src.swinging;
        this.swingTime = src.swingTime;
        this.swingingArm = src.swingingArm;
        this.attackAnim = src.attackAnim;
        this.oAttackAnim = src.oAttackAnim;
        this.hurtTime = src.hurtTime;
        this.hurtDuration = src.hurtDuration;

        setMainArm(src.getMainArm());
        this.entityData.set(DATA_PLAYER_MODE_CUSTOMISATION, src.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION)); // which skin layers are shown
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack wanted = src.getItemBySlot(slot);
            if (!ItemStack.matches(getItemBySlot(slot), wanted)) {
                setItemSlot(slot, wanted);
            }
        }
    }
}
