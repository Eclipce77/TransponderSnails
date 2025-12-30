package net.eclipce.transpondersnails.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Collections;
import java.util.List;

/**
 * White Transponder Snail Block with rotation and shell color dyeing support.
 * Shell can be dyed any of the 16 Minecraft colors.
 * Uses blockstate properties instead of block entity for simplicity.
 */
public class WhiteTransponderSnailBlock extends HorizontalDirectionalBlock {

    // Shell color property (0-15 for DyeColor IDs)
    public static final IntegerProperty SHELL_COLOR = IntegerProperty.create("shell_color", 0, 15);

    public WhiteTransponderSnailBlock(Properties properties) {
        super(properties);
        // Default to facing north with white shell
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SHELL_COLOR, 0)); // White
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHELL_COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Get shell color from item NBT if available
        ItemStack stack = context.getItemInHand();
        int shellColor = 0; // Default white

        if (stack.hasTag()) {
            CompoundTag nbt = stack.getTag();
            if (nbt.contains("shell_color")) {
                shellColor = nbt.getInt("shell_color");
            }
        }

        // Face the player when placed
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(SHELL_COLOR, shellColor);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack itemInHand = player.getItemInHand(hand);
        Item item = itemInHand.getItem();

        // Check if player is holding a dye
        if (item instanceof DyeItem dyeItem) {
            DyeColor dyeColor = dyeItem.getDyeColor();
            int newColorId = dyeColor.getId();
            int currentColorId = state.getValue(SHELL_COLOR);

            // Only update if color is different
            if (newColorId != currentColorId) {
                // Update block state with new shell color
                level.setBlock(pos, state.setValue(SHELL_COLOR, newColorId), 3);

                // Send message to player
                if (player instanceof ServerPlayer serverPlayer) {
                    String colorName = dyeColor.getName().replace("_", " ");
                    String displayName = colorName.substring(0, 1).toUpperCase() + colorName.substring(1);
                    serverPlayer.sendSystemMessage(
                            Component.literal("White Transponder Snail shell dyed " + displayName + "!")
                                    .withStyle(net.minecraft.ChatFormatting.GREEN)
                    );
                }

                // Consume dye in survival mode
                if (!player.isCreative()) {
                    itemInHand.shrink(1);
                }

                return InteractionResult.CONSUME;
            } else {
                // Already this color
                if (player instanceof ServerPlayer serverPlayer) {
                    String colorName = dyeColor.getName().replace("_", " ");
                    String displayName = colorName.substring(0, 1).toUpperCase() + colorName.substring(1);
                    serverPlayer.sendSystemMessage(
                            Component.literal("Shell is already " + displayName + "!")
                                    .withStyle(net.minecraft.ChatFormatting.YELLOW)
                    );
                }
                return InteractionResult.PASS;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        // Create item with shell color preserved
        ItemStack stack = new ItemStack(this);
        CompoundTag nbt = stack.getOrCreateTag();
        nbt.putInt("shell_color", state.getValue(SHELL_COLOR));

        return Collections.singletonList(stack);
    }

    /**
     * Get the shell color model variant name
     */
    public static String getShellVariantModel(int shellColor) {
        DyeColor dyeColor = DyeColor.byId(shellColor);
        return "white_transponder_snail_shell_" + dyeColor.getName();
    }

    /**
     * Get shell color from block state
     */
    public static int getShellColor(BlockState state) {
        return state.getValue(SHELL_COLOR);
    }

    /**
     * Get shell color name for display
     */
    public static String getShellColorName(BlockState state) {
        int colorId = state.getValue(SHELL_COLOR);
        DyeColor color = DyeColor.byId(colorId);
        String name = color.getName().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}