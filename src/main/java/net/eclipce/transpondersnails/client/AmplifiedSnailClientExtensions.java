package net.eclipce.transpondersnails.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Client-only "megaphone" animation for the Amplified Transponder Snail.
 *
 * The item uses UseAnim.CUSTOM, which switches off vanilla's goat horn handling in both views, and this
 * class takes over:
 *
 *  THIRD PERSON - {@link #getArmPose}: a custom arm pose, lower than the goat horn (~63 degrees up
 *  instead of 85), angled in toward the face and following the head's pitch. Together with the in-use
 *  item models (models/item/amplified_transponder_snail_using_*.json) this puts the microphone capsule
 *  at the player's mouth with the snail pointing straight ahead.
 *
 *  FIRST PERSON - {@link #applyForgeHandTransform}: slides the snail from its normal hand position to the
 *  center of the screen (same size/angle as the normal hold) and back again when released.
 *
 * All values were solved against 1.20.1's held-item transform chain; they're constants so they can be
 * nudged after testing in game.
 */
public final class AmplifiedSnailClientExtensions implements IClientItemExtensions {

    /**
     * The custom arm pose MUST exist before any player is rendered.
     *
     * Vanilla code like HumanoidModel.poseRightArm switches on ArmPose; Java compiles that into a
     * hidden lookup table sized to the number of ArmPose values that exist the first time the switch
     * runs. A pose created later (e.g. lazily on first use) is past the end of that table ->
     * ArrayIndexOutOfBoundsException. This field is initialized when the class loads, which happens
     * in AmplifiedTransponderSnailItem#initializeClient() during item registration - long before
     * anything renders.
     */
    public static final HumanoidModel.ArmPose MEGAPHONE_POSE = HumanoidModel.ArmPose.create(
            "TRANSPONDERSNAILS_MEGAPHONE", false, AmplifiedSnailClientExtensions::poseArm);

    public static final AmplifiedSnailClientExtensions INSTANCE = new AmplifiedSnailClientExtensions();

    // =================== THIRD PERSON ARM (radians) ===================

    /** How far the arm is raised when looking straight ahead (vanilla goat horn: 1.4835 = 85 deg). */
    private static final float ARM_RAISE = 1.095F;          // ~63 deg
    /** How far the arm angles in toward the face (vanilla goat horn: 0.5236 = 30 deg). */
    private static final float ARM_INWARD = 0.54F;          // ~31 deg
    /** How strongly the arm follows looking up/down (vanilla: 1.0). 0.7 keeps the capsule on the mouth. */
    private static final float HEAD_PITCH_FOLLOW = 0.7F;
    /** Same head pitch clamp vanilla uses for the goat horn. */
    private static final float HEAD_PITCH_LIMIT = 1.2F;

    // =================== FIRST PERSON (blocks, vanilla hold = x ±0.56, y -0.52, z -0.72) ===================

    private static final float HOLD_X = 0.56F;
    private static final float HOLD_Y = -0.52F;
    private static final float HOLD_Z = -0.72F;

    private static final float CENTER_X = 0.0F;             // 0 = middle of the screen
    private static final float CENTER_Y = -0.58F;           // same height as the normal hold
    private static final float CENTER_Z = -0.72F;           // same distance as the normal hold

    /**
     * Rotation applied at the center position (degrees), mirrored automatically for the left hand.
     *  YAW   - turns the snail left/right. Negative turns its face toward the camera, positive away.
     *  PITCH - tips the snail's front up (negative) or down (positive).
     *  ROLL  - tilts it clockwise (negative) or counter-clockwise (positive).
     */
    private static final float CENTER_YAW = 30.0F;
    private static final float CENTER_PITCH = 0.0F;
    private static final float CENTER_ROLL = -10.0F;

    /** Seconds to slide between the side and the center. */
    private static final float SLIDE_SECONDS = 0.2F;

    // Per-arm slide progress (0 = normal hold, 1 = centered). Render thread only.
    private final float[] slideProgress = new float[2];
    private final long[] lastFrameNanos = new long[2];

    private AmplifiedSnailClientExtensions() {
    }

    // =================== SHARED ===================

    /** True if this entity is currently using (holding right-click with) this exact stack. */
    public static boolean isUsingStack(LivingEntity entity, ItemStack stack) {
        return entity != null
                && entity.isUsingItem()
                && entity.getItemInHand(entity.getUsedItemHand()) == stack;
    }

    // =================== THIRD PERSON ===================

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (entity.isUsingItem() && entity.getUsedItemHand() == hand) {
            return MEGAPHONE_POSE;
        }
        return null; // normal ITEM pose
    }

    private static void poseArm(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        float headPitch = Mth.clamp(model.head.xRot, -HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT);
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;

        part.xRot = headPitch * HEAD_PITCH_FOLLOW - ARM_RAISE;
        part.yRot = model.head.yRot + (arm == HumanoidArm.RIGHT ? -ARM_INWARD : ARM_INWARD);
    }

    // =================== FIRST PERSON ===================

    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm,
                                           ItemStack itemInHand, float partialTick, float equipProcess,
                                           float swingProcess) {
        int index = arm == HumanoidArm.RIGHT ? 0 : 1;

        // Is this arm's stack the one being used?
        boolean using = false;
        if (isUsingStack(player, itemInHand)) {
            HumanoidArm usedArm = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? player.getMainArm()
                    : player.getMainArm().getOpposite();
            using = usedArm == arm;
        }

        // Advance the slide by real frame time so it's smooth at any FPS
        long now = System.nanoTime();
        float dt = lastFrameNanos[index] == 0L ? 0.0F : (now - lastFrameNanos[index]) / 1.0E9F;
        lastFrameNanos[index] = now;
        dt = Math.min(dt, 0.1F);

        float target = using ? 1.0F : 0.0F;
        float step = dt / SLIDE_SECONDS;
        slideProgress[index] = Mth.clamp(slideProgress[index] + Mth.clamp(target - slideProgress[index], -step, step), 0.0F, 1.0F);

        if (slideProgress[index] <= 0.0F) {
            return false; // Normal hold - let vanilla do everything (including swing animation)
        }

        float t = slideProgress[index];
        float eased = t * t * (3.0F - 2.0F * t); // smoothstep
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;

        float x = Mth.lerp(eased, side * HOLD_X, side * CENTER_X);
        float y = Mth.lerp(eased, HOLD_Y, CENTER_Y) + equipProcess * -0.6F;
        float z = Mth.lerp(eased, HOLD_Z, CENTER_Z);
        poseStack.translate(x, y, z);

        // Rotate around the hand position, easing in with the slide (0 at the normal hold)
        poseStack.mulPose(Axis.YP.rotationDegrees(eased * CENTER_YAW * side));
        poseStack.mulPose(Axis.XP.rotationDegrees(eased * CENTER_PITCH));
        poseStack.mulPose(Axis.ZP.rotationDegrees(eased * CENTER_ROLL * side));
        return true;
    }
}