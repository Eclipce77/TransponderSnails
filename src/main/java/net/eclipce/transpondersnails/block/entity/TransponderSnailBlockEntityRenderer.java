package net.eclipce.transpondersnails.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

public class TransponderSnailBlockEntityRenderer implements BlockEntityRenderer<TransponderSnailBlockEntity> {

    public TransponderSnailBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TransponderSnailBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        BlockState blockState = blockEntity.getBlockState();
        boolean hasSound = blockState.getValue(TransponderSnailBlock.HAS_SOUND);
        boolean inCall = blockState.getValue(TransponderSnailBlock.IN_CALL);
        Direction facing = blockState.getValue(TransponderSnailBlock.FACING);

        // Get colors
        int bodyColor = blockEntity.getBodyColor();
        int shellColorId = blockEntity.getShellColor();
        String dyeColorName = DyeColor.byId(shellColorId).getName();

        float r = ((bodyColor >> 16) & 0xFF) / 255.0f;
        float g = ((bodyColor >> 8) & 0xFF) / 255.0f;
        float b = (bodyColor & 0xFF) / 255.0f;

        // Add this debug logging:
        System.out.println("DEBUG Renderer: shellColorId=" + shellColorId +
                ", dyeColorName=" + dyeColorName +
                ", hasSound=" + hasSound +
                ", inCall=" + inCall);

        // Get model name including shell
        String modelName = getModelName(hasSound, inCall, shellColorId);
        ResourceLocation modelPath = new ResourceLocation(TransponderSnails.MOD_ID, "block/" + modelName);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(modelPath);

        if (model == null || model == Minecraft.getInstance().getModelManager().getMissingModel()) {
            return;
        }

        poseStack.pushPose();

        // Apply rotation based on facing direction
        poseStack.translate(0.5, 0, 0.5);
        float yRot = switch (facing) {
            case SOUTH -> 180f;
            case WEST -> 90f;
            case EAST -> 270f;
            default -> 0f; // NORTH
        };
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yRot));
        poseStack.translate(-0.5, 0, -0.5);

        RenderType renderType = RenderType.cutout();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

        // Render with body color tint and proper lighting
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                poseStack.last(),
                vertexConsumer,
                blockState,
                model,
                r, g, b,
                packedLight,  // Use proper lighting
                packedOverlay,
                ModelData.EMPTY,
                renderType
        );

        poseStack.popPose();
    }

    private String getModelName(boolean hasSound, boolean inCall, int shellColorId) {
        String dyeColorName = DyeColor.byId(shellColorId).getName();
        String stateSuffix = getStateSuffix(hasSound, inCall);
        return "transponder_snail_shell_" + dyeColorName + stateSuffix;
    }

    private String getStateSuffix(boolean hasSound, boolean inCall) {
        if (hasSound && inCall) {
            return "_active";
        } else if (inCall) {
            return "_call";
        } else if (hasSound) {
            return "_sound";
        } else {
            return ""; // Base model: transponder_snail_shell_white
        }
    }

    private String getBaseState(boolean hasSound, boolean inCall) {
        if (hasSound && inCall) {
            return "transponder_snail_active";
        } else if (inCall) {
            return "transponder_snail_call";
        } else if (hasSound) {
            return "transponder_snail_sound";
        } else {
            return "transponder_snail";
        }
    }
}