package net.eclipce.transpondersnails.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.eclipce.transpondersnails.entity.custom.BabyBlackTransponderSnailEntity;
import net.eclipce.transpondersnails.item.BabyBlackTransponderSnailItem;
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
 * Renderer for the Baby Black Transponder Snail Entity
 * Uses the item model with shell color variants via shell_color item property
 * 
 * FIX APPLIED: Changed NBT tag from "ShellColor" to "shell_color" to match
 * BabyBlackTransponderSnailItem.SHELL_COLOR_TAG constant
 */
public class BabyBlackTransponderSnailRenderer extends EntityRenderer<BabyBlackTransponderSnailEntity> {

    private final ItemRenderer itemRenderer;

    // Debug flag - set to false once everything works
    private static final boolean DEBUG = false;
    private static int debugCounter = 0;

    public BabyBlackTransponderSnailRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BabyBlackTransponderSnailEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // Position: Lift the model up so it's visible above ground
        poseStack.translate(0.0D, 0.225D, 0.0D);

        // Rotation: Face the correct direction
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        // Get entity's shell color
        int shellColor = entity.getShellColor();

        // Debug output (only every 100 frames to reduce spam)
        if (DEBUG && debugCounter++ % 100 == 0) {
        }

        // Create ItemStack with shell color NBT
        ItemStack itemStack = new ItemStack(ModItems.BABY_BLACK_TRANSPONDER_SNAIL.get());
        CompoundTag nbt = itemStack.getOrCreateTag();

        // FIX: Use "shell_color" to match BabyBlackTransponderSnailItem.SHELL_COLOR_TAG
        // Previously used "ShellColor" which caused the item property predicate to fail
        nbt.putInt("shell_color", shellColor);

        // Render the item model
        itemRenderer.renderStatic(
                itemStack,
                ItemDisplayContext.GROUND,
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
    public ResourceLocation getTextureLocation(BabyBlackTransponderSnailEntity entity) {
        // Not directly used for item models, but required by EntityRenderer
        return InventoryMenu.BLOCK_ATLAS;
    }
}
