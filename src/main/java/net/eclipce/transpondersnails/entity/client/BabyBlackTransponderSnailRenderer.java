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

    /** The baby's scale: half the adult's 3.0. */
    private static final float BABY_SCALE = 1.5F;

    /** From the model's centre (y = 0.5) down to its lowest point (0.238 px of 16): 0.5 - 0.238 / 16. */
    private static final double MODEL_LIFT = 0.5D - 0.238D / 16.0D;

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

        // Position: with the NONE context the model is centred on the origin (its 0..16 box maps to -0.5..0.5), so lift it by the distance from that
        // centre down to the model's lowest point, scaled, and it stands on the ground (the same way the adult renderer does it)
        poseStack.translate(0.0D, MODEL_LIFT * BABY_SCALE, 0.0D);

        // Scale: the baby is half the adult's size (adult 3.0 x 1.15; the baby model is the same geometry), which makes it the size of its hitbox
        poseStack.scale(BABY_SCALE, BABY_SCALE, BABY_SCALE);

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
        // NONE, not GROUND: the GROUND transform of the item model is for a dropped item (it turns the model upside down and tilts it 35 degrees), which is
        // what made the baby snails lie flat and turned over. NONE applies no display transform; the position, scale and facing are set here.
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
    public ResourceLocation getTextureLocation(BabyBlackTransponderSnailEntity entity) {
        // Not directly used for item models, but required by EntityRenderer
        return InventoryMenu.BLOCK_ATLAS;
    }
}
