package net.eclipce.transpondersnails.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.transpondersnails.entity.animations.HornedDenDenMushiAnimationDefinitions;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.Entity;

/**
 * Model for the Horned Den Den Mushi.
 *
 * Identical structure to {@link DenDenMushiModel} with three additional horn
 * parts (right_horn, left_horn, back_horn) added as children of the shell.
 * The horn geometry comes directly from the Blockbench export.
 *
 * Texture sheet: 64 × 64  (same atlas size as the base DDM)
 */
public class HornedDenDenMushiModel<T extends Entity> extends HierarchicalModel<T> {

    // All parts from the base DDM model
    public final ModelPart dendenmushi;
    public final ModelPart eyes;
    public final ModelPart eyelids;
    public final ModelPart eyeballs;
    public final ModelPart stalks;
    public final ModelPart head;
    public final ModelPart shell;
    public final ModelPart body;
    public final ModelPart upper;
    public final ModelPart lowerbody;

    // Horn parts — children of shell
    public final ModelPart right_horn;
    public final ModelPart left_horn;
    public final ModelPart back_horn;

    public HornedDenDenMushiModel(ModelPart root) {
        this.dendenmushi = root.getChild("dendenmushi");
        this.eyes        = this.dendenmushi.getChild("eyes");
        this.eyelids     = this.eyes.getChild("eyelids");
        this.eyeballs    = this.eyes.getChild("eyeballs");
        this.stalks      = this.eyes.getChild("stalks");
        this.head        = this.dendenmushi.getChild("head");
        this.shell       = this.dendenmushi.getChild("shell");
        this.right_horn  = this.shell.getChild("right_horn");
        this.left_horn   = this.shell.getChild("left_horn");
        this.back_horn   = this.shell.getChild("back_horn");
        this.body        = this.dendenmushi.getChild("body");
        this.upper       = this.body.getChild("upper");
        this.lowerbody   = this.body.getChild("lowerbody");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        // ---- Root ----
        PartDefinition dendenmushi = partdefinition.addOrReplaceChild(
                "dendenmushi", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

        // ---- Eyes group ----
        PartDefinition eyes = dendenmushi.addOrReplaceChild(
                "eyes", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        eyes.addOrReplaceChild("eyelids",
                CubeListBuilder.create()
                        .texOffs(40, 9).addBox( 1.0F, -9.0F, -5.0008F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 9).addBox(-3.0F, -9.0F, -5.0008F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        eyes.addOrReplaceChild("eyeballs",
                CubeListBuilder.create()
                        .texOffs(38, 39).addBox( 1.0F, -9.0F, -5.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(40,  0).addBox(-3.0F, -9.0F, -5.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        eyes.addOrReplaceChild("stalks",
                CubeListBuilder.create()
                        .texOffs(40, 3).addBox(-2.0F, -7.0F, -5.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(40, 6).addBox( 1.0F, -7.0F, -5.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // ---- Head ----
        dendenmushi.addOrReplaceChild("head",
                CubeListBuilder.create()
                        .texOffs(36, 20).addBox(-2.0F, -6.0F, -4.0F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(26, 39).addBox(-2.0F, -5.0F, -6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
                        .texOffs(26, 26).addBox(-2.0F, -3.0F, -6.0F, 4.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // ---- Shell (with horns as children) ----
        PartDefinition shell = dendenmushi.addOrReplaceChild("shell",
                CubeListBuilder.create()
                        .texOffs(26, 32).addBox(-3.0F,  -9.0F,  6.0F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs(36, 13).addBox(-3.0F,  -9.0F, -2.0F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
                        .texOffs( 0, 26).addBox(-3.0F, -10.0F, -1.0F, 6.0F, 8.0F, 7.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        // Right horn — attached to the left side of the shell top
        shell.addOrReplaceChild("right_horn",
                CubeListBuilder.create()
                        .texOffs(38, 17).addBox( 0.0F, -0.75F, -0.5F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F))
                        .texOffs(38, 17).addBox( 0.0F, -1.0F,   0.0F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F))
                        .texOffs(38, 17).addBox( 0.0F, -0.75F,  0.0F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F))
                        .texOffs(38, 17).addBox( 0.0F, -1.0F,  -0.5F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F))
                        .texOffs(21, 39).addBox(-0.5F,  0.5F,  -0.5F, 1.0F, 0.5F,  1.0F, new CubeDeformation(0.0F))
                        .texOffs(38, 16).addBox(-0.5F, -0.5F,  -0.5F, 1.0F, 1.0F,  1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(-3.5F, -9.5F, 2.5F, 0.0F, 0.0F, -0.9163F));

        // Left horn — mirrored across the shell top
        shell.addOrReplaceChild("left_horn",
                CubeListBuilder.create()
                        .texOffs(38, 17).mirror().addBox(-0.5F, -0.75F, -0.5F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(38, 17).mirror().addBox(-0.5F, -1.0F,   0.0F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(38, 17).mirror().addBox(-0.5F, -0.75F,  0.0F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(38, 17).mirror().addBox(-0.5F, -1.0F,  -0.5F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(21, 39).mirror().addBox(-0.5F,  0.5F,  -0.5F, 1.0F, 0.5F,  1.0F, new CubeDeformation(0.0F)).mirror(false)
                        .texOffs(38, 16).mirror().addBox(-0.5F, -0.5F,  -0.5F, 1.0F, 1.0F,  1.0F, new CubeDeformation(0.0F)).mirror(false),
                PartPose.offsetAndRotation(3.5F, -9.5F, 2.5F, 0.0F, 0.0F, 0.9163F));

        // Back horn — sits at the rear of the shell, tilted backward
        shell.addOrReplaceChild("back_horn",
                CubeListBuilder.create()
                        .texOffs(38, 17).addBox(-0.1667F, -0.375F, -0.5F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F))
                        .texOffs(38, 17).addBox(-0.1667F, -0.625F,  0.0F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F))
                        .texOffs(38, 17).addBox(-0.1667F, -0.375F,  0.0F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F))
                        .texOffs(38, 17).addBox(-0.1667F, -0.625F, -0.5F, 0.5F, 0.25F, 0.5F, new CubeDeformation(0.0F))
                        .texOffs(21, 39).addBox(-0.6667F,  0.875F, -0.5F, 1.0F, 0.5F,  1.0F, new CubeDeformation(0.0F))
                        .texOffs(38, 16).addBox(-0.6667F, -0.125F, -0.5F, 1.0F, 1.0F,  1.0F, new CubeDeformation(0.0F)),
                PartPose.offsetAndRotation(0.0F, -8.8605F, 7.696F, -1.5708F, 0.6109F, -1.5708F));

        // ---- Body ----
        PartDefinition body = dendenmushi.addOrReplaceChild(
                "body", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

        body.addOrReplaceChild("upper",
                CubeListBuilder.create()
                        .texOffs(0, 13).addBox(-3.0F, -2.0F, -6.0F, 6.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        body.addOrReplaceChild("lowerbody",
                CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -1.0F, -6.0F, 8.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)),
                PartPose.offset(0.0F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 64, 64);
    }

    // -----------------------------------------------------------------------
    // Render helpers (mirrors DenDenMushiModel for the layer system)
    // -----------------------------------------------------------------------

    public void renderBodyOnly(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        this.eyes.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    public void renderShellOnly(PoseStack poseStack, VertexConsumer vertexConsumer,
                                int packedLight, int packedOverlay,
                                float red, float green, float blue, float alpha) {
        // Renders shell and its horn children
        this.shell.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    // -----------------------------------------------------------------------
    // Animation
    // -----------------------------------------------------------------------

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
                          float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // Walking is driven by limb swing; idle uses the entity's AnimationState.
        // Cast to DenDenMushiEntity since HornedDenDenMushiEntity extends it and
        // idleAnimationState is declared public final there.
        this.animateWalk(HornedDenDenMushiAnimationDefinitions.walking, limbSwing, limbSwingAmount, 1.5f, 2f);
        this.animate(((DenDenMushiEntity) entity).idleAnimationState,
                HornedDenDenMushiAnimationDefinitions.idle, ageInTicks, 2f);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer,
                               int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        dendenmushi.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public ModelPart root() {
        return dendenmushi;
    }
}