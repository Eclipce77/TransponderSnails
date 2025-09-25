package net.eclipce.transpondersnails.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.transpondersnails.entity.animations.DenDenMushiAnimationDefinitions;
import net.eclipce.transpondersnails.entity.custom.DenDenMushiEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.world.entity.Entity;

public class DenDenMushiModel<T extends Entity> extends HierarchicalModel<T> {
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

	public DenDenMushiModel(ModelPart root) {
		this.dendenmushi = root.getChild("dendenmushi");
		this.eyes = this.dendenmushi.getChild("eyes");
		this.eyelids = this.eyes.getChild("eyelids");
		this.eyeballs = this.eyes.getChild("eyeballs");
		this.stalks = this.eyes.getChild("stalks");
		this.head = this.dendenmushi.getChild("head");
		this.shell = this.dendenmushi.getChild("shell");
		this.body = this.dendenmushi.getChild("body");
		this.upper = this.body.getChild("upper");
		this.lowerbody = this.body.getChild("lowerbody");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition dendenmushi = partdefinition.addOrReplaceChild("dendenmushi", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition eyes = dendenmushi.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition eyelids = eyes.addOrReplaceChild("eyelids", CubeListBuilder.create().texOffs(40, 9).addBox(1.0F, -9.0F, -5.0008F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F))
		.texOffs(40, 9).addBox(-3.0F, -9.0F, -5.0008F, 2.0F, 2.0F, 0.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition eyeballs = eyes.addOrReplaceChild("eyeballs", CubeListBuilder.create().texOffs(38, 39).addBox(1.0F, -9.0F, -5.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(40, 0).addBox(-3.0F, -9.0F, -5.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition stalks = eyes.addOrReplaceChild("stalks", CubeListBuilder.create().texOffs(40, 3).addBox(-2.0F, -7.0F, -5.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(40, 6).addBox(1.0F, -7.0F, -5.0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition head = dendenmushi.addOrReplaceChild("head", CubeListBuilder.create().texOffs(36, 20).addBox(-2.0F, -6.0F, -4.0F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(26, 39).addBox(-2.0F, -5.0F, -6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(26, 26).addBox(-2.0F, -3.0F, -6.0F, 4.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition shell = dendenmushi.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(26, 32).addBox(-3.0F, -9.0F, 6.0F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(36, 13).addBox(-3.0F, -9.0F, -2.0F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
		.texOffs(0, 26).addBox(-3.0F, -10.0F, -1.0F, 6.0F, 8.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition body = dendenmushi.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition upper = body.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(0, 13).addBox(-3.0F, -2.0F, -6.0F, 6.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition lowerbody = body.addOrReplaceChild("lowerbody", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -1.0F, -6.0F, 8.0F, 1.0F, 12.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	// Also, for better rendering control, you might want to add a method to render body parts separately:
	public void renderBodyOnly(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		// Render everything except the shell
		this.eyes.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		this.head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		this.body.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	public void renderShellOnly(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		// Render only the shell
		this.shell.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);

		this.animateWalk(DenDenMushiAnimationDefinitions.walking, limbSwing, limbSwingAmount, 1.5f, 2f);
		this.animate(((DenDenMushiEntity) entity).idleAnimationState, DenDenMushiAnimationDefinitions.idle, ageInTicks, 2f);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		dendenmushi.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	@Override
	public ModelPart root() {
		return dendenmushi;
	}
}