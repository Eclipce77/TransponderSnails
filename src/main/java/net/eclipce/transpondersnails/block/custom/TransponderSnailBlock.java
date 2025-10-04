package net.eclipce.transpondersnails.block.custom;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.entity.ModBlockEntities;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TransponderSnailBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Block properties for visual states
    public static final BooleanProperty HAS_SOUND = BooleanProperty.create("has_sound");
    public static final BooleanProperty IN_CALL = BooleanProperty.create("in_call");
    public static final IntegerProperty SHELL_COLOR = IntegerProperty.create("shell_color", 0, 15);

    // Define shapes for each direction manually
    // Original NORTH: (4, 0, 2) to (12, 10.5, 15)
    private static final VoxelShape SHAPE_NORTH = Block.box(4, 0, 2, 12, 10.5, 15);
    private static final VoxelShape SHAPE_SOUTH = Block.box(4, 0, 1, 12, 10.5, 14);  // 180° rotation
    private static final VoxelShape SHAPE_EAST = Block.box(1, 0, 4, 14, 10.5, 12);   // 90° rotation
    private static final VoxelShape SHAPE_WEST = Block.box(2, 0, 4, 15, 10.5, 12);   // 270° rotation

    // Create map for easy lookup
    private static final Map<Direction, VoxelShape> SHAPES = new HashMap<>();

    static {
        SHAPES.put(Direction.NORTH, SHAPE_NORTH);
        SHAPES.put(Direction.SOUTH, SHAPE_SOUTH);
        SHAPES.put(Direction.EAST, SHAPE_EAST);
        SHAPES.put(Direction.WEST, SHAPE_WEST);
    }

    public TransponderSnailBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_SOUND, false)
                .setValue(IN_CALL, false)
                .setValue(SHELL_COLOR, 0));
    }

    /**
     * Get the shape for the given direction
     */
    private static VoxelShape getShapeForDirection(Direction direction) {
        return SHAPES.getOrDefault(direction, SHAPE_NORTH);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        Direction facing = pState.getValue(FACING);
        return getShapeForDirection(facing);
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        Direction facing = pState.getValue(FACING);
        return getShapeForDirection(facing);
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_SOUND, IN_CALL, SHELL_COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Face the player when placed
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        System.out.println("TransponderSnailBlock.use() called - Hand: " + hand +
                ", ClientSide: " + level.isClientSide +
                ", HeldItem: " + player.getItemInHand(hand).getItem());

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (hand != InteractionHand.MAIN_HAND) {
            System.out.println("Returning PASS - not main hand");
            return InteractionResult.PASS;
        }

        ItemStack itemStack = player.getItemInHand(hand);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (blockEntity instanceof TransponderSnailBlockEntity snailEntity && player instanceof ServerPlayer serverPlayer) {

            // Check for dye interaction FIRST
            if (itemStack.getItem() instanceof DyeItem dyeItem) {
                DyeColor dyeColor = dyeItem.getDyeColor();
                int newShellColor = dyeColor.getId();
                int currentShellColor = state.getValue(SHELL_COLOR);

                if (newShellColor != currentShellColor) {
                    // Ensure colors are initialized
                    if (!snailEntity.isColorsInitialized()) {
                        snailEntity.ensureColorsInitialized();
                    }

                    // Update blockstate
                    BlockState newState = state.setValue(SHELL_COLOR, newShellColor);
                    level.setBlock(pos, newState, Block.UPDATE_ALL);

                    // Update block entity and notify player
                    snailEntity.setShellColor(newShellColor);
                    serverPlayer.sendSystemMessage(Component.literal("Transponder Snail shell dyed " +
                                    dyeColor.getName().replace("_", " ") + "!")
                            .withStyle(net.minecraft.ChatFormatting.GREEN));

                    if (!player.isCreative()) {
                        itemStack.shrink(1);
                    }
                    return InteractionResult.SUCCESS;
                } else {
                    serverPlayer.sendSystemMessage(Component.literal("Transponder Snail is already " +
                                    dyeColor.getName().replace("_", " ") + "!")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW));
                    return InteractionResult.FAIL;
                }
            }

            // NOT a dye - proceed with normal interaction (GUI, calls, etc.)
            boolean isSneaking = player.isShiftKeyDown();
            return snailEntity.onPlayerInteraction(serverPlayer, isSneaking);
        }

        return InteractionResult.FAIL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
        return new TransponderSnailBlockEntity(pPos, pState);
    }

    private BlockEntityType<TransponderSnailBlockEntity> getBlockEntityType() {
        BlockEntityType<TransponderSnailBlockEntity> type = ModBlockEntities.TRANSPONDER_SNAIL_BE.get();
        if (type == null) { // This should be a null check, not using the type directly as boolean
            throw new RuntimeException("BlockEntityType not implemented - register in your mod initialization");
        }
        return type;
    }

    // If you're checking if the BlockEntityType exists, use this pattern:
    public boolean hasBlockEntityType() {
        return ModBlockEntities.TRANSPONDER_SNAIL_BE.get() != null;
    }

    // If you're in a conditional and need to check the type:
    public void someMethod() {
        BlockEntityType<TransponderSnailBlockEntity> type = getBlockEntityType();
        if (type != null) { // Check the type variable, not the method return
            // Do something with the type
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level pLevel, BlockState pState, BlockEntityType<T> pBlockEntityType) {
        return null;
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (pState.is(pNewState.getBlock())) {
            BlockEntity blockEntity = pLevel.getBlockEntity(pPos);
            if (blockEntity instanceof TransponderSnailBlockEntity snailBlockEntity) {
                UUID activeCall = snailBlockEntity.getActiveCallId();
                if (activeCall != null) {
                    // Handle active call cleanup if needed
                }
            }
        }

        super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
    }

    /**
     * Called when the block is placed - restore data from item if available
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        System.out.println("=== setPlacedBy START ===");
        System.out.println("Item NBT: " + (stack.hasTag() ? stack.getTag() : "NONE"));

        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TransponderSnailBlockEntity snailBE) {
                System.out.println("BEFORE loadFromItem - UUID: " + snailBE.snailUUID + ", Number: " + snailBE.assignedSnailNumber);

                snailBE.loadFromItem(stack);

                System.out.println("AFTER loadFromItem - UUID: " + snailBE.snailUUID + ", Number: " + snailBE.assignedSnailNumber);

                CompoundTag nbt = stack.getTag();
                int shellColor = 0;

                if (nbt != null && nbt.contains("body_color")) {
                    snailBE.bodyColor = nbt.getInt("body_color");
                    shellColor = nbt.getInt("shell_color");
                    snailBE.colorsInitialized = true;
                } else {
                    snailBE.ensureColorsInitialized();
                }

                BlockState newState = level.getBlockState(pos).setValue(SHELL_COLOR, shellColor);
                level.setBlock(pos, newState, Block.UPDATE_ALL);

                snailBE.setChanged();

                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.getChunkSource().blockChanged(pos);
                }
                level.sendBlockUpdated(pos, state, newState, Block.UPDATE_ALL_IMMEDIATE);

                System.out.println("FINAL - UUID: " + snailBE.snailUUID + ", Number: " + snailBE.assignedSnailNumber + ", Initialized: " + snailBE.initialized);
            }
        }
        System.out.println("=== setPlacedBy END ===");
    }

    /**
     * Get the shell variant model name based on shell color
     */
    public static String getShellVariantModel(int shellColor) {
        DyeColor dyeColor = DyeColor.byId(shellColor);
        return "transponder_snail_shell_" + dyeColor.getName();
    }

    // =================== NEW: BLOCK STATE MANAGEMENT FOR VISUAL UPDATES ===================

    /**
     * Updates the visual state of the block based on sound and call status
     * This is the method that your TransponderSnailBlockEntity calls
     */
    public static void updateVisualState(Level level, BlockPos pos, boolean hasSound, boolean inCall) {
        if (level.isClientSide()) {
            return; // Only update on server side
        }

        BlockState currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof TransponderSnailBlock)) {
            return;
        }

        // Check if state actually needs to change
        boolean currentHasSound = currentState.getValue(HAS_SOUND);
        boolean currentInCall = currentState.getValue(IN_CALL);

        if (currentHasSound != hasSound || currentInCall != inCall) {
            BlockState newState = currentState
                    .setValue(HAS_SOUND, hasSound)
                    .setValue(IN_CALL, inCall);

            // Update the block state - this will trigger a render update
            level.setBlock(pos, newState, Block.UPDATE_ALL);

            System.out.println("TransponderSnailBlock: Updated visual state at " + pos +
                    " - Sound: " + hasSound + ", Call: " + inCall);
        }
    }

    /**
     * Convenience method to update only sound state
     */
    public static void updateSoundState(Level level, BlockPos pos, boolean hasSound) {
        BlockState currentState = level.getBlockState(pos);
        if (currentState.getBlock() instanceof TransponderSnailBlock) {
            boolean currentInCall = currentState.getValue(IN_CALL);
            updateVisualState(level, pos, hasSound, currentInCall);
        }
    }

    /**
     * Convenience method to update only call state
     */
    public static void updateCallState(Level level, BlockPos pos, boolean inCall) {
        BlockState currentState = level.getBlockState(pos);
        if (currentState.getBlock() instanceof TransponderSnailBlock) {
            boolean currentHasSound = currentState.getValue(HAS_SOUND);
            updateVisualState(level, pos, currentHasSound, inCall);
        }
    }

    /**
     * Gets the current visual state for debugging
     */
    public static String getVisualStateString(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TransponderSnailBlock)) {
            return "Not a Transponder Snail block";
        }

        boolean hasSound = state.getValue(HAS_SOUND);
        boolean inCall = state.getValue(IN_CALL);
        Direction facing = state.getValue(FACING);

        return String.format("Sound: %s, Call: %s, Facing: %s", hasSound, inCall, facing);
    }

    /**
     * Check if block currently has the "sound" visual state
     */
    public static boolean hasVisualSoundState(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TransponderSnailBlock) {
            return state.getValue(HAS_SOUND);
        }
        return false;
    }

    /**
     * Check if block currently has the "in call" visual state
     */
    public static boolean hasVisualCallState(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TransponderSnailBlock) {
            return state.getValue(IN_CALL);
        }
        return false;
    }

    /**
     * Updates the shell color blockstate
     */
    public static void updateShellColor(Level level, BlockPos pos, int shellColor) {
        if (level.isClientSide()) {
            return;
        }

        BlockState currentState = level.getBlockState(pos);
        if (!(currentState.getBlock() instanceof TransponderSnailBlock)) {
            return;
        }
    }

    // =================== DEBUG METHODS ===================

    /**
     * Debug method to print hitbox information for all directions
     */
    public static void debugPrintHitboxes() {
        System.out.println("=== TransponderSnail Hitbox Debug ===");
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = getShapeForDirection(dir);
            System.out.println(dir + ": " + shape.bounds());
        }
        System.out.println("=====================================");
    }
}