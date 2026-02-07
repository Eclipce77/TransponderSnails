package net.eclipce.transpondersnails.block.custom;

import net.eclipce.transpondersnails.block.ModBlocks;
import net.eclipce.transpondersnails.voice.server.WhiteSnailProtectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Wire block for connecting Black Transponder Snails to Lightning Rods.
 * Similar to redstone placement but no power/signal - just connectivity.
 *
 * Features:
 * - Default straight-line appearance based on player facing direction
 * - Vertical connections - wire can climb up blocks AND slabs
 * - Instant texture updates when neighbors change
 * - Compatible with bottom slabs (wire sits ON TOP of slab surface)
 * - Connects to wire, lightning rods, transponder snails
 */
public class WireBlock extends Block {

    // Maximum distance wire can trace to find lightning rods
    public static final int MAX_WIRE_RANGE = 64;

    // Horizontal connection properties
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");

    // Vertical connection properties - wire going UP on the side of blocks
    public static final BooleanProperty UP_NORTH = BooleanProperty.create("up_north");
    public static final BooleanProperty UP_SOUTH = BooleanProperty.create("up_south");
    public static final BooleanProperty UP_EAST = BooleanProperty.create("up_east");
    public static final BooleanProperty UP_WEST = BooleanProperty.create("up_west");

    // Default axis - tracks which direction wire was placed in for isolated wires
    public static final EnumProperty<WireAxis> AXIS = EnumProperty.create("axis", WireAxis.class);

    // =================== SHAPES ===================

    // Standard floor wire (on solid blocks or top slabs)
    private static final VoxelShape SHAPE_FLOOR = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    // Wire placed ABOVE a bottom slab - sits on slab surface
    // Negative Y extends DOWN into the block below
    private static final VoxelShape SHAPE_ON_SLAB = Block.box(0.0D, -8.0D, 0.0D, 16.0D, -7.0D, 16.0D);

    // Vertical side shapes for climbing up FULL BLOCKS (from floor level Y=0 to Y=16)
    private static final VoxelShape SHAPE_UP_NORTH_FULL = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 1.0D);
    private static final VoxelShape SHAPE_UP_SOUTH_FULL = Block.box(6.0D, 0.0D, 15.0D, 10.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_UP_EAST_FULL = Block.box(15.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape SHAPE_UP_WEST_FULL = Block.box(0.0D, 0.0D, 6.0D, 1.0D, 16.0D, 10.0D);

    // Vertical side shapes for climbing from SLAB level (from Y=-8 to Y=16)
    private static final VoxelShape SHAPE_UP_NORTH_FROM_SLAB = Block.box(6.0D, -8.0D, 0.0D, 10.0D, 16.0D, 1.0D);
    private static final VoxelShape SHAPE_UP_SOUTH_FROM_SLAB = Block.box(6.0D, -8.0D, 15.0D, 10.0D, 16.0D, 16.0D);
    private static final VoxelShape SHAPE_UP_EAST_FROM_SLAB = Block.box(15.0D, -8.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    private static final VoxelShape SHAPE_UP_WEST_FROM_SLAB = Block.box(0.0D, -8.0D, 6.0D, 1.0D, 16.0D, 10.0D);

    // Vertical side shapes for climbing TO a slab (from Y=0 to Y=8 - half height)
    private static final VoxelShape SHAPE_UP_NORTH_TO_SLAB = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 8.0D, 1.0D);
    private static final VoxelShape SHAPE_UP_SOUTH_TO_SLAB = Block.box(6.0D, 0.0D, 15.0D, 10.0D, 8.0D, 16.0D);
    private static final VoxelShape SHAPE_UP_EAST_TO_SLAB = Block.box(15.0D, 0.0D, 6.0D, 16.0D, 8.0D, 10.0D);
    private static final VoxelShape SHAPE_UP_WEST_TO_SLAB = Block.box(0.0D, 0.0D, 6.0D, 1.0D, 8.0D, 10.0D);

    public WireBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP_NORTH, false)
                .setValue(UP_SOUTH, false)
                .setValue(UP_EAST, false)
                .setValue(UP_WEST, false)
                .setValue(AXIS, WireAxis.EW));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP_NORTH, UP_SOUTH, UP_EAST, UP_WEST, AXIS);
    }

    // =================== AXIS ENUM ===================

    public enum WireAxis implements net.minecraft.util.StringRepresentable {
        EW("ew"),   // East-West axis
        NS("ns");   // North-South axis

        private final String name;

        WireAxis(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }

        public static WireAxis fromPlayerFacing(Direction facing) {
            return (facing == Direction.NORTH || facing == Direction.SOUTH) ? EW : NS;
        }
    }

    // =================== SHAPE ===================

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Check if we're above a bottom slab
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        boolean onSlab = isBottomSlab(belowState);

        VoxelShape baseShape = onSlab ? SHAPE_ON_SLAB : SHAPE_FLOOR;

        // Add vertical shapes for UP connections
        if (state.getValue(UP_NORTH)) {
            baseShape = Shapes.or(baseShape, getUpShape(level, pos, Direction.NORTH, onSlab));
        }
        if (state.getValue(UP_SOUTH)) {
            baseShape = Shapes.or(baseShape, getUpShape(level, pos, Direction.SOUTH, onSlab));
        }
        if (state.getValue(UP_EAST)) {
            baseShape = Shapes.or(baseShape, getUpShape(level, pos, Direction.EAST, onSlab));
        }
        if (state.getValue(UP_WEST)) {
            baseShape = Shapes.or(baseShape, getUpShape(level, pos, Direction.WEST, onSlab));
        }

        return baseShape;
    }

    /**
     * Get the appropriate UP shape based on:
     * - Whether we're starting from a slab (onSlab)
     * - Whether we're connecting TO a slab (neighbor is bottom slab with wire above)
     */
    private VoxelShape getUpShape(BlockGetter level, BlockPos pos, Direction direction, boolean onSlab) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);

        // Check if we're connecting to wire on a bottom slab (half height)
        boolean toSlab = isBottomSlab(neighborState);

        if (onSlab) {
            // Starting from slab level - full climb from -8 to 16
            return switch (direction) {
                case NORTH -> SHAPE_UP_NORTH_FROM_SLAB;
                case SOUTH -> SHAPE_UP_SOUTH_FROM_SLAB;
                case EAST -> SHAPE_UP_EAST_FROM_SLAB;
                case WEST -> SHAPE_UP_WEST_FROM_SLAB;
                default -> SHAPE_UP_NORTH_FROM_SLAB;
            };
        } else if (toSlab) {
            // Going up to a slab - half height (0 to 8)
            return switch (direction) {
                case NORTH -> SHAPE_UP_NORTH_TO_SLAB;
                case SOUTH -> SHAPE_UP_SOUTH_TO_SLAB;
                case EAST -> SHAPE_UP_EAST_TO_SLAB;
                case WEST -> SHAPE_UP_WEST_TO_SLAB;
                default -> SHAPE_UP_NORTH_TO_SLAB;
            };
        } else {
            // Full block to full block - standard full climb
            return switch (direction) {
                case NORTH -> SHAPE_UP_NORTH_FULL;
                case SOUTH -> SHAPE_UP_SOUTH_FULL;
                case EAST -> SHAPE_UP_EAST_FULL;
                case WEST -> SHAPE_UP_WEST_FULL;
                default -> SHAPE_UP_NORTH_FULL;
            };
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // No collision - walk through like redstone
    }

    // =================== SLAB DETECTION ===================

    private boolean isBottomSlab(BlockState state) {
        if (state.getBlock() instanceof SlabBlock) {
            if (state.hasProperty(SlabBlock.TYPE)) {
                SlabType type = state.getValue(SlabBlock.TYPE);
                return type == SlabType.BOTTOM;
            }
        }
        return false;
    }

    private boolean isTopOrDoubleSlab(BlockState state) {
        if (state.getBlock() instanceof SlabBlock) {
            if (state.hasProperty(SlabBlock.TYPE)) {
                SlabType type = state.getValue(SlabBlock.TYPE);
                return type == SlabType.TOP || type == SlabType.DOUBLE;
            }
        }
        return false;
    }

    /**
     * Check if a block can be "climbed" - solid blocks OR top/double slabs
     */
    private boolean isClimbable(BlockState state, BlockGetter level, BlockPos pos) {
        // Full solid blocks
        if (state.isSolidRender(level, pos)) {
            return true;
        }
        // Top slabs and double slabs act as full blocks for climbing
        if (isTopOrDoubleSlab(state)) {
            return true;
        }
        return false;
    }

    // =================== PLACEMENT ===================

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();

        // Get horizontal connections
        boolean north = shouldConnectHorizontal(level, pos, Direction.NORTH);
        boolean south = shouldConnectHorizontal(level, pos, Direction.SOUTH);
        boolean east = shouldConnectHorizontal(level, pos, Direction.EAST);
        boolean west = shouldConnectHorizontal(level, pos, Direction.WEST);

        // Get vertical connections (going UP)
        boolean upNorth = shouldConnectUp(level, pos, Direction.NORTH);
        boolean upSouth = shouldConnectUp(level, pos, Direction.SOUTH);
        boolean upEast = shouldConnectUp(level, pos, Direction.EAST);
        boolean upWest = shouldConnectUp(level, pos, Direction.WEST);

        // Determine axis from player facing
        Direction playerFacing = context.getHorizontalDirection();
        WireAxis axis = WireAxis.fromPlayerFacing(playerFacing);

        // If no horizontal connections, apply default orientation based on axis
        if (!north && !south && !east && !west) {
            if (axis == WireAxis.EW) {
                east = true;
                west = true;
            } else {
                north = true;
                south = true;
            }
        }

        return this.defaultBlockState()
                .setValue(NORTH, north)
                .setValue(SOUTH, south)
                .setValue(EAST, east)
                .setValue(WEST, west)
                .setValue(UP_NORTH, upNorth)
                .setValue(UP_SOUTH, upSouth)
                .setValue(UP_EAST, upEast)
                .setValue(UP_WEST, upWest)
                .setValue(AXIS, axis);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);

        // Allow placement on bottom slabs
        if (isBottomSlab(belowState)) {
            return true;
        }

        // Allow placement on top slabs and double slabs
        if (isTopOrDoubleSlab(belowState)) {
            return true;
        }

        // Standard check - any block with sturdy top face
        return belowState.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return false;
    }

    // =================== UPDATES ===================

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Break if block below is removed
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        // Update connections based on direction
        if (direction.getAxis().isHorizontal()) {
            boolean newHorizontal = shouldConnectHorizontal(level, pos, direction);
            boolean newUp = shouldConnectUp(level, pos, direction);

            BlockState newState = state
                    .setValue(getHorizontalPropertyForDirection(direction), newHorizontal)
                    .setValue(getUpPropertyForDirection(direction), newUp);

            return applyDefaultOrientationIfNeeded(newState);
        }

        // If vertical direction changed (UP), update all up connections
        if (direction == Direction.UP) {
            BlockState newState = state
                    .setValue(UP_NORTH, shouldConnectUp(level, pos, Direction.NORTH))
                    .setValue(UP_SOUTH, shouldConnectUp(level, pos, Direction.SOUTH))
                    .setValue(UP_EAST, shouldConnectUp(level, pos, Direction.EAST))
                    .setValue(UP_WEST, shouldConnectUp(level, pos, Direction.WEST));
            return newState;
        }

        return state;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos neighborPos, boolean isMoving) {
        if (!level.isClientSide) {
            // Check if we should break
            if (!canSurvive(state, level, pos)) {
                dropResources(state, level, pos);
                level.removeBlock(pos, false);
                return;
            }

            // Update all connections
            boolean north = shouldConnectHorizontal(level, pos, Direction.NORTH);
            boolean south = shouldConnectHorizontal(level, pos, Direction.SOUTH);
            boolean east = shouldConnectHorizontal(level, pos, Direction.EAST);
            boolean west = shouldConnectHorizontal(level, pos, Direction.WEST);

            boolean upNorth = shouldConnectUp(level, pos, Direction.NORTH);
            boolean upSouth = shouldConnectUp(level, pos, Direction.SOUTH);
            boolean upEast = shouldConnectUp(level, pos, Direction.EAST);
            boolean upWest = shouldConnectUp(level, pos, Direction.WEST);

            BlockState newState = state
                    .setValue(NORTH, north)
                    .setValue(SOUTH, south)
                    .setValue(EAST, east)
                    .setValue(WEST, west)
                    .setValue(UP_NORTH, upNorth)
                    .setValue(UP_SOUTH, upSouth)
                    .setValue(UP_EAST, upEast)
                    .setValue(UP_WEST, upWest);

            // Apply default orientation if completely isolated
            newState = applyDefaultOrientationIfNeeded(newState);

            if (newState != state) {
                level.setBlock(pos, newState, 3);
            }
        }
    }

    private BlockState applyDefaultOrientationIfNeeded(BlockState state) {
        boolean north = state.getValue(NORTH);
        boolean south = state.getValue(SOUTH);
        boolean east = state.getValue(EAST);
        boolean west = state.getValue(WEST);

        // Check if any horizontal connections exist
        if (!north && !south && !east && !west) {
            WireAxis axis = state.getValue(AXIS);
            if (axis == WireAxis.EW) {
                return state.setValue(EAST, true).setValue(WEST, true);
            } else {
                return state.setValue(NORTH, true).setValue(SOUTH, true);
            }
        }

        return state;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            // ✨ Notify White Snail protection manager of wire network change
            WhiteSnailProtectionManager.getInstance().onWireNetworkChanged(level, pos);
            
            // Notify horizontal neighbors
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);

                if (neighborState.getBlock() instanceof WireBlock) {
                    level.neighborChanged(neighborPos, this, pos);
                }

                // Also notify wire above adjacent blocks (for down-going connections)
                BlockPos aboveNeighbor = neighborPos.above();
                BlockState aboveNeighborState = level.getBlockState(aboveNeighbor);
                if (aboveNeighborState.getBlock() instanceof WireBlock) {
                    level.neighborChanged(aboveNeighbor, this, pos);
                }
            }

            // Notify wire below (if we're on top of a block with wire next to it)
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos belowNeighbor = pos.below().relative(dir);
                BlockState belowNeighborState = level.getBlockState(belowNeighbor);
                if (belowNeighborState.getBlock() instanceof WireBlock) {
                    level.neighborChanged(belowNeighbor, this, pos);
                }
            }
        }
        super.onPlace(state, level, pos, oldState, isMoving);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            // ✨ Notify White Snail protection manager of wire network change
            WhiteSnailProtectionManager.getInstance().onWireNetworkChanged(level, pos);
            
            // Notify horizontal neighbors
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos neighborPos = pos.relative(dir);
                BlockState neighborState = level.getBlockState(neighborPos);

                if (neighborState.getBlock() instanceof WireBlock) {
                    level.neighborChanged(neighborPos, this, pos);
                }

                // Also notify wire above adjacent blocks
                BlockPos aboveNeighbor = neighborPos.above();
                BlockState aboveNeighborState = level.getBlockState(aboveNeighbor);
                if (aboveNeighborState.getBlock() instanceof WireBlock) {
                    level.neighborChanged(aboveNeighbor, this, pos);
                }
            }

            // Notify wire below
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos belowNeighbor = pos.below().relative(dir);
                BlockState belowNeighborState = level.getBlockState(belowNeighbor);
                if (belowNeighborState.getBlock() instanceof WireBlock) {
                    level.neighborChanged(belowNeighbor, this, pos);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // =================== CONNECTION LOGIC ===================

    /**
     * Check if wire should connect horizontally in the given direction
     */
    private boolean shouldConnectHorizontal(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);

        // Direct horizontal connection to connectable block
        if (canConnectTo(neighborState)) {
            return true;
        }

        // Check for wire going DOWN from above: if neighbor is climbable or bottom slab
        // and there's wire above it
        if (isClimbable(neighborState, level, neighborPos) || isBottomSlab(neighborState)) {
            BlockPos aboveNeighbor = neighborPos.above();
            BlockState aboveNeighborState = level.getBlockState(aboveNeighbor);
            if (aboveNeighborState.getBlock() instanceof WireBlock) {
                return true;
            }
        }

        // Check for wire at a lower level: if neighbor is air and there's connectable block below
        if (neighborState.isAir()) {
            BlockPos belowNeighbor = neighborPos.below();
            BlockState belowNeighborState = level.getBlockState(belowNeighbor);
            if (canConnectTo(belowNeighborState)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if wire should show vertical "going up" connection in the given direction.
     * This happens when there's a climbable block adjacent (solid block, top slab)
     * OR a bottom slab, and wire/rod on top of that.
     */
    private boolean shouldConnectUp(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);

        // Check if there's nothing blocking above us
        BlockPos abovePos = pos.above();
        BlockState aboveState = level.getBlockState(abovePos);
        if (aboveState.isSolidRender(level, abovePos)) {
            // Blocked above - can't go up
            return false;
        }

        // Case 1: Neighbor is climbable (solid block or top/double slab)
        if (isClimbable(neighborState, level, neighborPos)) {
            BlockPos aboveNeighbor = neighborPos.above();
            BlockState aboveNeighborState = level.getBlockState(aboveNeighbor);
            return canConnectTo(aboveNeighborState);
        }

        // Case 2: Neighbor is a bottom slab - wire can climb up to it
        if (isBottomSlab(neighborState)) {
            // Wire on a bottom slab is in the air block above the slab
            BlockPos aboveNeighbor = neighborPos.above();
            BlockState aboveNeighborState = level.getBlockState(aboveNeighbor);
            if (canConnectTo(aboveNeighborState)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if wire can connect to the given block.
     *
     * =====================================================
     * ADD NEW CONNECTABLE BLOCKS HERE:
     * Just add another check like:
     *   if (block == ModBlocks.YOUR_NEW_BLOCK.get()) return true;
     * =====================================================
     */
    private boolean canConnectTo(BlockState state) {
        Block block = state.getBlock();

        // =================== WIRE ===================
        // Connect to other wire
        if (block instanceof WireBlock) {
            return true;
        }

        // =================== LIGHTNING RODS ===================
        // Connect to vanilla lightning rods
        if (block instanceof LightningRodBlock) {
            return true;
        }

        // =================== TRANSPONDER SNAILS ===================
        // Regular Transponder Snail (receiver)
        if (block == ModBlocks.TRANSPONDER_SNAIL.get()) {
            return true;
        }

        // Transponder Snail Transmitter
        if (block == ModBlocks.TRANSPONDER_SNAIL_TRANSMITTER.get()) {
            return true;
        }

        // White Transponder Snail
        if (block == ModBlocks.WHITE_TRANSPONDER_SNAIL.get()) {
            return true;
        }

        if (block == ModBlocks.BLACK_TRANSPONDER_SNAIL_BLOCK.get()) {
             return true;
        }

        // =================== BLACK TRANSPONDER SNAILS ===================
        // Uncomment these when Black Transponder Snail blocks are implemented:
        //
        // if (block == ModBlocks.BABY_BLACK_TRANSPONDER_SNAIL_BLOCK.get()) {
        //     return true;
        // }

        // =================== ADD NEW BLOCKS HERE ===================
        // To add a new block that wire can connect to, simply add:
        //
        // if (block == ModBlocks.YOUR_BLOCK_NAME.get()) {
        //     return true;
        // }
        //
        // Examples:
        // if (block == ModBlocks.SIGNAL_BOOSTER.get()) return true;
        // if (block == ModBlocks.RELAY_STATION.get()) return true;

        return false;
    }

    private static BooleanProperty getHorizontalPropertyForDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> throw new IllegalArgumentException("Invalid horizontal direction: " + direction);
        };
    }

    private static BooleanProperty getUpPropertyForDirection(Direction direction) {
        return switch (direction) {
            case NORTH -> UP_NORTH;
            case SOUTH -> UP_SOUTH;
            case EAST -> UP_EAST;
            case WEST -> UP_WEST;
            default -> throw new IllegalArgumentException("Invalid horizontal direction: " + direction);
        };
    }

    // =================== NETWORK TRACING ===================

    /**
     * Trace from a starting position to find a single connected lightning rod.
     * Traces both horizontally AND vertically (up/down blocks and slabs).
     *
     * @param level The world
     * @param startPos Starting position (wire or snail block)
     * @return Position of a connected lightning rod, or null
     */
    @Nullable
    public static BlockPos traceToLightningRod(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty() && visited.size() < MAX_WIRE_RANGE * MAX_WIRE_RANGE) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);
            Block block = state.getBlock();

            // Check if this is a lightning rod
            if (block instanceof LightningRodBlock) {
                return current;
            }

            // Only trace through wire blocks (except for starting position)
            if (!(block instanceof WireBlock) && !current.equals(startPos)) {
                continue;
            }

            // Check all horizontal neighbors with vertical transitions
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                checkAndAddNeighbor(level, current, dir, visited, queue);
            }
        }

        return null;
    }

    /**
     * Helper to check neighbor positions including up/down transitions
     */
    private static void checkAndAddNeighbor(Level level, BlockPos current, Direction dir,
                                            Set<BlockPos> visited, Queue<BlockPos> queue) {
        // Direct horizontal neighbor
        BlockPos neighbor = current.relative(dir);
        addIfConnectable(level, neighbor, visited, queue);

        // Neighbor above (wire going up)
        BlockPos aboveNeighbor = neighbor.above();
        addIfConnectable(level, aboveNeighbor, visited, queue);

        // Neighbor below (wire going down)
        BlockPos belowNeighbor = neighbor.below();
        addIfConnectable(level, belowNeighbor, visited, queue);
    }

    private static void addIfConnectable(Level level, BlockPos pos, Set<BlockPos> visited, Queue<BlockPos> queue) {
        if (!visited.contains(pos)) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof WireBlock || state.getBlock() instanceof LightningRodBlock) {
                visited.add(pos);
                queue.add(pos);
            }
        }
    }

    /**
     * Trace from a starting position to find ALL connected lightning rods.
     *
     * @param level The world
     * @param startPos Starting position (wire or snail block)
     * @return Set of all connected lightning rod positions
     */
    public static Set<BlockPos> traceToAllLightningRods(Level level, BlockPos startPos) {
        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> lightningRods = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        queue.add(startPos);
        visited.add(startPos);

        while (!queue.isEmpty() && visited.size() < MAX_WIRE_RANGE * MAX_WIRE_RANGE) {
            BlockPos current = queue.poll();
            BlockState state = level.getBlockState(current);
            Block block = state.getBlock();

            // Check if this is a lightning rod
            if (block instanceof LightningRodBlock) {
                lightningRods.add(current);
            }

            // Only trace through wire blocks (except for starting position)
            if (!(block instanceof WireBlock) && !current.equals(startPos)) {
                continue;
            }

            // Check all directions including vertical transitions
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                checkAndAddNeighbor(level, current, dir, visited, queue);
            }
        }

        return lightningRods;
    }

    /**
     * Count how many lightning rods are connected to this wire network.
     */
    public static int countConnectedLightningRods(Level level, BlockPos startPos) {
        Set<BlockPos> lightningRodPositions = traceToAllLightningRods(level, startPos);

        Set<BlockPos> countedBottoms = new HashSet<>();
        int totalRods = 0;

        for (BlockPos rodPos : lightningRodPositions) {
            BlockPos bottom = findBottomOfLightningRodStack(level, rodPos);
            if (!countedBottoms.contains(bottom)) {
                countedBottoms.add(bottom);
                totalRods += countLightningRodStack(level, bottom);
            }
        }

        return totalRods;
    }

    private static int countLightningRodStack(Level level, BlockPos bottomPos) {
        int count = 0;
        BlockPos current = bottomPos;

        while (level.getBlockState(current).getBlock() instanceof LightningRodBlock) {
            count++;
            current = current.above();
            if (count > 64) break;
        }

        return count;
    }

    private static BlockPos findBottomOfLightningRodStack(Level level, BlockPos pos) {
        BlockPos current = pos;
        while (level.getBlockState(current.below()).getBlock() instanceof LightningRodBlock) {
            current = current.below();
            if (current.getY() < level.getMinBuildHeight()) break;
        }
        return current;
    }

    /**
     * Check if a position is connected to any wire network
     */
    public static boolean isConnectedToWireNetwork(Level level, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(dir);
            if (level.getBlockState(neighbor).getBlock() instanceof WireBlock) {
                return true;
            }
        }
        return false;
    }
}