package net.eclipce.transpondersnails.block.entity;

import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.block.custom.BlackTransponderSnailBlock;
import net.eclipce.transpondersnails.block.custom.WireBlock;
import net.eclipce.transpondersnails.config.ModConfig;
import net.eclipce.transpondersnails.voice.server.CallInterceptionManager;
import net.eclipce.transpondersnails.voice.server.CallSession;
import net.eclipce.transpondersnails.voice.server.TransponderCallManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;

/**
 * Block Entity for Black Transponder Snail Block
 *
 * Handles:
 * - Shell color storage
 * - Open/close state
 * - Lightning rod connection detection
 * - Range calculation based on antenna
 * - Call interception management
 * - Audio activity tracking
 */
public class BlackTransponderSnailBlockEntity extends BlockEntity {

    // NBT Keys
    private static final String TAG_SHELL_COLOR = "ShellColor";
    private static final String TAG_OPEN = "Open";
    private static final String TAG_CONNECTED_ROD_POS = "ConnectedRodPos";
    private static final String TAG_ROD_COUNT = "RodCount";
    private static final String TAG_CALCULATED_RANGE = "CalculatedRange";
    private static final String TAG_INTERCEPTING_CALL = "InterceptingCall";
    private static final String TAG_LAST_INTERACTOR = "LastInteractor";

    // State
    private int shellColor = DyeColor.YELLOW.getId();
    private boolean isOpen = false;

    // Antenna connection
    @Nullable
    private BlockPos connectedLightningRodPos = null;
    private int lightningRodCount = 0;
    private int calculatedRange = 0;
    private boolean antennaValid = false;

    // Interception state
    @Nullable
    private UUID interceptingCallId = null;
    @Nullable
    private UUID lastInteractorId = null;

    // Audio activity tracking
    private long lastAudioActivityTime = 0;
    private static final long AUDIO_ACTIVITY_WINDOW_MS = 500;

    // Call state for visual feedback (0=idle, 1=sound, 2=call, 3=active)
    private int callState = 0;

    public BlackTransponderSnailBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BLACK_TRANSPONDER_SNAIL_BE.get(), pos, state);

        // Initialize from block state if available
        if (state.hasProperty(BlackTransponderSnailBlock.SHELL_COLOR)) {
            this.shellColor = state.getValue(BlackTransponderSnailBlock.SHELL_COLOR);
        }
        if (state.hasProperty(BlackTransponderSnailBlock.OPEN)) {
            this.isOpen = state.getValue(BlackTransponderSnailBlock.OPEN);
        }
    }

    // =================== Server Tick ===================

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlackTransponderSnailBlockEntity blockEntity) {
        if (level.isClientSide) return;

        // Update interception validation every second
        if (level.getGameTime() % 20 == 0) {
            blockEntity.validateInterception();
        }

        // Update call state for visual feedback
        blockEntity.updateCallState(level, pos, state);
    }

    // =================== Antenna Connection ===================

    /**
     * Update the antenna connection by scanning for wire connections to lightning rods
     */
    public void updateAntennaConnection() {
        if (level == null || level.isClientSide) return;

        BlockPos oldRodPos = connectedLightningRodPos;
        int oldRodCount = lightningRodCount;

        // Reset connection
        connectedLightningRodPos = null;
        lightningRodCount = 0;
        antennaValid = false;

        // Scan for wire connections in cardinal directions
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos neighborPos = worldPosition.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);

            // Check for direct wire connection
            if (neighborState.getBlock() instanceof WireBlock) {
                // Trace wire to find lightning rod (only 2 arguments!)
                BlockPos rodPos = WireBlock.traceToLightningRod(level, neighborPos);

                if (rodPos != null) {
                    // Found a lightning rod - count the stack
                    int rodCount = BlackTransponderSnailBlock.countLightningRodStack(level, rodPos);

                    if (rodCount > 0) {
                        // Valid antenna found!
                        connectedLightningRodPos = rodPos;
                        lightningRodCount = rodCount;
                        antennaValid = true;
                        break; // Use first valid connection
                    }
                }
            }

            // Check for direct lightning rod connection (adjacent)
            if (neighborState.getBlock() instanceof LightningRodBlock) {
                int rodCount = BlackTransponderSnailBlock.countLightningRodStack(level, neighborPos);

                if (rodCount > 0) {
                    connectedLightningRodPos = neighborPos;
                    lightningRodCount = rodCount;
                    antennaValid = true;
                    break;
                }
            }
        }

        // Recalculate range
        calculatedRange = calculateRange();

        // Check if anything changed
        boolean rodCountChanged = lightningRodCount != oldRodCount;
        boolean rodPosChanged = (connectedLightningRodPos == null) != (oldRodPos == null) ||
                (connectedLightningRodPos != null && !connectedLightningRodPos.equals(oldRodPos));

        if (rodPosChanged || rodCountChanged) {
            if (antennaValid) {
                System.out.println("BlackTransponderSnailBlockEntity: Antenna connected at " + worldPosition +
                        " - " + lightningRodCount + " rods, range: " + calculatedRange + " blocks");
            } else if (oldRodPos != null) {
                System.out.println("BlackTransponderSnailBlockEntity: Antenna disconnected at " + worldPosition);
            }

            // Mark dirty and sync to client IMMEDIATELY
            setChanged();
            syncToClient();
        }
    }

    /**
     * Sync block entity data to client immediately
     * Call this when lightning rod count or other visual properties change
     */
    private void syncToClient() {
        if (level != null && !level.isClientSide && level instanceof ServerLevel serverLevel) {
            // Send block update to all tracking clients
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Calculate the interception range based on lightning rod count
     */
    public int calculateRange() {
        double baseRange = ModConfig.getAdultBlackSnailMinRange();
        double maxRange = ModConfig.getAdultBlackSnailMaxRange();

        if (!antennaValid || lightningRodCount == 0) {
            // No antenna - use default handheld range
            return (int) ModConfig.getAdultBlackSnailDefaultRange();
        }

        return BlackTransponderSnailBlock.calculateRange(baseRange, lightningRodCount, maxRange);
    }

    /**
     * Get number of connected lightning rods
     */
    public int countLightningRods() {
        return lightningRodCount;
    }

    /**
     * Check if antenna is valid
     */
    public boolean isAntennaValid() {
        return antennaValid;
    }

    // =================== Interception Management ===================

    /**
     * Start interception when a player opens the snail block
     */
    /**
     * Start interception - STATE MANAGEMENT ONLY
     * Messages are handled by InterceptionHelper
     */
    public void startInterception(ServerPlayer player) {
        if (level == null || level.isClientSide) return;

        lastInteractorId = player.getUUID();

        // Update antenna connection before calculating range
        updateAntennaConnection();

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        if (callManager == null) {
            return;
        }

        // Check if already intercepting
        if (interceptingCallId != null) {
            return;
        }

        // NOTE: InterceptionHelper handles all messaging and call finding
        // This method only tracks state once interception begins
    }

    /**
     * Stop interception when a player closes the snail block
     * STATE MANAGEMENT ONLY - Messages handled by InterceptionHelper
     */
    public void stopInterception(ServerPlayer player) {
        if (level == null || level.isClientSide) return;

        if (interceptingCallId == null) {
            return;
        }

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        if (callManager != null) {
            CallInterceptionManager interceptionManager = callManager.getInterceptionManager();
            if (interceptionManager != null) {
                interceptionManager.stopInterception(player.getUUID());
            }
        }

        interceptingCallId = null;
        lastInteractorId = null;
        setChanged();
        syncToClient();
    }

    /**
     * Stop interception without a player reference (uses stored lastInteractorId)
     * Called when block is broken or removed
     */
    public void stopInterception() {
        if (level == null || level.isClientSide) return;

        if (interceptingCallId == null) {
            return;
        }

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        if (callManager != null && lastInteractorId != null) {
            CallInterceptionManager interceptionManager = callManager.getInterceptionManager();
            if (interceptionManager != null) {
                interceptionManager.stopInterception(lastInteractorId);
            }
        }

        interceptingCallId = null;
        lastInteractorId = null;
        setChanged();
        syncToClient();
    }

    /**
     * Validate that interception is still valid
     */
    private void validateInterception() {
        if (interceptingCallId == null || level == null || level.isClientSide) return;

        TransponderCallManager callManager = TransponderSnails.getCallManager();
        if (callManager == null) {
            interceptingCallId = null;
            setChanged();
            return;
        }

        // Check if call still exists
        CallSession call = callManager.getCallSessionById(interceptingCallId);
        if (call == null || call.getState() != CallSession.CallState.CONNECTED) {
            // Call ended
            interceptingCallId = null;
            setChanged();
            syncToClient();

            // Notify player
            if (lastInteractorId != null && level instanceof ServerLevel serverLevel) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(lastInteractorId);
                if (player != null) {
                    player.displayClientMessage(
                            Component.literal("Call ended").withStyle(ChatFormatting.GRAY),
                            true
                    );
                }
            }
        }
    }

    /**
     * Find a nearby active call within range
     */
    @Nullable
    private UUID findNearbyCall(ServerPlayer player, int range, TransponderCallManager callManager) {
        Collection<CallSession> activeCalls = callManager.getActiveCalls();

        UUID nearestCallId = null;
        double nearestDistance = Double.MAX_VALUE;

        for (CallSession call : activeCalls) {
            if (call.getState() != CallSession.CallState.CONNECTED) {
                continue;
            }

            // Skip if player is a participant
            if (call.isParticipant(player.getUUID())) {
                continue;
            }

            // Calculate distance to nearest participant
            double distance = getDistanceToNearestParticipant(call, callManager);

            if (distance < range && distance < nearestDistance) {
                nearestDistance = distance;
                nearestCallId = call.getCallId();
            }
        }

        return nearestCallId;
    }

    /**
     * Get distance from this block to nearest call participant
     */
    private double getDistanceToNearestParticipant(CallSession call, TransponderCallManager callManager) {
        double minDistance = Double.MAX_VALUE;

        for (CallSession.CallParticipant participant : call.getAllParticipants()) {
            double distance;

            if (participant.isHandheld() && participant.hasActivePlayer()) {
                ServerPlayer participantPlayer = callManager.getPlayerById(participant.getPlayerId());
                if (participantPlayer != null) {
                    distance = Math.sqrt(worldPosition.distSqr(participantPlayer.blockPosition()));
                    minDistance = Math.min(minDistance, distance);
                }
            } else if (participant.isBlock() && participant.getBlockPosition() != null) {
                distance = Math.sqrt(worldPosition.distSqr(participant.getBlockPosition()));
                minDistance = Math.min(minDistance, distance);
            }
        }

        return minDistance;
    }

    // =================== Call State & Audio Activity ===================

    /**
     * Mark that audio was received (for visual feedback)
     */
    public void markAudioActivity() {
        lastAudioActivityTime = System.currentTimeMillis();
    }

    /**
     * Check if there's recent audio activity
     */
    public boolean hasRecentAudioActivity() {
        return (System.currentTimeMillis() - lastAudioActivityTime) < AUDIO_ACTIVITY_WINDOW_MS;
    }

    /**
     * Update the call state for visual feedback
     */
    private void updateCallState(Level level, BlockPos pos, BlockState state) {
        if (!isOpen) {
            if (callState != 0) {
                callState = 0;
                updateBlockCallState();
            }
            return;
        }

        int newCallState;

        if (interceptingCallId == null) {
            newCallState = 1; // SOUND - searching
        } else if (hasRecentAudioActivity()) {
            newCallState = 3; // ACTIVE - receiving audio
        } else {
            newCallState = 2; // CALL - intercepting but no audio
        }

        if (newCallState != callState) {
            callState = newCallState;
            updateBlockCallState();
        }
    }

    /**
     * Update the block state to match call state
     */
    private void updateBlockCallState() {
        if (level == null || level.isClientSide) return;

        BlockState state = level.getBlockState(worldPosition);
        if (state.hasProperty(BlackTransponderSnailBlock.CALL_STATE)) {
            if (state.getValue(BlackTransponderSnailBlock.CALL_STATE) != callState) {
                // Use flag 3 for immediate client update
                level.setBlock(worldPosition, state.setValue(BlackTransponderSnailBlock.CALL_STATE, callState), 3);
            }
        }
    }

    // =================== Getters & Setters ===================

    public int getShellColor() {
        return shellColor;
    }

    public void setShellColor(int color) {
        this.shellColor = color;
        setChanged();
        syncToClient();
    }

    public DyeColor getShellDyeColor() {
        return DyeColor.byId(shellColor);
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        this.isOpen = open;
        setChanged();
        syncToClient();
    }

    public int getCallState() {
        return callState;
    }

    @Nullable
    public UUID getInterceptingCallId() {
        return interceptingCallId;
    }

    public void setInterceptingCallId(@Nullable UUID callId) {
        this.interceptingCallId = callId;
        setChanged();
        syncToClient();
    }

    @Nullable
    public BlockPos getConnectedLightningRodPos() {
        return connectedLightningRodPos;
    }

    public int getCalculatedRange() {
        return calculatedRange;
    }

    public int getLightningRodCount() {
        return lightningRodCount;
    }

    // =================== NBT Serialization ===================

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);

        tag.putInt(TAG_SHELL_COLOR, shellColor);
        tag.putBoolean(TAG_OPEN, isOpen);
        tag.putInt(TAG_ROD_COUNT, lightningRodCount);
        tag.putInt(TAG_CALCULATED_RANGE, calculatedRange);

        if (connectedLightningRodPos != null) {
            tag.putLong(TAG_CONNECTED_ROD_POS, connectedLightningRodPos.asLong());
        }

        if (interceptingCallId != null) {
            tag.putUUID(TAG_INTERCEPTING_CALL, interceptingCallId);
        }

        if (lastInteractorId != null) {
            tag.putUUID(TAG_LAST_INTERACTOR, lastInteractorId);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);

        shellColor = tag.getInt(TAG_SHELL_COLOR);
        isOpen = tag.getBoolean(TAG_OPEN);
        lightningRodCount = tag.getInt(TAG_ROD_COUNT);
        calculatedRange = tag.getInt(TAG_CALCULATED_RANGE);

        if (tag.contains(TAG_CONNECTED_ROD_POS)) {
            connectedLightningRodPos = BlockPos.of(tag.getLong(TAG_CONNECTED_ROD_POS));
            antennaValid = lightningRodCount > 0;
        }

        if (tag.hasUUID(TAG_INTERCEPTING_CALL)) {
            interceptingCallId = tag.getUUID(TAG_INTERCEPTING_CALL);
        }

        if (tag.hasUUID(TAG_LAST_INTERACTOR)) {
            lastInteractorId = tag.getUUID(TAG_LAST_INTERACTOR);
        }
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}