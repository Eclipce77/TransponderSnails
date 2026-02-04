package net.eclipce.transpondersnails.block.custom;

import net.eclipce.transpondersnails.block.entity.BlackTransponderSnailBlockEntity;
import net.eclipce.transpondersnails.block.entity.ModBlockEntities;
import net.eclipce.transpondersnails.item.BlackTransponderSnailItem;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Black Transponder Snail Block - Placeable interception snail
 *
 * Features:
 * - Open/Close states for interception control
 * - Dyeable shell (16 colors)
 * - Connect to lightning rod stacks via wire for range extension
 * - Range calculation: base + (rod_count × 5), capped at max
 * - Audio plays from block location (locational audio)
 * - All visual states from item version (idle, sound, call, active)
 */
public class BlackTransponderSnailBlock extends Block implements EntityBlock {

    // Block state properties
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final IntegerProperty SHELL_COLOR = IntegerProperty.create("shell_color", 0, 15);

    // Call state for visual feedback (matches item predicates)
    // 0 = idle, 1 = sound (searching), 2 = call (intercepting), 3 = active (audio)
    public static final IntegerProperty CALL_STATE = IntegerProperty.create("call_state", 0, 3);

    // VoxelShape for the snail block (similar to transponder snail)
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 8.0D, 14.0D);

    // Range constants
    public static final int RANGE_PER_LIGHTNING_ROD = 5;
    public static final int MAX_LIGHTNING_ROD_STACK = 60; // Max 60 rods = 300 extra blocks

    public BlackTransponderSnailBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(SHELL_COLOR, DyeColor.YELLOW.getId()) // Default yellow
                .setValue(CALL_STATE, 0) // Idle
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, SHELL_COLOR, CALL_STATE);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();

        // Get shell color from item NBT
        ItemStack stack = context.getItemInHand();
        int shellColor = DyeColor.YELLOW.getId(); // Default

        if (stack.hasTag()) {
            CompoundTag tag = stack.getTag();
            if (tag.contains("shell_color")) {
                shellColor = tag.getInt("shell_color");
            }
        }

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(OPEN, false)
                .setValue(SHELL_COLOR, shellColor)
                .setValue(CALL_STATE, 0);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
                // Transfer NBT data from item to block entity
                if (stack.hasTag()) {
                    CompoundTag tag = stack.getTag();
                    if (tag.contains("shell_color")) {
                        snailBE.setShellColor(tag.getInt("shell_color"));
                    }
                }

                // Initialize the antenna connection
                snailBE.updateAntennaConnection();
            }
        }
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                          @NotNull Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        // Check for dye application
        if (heldItem.getItem() instanceof DyeItem dyeItem) {
            if (!level.isClientSide) {
                DyeColor newColor = dyeItem.getDyeColor();
                level.setBlock(pos, state.setValue(SHELL_COLOR, newColor.getId()), 3);

                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
                    snailBE.setShellColor(newColor.getId());
                }

                // Consume dye in survival mode
                if (!player.getAbilities().instabuild) {
                    heldItem.shrink(1);
                }

                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);

                player.displayClientMessage(
                        Component.literal("Shell dyed " + newColor.getName()).withStyle(ChatFormatting.GREEN),
                        true
                );
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // Toggle open/close state (right-click without dye)
        if (!level.isClientSide) {
            boolean newOpenState = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, newOpenState), 3);

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
                snailBE.setOpen(newOpenState);

                if (player instanceof ServerPlayer serverPlayer) {
                    if (newOpenState) {
                        // Opening - start interception
                        snailBE.startInterception(serverPlayer);

                        // Display range info
                        int range = snailBE.calculateRange();
                        int rodCount = snailBE.countLightningRods();
                        String rangeMessage = "Interception started - Range: " + range + " blocks";
                        if (rodCount > 0) {
                            rangeMessage += " (+" + rodCount + " lightning rods)";
                        }
                        player.displayClientMessage(
                                Component.literal(rangeMessage).withStyle(ChatFormatting.GREEN),
                                true
                        );
                    } else {
                        // Closing - stop interception
                        snailBE.stopInterception();
                        player.displayClientMessage(
                                Component.literal("Interception stopped").withStyle(ChatFormatting.GRAY),
                                true
                        );
                    }
                }
            }

            // Play sound
            level.playSound(null, pos,
                    newOpenState ? SoundEvents.SHULKER_OPEN : SoundEvents.SHULKER_CLOSE,
                    SoundSource.BLOCKS, 0.5F, 1.2F);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                                @NotNull Block block, @NotNull BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
                // Update antenna connection when neighbors change
                snailBE.updateAntennaConnection();
            }
        }
    }

    @Override
    public @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction,
                                           @NotNull BlockState neighborState, @NotNull LevelAccessor level,
                                           @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        // Schedule antenna recalculation
        if (!level.isClientSide() && level instanceof Level realLevel) {
            realLevel.scheduleTick(pos, this, 1);
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
            snailBE.updateAntennaConnection();
        }
    }

    // =================== Block Entity ===================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BlackTransponderSnailBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state,
                                                                  @NotNull BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.BLACK_TRANSPONDER_SNAIL_BE.get(),
                BlackTransponderSnailBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> serverType, BlockEntityType<E> clientType, BlockEntityTicker<? super E> ticker) {
        return clientType == serverType ? (BlockEntityTicker<A>) ticker : null;
    }

    // =================== Drops ===================

    @Override
    public @NotNull List<ItemStack> getDrops(@NotNull BlockState state, LootParams.@NotNull Builder builder) {
        // Create item with preserved NBT
        ItemStack drop = new ItemStack(ModItems.BLACK_TRANSPONDER_SNAIL.get());

        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
            CompoundTag tag = drop.getOrCreateTag();
            tag.putInt("shell_color", snailBE.getShellColor());
            tag.putBoolean("is_open", false); // Always drop closed
        } else {
            // Fallback to block state
            CompoundTag tag = drop.getOrCreateTag();
            tag.putInt("shell_color", state.getValue(SHELL_COLOR));
            tag.putBoolean("is_open", false);
        }

        return List.of(drop);
    }

    @Override
    public void playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Player player) {
        // Stop interception when block is broken
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
                snailBE.stopInterception();
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                         @NotNull BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BlackTransponderSnailBlockEntity snailBE) {
                snailBE.stopInterception();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // =================== Static Helpers ===================

    /**
     * Count lightning rods stacked vertically above a position
     * Stops counting if blocked within 3 blocks above the top rod
     *
     * @param level The level
     * @param basePos The position of the bottom lightning rod
     * @return Number of valid lightning rods, or 0 if connection failed
     */
    public static int countLightningRodStack(Level level, BlockPos basePos) {
        int count = 0;
        BlockPos checkPos = basePos;

        // First, verify the base position is a lightning rod
        if (!(level.getBlockState(checkPos).getBlock() instanceof LightningRodBlock)) {
            return 0;
        }

        // Count upward
        while (count < MAX_LIGHTNING_ROD_STACK) {
            BlockState state = level.getBlockState(checkPos);

            if (state.getBlock() instanceof LightningRodBlock) {
                count++;
                checkPos = checkPos.above();
            } else {
                break;
            }
        }

        // Validate: check 0-3 blocks above top rod are not blocking
        BlockPos topRodPos = basePos.above(count - 1);
        if (!validateAntennaTop(level, topRodPos)) {
            return 0; // Connection failed - blocked
        }

        // Validate: check if top rod has sky access (not underground)
        if (!hasSkylightAccess(level, topRodPos)) {
            return 0; // Connection failed - underground
        }

        return count;
    }

    /**
     * Validate that the top of the antenna is not blocked
     * Checks 0-3 blocks above the top lightning rod
     */
    private static boolean validateAntennaTop(Level level, BlockPos topRodPos) {
        for (int i = 1; i <= 3; i++) {
            BlockPos checkPos = topRodPos.above(i);
            BlockState state = level.getBlockState(checkPos);

            // Air and non-solid blocks are OK
            if (state.isAir() || !state.isSolidRender(level, checkPos)) {
                continue;
            }

            // Solid block found - antenna is blocked
            return false;
        }
        return true;
    }

    /**
     * Check if position has access to skylight (not underground)
     */
    private static boolean hasSkylightAccess(Level level, BlockPos pos) {
        // Check if there's a direct path to sky
        return level.canSeeSky(pos.above());
    }

    /**
     * Calculate range based on lightning rod count
     *
     * @param baseRange Base range from config
     * @param lightningRodCount Number of stacked lightning rods
     * @param maxRange Maximum allowed range
     * @return Calculated range in blocks
     */
    public static int calculateRange(double baseRange, int lightningRodCount, double maxRange) {
        double extraRange = lightningRodCount * RANGE_PER_LIGHTNING_ROD;
        return (int) Math.min(baseRange + extraRange, maxRange);
    }
}