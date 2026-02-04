package net.eclipce.transpondersnails.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.eclipce.transpondersnails.block.custom.BlackTransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.BlackTransponderSnailBlockEntity;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Block Entity Renderer for Black Transponder Snail Block
 * Renders the item model with shell color variants.
 *
 * FIXED: Uses ItemDisplayContext.NONE to bypass model display transforms
 * and applies explicit positioning/rotation for consistent rendering
 * with the entity renderer.
 */
public class BlackTransponderSnailBlockEntityRenderer implements BlockEntityRenderer<BlackTransponderSnailBlockEntity> {

    private final ItemRenderer itemRenderer;

    // Scale factor - same as entity renderer BASE_SCALE
    private static final float BLOCK_SCALE = 3.0f;

    // Y offset to lift model off ground - pushed up on Y axis
    private static final double Y_OFFSET = 1.455D;

    // Debug flag
    private static final boolean DEBUG = false;
    private static int debugCounter = 0;

    public BlackTransponderSnailBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(BlackTransponderSnailBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {

        if (blockEntity.getLevel() == null) {
            return;
        }

        BlockState state = blockEntity.getBlockState();

        // Get block properties from blockstate (which is synced to client)
        boolean isOpen = state.getValue(BlackTransponderSnailBlock.OPEN);
        Direction facing = state.getValue(BlackTransponderSnailBlock.FACING);
        int shellColor = state.getValue(BlackTransponderSnailBlock.SHELL_COLOR);

        // Debug output
        if (DEBUG && debugCounter++ % 100 == 0) {
            System.out.println("=== BLACK SNAIL BLOCK BER DEBUG ===");
            System.out.println("Position: " + blockEntity.getBlockPos());
            System.out.println("Shell Color: " + shellColor + " (" + DyeColor.byId(shellColor).getName() + ")");
            System.out.println("Open: " + isOpen);
            System.out.println("Facing: " + facing);
            System.out.println("===================================");
        }

        poseStack.pushPose();

        // Center in block and lift off ground
        poseStack.translate(0.5D, Y_OFFSET, 0.5D);

        // Apply scale - same as entity renderer
        poseStack.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);

        // Rotate based on facing direction
        float rotation = getRotationFromFacing(facing);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));

        // Create ItemStack with appropriate state
        ItemStack itemStack = new ItemStack(ModItems.BLACK_TRANSPONDER_SNAIL.get());
        CompoundTag nbt = itemStack.getOrCreateTag();

        // Set shell color (matches BlackTransponderSnailItem.SHELL_COLOR_TAG)
        nbt.putInt("shell_color", shellColor);

        // Set open state (matches BlackTransponderSnailItem.OPEN_STATE_TAG)
        nbt.putBoolean("is_open", isOpen);

        // Render the item model
        // Using NONE context - no display transforms applied, we handle positioning ourselves
        itemRenderer.renderStatic(
                itemStack,
                ItemDisplayContext.NONE,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                blockEntity.getLevel(),
                (int) blockEntity.getBlockPos().asLong()
        );

        poseStack.popPose();
    }

    /**
     * Convert facing direction to Y rotation in degrees
     */
    private float getRotationFromFacing(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0f;
            case SOUTH -> 180.0f;
            case WEST -> 90.0f;
            case EAST -> -90.0f;
            default -> 0.0f;
        };
    }

    /**
     * Render distance - how far away the BER will render
     */
    @Override
    public int getViewDistance() {
        return 64;
    }
}