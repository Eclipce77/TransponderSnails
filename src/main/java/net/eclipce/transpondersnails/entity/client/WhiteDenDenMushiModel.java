package net.eclipce.transpondersnails.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.transpondersnails.entity.animations.WhiteDenDenMushiAnimations;
import net.eclipce.transpondersnails.entity.custom.WhiteDenDenMushiEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/**
 * White Den Den Mushi Model
 * HierarchicalModel with animation support
 */
public class WhiteDenDenMushiModel<T extends WhiteDenDenMushiEntity> extends HierarchicalModel<T> {

	private final ModelPart whitedendenmushi;
	private final ModelPart eyes;
	private final ModelPart eyeballs;
	private final ModelPart stalks;
	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart upper;
	private final ModelPart lowerbody;
	private final ModelPart shell;

	public WhiteDenDenMushiModel(ModelPart root) {
		this.whitedendenmushi = root.getChild("whitedendenmushi");
		this.eyes = this.whitedendenmushi.getChild("eyes");
		this.eyeballs = this.eyes.getChild("eyeballs");
		this.stalks = this.eyes.getChild("stalks");
		this.head = this.whitedendenmushi.getChild("head");
		this.body = this.whitedendenmushi.getChild("body");
		this.upper = this.body.getChild("upper");
		this.lowerbody = this.body.getChild("lowerbody");
		this.shell = this.whitedendenmushi.getChild("shell");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition whitedendenmushi = partdefinition.addOrReplaceChild("whitedendenmushi", CubeListBuilder.create(), PartPose.offset(8.0F, 24.0F, -8.0F));

		PartDefinition eyes = whitedendenmushi.addOrReplaceChild("eyes", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition eyeballs = eyes.addOrReplaceChild("eyeballs", CubeListBuilder.create().texOffs(38, 39).addBox(-7.0F, -10.0F, 4.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(40, 0).addBox(-11.0F, -10.0F, 4.0F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition stalks = eyes.addOrReplaceChild("stalks", CubeListBuilder.create().texOffs(40, 3).addBox(-10.0F, -8.0F, 4.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(40, 6).addBox(-7.0F, -8.0F, 4.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition head = whitedendenmushi.addOrReplaceChild("head", CubeListBuilder.create().texOffs(36, 21).addBox(-8.0F, -5.0F, 2.0F, 3.5F, 1.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(34, 20).addBox(-8.0F, -5.0F, 4.0F, 3.7F, 1.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(36, 21).addBox(-11.0F, -6.0F, 3.0F, 6.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(35, 20).addBox(-10.0F, -7.0F, 3.0F, 4.0F, 3.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(34, 20).addBox(-11.0F, -6.0F, 5.0F, 6.0F, 2.0F, 1.5F, new CubeDeformation(0.0F))
				.texOffs(36, 21).addBox(-11.5F, -5.0F, 2.0F, 3.5F, 1.0F, 2.0F, new CubeDeformation(0.0F))
				.texOffs(34, 20).addBox(-11.7F, -5.0F, 4.0F, 3.7F, 1.0F, 3.0F, new CubeDeformation(0.0F))
				.texOffs(25, 39).addBox(-12.0F, -4.0F, 2.0F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(24, 26).addBox(-12.0F, -3.0F, 2.0F, 8.0F, 0.5F, 5.0F, new CubeDeformation(0.0F))
				.texOffs(23, 25).addBox(-12.0F, -2.5F, 2.0F, 8.0F, 0.5F, 6.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition body = whitedendenmushi.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition upper = body.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(7, 19).addBox(-11.5F, -2.0F, 2.0F, 7.0F, 1.0F, 6.0F, new CubeDeformation(0.0F))
				.texOffs(7, 18).addBox(-11.5F, -1.5F, 8.0F, 7.0F, 0.5F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition lowerbody = body.addOrReplaceChild("lowerbody", CubeListBuilder.create().texOffs(-2, -2).addBox(-12.5F, -1.0F, 1.0F, 9.0F, 1.0F, 14.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		PartDefinition shell = whitedendenmushi.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(26, 32).addBox(-11.0F, -8.5F, 15.0F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(36, 13).addBox(-11.0F, -8.5F, 7.0F, 6.0F, 6.0F, 1.0F, new CubeDeformation(0.0F))
				.texOffs(0, 26).addBox(-11.0F, -9.5F, 8.0F, 6.0F, 8.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		this.root().getAllParts().forEach(ModelPart::resetPose);

		// Apply walking animation
		this.animateWalk(WhiteDenDenMushiAnimations.walking, limbSwing, limbSwingAmount, 1.5f, 2f);

		// Apply idle animation
		this.animate(entity.idleAnimationState, WhiteDenDenMushiAnimations.idle, ageInTicks, 2f);
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay,
							   float red, float green, float blue, float alpha) {
		whitedendenmushi.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}

	@Override
	public ModelPart root() {
		return whitedendenmushi;
	}
}