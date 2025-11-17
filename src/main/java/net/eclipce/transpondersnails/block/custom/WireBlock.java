package net.eclipce.transpondersnails.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Placeable wire block for connecting Transponder Snails
 * Similar to tripwire but without triggering mechanics
 */
public class WireBlock extends Block {

    // Connection properties for each cardinal direction
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    // Collision shape - very thin and flat like tripwire
    private static final VoxelShape SHAPE = Block.box(0.0D, 1.0D, 0.0D, 16.0D, 2.5D, 16.0D);

    public WireBlock(Properties properties) {
        super(properties);
        // Default state: no connections
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @NotNull VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return Shapes.empty(); // No collision like tripwire
    }

    /**
     * Gets the block state for placement
     * Automatically connects to adjacent wire blocks
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        return this.defaultBlockState()
                .setValue(NORTH, canConnectTo(level.getBlockState(pos.north())))
                .setValue(SOUTH, canConnectTo(level.getBlockState(pos.south())))
                .setValue(EAST, canConnectTo(level.getBlockState(pos.east())))
                .setValue(WEST, canConnectTo(level.getBlockState(pos.west())));
    }

    /**
     * Updates the block when a neighbor changes
     * Recalculates connections
     */
    @Override
    public @NotNull BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighborState,
                                           @NotNull LevelAccessor level, @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        // Only update horizontal connections (no vertical)
        if (direction.getAxis().isHorizontal()) {
            BooleanProperty property = getPropertyForDirection(direction);
            return state.setValue(property, canConnectTo(neighborState));
        }

        return state;
    }

    /**
     * Determines if this wire can connect to the given block state
     */
    private boolean canConnectTo(BlockState state) {
        // Connect to other wire blocks
        if (state.getBlock() instanceof WireBlock) {
            return true;
        }

        // TODO: Add connection to Transponder Snail blocks when implemented
        // if (state.getBlock() instanceof BlackTransponderSnailBlock) return true;
        // if (state.getBlock() instanceof WhiteTransponderSnailBlock) return true;

        return false;
    }

    /**
     * Gets the block state property for a given direction
     */
    private BooleanProperty getPropertyForDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Invalid direction: " + direction);
        };
    }

    /**
     * Makes the wire render as a cutout (transparent)
     */
    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }

    /**
     * Wire should not be solid
     */
    @Override
    public boolean isCollisionShapeFullBlock(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return false;
    }
}