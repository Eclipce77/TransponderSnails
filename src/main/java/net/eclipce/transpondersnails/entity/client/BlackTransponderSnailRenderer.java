package net.eclipce.transpondersnails.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.eclipce.transpondersnails.entity.custom.BlackTransponderSnailEntity;
import net.eclipce.transpondersnails.item.BlackTransponderSnailItem;
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
 * Uses the item model with shell color variants and applies scale
 */
public class BlackTransponderSnailRenderer extends EntityRenderer<BlackTransponderSnailEntity> {

    private final ItemRenderer itemRenderer;
    
    // Debug flag - set to false once everything works
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
        float scale = entity.getScale();

        // Position: Lift the model up so it's visible above ground
        // Adjusted for scale - the model's display settings handle most of the positioning
        poseStack.translate(0.0D, 0.85D * scale, 0.0D);

        // Apply scale transformation
        poseStack.scale(scale, scale, scale);

        // Rotation: Face the correct direction
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        // Get entity's shell color
        int shellColor = entity.getShellColor();
        
        // Debug output (only every 100 frames to reduce spam)
        if (DEBUG && debugCounter++ % 100 == 0) {
            System.out.println("=== BLACK SNAIL RENDERER DEBUG ===");
            System.out.println("Entity ID: " + entity.getId());
            System.out.println("Shell Color: " + shellColor + " (" + DyeColor.byId(shellColor).getName() + ")");
            System.out.println("Scale: " + scale);
            System.out.println("==================================");
        }

        // Create ItemStack with shell color NBT
        ItemStack itemStack = new ItemStack(ModItems.BLACK_TRANSPONDER_SNAIL.get());
        CompoundTag nbt = itemStack.getOrCreateTag();
        
        // Store shell color - the item property function reads "ShellColor" tag
        nbt.putInt("ShellColor", shellColor);

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
    public ResourceLocation getTextureLocation(BlackTransponderSnailEntity entity) {
        // Not directly used for item models, but required by EntityRenderer
        return InventoryMenu.BLOCK_ATLAS;
    }
}
