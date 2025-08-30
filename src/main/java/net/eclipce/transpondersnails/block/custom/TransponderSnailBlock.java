package net.eclipce.transpondersnails.block.custom;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.entity.ModBlockEntities;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class TransponderSnailBlock extends Block implements EntityBlock {
    // Updated hitbox: moved 4 pixels on X axis and 2 pixels on Z axis
    // Original: (0, 0, 0) to (8, 10.5, 13)
    // New: (4, 0, 2) to (12, 10.5, 15)
    public static final VoxelShape SHAPE = TransponderSnailBlock.box(4, 0, 2, 12, 10.5, 15);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public TransponderSnailBlock(Properties pProperties) {
        super(pProperties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE; // Use the same shape for collision as for visual
    }

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
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
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (!pLevel.isClientSide()) {
            BlockEntity entity = pLevel.getBlockEntity(pPos);
            if(entity instanceof TransponderSnailBlockEntity) {
                NetworkHooks.openScreen(((ServerPlayer)pPlayer), (TransponderSnailBlockEntity)entity, pPos);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.SUCCESS;
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

                }
            }
        }

        super.onRemove(pState, pLevel, pPos ,pNewState, pIsMoving);
    }

    /**
     * Called when the block is placed - restore data from item if available
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof TransponderSnailBlockEntity snailBE) {
                // Restore snail data from the item
                snailBE.loadFromItem(stack);
                System.out.println("TransponderSnailBlock: Placed block, restoring data from item");
            }
        }
    }

    /**
     * Override to preserve snail data when block is broken
     */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);

        // Get the block entity to save its data
        BlockEntity blockEntity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof TransponderSnailBlockEntity snailBE && snailBE.hasAssignedNumber()) {
            // Save the snail data to each dropped item
            for (ItemStack drop : drops) {
                if (drop.getItem() == this.asItem()) {
                    snailBE.saveToItem(drop);
                    System.out.println("TransponderSnailBlock: Saving snail data to dropped item");
                }
            }
        }

        return drops;
    }

    /**
     * Alternative method for preserving data when broken by player
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (blockEntity instanceof TransponderSnailBlockEntity snailBE && snailBE.hasAssignedNumber()) {
            // Create item with preserved data
            ItemStack itemStack = new ItemStack(this);
            snailBE.saveToItem(itemStack);

            // Drop the item
            popResource(level, pos, itemStack);

            // Prevent default drop
            state.spawnAfterBreak((ServerLevel) level, pos, tool, true);
            return;
        }

        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }
}
