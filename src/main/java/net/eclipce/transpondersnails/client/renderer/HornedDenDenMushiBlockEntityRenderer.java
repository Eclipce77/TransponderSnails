package net.eclipce.transpondersnails.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.eclipce.transpondersnails.block.custom.HornedDenDenMushiBlock;
import net.eclipce.transpondersnails.block.entity.HornedDenDenMushiBlockEntity;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the Horned Den Den Mushi block using the same item model that the
 * held/dropped item uses, at a scale that matches the TransponderSnail block.
 *
 * Pipeline:
 *  1. Read FACING and SHELL_COLOR from the block state.
 *  2. Build an ItemStack of HORNED_DEN_DEN_MUSHI with the ShellColor NBT tag
 *     so the item property predicate selects the correct shell-colour variant.
 *  3. Apply transforms: centre on block → rotate for FACING → render.
 *
 * Scale notes
 * -----------
 * The Horned Den Den Mushi item model uses 0-16 coordinate units, where
 * 16 units = 1 block.  With ItemDisplayContext.NONE (no display transforms),
 * the model renders at its native size — approximately the same footprint as
 * the TransponderSnail block model — so NO extra scale factor is needed.
 *
 * If the snail appears too large or too small after in-game testing, adjust
 * the SCALE constant below.
 */
public class HornedDenDenMushiBlockEntityRenderer
        implements BlockEntityRenderer<HornedDenDenMushiBlockEntity> {

    // ── Tweak this if the in-game size needs adjustment ──────────────────────
    private static final float SCALE = 1.0f;
    // ─────────────────────────────────────────────────────────────────────────

    /** How far (in blocks) a player must be before the BER stops rendering. */
    private static final int VIEW_DISTANCE = 64;

    public HornedDenDenMushiBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        // Context is unused — we rely on the item renderer directly.
    }

    @Override
    public void render(HornedDenDenMushiBlockEntity blockEntity,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int combinedLight,
                       int combinedOverlay) {

        BlockState state = blockEntity.getBlockState();

        // ── 1. Determine facing ───────────────────────────────────────────────
        Direction facing = state.getValue(HornedDenDenMushiBlock.FACING);

        // ── 2. Build ItemStack with shell colour + body colour NBT ───────────
        //    ShellColor  → drives the shell_color item predicate (variant model)
        //    BodyColor   → read by the RegisterColorHandlersEvent.Item handler at
        //                  tintIndex 0, so the body gets the correct RGB tint.
        int shellColorId = state.getValue(HornedDenDenMushiBlock.SHELL_COLOR);
        int bodyColor    = blockEntity.getBodyColor();

        ItemStack renderStack = new ItemStack(ModItems.HORNED_DEN_DEN_MUSHI.get());
        CompoundTag nbt = renderStack.getOrCreateTag();
        nbt.putInt("ShellColor", shellColorId);
        nbt.putInt("BodyColor",  bodyColor);
        // Eyes: closed while it is switched off (like the other snails when idle), open when it is on. The model picks the variant from this.
        boolean active = state.getValue(HornedDenDenMushiBlock.ACTIVE);
        nbt.putBoolean("EyesClosed", !active);

        // ── 3. Transforms ─────────────────────────────────────────────────────
        poseStack.pushPose();

        // Translate to the block centre on all three axes.
        //
        // Why Y = 0.5:
        //   With ItemDisplayContext.NONE, item model coords map from (0–16)
        //   model-space to (–0.5 … +0.5) block-space.  The snail's geometry
        //   starts at y=0 in model-space (= –0.5 in render-space).  With
        //   Y = 0.0 the bottom of the model would sit 0.5 blocks underground.
        //   Y = 0.5 shifts the model up so its bottom face lands exactly on
        //   the block floor (world Y = 0.5 – 0.5 = 0.0).
        poseStack.translate(0.5, 0.5, 0.5);

        // Rotate to face the correct direction.
        // The item model's "front" (eyes / face) points toward -Z (NORTH) by
        // default.  Direction.toYRot():  SOUTH=0, WEST=90, NORTH=180, EAST=270
        //
        // Correct formula: 180 - toYRot()
        //   NORTH (180°) → 0°    (no rotation needed)
        //   SOUTH   (0°) → 180°  (flip 180°)
        //   WEST   (90°) →  90°  (rotate 90° CCW)
        //   EAST  (270°) → -90°  (rotate 90° CW)
        float yRot = 180f - facing.toYRot();
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        // Origin stays at (0.5, 0, 0.5) — block centre.
        // ItemDisplayContext.NONE renders the item centred at the current origin,
        // so no undo-translate is needed (and doing one in rotated space would
        // shift the model off-centre for non-NORTH facings).

        // Apply scale (1.0 = native, matching the TransponderSnail block size)
        if (SCALE != 1.0f) {
            poseStack.translate(0.5, 0.0, 0.5);
            poseStack.scale(SCALE, SCALE, SCALE);
            poseStack.translate(-0.5, 0.0, -0.5);
        }

        // ── 4. Render the item model ──────────────────────────────────────────
        Minecraft.getInstance().getItemRenderer().renderStatic(
                renderStack,
                ItemDisplayContext.NONE,    // No display transforms — raw model geometry
                combinedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                blockEntity.getLevel(),
                0                           // Seed (unused for item models)
        );

        // The eyes flash red while it is switched on, like the Visual Snail's eyes light up in a call: only the FRONT of each eye, drawn with the eye's
        // own texture tinted red (the white of the eye goes red, the black pupil stays black). The red is faded IN over the normal eye and back OUT
        // again - one slow pulse every 1.6 seconds. At the bottom of the pulse the eye looks completely normal (nothing is drawn), at the top it is
        // fully red; it never goes dark. It also eases in and out as the snail is switched on and off.
        float glow = blockEntity.stepEyeGlow(active);   // 0 the moment it is switched off: the eyes close, and no red is left on them
        if (glow > 0.01F) {
            net.minecraft.client.resources.model.BakedModel eyes = net.eclipce.transpondersnails.client.HornedGlowModels.eyes();
            if (eyes != null) {
                long time = blockEntity.getLevel() != null ? blockEntity.getLevel().getGameTime() : 0L;
                float pulse = 0.5F - 0.5F * (float) Math.cos((time + partialTick) * (2.0 * Math.PI / 32.0));   // 0 (a normal eye) .. 1 (fully red) .. 0
                float alpha = glow * pulse;
                if (alpha > 0.004F) {
                    poseStack.pushPose();
                    poseStack.translate(-0.5, -0.5, -0.5);   // the frame the item model itself is drawn in
                    com.mojang.blaze3d.vertex.VertexConsumer vc = bufferSource.getBuffer(net.minecraft.client.renderer.Sheets.translucentCullBlockSheet());
                    com.mojang.blaze3d.vertex.PoseStack.Pose last = poseStack.last();
                    net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(42L);
                    for (Direction side : Direction.values()) {
                        for (net.minecraft.client.renderer.block.model.BakedQuad quad : eyes.getQuads(null, side, random)) putEyeQuad(vc, last, quad, alpha);
                    }
                    for (net.minecraft.client.renderer.block.model.BakedQuad quad : eyes.getQuads(null, null, random)) putEyeQuad(vc, last, quad, alpha);
                    poseStack.popPose();
                }
            }
        }

        poseStack.popPose();
    }

    /** One quad of the red eye: the eye's own texture, red, full bright, with the given opacity (so that it can fade in over the normal eye). */
    private static void putEyeQuad(com.mojang.blaze3d.vertex.VertexConsumer vc, com.mojang.blaze3d.vertex.PoseStack.Pose pose,
                                   net.minecraft.client.renderer.block.model.BakedQuad quad, float alpha) {
        int[] data = quad.getVertices();                       // 4 vertices of 8 ints: x y z, colour, u v, light, normal
        net.minecraft.core.Vec3i normal = quad.getDirection().getNormal();
        for (int i = 0; i < 4; i++) {
            int o = i * 8;
            vc.vertex(pose.pose(), Float.intBitsToFloat(data[o]), Float.intBitsToFloat(data[o + 1]), Float.intBitsToFloat(data[o + 2]))
                    .color(1.0F, 0.10F, 0.10F, alpha)
                    .uv(Float.intBitsToFloat(data[o + 4]), Float.intBitsToFloat(data[o + 5]))
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                    .normal(pose.normal(), normal.getX(), normal.getY(), normal.getZ())
                    .endVertex();
        }
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }
}