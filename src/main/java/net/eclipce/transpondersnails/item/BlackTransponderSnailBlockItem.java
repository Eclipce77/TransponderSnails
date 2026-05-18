package net.eclipce.transpondersnails.item;

import net.eclipce.transpondersnails.block.custom.BlackTransponderSnailBlock;
import net.eclipce.transpondersnails.block.entity.BlackTransponderSnailBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Block item for Black Transponder Snail Block
 * Handles NBT data preservation for shell color and other properties
 */
public class BlackTransponderSnailBlockItem extends BlockItem {

    // NBT keys - must match the block entity
    public static final String TAG_SHELL_COLOR = "ShellColor";
    public static final String TAG_BLOCK_ENTITY_TAG = "BlockEntityTag";

    public BlackTransponderSnailBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @NotNull InteractionResult place(@NotNull BlockPlaceContext context) {
        InteractionResult result = super.place(context);

        if (result.consumesAction() && context.getLevel() != null && !context.getLevel().isClientSide()) {
            BlockPos pos = context.getClickedPos();
            Level level = context.getLevel();
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
                // Transfer shell color from item to block entity
                ItemStack stack = context.getItemInHand();
                int shellColor = getShellColorId(stack);
                snailBE.setShellColor(shellColor);

                // Update block state to reflect shell color
                BlockState state = level.getBlockState(pos);
                level.setBlock(pos, state.setValue(BlackTransponderSnailBlock.SHELL_COLOR, shellColor), 3);

            }
        }

        return result;
    }

    /**
     * Get shell color ID from item NBT
     * @param stack The item stack
     * @return The shell color ID (0-15, default is yellow = 4)
     */
    public static int getShellColorId(@NotNull ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        if (nbt != null) {
            // Check top-level NBT first
            if (nbt.contains(TAG_SHELL_COLOR)) {
                return nbt.getInt(TAG_SHELL_COLOR);
            }
            // Check BlockEntityTag for placed-then-broken items
            if (nbt.contains(TAG_BLOCK_ENTITY_TAG)) {
                CompoundTag beTag = nbt.getCompound(TAG_BLOCK_ENTITY_TAG);
                if (beTag.contains(TAG_SHELL_COLOR)) {
                    return beTag.getInt(TAG_SHELL_COLOR);
                }
            }
        }
        return DyeColor.YELLOW.getId(); // Default to yellow
    }

    /**
     * Set shell color ID in item NBT
     * @param stack The item stack
     * @param colorId The shell color ID (0-15)
     */
    public static void setShellColorId(@NotNull ItemStack stack, int colorId) {
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt(TAG_SHELL_COLOR, colorId);

        // Also set in BlockEntityTag for consistency
        CompoundTag beTag = nbt.contains(TAG_BLOCK_ENTITY_TAG) ?
                nbt.getCompound(TAG_BLOCK_ENTITY_TAG) : new CompoundTag();
        beTag.putInt(TAG_SHELL_COLOR, colorId);
        nbt.put(TAG_BLOCK_ENTITY_TAG, beTag);
    }

    /**
     * Get shell color as DyeColor
     * @param stack The item stack
     * @return The DyeColor
     */
    public static DyeColor getShellColor(@NotNull ItemStack stack) {
        return DyeColor.byId(getShellColorId(stack));
    }

    /**
     * Set shell color from DyeColor
     * @param stack The item stack
     * @param color The DyeColor
     */
    public static void setShellColor(@NotNull ItemStack stack, DyeColor color) {
        setShellColorId(stack, color.getId());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level,
                                @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        // Show shell color
        DyeColor shellColor = getShellColor(stack);
        tooltip.add(Component.translatable("item.transpondersnails.shell_color")
                .append(": ")
                .append(Component.translatable("color.minecraft." + shellColor.getName()))
                .withStyle(ChatFormatting.GRAY));

        // Show that this is a placeable interception snail
        tooltip.add(Component.literal("Placeable interception snail")
                .withStyle(ChatFormatting.DARK_PURPLE));

        // Show lightning rod hint
        tooltip.add(Component.literal("Connect to lightning rods via wire")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.literal("for extended range")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));

        // Advanced tooltip info
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal("Shell Color ID: " + getShellColorId(stack))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        // Could customize name based on shell color if desired
        return super.getName(stack);
    }
}