package net.eclipce.transpondersnails.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.item.DenDenMushiItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;

public class DenDenMushiItemRenderer extends BlockEntityWithoutLevelRenderer {

    // Texture resources for body tinting
    private static final ResourceLocation BODY_BASE_TEXTURE = new ResourceLocation(TransponderSnails.MOD_ID,
            "textures/item/den_den_mushi/snail/den_den_mushi_snail_body.png");
    private static final ResourceLocation BODY_DETAILS_TEXTURE = new ResourceLocation(TransponderSnails.MOD_ID,
            "textures/item/den_den_mushi/snail/den_den_mushi_snail_eyes.png");

    public DenDenMushiItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int combinedLight, int combinedOverlay) {

        if (!(stack.getItem() instanceof DenDenMushiItem)) {
            return;
        }

        // Get the appropriate shell model based on shell color
        int shellColor = DenDenMushiItem.getShellColor(stack);
        DyeColor dyeColor = DyeColor.byId(shellColor);

        ModelResourceLocation shellModelLocation = new ModelResourceLocation(
                new ResourceLocation(TransponderSnails.MOD_ID, "den_den_mushi_shell_" + dyeColor.getName()),
                "inventory"
        );

        // Get the model from the model manager
        BakedModel shellModel = Minecraft.getInstance().getModelManager().getModel(shellModelLocation);
        if (shellModel != null) {
            // Render the shell with no tint (white)
            Minecraft.getInstance().getItemRenderer().render(stack, displayContext, false, poseStack, buffer,
                    combinedLight, combinedOverlay, shellModel);
        }

        // If the item is captured (has body color data), render tinted body
        if (DenDenMushiItem.isCaptured(stack)) {
            renderTintedBody(stack, displayContext, poseStack, buffer, combinedLight, combinedOverlay);
        }
    }

    private void renderTintedBody(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                                  MultiBufferSource buffer, int combinedLight, int combinedOverlay) {

        int bodyColor = DenDenMushiItem.getBodyColor(stack);
        float r = ((bodyColor >> 16) & 0xFF) / 255.0f;
        float g = ((bodyColor >> 8) & 0xFF) / 255.0f;
        float b = (bodyColor & 0xFF) / 255.0f;

        // This would need to render the body parts with tinting
        // You'd need to adapt your entity model for item rendering
        // This is complex and might be better handled with multiple model files

        // For now, this is a placeholder showing the concept
        VertexConsumer bodyConsumer = buffer.getBuffer(RenderType.entityTranslucent(BODY_BASE_TEXTURE));
        // Render body geometry with tint color (r, g, b, 1.0f)

        VertexConsumer detailConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(BODY_DETAILS_TEXTURE));
        // Render detail geometry with no tint (1.0f, 1.0f, 1.0f, 1.0f)
    }
}