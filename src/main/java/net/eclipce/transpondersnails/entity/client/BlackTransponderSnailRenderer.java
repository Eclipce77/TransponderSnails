package net.eclipce.transpondersnails.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.eclipce.transpondersnails.entity.custom.BlackTransponderSnailEntity;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renderer for the Black Transponder Snail Entity
 * Uses the item model with shell color variants
 *
 * FIXED: Uses ItemDisplayContext.NONE to bypass model display transforms
 * and applies explicit positioning/rotation for consistent rendering
 * with the block entity renderer.
 */
public class BlackTransponderSnailRenderer extends EntityRenderer<BlackTransponderSnailEntity> {

    private final ItemRenderer itemRenderer;

    // Base scale for the entity (entity.getScale() multiplies this)
    private static final float BASE_SCALE = 3.0f;

    // Y offset to lift model off ground - pushed up on Y axis (same as BER)
    private static final double Y_OFFSET = 1.455D;

    // Debug flag
    private static final boolean DEBUG = false;
    private static int debugCounter = 0;

    public BlackTransponderSnailRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BlackTransponderSnailEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // Get the entity's scale factor
        float entityScale = entity.getScale();
        float totalScale = BASE_SCALE * entityScale;

        // Position: Lift the model up so it sits on the ground
        poseStack.translate(0.0D, Y_OFFSET * entityScale, 0.0D);

        // Apply scale transformation
        poseStack.scale(totalScale, totalScale, totalScale);

        // Rotation: Face the correct direction (entity's yaw)
        // Add 180 to face the direction the entity is looking
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        // Get entity's shell color and active state
        int shellColor = entity.getShellColor();
        boolean isActive = entity.isActive();

        // Debug output (only every 100 frames to reduce spam)
        if (DEBUG && debugCounter++ % 100 == 0) {
        }

        // Create ItemStack with shell color and open state NBT
        ItemStack itemStack = new ItemStack(ModItems.BLACK_TRANSPONDER_SNAIL.get());
        CompoundTag nbt = itemStack.getOrCreateTag();

        // Set shell color (matches BlackTransponderSnailItem.SHELL_COLOR_TAG)
        nbt.putInt("shell_color", shellColor);

        // Set open state based on entity's active state
        // Entity "active" (intercepting) = item "open" (shell up)
        nbt.putBoolean("is_open", isActive);

        // Render the item model
        // Using NONE context - no display transforms applied, we handle positioning ourselves
        itemRenderer.renderStatic(
                itemStack,
                ItemDisplayContext.NONE,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BlackTransponderSnailEntity entity) {
        // Not directly used for item models, but required by EntityRenderer
        return InventoryMenu.BLOCK_ATLAS;
    }
}