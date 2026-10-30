package net.eclipce.transpondersnails.visual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.eclipce.transpondersnails.block.custom.TransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.visual.ProjectorHeadAim;
import net.eclipce.transpondersnails.visual.VisualRoles;
import net.eclipce.transpondersnails.visual.VisualSnailRole;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.slf4j.Logger;

/**
 * Draws the head of a placed Visual Transponder Snail (the projector): its top part, the lens, as a model of its own, TURNED towards where the screen has been moved to. The head
 * is not in the snail's static models (those cannot move); the inventory and hand icons still have it there, in their own copies of the model. See {@link ProjectorHeadAim} for
 * where it points and why, {@link VisualProjectorHead} for the glide and the shared turn.
 *
 * Registered for the snail block entity type in ClientSetup; every block entity of that type comes through here (the phone snails too), so it leaves at once for anything that
 * is not a projector. The head is drawn all the time, not only in a call: it points where the screen WOULD go.
 */
@OnlyIn(Dist.CLIENT)
public class VisualProjectorHeadRenderer implements BlockEntityRenderer<TransponderSnailBlockEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean warnedMissing = false;

    public VisualProjectorHeadRenderer(BlockEntityRendererProvider.Context context) {
        // nothing needed from the context: the head is a baked model, drawn by hand
    }

    @Override
    public void render(TransponderSnailBlockEntity snail, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!snail.isVisualSnail()) {
            return;
        }
        Level level = snail.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = snail.getBlockState();
        if (VisualRoles.of(state.getBlock()) != VisualSnailRole.DUPLEX || !state.hasProperty(TransponderSnailBlock.FACING)) {
            return; // the camera snails have no projector
        }
        BakedModel head = VisualGlowModels.get(VisualGlowModels.projectorHead(VisualGlowModels.projectorSnail()));
        if (head == null) {
            if (!warnedMissing) {
                warnedMissing = true;
                LOGGER.warn("VisualProjectorHeadRenderer: the head model is missing (assets/transpondersnails/models/block/{}.json) - the projector is drawn without its head",
                        VisualGlowModels.projectorHead(VisualGlowModels.projectorSnail()));
            }
            return;
        }
        Direction facing = state.getValue(TransponderSnailBlock.FACING);
        ProjectorHeadAim.Pose turn = VisualProjectorHead.advance(snail);

        pose.pushPose();
        try {
            // the model is for a snail facing north: turn it the way the blockstate turns the snail, then the head about its pivot
            pose.translate(0.5, 0.5, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-VisualProjectorHead.yRotation(facing)));
            pose.translate(-0.5, -0.5, -0.5);
            VisualProjectorHead.turn(pose, turn);

            VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
            PoseStack.Pose last = pose.last();
            RandomSource random = RandomSource.create(42L);
            for (Direction side : Direction.values()) {
                for (BakedQuad quad : head.getQuads(null, side, random)) {
                    put(vc, last, quad, level, facing, packedLight);
                }
            }
            for (BakedQuad quad : head.getQuads(null, null, random)) {
                put(vc, last, quad, level, facing, packedLight);
            }
        } finally {
            pose.popPose();
        }
    }

    /** One quad of the head, shaded by the side it looks to in the world (as the game shades the rest of the snail), so the head does not change colour as it turns. */
    private static void put(VertexConsumer vc, PoseStack.Pose pose, BakedQuad quad, Level level, Direction facing, int packedLight) {
        float shade = level.getShade(VisualProjectorHead.rotated(quad.getDirection(), facing), quad.isShade());
        vc.putBulkData(pose, quad, shade, shade, shade, packedLight, OverlayTexture.NO_OVERLAY);
    }
}
