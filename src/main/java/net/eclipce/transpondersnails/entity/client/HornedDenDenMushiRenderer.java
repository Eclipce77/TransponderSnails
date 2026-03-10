package net.eclipce.transpondersnails.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.entity.custom.HornedDenDenMushiEntity;
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
 * Renderer for the Horned Den Den Mushi.
 *
 * Uses the same three-layer rendering strategy as {@link DenDenMushiRenderer}:
 *
 *   Layer 0 (base render)  — per-dye-colour shell texture,     no tint
 *   Layer 1 (TintedBodyLayer) — greyscale body texture,        RGB-tinted
 *   Layer 2 (DetailsLayer)    — eyes / detail overlay texture, no tint
 *
 * Texture paths are shared with the regular Den Den Mushi:
 *   Shell:   textures/entity/den_den_mushi/shell/den_den_mushi_shell_<color>.png
 *   Body:    textures/entity/den_den_mushi/snail/den_den_mushi_snail_body.png
 *   Details: textures/entity/den_den_mushi/snail/den_den_mushi_snail_eyes.png
 */
public class HornedDenDenMushiRenderer
        extends MobRenderer<HornedDenDenMushiEntity, HornedDenDenMushiModel<HornedDenDenMushiEntity>> {

    // Reuse the same textures as the regular Den Den Mushi — 16 shell textures (one per DyeColor),
    // the same greyscale body texture, and the same eye/detail overlay.
    private static final ResourceLocation[] SHELL_TEXTURES = new ResourceLocation[16];

    private static final ResourceLocation BODY_BASE_TEXTURE = new ResourceLocation(
            TransponderSnails.MOD_ID,
            "textures/entity/den_den_mushi/snail/den_den_mushi_snail_body.png");

    private static final ResourceLocation BODY_DETAILS_TEXTURE = new ResourceLocation(
            TransponderSnails.MOD_ID,
            "textures/entity/den_den_mushi/snail/den_den_mushi_snail_eyes.png");

    static {
        for (DyeColor color : DyeColor.values()) {
            SHELL_TEXTURES[color.getId()] = new ResourceLocation(
                    TransponderSnails.MOD_ID,
                    "textures/entity/den_den_mushi/shell/den_den_mushi_shell_"
                            + color.getName() + ".png");
        }
    }

    public HornedDenDenMushiRenderer(EntityRendererProvider.Context context) {
        super(context,
                new HornedDenDenMushiModel<>(context.bakeLayer(ModModelLayers.HORNED_DEN_DEN_MUSHI_LAYER)),
                0.5f);

        this.addLayer(new TintedBodyLayer(this));
        this.addLayer(new DetailsLayer(this));
    }

    // Base render uses the per-colour shell texture
    @Override
    public ResourceLocation getTextureLocation(HornedDenDenMushiEntity entity) {
        int shellColor = entity.getShellColor();
        return SHELL_TEXTURES[shellColor];
    }

    @Override
    public void render(HornedDenDenMushiEntity entity, float entityYaw, float partialTicks,
                       PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
        if (entity.isBaby()) {
            matrixStack.scale(0.5f, 0.5f, 0.5f);
        }
        super.render(entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    // -----------------------------------------------------------------------
    // Layer 1 — greyscale body texture, tinted with entity's body colour
    // -----------------------------------------------------------------------
    private static class TintedBodyLayer
            extends RenderLayer<HornedDenDenMushiEntity, HornedDenDenMushiModel<HornedDenDenMushiEntity>> {

        public TintedBodyLayer(
                RenderLayerParent<HornedDenDenMushiEntity, HornedDenDenMushiModel<HornedDenDenMushiEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           HornedDenDenMushiEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick,
                           float ageInTicks, float netHeadYaw, float headPitch) {

            int bodyColor = entity.getBodyColor();
            float r = ((bodyColor >> 16) & 0xFF) / 255.0f;
            float g = ((bodyColor >>  8) & 0xFF) / 255.0f;
            float b = ( bodyColor        & 0xFF) / 255.0f;

            VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(BODY_BASE_TEXTURE));
            HornedDenDenMushiModel<HornedDenDenMushiEntity> model = this.getParentModel();

            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, r, g, b, 1.0f);
        }
    }

    // -----------------------------------------------------------------------
    // Layer 2 — detail / eye texture, no tint
    // -----------------------------------------------------------------------
    private static class DetailsLayer
            extends RenderLayer<HornedDenDenMushiEntity, HornedDenDenMushiModel<HornedDenDenMushiEntity>> {

        public DetailsLayer(
                RenderLayerParent<HornedDenDenMushiEntity, HornedDenDenMushiModel<HornedDenDenMushiEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                           HornedDenDenMushiEntity entity,
                           float limbSwing, float limbSwingAmount, float partialTick,
                           float ageInTicks, float netHeadYaw, float headPitch) {

            VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(BODY_DETAILS_TEXTURE));
            HornedDenDenMushiModel<HornedDenDenMushiEntity> model = this.getParentModel();

            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        }
    }
}