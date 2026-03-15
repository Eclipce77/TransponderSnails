package net.eclipce.transpondersnails.block.custom;

import net.eclipce.transpondersnails.block.entity.ModBlockEntities;
import net.eclipce.transpondersnails.block.entity.WhiteTransponderSnailBlockEntity;
import net.eclipce.transpondersnails.voice.server.WhiteSnailProtectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * White Transponder Snail Block - Provides call interception protection
 *
 * REWRITTEN STATE SYSTEM:
 * - Shell color is stored in BlockEntity (not blockstate) to avoid variant explosion
 * - Blockstate only contains: facing, snail_state, wire_left, wire_right
 * - Total variants: 4 directions × 3 states × 4 wire combos = 48 (manageable!)
 *
 * Visual States (snail_state property):
 * - IDLE (0): Default state, not protecting any call
 * - CONNECTED (1): Currently protecting an active call
 * - BLOCKING (2): Actively blocking an interception attempt
 *
 * Wire Connection States:
 * - wire_left: True if connected to wire on left side (when viewing from front)
 * - wire_right: True if connected to wire on right side (when viewing from front)
 */
public class WhiteTransponderSnailBlock extends HorizontalDirectionalBlock implements EntityBlock {

    // Blockstate properties (NO shell_color - that's in the BE now!)
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty SNAIL_STATE = IntegerProperty.create("snail_state", 0, 2);
    public static final BooleanProperty WIRE_LEFT = BooleanProperty.create("wire_left");
    public static final BooleanProperty WIRE_RIGHT = BooleanProperty.create("wire_right");

    // Snail state constants for readability
    public static final int STATE_IDLE = 0;
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_BLOCKING = 2;

    // Voxel shapes for the snail (same as regular transponder snail)
    private static final VoxelShape SHAPE_NORTH = Block.box(3.5, 0, 0, 12.5, 11, 15);
    private static final VoxelShape SHAPE_SOUTH = Block.box(3.5, 0, 0, 12.5, 11, 15);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 0, 3.5, 15, 11, 12.5);
    private static final VoxelShape SHAPE_WEST = Block.box(0, 0, 3.5, 15, 11, 12.5);

    public WhiteTransponderSnailBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SNAIL_STATE, STATE_IDLE)
                .setValue(WIRE_LEFT, false)
                .setValue(WIRE_RIGHT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Only these 4 properties - NO shell_color!
        builder.add(FACING, SNAIL_STATE, WIRE_LEFT, WIRE_RIGHT);
    }

    // =================== BLOCK ENTITY ===================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WhiteTransponderSnailBlockEntity(pos, state);
    }

    // =================== PLACEMENT & SHAPE ===================

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();

        // Check for wire connections on placement
        boolean wireLeft = checkWireConnection(level, pos, facing, true);
        boolean wireRight = checkWireConnection(level, pos, facing, false);

        return this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(SNAIL_STATE, STATE_IDLE)
                .setValue(WIRE_LEFT, wireLeft)
                .setValue(WIRE_RIGHT, wireRight);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        
        // Copy shell color from item NBT to block entity
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WhiteTransponderSnailBlockEntity whiteBE) {
                CompoundTag tag = stack.getTag();
                if (tag != null) {
                    // Check for shell_color in item NBT
                    if (tag.contains("shell_color")) {
                        whiteBE.setShellColorId(tag.getInt("shell_color"));
                    } else if (tag.contains("BlockEntityTag")) {
                        // Also check BlockEntityTag (for items created from breaking)
                        CompoundTag beTag = tag.getCompound("BlockEntityTag");
                        if (beTag.contains("ShellColor")) {
                            whiteBE.setShellColorId(beTag.getInt("ShellColor"));
                        }
                    }
                }
            }
            
            // ✨ Notify protection manager of White Snail placement
            WhiteSnailProtectionManager.getInstance().onWhiteSnailChanged(level, pos, true);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // ✨ Notify protection manager BEFORE removal (only if actually being removed, not replaced)
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            WhiteSnailProtectionManager.getInstance().onWhiteSnailChanged(level, pos, false);
        }
        
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    // =================== INTERACTIONS ===================

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        // Handle dyeing the shell
        if (heldItem.getItem() instanceof DyeItem dyeItem) {
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof WhiteTransponderSnailBlockEntity whiteBE) {
                    DyeColor color = dyeItem.getDyeColor();
                    whiteBE.setShellColor(color);

                    level.playSound(null, pos, SoundEvents.DYE_USE,
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                    
                    System.out.println("WhiteTransponderSnailBlock: Dyed shell to " + color.getName() + 
                            " at " + pos);

                    // Consume dye in survival mode
                    if (!player.isCreative()) {
                        heldItem.shrink(1);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    // =================== WIRE CONNECTIONS ===================

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Only update wire connections on horizontal neighbor changes
        if (direction.getAxis().isHorizontal() && level instanceof Level realLevel) {
            Direction facing = state.getValue(FACING);
            boolean wireLeft = checkWireConnection(realLevel, pos, facing, true);
            boolean wireRight = checkWireConnection(realLevel, pos, facing, false);

            if (wireLeft != state.getValue(WIRE_LEFT) || wireRight != state.getValue(WIRE_RIGHT)) {
                return state.setValue(WIRE_LEFT, wireLeft).setValue(WIRE_RIGHT, wireRight);
            }
        }
        return state;
    }

    /**
     * Check if there's a wire connection on the specified side
     * @param level The level
     * @param pos The snail's position
     * @param facing The direction the snail is facing
     * @param checkLeft True to check left side, false to check right side
     * @return True if a wire is connected on that side
     */
    private boolean checkWireConnection(Level level, BlockPos pos, Direction facing, boolean checkLeft) {
        // Determine which direction is "left" or "right" based on facing
        Direction checkDir;
        if (checkLeft) {
            checkDir = facing.getCounterClockWise();
        } else {
            checkDir = facing.getClockWise();
        }

        BlockPos neighborPos = pos.relative(checkDir);
        BlockState neighborState = level.getBlockState(neighborPos);

        // Check if neighbor is a wire block
        return neighborState.getBlock() instanceof WireBlock;
    }

    /**
     * Update wire connection states (call after wire network changes)
     */
    public static void updateWireConnections(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WhiteTransponderSnailBlock block) {
            Direction facing = state.getValue(FACING);
            boolean wireLeft = block.checkWireConnection(level, pos, facing, true);
            boolean wireRight = block.checkWireConnection(level, pos, facing, false);

            if (wireLeft != state.getValue(WIRE_LEFT) || wireRight != state.getValue(WIRE_RIGHT)) {
                level.setBlock(pos, state
                        .setValue(WIRE_LEFT, wireLeft)
                        .setValue(WIRE_RIGHT, wireRight), 3);
                System.out.println("WhiteTransponderSnailBlock: Updated wire connections at " + pos +
                        " (left=" + wireLeft + ", right=" + wireRight + ")");
            }
        }
    }

    // =================== SNAIL STATE MANAGEMENT ===================

    /**
     * Set the snail to CONNECTED state (protecting a call)
     */
    public static void setConnectedState(Level level, BlockPos pos) {
        setSnailState(level, pos, STATE_CONNECTED);
    }

    /**
     * Set the snail to BLOCKING state (actively blocking interception)
     */
    public static void setBlockingState(Level level, BlockPos pos) {
        setSnailState(level, pos, STATE_BLOCKING);
    }

    /**
     * Set the snail back to IDLE state
     */
    public static void setIdleState(Level level, BlockPos pos) {
        setSnailState(level, pos, STATE_IDLE);
    }

    /**
     * Set the snail state by integer value
     * @param level The level
     * @param pos The block position
     * @param newState The new state (0=IDLE, 1=CONNECTED, 2=BLOCKING)
     */
    public static void setSnailState(Level level, BlockPos pos, int newState) {
        if (newState < STATE_IDLE || newState > STATE_BLOCKING) {
            System.err.println("WhiteTransponderSnailBlock: Invalid state " + newState);
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WhiteTransponderSnailBlock) {
            int currentState = state.getValue(SNAIL_STATE);
            if (currentState != newState) {
                level.setBlock(pos, state.setValue(SNAIL_STATE, newState), 3);
                System.out.println("WhiteTransponderSnailBlock: Set " + getStateName(newState) + " state at " + pos);
            }
        }
    }

    /**
     * Get the current snail state
     */
    public static int getSnailState(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WhiteTransponderSnailBlock) {
            return state.getValue(SNAIL_STATE);
        }
        return STATE_IDLE;
    }

    /**
     * Get a human-readable name for a snail state
     */
    public static String getStateName(int state) {
        switch (state) {
            case STATE_IDLE: return "IDLE";
            case STATE_CONNECTED: return "CONNECTED";
            case STATE_BLOCKING: return "BLOCKING";
            default: return "UNKNOWN(" + state + ")";
        }
    }

    // =================== DROPS ===================

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        
        // Get the block entity to preserve shell color
        BlockEntity be = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof WhiteTransponderSnailBlockEntity whiteBE) {
            for (ItemStack stack : drops) {
                if (stack.is(this.asItem())) {
                    // Save shell color to item NBT
                    CompoundTag tag = stack.getOrCreateTag();
                    tag.putInt("shell_color", whiteBE.getShellColorId());
                    
                    // Also save in BlockEntityTag for consistency
                    CompoundTag beTag = new CompoundTag();
                    beTag.putInt("ShellColor", whiteBE.getShellColorId());
                    tag.put("BlockEntityTag", beTag);
                }
            }
        }
        
        return drops;
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        
        // Copy shell color to picked item
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof WhiteTransponderSnailBlockEntity whiteBE) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt("shell_color", whiteBE.getShellColorId());
        }
        
        return stack;
    }

    // =================== RENDERING ===================

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
