package net.eclipce.transpondersnails.visual;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;

/**
 * Block -> {@link VisualSnailRole}. Looks at the block's REGISTRY NAME (see {@link VisualCallConstants#DUPLEX_SNAIL_ID} /
 * {@link VisualCallConstants#CAMERA_SNAIL_ID}), so it does not depend on what the constants in ModBlocks are called.
 */
public final class VisualRoles {

    private VisualRoles() {}

    /** @return the role of a Visual snail block, or null for every other block */
    @Nullable
    public static VisualSnailRole of(@Nullable Block block) {
        if (block == null) return null;
        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(block);
        if (key == null || !TransponderSnails.MOD_ID.equals(key.getNamespace())) return null;
        return VisualSnailRole.ofPath(key.getPath());
    }

    /** @return the role of the block this block entity currently sits in, or null */
    @Nullable
    public static VisualSnailRole ofEntity(@Nullable BlockEntity blockEntity) {
        return blockEntity == null ? null : of(blockEntity.getBlockState().getBlock());
    }
}
