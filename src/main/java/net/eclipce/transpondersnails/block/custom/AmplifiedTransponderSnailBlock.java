package net.eclipce.transpondersnails.block.custom;

import net.eclipce.transpondersnails.sound.ModSounds;
import net.eclipce.transpondersnails.voice.server.AmplifiedSnailManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Amplified Transponder Snail - an in-world megaphone.
 *
 * Extends TransponderSnailBlock so it keeps the shared TransponderSnailBlockEntity (body color, drops,
 * color inheritance) exactly as before. It only adds its own AMPLIFIER_STATE property, which drives the
 * model (see blockstates/amplified_transponder_snail.json). The inherited HAS_SOUND / IN_CALL properties
 * are not used by this block's blockstate file, so nothing the shared block entity does to them can
 * interfere with the megaphone visuals.
 */
public class AmplifiedTransponderSnailBlock extends TransponderSnailBlock {

    // Same state order used for the item model predicate (value = state * 0.25)
    public static final int STATE_IDLE = 0;    // Not in use
    public static final int STATE_SOUND = 1;   // Just deactivated
    public static final int STATE_CALL = 2;    // On, nobody talking
    public static final int STATE_ACTIVE = 3;  // On, transmitting audio

    public static final IntegerProperty AMPLIFIER_STATE = IntegerProperty.create("amplifier_state", 0, 3);

    /** How long the "sound" state shows after switching off (ticks). */
    public static final int SOUND_STATE_TICKS = 20;

    public AmplifiedTransponderSnailBlock(Properties properties) {
        super(properties, false); // hasShell = false: no compatible shell to dye
        this.registerDefaultState(this.defaultBlockState().setValue(AMPLIFIER_STATE, STATE_IDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AMPLIFIER_STATE);
    }

    /**
     * The direction the snail's head (megaphone) faces = the broadcast direction.
     *
     * TransponderSnailBlock places with FACING pointing TOWARD the player, and the blockstate file rotates
     * the model so the head faces away from them - so the head points opposite FACING.
     * Looked up by name so it works whatever DirectionProperty instance TransponderSnailBlock uses.
     */
    public static Direction getHeadDirection(BlockState state) {
        DirectionProperty facingProperty = findFacingProperty(state);
        return facingProperty != null ? state.getValue(facingProperty).getOpposite() : Direction.SOUTH;
    }

    @Nullable
    private static DirectionProperty findFacingProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty && "facing".equals(property.getName())) {
                return directionProperty;
            }
        }
        return null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        // Keep any sneak interaction the base snail block provides
        if (player.isSecondaryUseActive()) {
            return super.use(state, level, pos, player, hand, hit);
        }

        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        AmplifiedSnailManager manager = AmplifiedSnailManager.get();
        int current = state.getValue(AMPLIFIER_STATE);
        boolean isOn = current == STATE_CALL || current == STATE_ACTIVE;

        if (isOn) {
            // ---- Turn OFF ----
            if (manager != null) {
                manager.deactivatePlaced(serverLevel, pos);
            }
            serverLevel.setBlock(pos, state.setValue(AMPLIFIER_STATE, STATE_SOUND), 3);
            serverLevel.scheduleTick(pos, this, SOUND_STATE_TICKS);
            playSnailSound(serverLevel, pos, ModSounds.SNAIL_DISCONNECTED.get());
        } else {
            // ---- Turn ON ----
            if (manager == null) {
                player.displayClientMessage(Component.literal("Voice chat is not available!")
                        .withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }

            manager.activatePlaced(serverLevel, pos, getHeadDirection(state));
            serverLevel.setBlock(pos, state.setValue(AMPLIFIER_STATE, STATE_CALL), 3);
            playSnailSound(serverLevel, pos, ModSounds.SNAIL_CONNECTED.get());
        }

        return InteractionResult.CONSUME;
    }

    /**
     * Plays a snail sound at the block's center for everyone nearby (including the player who clicked).
     * Played directly rather than through CallSoundManager, whose tracked "ambient" sounds are tied to
     * call-snail blockstates.
     */
    private static void playSnailSound(ServerLevel level, BlockPos pos, SoundEvent sound) {
        level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                sound, SoundSource.BLOCKS, 1.0f, 1.0f);
    }

    /** Ends the "sound" state after switching off. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);

        BlockState current = level.getBlockState(pos);
        if (current.is(this) && current.getValue(AMPLIFIER_STATE) == STATE_SOUND) {
            level.setBlock(pos, current.setValue(AMPLIFIER_STATE, STATE_IDLE), 3);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // Only when the block is actually replaced - NOT on our own AMPLIFIER_STATE changes
        if (!level.isClientSide && !state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            AmplifiedSnailManager manager = AmplifiedSnailManager.get();
            if (manager != null) {
                manager.deactivatePlaced(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}