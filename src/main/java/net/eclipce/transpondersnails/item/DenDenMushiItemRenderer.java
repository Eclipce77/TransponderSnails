package net.eclipce.transpondersnails.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.client.DenDenMushiModel;
import net.eclipce.transpondersnails.entity.client.ModModelLayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Custom renderer for Den Den Mushi items that uses the actual 3D entity model.
 * Supports both adult and baby Den Den Mushi with proper color tinting.
 */
public class DenDenMushiItemRenderer extends BlockEntityWithoutLevelRenderer {

    // Shell textures (one per dye color)
    private static final ResourceLocation[] SHELL_TEXTURES = new ResourceLocation[16];

    // Body textures
    private static final ResourceLocation BODY_BASE_TEXTURE = new ResourceLocation(TransponderSnails.MOD_ID,
            "textures/entity/den_den_mushi/snail/den_den_mushi_snail_body.png"); // Grayscale, will be tinted
    private static final ResourceLocation BODY_DETAILS_TEXTURE = new ResourceLocation(TransponderSnails.MOD_ID,
            "textures/entity/den_den_mushi/snail/den_den_mushi_snail_eyes.png"); // Eyes and other details, no tint

    static {
        for (DyeColor color : DyeColor.values()) {
            SHELL_TEXTURES[color.getId()] = new ResourceLocation(TransponderSnails.MOD_ID,
                    "textures/entity/den_den_mushi/shell/den_den_mushi_shell_" + color.getName() + ".png");
        }
    }

    private final DenDenMushiModel<?> model;

    public DenDenMushiItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
        this.model = new DenDenMushiModel<>(modelSet.bakeLayer(ModModelLayers.DEN_DEN_MUSHI_LAYER));
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                             MultiBufferSource buffer, int combinedLight, int combinedOverlay) {

        // Check if this is a Den Den Mushi item (adult or baby)
        boolean isBaby = stack.getItem() instanceof BabyDenDenMushiItem;
        boolean isAdult = stack.getItem() instanceof DenDenMushiItem;

        if (!isBaby && !isAdult) {
            return;
        }

        // Get colors based on item type
        int shellColor;
        int bodyColor;
        boolean isCaptured;

        if (isBaby) {
            shellColor = BabyDenDenMushiItem.getShellColor(stack);
            bodyColor = BabyDenDenMushiItem.getBodyColor(stack);
            isCaptured = BabyDenDenMushiItem.isCaptured(stack);
        } else {
            shellColor = DenDenMushiItem.getShellColor(stack);
            bodyColor = DenDenMushiItem.getBodyColor(stack);
            isCaptured = DenDenMushiItem.isCaptured(stack);
        }

        // Only render if captured (has color data)
        if (!isCaptured) {
            return;
        }

        // Setup pose stack for rendering
        poseStack.pushPose();

        // Position and scale based on display context
        setupTransforms(poseStack, displayContext, isBaby);

        // Reset model pose
        this.model.root().getAllParts().forEach(net.minecraft.client.model.geom.ModelPart::resetPose);

        // Render shell with shell color texture (no tint)
        ResourceLocation shellTexture = SHELL_TEXTURES[shellColor];
        VertexConsumer shellConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(shellTexture));
        this.model.shell.render(poseStack, shellConsumer, combinedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);

        // Render body base with tint
        float r = ((bodyColor >> 16) & 0xFF) / 255.0f;
        float g = ((bodyColor >> 8) & 0xFF) / 255.0f;
        float b = (bodyColor & 0xFF) / 255.0f;

        VertexConsumer bodyConsumer = buffer.getBuffer(RenderType.entityTranslucent(BODY_BASE_TEXTURE));
        this.model.eyes.render(poseStack, bodyConsumer, combinedLight, OverlayTexture.NO_OVERLAY, r, g, b, 1.0f);
        this.model.head.render(poseStack, bodyConsumer, combinedLight, OverlayTexture.NO_OVERLAY, r, g, b, 1.0f);
        this.model.body.render(poseStack, bodyConsumer, combinedLight, OverlayTexture.NO_OVERLAY, r, g, b, 1.0f);

        // Render details (eyes, etc.) with no tint
        VertexConsumer detailConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(BODY_DETAILS_TEXTURE));
        this.model.eyes.render(poseStack, detailConsumer, combinedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        this.model.head.render(poseStack, detailConsumer, combinedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        this.model.body.render(poseStack, detailConsumer, combinedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);

        poseStack.popPose();
    }

    /**
     * Sets up transformations based on display context and whether it's a baby
     */
    private void setupTransforms(PoseStack poseStack, ItemDisplayContext displayContext, boolean isBaby) {
        // Base scale - babies are half size
        float scale = isBaby ? 8.0f : 16.0f;

        switch (displayContext) {
            case GUI:
                // Inventory/GUI rendering
                poseStack.translate(0.5f, 0.25f, 0.0f);
                poseStack.scale(scale, scale, scale);
                poseStack.mulPose(Axis.XP.rotationDegrees(30));
                poseStack.mulPose(Axis.YP.rotationDegrees(225));
                break;

            case GROUND:
                // Item on ground
                poseStack.translate(0.5f, 0.2f, 0.5f);
                poseStack.scale(scale * 0.5f, scale * 0.5f, scale * 0.5f);
                break;

            case FIXED:
                // Item frame
                poseStack.translate(0.5f, 0.5f, 0.0f);
                poseStack.scale(scale, scale, scale);
                poseStack.mulPose(Axis.XP.rotationDegrees(0));
                break;

            case THIRD_PERSON_LEFT_HAND:
            case THIRD_PERSON_RIGHT_HAND:
                // Third person hand
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(scale * 0.75f, scale * 0.75f, scale * 0.75f);
                poseStack.mulPose(Axis.XP.rotationDegrees(75));
                poseStack.mulPose(Axis.YP.rotationDegrees(45));
                break;

            case FIRST_PERSON_LEFT_HAND:
            case FIRST_PERSON_RIGHT_HAND:
                // First person hand
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(scale * 0.75f, scale * 0.75f, scale * 0.75f);
                poseStack.mulPose(Axis.XP.rotationDegrees(0));
                poseStack.mulPose(Axis.YP.rotationDegrees(180));
                break;

            case HEAD:
                // On head (like helmet)
                poseStack.translate(0.5f, 0.75f, 0.5f);
                poseStack.scale(scale, scale, scale);
                break;

            default:
                // Default positioning
                poseStack.translate(0.5f, 0.5f, 0.5f);
                poseStack.scale(scale, scale, scale);
                break;
        }
    }
}