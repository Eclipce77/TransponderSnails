package net.eclipce.transpondersnails.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.custom.WhiteDenDenMushiEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/**
 * Renderer for White Den Den Mushi
 * Renders in layers: shell -> white body -> eyes
 */
public class WhiteDenDenMushiRenderer extends MobRenderer<WhiteDenDenMushiEntity, WhiteDenDenMushiModel<WhiteDenDenMushiEntity>> {

    // Shell textures (reused from regular Den Den Mushi)
    private static final ResourceLocation[] SHELL_TEXTURES = new ResourceLocation[16];

    // White body texture
    private static final ResourceLocation WHITE_BODY_TEXTURE = new ResourceLocation(TransponderSnails.MOD_ID,
            "textures/entity/white_den_den_mushi/snail/white_den_den_mushi.png");

    // Eyes texture (reused from regular Den Den Mushi)
    private static final ResourceLocation EYES_TEXTURE = new ResourceLocation(TransponderSnails.MOD_ID,
            "textures/entity/den_den_mushi/snail/den_den_mushi_snail_eyes.png");

    static {
        // Initialize shell texture array
        for (DyeColor color : DyeColor.values()) {
            SHELL_TEXTURES[color.getId()] = new ResourceLocation(TransponderSnails.MOD_ID,
                    "textures/entity/den_den_mushi/shell/den_den_mushi_shell_" + color.getName() + ".png");
        }
    }

    public WhiteDenDenMushiRenderer(EntityRendererProvider.Context pContext) {
        super(pContext, new WhiteDenDenMushiModel<>(pContext.bakeLayer(ModModelLayers.WHITE_DEN_DEN_MUSHI_LAYER)), 0.5f);

        // Add rendering layers
        this.addLayer(new WhiteBodyLayer(this));
        this.addLayer(new EyesLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WhiteDenDenMushiEntity entity) {
        // Base texture is the shell
        int shellColor = entity.getShellColor();
        return SHELL_TEXTURES[shellColor];
    }

    @Override
    public void render(WhiteDenDenMushiEntity pEntity, float pEntityYaw, float pPartialTicks, PoseStack pMatrixStack,
                       MultiBufferSource pBuffer, int pPackedLight) {
        if(pEntity.isBaby()) {
            pMatrixStack.scale(0.5f, 0.5f, 0.5f);
        }

        super.render(pEntity, pEntityYaw, pPartialTicks, pMatrixStack, pBuffer, pPackedLight);
    }

    /**
     * Layer 1: White body (rendered over shell)
     */
    private static class WhiteBodyLayer extends RenderLayer<WhiteDenDenMushiEntity, WhiteDenDenMushiModel<WhiteDenDenMushiEntity>> {

        public WhiteBodyLayer(RenderLayerParent<WhiteDenDenMushiEntity, WhiteDenDenMushiModel<WhiteDenDenMushiEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           WhiteDenDenMushiEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(WHITE_BODY_TEXTURE));
            WhiteDenDenMushiModel<WhiteDenDenMushiEntity> model = this.getParentModel();

            // IMPORTANT: Apply entity animations/pose before rendering
            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            // Pure white (no tint)
            model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    /**
     * Layer 2: Eyes (rendered on top)
     */
    private static class EyesLayer extends RenderLayer<WhiteDenDenMushiEntity, WhiteDenDenMushiModel<WhiteDenDenMushiEntity>> {

        public EyesLayer(RenderLayerParent<WhiteDenDenMushiEntity, WhiteDenDenMushiModel<WhiteDenDenMushiEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           WhiteDenDenMushiEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(EYES_TEXTURE));
            WhiteDenDenMushiModel<WhiteDenDenMushiEntity> model = this.getParentModel();

            // IMPORTANT: Apply entity animations/pose before rendering
            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

            model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }
}