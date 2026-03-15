package net.eclipce.transpondersnails.block.custom;

import net.eclipce.transpondersnails.block.entity.HornedDenDenMushiBlockEntity;
import net.eclipce.transpondersnails.block.entity.ModBlockEntities;
import net.eclipce.transpondersnails.item.HornedDenDenMushiItem;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import javax.annotation.Nullable;
import java.util.List;

/**
 * The Horned Den Den Mushi block — a stationary jammer.
 *
 * Placed by crouching + right-clicking the Horned Den Den Mushi item.
 * Blocks all transponder snail calls within a configurable sphere radius.
 * On break, drops the HornedDenDenMushiItem with the correct shell/body colours.
 *
 * Rendering is handled entirely by HornedDenDenMushiBlockEntityRenderer.
 * The block model JSON supplies only a particle texture — no visible geometry
 * is needed at the block-model level.
 */
public class HornedDenDenMushiBlock extends Block implements EntityBlock {

    // =================== BLOCK STATE PROPERTIES ===================

    /** Which way the snail faces when placed. */
    public static final DirectionProperty FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    /**
     * DyeColor id (0-15) stored in the block state so the BER can read the
     * shell colour without loading the block entity every frame.
     */
    public static final IntegerProperty SHELL_COLOR =
            IntegerProperty.create("shell_color", 0, 15);

    // =================== VOXEL SHAPES ===================
    // One shape per horizontal facing — rotated to match the item model orientation.
    // Base (NORTH): Block.box(4, 0, 0.5, 12, 10.5, 14)
    // SOUTH  : mirror Z  → 16-14=2, 16-0.5=15.5
    // WEST   : swap X↔Z depth → X: 0.5-14, Z: 4-12
    // EAST   : mirror WEST in X → 16-14=2, 16-0.5=15.5

    private static final VoxelShape SHAPE_NORTH = Block.box(4, 0, 2, 12, 10.5, 15);
    private static final VoxelShape SHAPE_SOUTH = Block.box(4, 0, 1, 12, 10.5, 14);  // 180° rotation
    private static final VoxelShape SHAPE_EAST = Block.box(1, 0, 4, 14, 10.5, 12);   // 90° rotation
    private static final VoxelShape SHAPE_WEST = Block.box(2, 0, 4, 15, 10.5, 12);   // 270° rotation

    // =================== CONSTRUCTOR ===================

    public HornedDenDenMushiBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(SHELL_COLOR, 0)
        );
    }

    // =================== BLOCK STATE ===================

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHELL_COLOR);
    }

    /**
     * When a player places this block (via the item's useOn sneak logic),
     * the facing is the direction the player is looking, and shell colour is
     * set by the caller before calling level.setBlock.
     */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(SHELL_COLOR, 0); // Overridden by useOn after placement
    }

    // =================== SHAPE ===================

    @Override
    public VoxelShape getShape(BlockState pState, BlockGetter pLevel,
                               BlockPos pPos, CollisionContext pContext) {
        return switch (pState.getValue(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case WEST  -> SHAPE_WEST;
            case EAST  -> SHAPE_EAST;
            default    -> SHAPE_NORTH; // NORTH and any unexpected value
        };
    }

    // =================== DYE RIGHT-CLICK ===================

    /**
     * Right-click with a DyeItem to change the shell colour.
     * Updates both the SHELL_COLOR blockstate property (read by the BER)
     * and the block entity (for persistence across chunk loads).
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);

        if (heldItem.getItem() instanceof DyeItem dyeItem) {
            if (!level.isClientSide) {
                DyeColor newColor  = dyeItem.getDyeColor();
                int newColorId     = newColor.getId();
                int currentColorId = state.getValue(SHELL_COLOR);

                if (newColorId != currentColorId) {
                    // Update blockstate so BER reads the new colour immediately
                    level.setBlock(pos, state.setValue(SHELL_COLOR, newColorId), 3);

                    // Update block entity so the colour is persisted
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof HornedDenDenMushiBlockEntity hornedBE) {
                        hornedBE.setShellColor(newColorId);
                    }

                    // Consume one dye in survival mode
                    if (!player.getAbilities().instabuild) {
                        heldItem.shrink(1);
                    }

                    level.playSound(null, pos, SoundEvents.DYE_USE,
                            SoundSource.BLOCKS, 1.0F, 1.0F);

                }
            }
            // sidedSuccess: SUCCESS on server (consumes action, swings arm),
            // CONSUME on client (prevents ghost items from appearing).
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    // =================== RENDERING ===================

    /**
     * INVISIBLE — the BER draws everything.
     * Returning INVISIBLE here tells Minecraft to skip the block-model pipeline
     * entirely and let the BlockEntityRenderer handle all rendering.
     */
    @Override
    public RenderShape getRenderShape(BlockState pState) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    // =================== BLOCK ENTITY ===================

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HornedDenDenMushiBlockEntity(pos, state);
    }

    /**
     * No ticker needed — the jammer is purely passive (no per-tick logic).
     */
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    // =================== DROPS ===================

    /**
     * Handles ALL destruction cases (player mine, explosion, piston, /setblock air, etc.).
     * Drops the Horned Den Den Mushi item with the correct shell and body colours
     * read from the block entity.
     */
    @Override
    public List<net.minecraft.world.item.ItemStack> getDrops(
            BlockState pState, LootParams.Builder pBuilder) {

        BlockEntity be = pBuilder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof HornedDenDenMushiBlockEntity hornedBE) {
            return List.of(
                    HornedDenDenMushiItem.createFromColors(
                            hornedBE.getShellColor(),
                            hornedBE.getBodyColor()
                    )
            );
        }
        // Fallback: drop a plain (white, default body) item
        return List.of(new net.minecraft.world.item.ItemStack(
                ModItems.HORNED_DEN_DEN_MUSHI.get()));
    }

    // =================== BREAK PARTICLES ===================

    /**
     * Supplies client-only rendering properties, including custom break particles.
     *
     * In Forge 1.18+, addDestroyEffects lives on IBlockRenderProperties (a
     * client-side interface), NOT on Block/IForgeBlock.  You must provide an
     * IBlockRenderProperties instance via initializeClient(); putting the method
     * directly on the Block class causes an @Override compile error.
     *
     * We spawn DustParticles coloured to match the snail's body colour instead
     * of vanilla TerrainParticles (which fail with a purple/black missing-texture
     * pattern because ENTITYBLOCK_ANIMATED blocks have no baked chunk-mesh model
     * to sample a particle sprite from).
     */
    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientBlockExtensions> consumer) {
        consumer.accept(new net.minecraftforge.client.extensions.common.IClientBlockExtensions() {
            @Override
            public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos,
                                             net.minecraft.client.particle.ParticleEngine manager) {
                // BlockParticleOption samples the baked block model (cube_all with the snail
                // body texture) to produce proper terrain-style pixel-fragment particles.
                // Minecraft automatically calls our RegisterColorHandlersEvent.Block handler
                // on each particle, so they are tinted to match the snail's body colour.
                net.minecraft.core.particles.BlockParticleOption particleData =
                        new net.minecraft.core.particles.BlockParticleOption(
                                net.minecraft.core.particles.ParticleTypes.BLOCK, state);

                // Distribute particles over the block's VoxelShape at vanilla density.
                VoxelShape shape = state.getShape(level, pos);
                shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
                    double dx = Math.min(1.0, x1 - x0);
                    double dy = Math.min(1.0, y1 - y0);
                    double dz = Math.min(1.0, z1 - z0);
                    int nx = Math.max(2, Mth.ceil(dx / 0.25));
                    int ny = Math.max(2, Mth.ceil(dy / 0.25));
                    int nz = Math.max(2, Mth.ceil(dz / 0.25));
                    for (int ix = 0; ix < nx; ix++) {
                        for (int iy = 0; iy < ny; iy++) {
                            for (int iz = 0; iz < nz; iz++) {
                                double px = pos.getX() + x0 + (ix + 0.5) / nx * dx;
                                double py = pos.getY() + y0 + (iy + 0.5) / ny * dy;
                                double pz = pos.getZ() + z0 + (iz + 0.5) / nz * dz;
                                level.addParticle(particleData, px, py, pz, 0.0, 0.0, 0.0);
                            }
                        }
                    }
                });

                return true; // suppress default TerrainParticle behaviour
            }
        });
    }
}