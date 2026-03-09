package net.eclipce.transpondersnails.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Clean, adjustable Curios renderer for Portable Black Transponder Snail.
 * Uses GROUND context for proper 3D depth.
 *
 * ADJUSTMENT GUIDE:
 * - Change ONE value at a time
 * - Test with F3+T reload or game restart
 * - Check in third-person view (F5)
 */
public class PortableBlackTransponderSnailCurioRenderer implements ICurioRenderer {

    // ============================================================
    // EASY ADJUSTMENT SECTION - CHANGE THESE VALUES
    // ============================================================

    // HEIGHT on arm (0=shoulder, 0.5=elbow, 0.75=wrist, 1=hand)
    private static final double WRIST_HEIGHT = 0.46;

    // DISTANCE from arm (how far it sticks out)
    private static final double OUTWARD_DISTANCE = -0.05;

    // FINE POSITIONING (small offsets for perfect placement)
    private static final double SIDE_OFFSET = -0.027;   // Left/right adjustment
    private static final double VERTICAL_OFFSET = -0.01875;  // Up/down fine-tune

    // ROTATION angles (in degrees)
    private static final float YAW = 0.0f;      // Spin around arm (90=outward, 0=forward)
    private static final float PITCH = 0.0f;   // Tip up/down (negative=forward)
    private static final float ROLL = 180.0f;      // Tilt/lean (small values look natural)

    // SIZE on wrist
    private static final float SCALE = 0.70f;

    // ============================================================

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack,
            SlotContext slotContext,
            PoseStack matrixStack,
            RenderLayerParent<T, M> renderLayerParent,
            MultiBufferSource renderTypeBuffer,
            int light,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {

        LivingEntity entity = slotContext.entity();
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();

        boolean isRightHand = slotContext.index() != 0;

        matrixStack.pushPose();

        if (renderLayerParent.getModel() instanceof HumanoidModel<?> humanoidModel) {
            // Attach to the correct arm
            if (isRightHand) {
                humanoidModel.rightArm.translateAndRotate(matrixStack);
            } else {
                humanoidModel.leftArm.translateAndRotate(matrixStack);
            }

            // Move to wrist position
            matrixStack.translate(0.0, WRIST_HEIGHT, 0.0);

            // Apply fine positioning offsets
            if (isRightHand) {
                matrixStack.translate(SIDE_OFFSET, VERTICAL_OFFSET, OUTWARD_DISTANCE);
            } else {
                matrixStack.translate(-SIDE_OFFSET, VERTICAL_OFFSET, -OUTWARD_DISTANCE);
            }

            // Apply rotation for natural wearing angle
            if (isRightHand) {
                matrixStack.mulPose(Axis.YP.rotationDegrees(YAW));
                matrixStack.mulPose(Axis.XP.rotationDegrees(PITCH));
                matrixStack.mulPose(Axis.ZP.rotationDegrees(ROLL));
            } else {
                matrixStack.mulPose(Axis.YP.rotationDegrees(-YAW));
                matrixStack.mulPose(Axis.XP.rotationDegrees(PITCH));
                matrixStack.mulPose(Axis.ZP.rotationDegrees(-ROLL));
            }

            // Scale to wrist size
            matrixStack.scale(SCALE, SCALE, SCALE);
        }

        // Render with GROUND context for proper 3D depth
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                light,
                OverlayTexture.NO_OVERLAY,
                matrixStack,
                renderTypeBuffer,
                entity.level(),
                entity.getId()
        );

        matrixStack.popPose();
    }
}