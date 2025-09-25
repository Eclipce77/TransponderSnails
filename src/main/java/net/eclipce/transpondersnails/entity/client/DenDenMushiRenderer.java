package net.eclipce.transpondersnails.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public class DenDenMushiRenderer extends MobRenderer<DenDenMushiEntity, DenDenMushiModel<DenDenMushiEntity>> {

    // Three separate texture types
    private static final ResourceLocation[] SHELL_TEXTURES = new ResourceLocation[16];
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

    public DenDenMushiRenderer(EntityRendererProvider.Context pContext) {
        super(pContext, new DenDenMushiModel<>(pContext.bakeLayer(ModModelLayers.DEN_DEN_MUSHI_LAYER)), 0.5f);

        // Add layers for additional textures
        this.addLayer(new TintedBodyLayer(this)); // Layer 1: Tinted body base
        this.addLayer(new DetailsLayer(this));    // Layer 2: Untinted details (eyes, etc.)
    }

    @Override
    public ResourceLocation getTextureLocation(DenDenMushiEntity entity) {
        // Base render uses shell texture
        int shellColor = entity.getShellColor();
        return SHELL_TEXTURES[shellColor];
    }

    @Override
    public void render(DenDenMushiEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pMatrixStack,
                       MultiBufferSource pBuffer, int pPackedLight) {
        if(pEntity.isBaby()) {
            pMatrixStack.scale(0.5f, 0.5f, 0.5f);
        }

        // Render base (shell texture, no tint)
        super.render(pEntity, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
    }

    // Layer 1: Renders the grayscale body texture WITH tint
    private static class TintedBodyLayer extends RenderLayer<DenDenMushiEntity, DenDenMushiModel<DenDenMushiEntity>> {

        public TintedBodyLayer(RenderLayerParent<DenDenMushiEntity, DenDenMushiModel<DenDenMushiEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           DenDenMushiEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

            int bodyColor = entity.getBodyColor();
            float r = ((bodyColor >> 16) & 0xFF) / 255.0f;
            float g = ((bodyColor >> 8) & 0xFF) / 255.0f;
            float b = (bodyColor & 0xFF) / 255.0f;

            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(BODY_BASE_TEXTURE));
            DenDenMushiModel<DenDenMushiEntity> model = this.getParentModel();

            // ✅ Apply entity animations/pose to the model
            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            // ✅ Render the entire model (so transforms are preserved)
            model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, r, g, b, 1.0f);
        }
    }

    // Layer 2: Renders the detail texture WITHOUT tint (eyes, markings, etc.)
    private static class DetailsLayer extends RenderLayer<DenDenMushiEntity, DenDenMushiModel<DenDenMushiEntity>> {

        public DetailsLayer(RenderLayerParent<DenDenMushiEntity, DenDenMushiModel<DenDenMushiEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           DenDenMushiEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(BODY_DETAILS_TEXTURE));
            DenDenMushiModel<DenDenMushiEntity> model = this.getParentModel();

            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        }
    }
}