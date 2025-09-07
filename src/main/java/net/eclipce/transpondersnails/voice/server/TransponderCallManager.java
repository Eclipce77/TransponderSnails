package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Transponder Call Manager with CallSession integration for blockstate management
 */
public class TransponderCallManager {

    private final VoicechatServerApi voiceChatApi;
    private final CallSoundManager soundManager;
    private final ScheduledExecutorService scheduler;

    // Active calls - now using full CallSession objects
    private final Map<UUID, CallSession> activeCalls = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerToCallId = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> snailToCallId = new ConcurrentHashMap<>();

    // Snail block tracking
    private final Map<Integer, TransponderSnailBlockEntity> registeredSnailBlocks = new ConcurrentHashMap<>();

    // Call icon tracking
    private final Set<UUID> playersInCall = ConcurrentHashMap.newKeySet();

    // Track which snails are currently ringing
    private final Map<Integer, UUID> ringingSnails = new ConcurrentHashMap<>();

    public TransponderCallManager(VoicechatServerApi voiceChatApi) {
        this.voiceChatApi = voiceChatApi;
        this.soundManager = new CallSoundManager();
        this.scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(this::cleanupInactiveCalls, 30, 30, TimeUnit.SECONDS);

        System.out.println("TransponderCallManager: Initialized with CallSession integration");
    }

    // =================== CALLSESSION BLOCKSTATE INTEGRATION ===================

    /**
     * Update block entities when call session state changes
     */
    private void updateBlockEntitiesForCall(CallSession callSession) {
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity blockEntity = getRegisteredSnailBlock(snailNumber);
            if (blockEntity != null) {
                blockEntity.setCallSession(callSession);
            }
        }
    }

    /**
     * Clear call session from block entities when call ends
     */
    private void clearCallSessionFromBlockEntities(CallSession callSession) {
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity blockEntity = getRegisteredSnailBlock(snailNumber);
            if (blockEntity != null) {
                blockEntity.clearCallSession();
            }
        }
    }

    /**
     * Create appropriate participant for a snail
     */
    private CallSession.CallParticipant createParticipantForSnail(int snailNumber, @Nullable ServerPlayer player) {
        TransponderSnailBlockEntity blockEntity = getRegisteredSnailBlock(snailNumber);

        if (blockEntity != null) {
            // It's a block snail
            if (player != null) {
                return CallSession.CallParticipant.blockWithPlayer(
                        player.getUUID(), snailNumber, blockEntity.getBlockPos());
            } else {
                return CallSession.CallParticipant.block(snailNumber, blockEntity.getBlockPos());
            }
        } else {
            // It's a handheld snail (implement this when you have handheld snails)
            if (player != null) {
                return CallSession.CallParticipant.handheld(player.getUUID(), snailNumber);
            } else {
                // This shouldn't happen for handheld snails
                throw new IllegalStateException("Handheld snail without player");
            }
        }
    }

    // =================== CALL ICON MANAGEMENT ===================

    public boolean isPlayerInCallForIcon(UUID playerId) {
        return playersInCall.contains(playerId);
    }

    private void addPlayerToCallIcon(UUID playerId) {
        playersInCall.add(playerId);
        System.out.println("TransponderCallManager: Added player " + playerId + " to call icon tracking");
    }

    private void removePlayerFromCallIcon(UUID playerId) {
        playersInCall.remove(playerId);
        System.out.println("TransponderCallManager: Removed player " + playerId + " from call icon tracking");
    }

    public Set<UUID> getPlayersWithCallIcon() {
        return new HashSet<>(playersInCall);
    }

    // =================== SNAIL BLOCK REGISTRATION ===================

    public void registerSnailBlock(int snailNumber, TransponderSnailBlockEntity blockEntity) {
        registeredSnailBlocks.put(snailNumber, blockEntity);
        System.out.println("TransponderCallManager: Registered snail block #" + snailNumber);
    }

    public void unregisterSnailBlock(int snailNumber) {
        registeredSnailBlocks.remove(snailNumber);
        stopRingingAtSnail(snailNumber);
        endCallBySnailNumber(snailNumber);
        System.out.println("TransponderCallManager: Unregistered snail block #" + snailNumber);
    }

    @Nullable
    public TransponderSnailBlockEntity getRegisteredSnailBlock(int snailNumber) {
        return registeredSnailBlocks.get(snailNumber);
    }

    public boolean isSnailBlockRegistered(int snailNumber) {
        return registeredSnailBlocks.containsKey(snailNumber);
    }

    // =================== ENHANCED CALL INITIATION ===================

    public boolean initiateCallBySnailNumber(ServerPlayer caller, int callerSnailNumber, int targetSnailNumber) {
        try {
            System.out.println("TransponderCallManager: Initiating call from snail #" + callerSnailNumber + " to #" + targetSnailNumber);

            // Basic validation
            if (callerSnailNumber == targetSnailNumber) {
                caller.sendSystemMessage(Component.literal("Cannot call your own snail!"));
                return false;
            }

            if (isInCall(caller.getUUID())) {
                caller.sendSystemMessage(Component.literal("You are already in a call!"));
                return false;
            }

            if (!snailExists(targetSnailNumber)) {
                caller.sendSystemMessage(Component.literal("Snail #" + targetSnailNumber + " does not exist!"));
                return false;
            }

            if (isSnailInCall(targetSnailNumber)) {
                handleTargetBusy(caller, callerSnailNumber, targetSnailNumber);
                return false;
            }

            // Create CallSession with proper participants
            UUID callId = UUID.randomUUID();
            CallSession.CallParticipant callerParticipant = createParticipantForSnail(callerSnailNumber, caller);
            CallSession callSession = new CallSession(callId, callerSnailNumber, callerParticipant);

            // Set initial state to INITIATING
            callSession.setState(CallSession.CallState.INITIATING);
            updateBlockEntitiesForCall(callSession);

            // Add target as participant
            CallSession.CallParticipant targetParticipant = createParticipantForSnail(targetSnailNumber, null);
            callSession.addParticipant(targetSnailNumber, targetParticipant);

            // Register the call
            activeCalls.put(callId, callSession);
            playerToCallId.put(caller.getUUID(), callId);
            snailToCallId.put(callerSnailNumber, callId);
            snailToCallId.put(targetSnailNumber, callId);

            // Change state to RINGING and start ringing
            callSession.setState(CallSession.CallState.RINGING);
            updateBlockEntitiesForCall(callSession);
            startRinging(callSession);

            return true;

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error initiating call: " + e.getMessage());
            e.printStackTrace();
            caller.sendSystemMessage(Component.literal("Failed to initiate call!"));
            return false;
        }
    }

    /**
     * Enhanced start ringing with CallSession integration
     */
    private void startRinging(CallSession callSession) {
        int targetSnailNumber = -1;
        int callerSnailNumber = -1;

        // Find caller and target from participants
        for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
            if (participant.hasActivePlayer()) {
                callerSnailNumber = participant.getSnailNumber();
            } else {
                targetSnailNumber = participant.getSnailNumber();
            }
        }

        if (targetSnailNumber == -1) {
            System.err.println("TransponderCallManager: Could not find target snail in call session");
            return;
        }

        // Mark target snail as ringing
        ringingSnails.put(targetSnailNumber, callSession.getCallId());

        TransponderSnailBlockEntity targetBlock = getRegisteredSnailBlock(targetSnailNumber);
        if (targetBlock != null) {
            // Pass the CallSession to the block entity
            targetBlock.onIncomingCall(callSession.getCallId(), callerSnailNumber, callSession);

            // Start spatial ringtone
            BlockPos targetPos = targetBlock.getBlockPos();
            ServerLevel level = (ServerLevel) targetBlock.getLevel();
            soundManager.playLocationalRingToneAtPosition(level, targetPos);

            System.out.println("TransponderCallManager: Started ringtone at TARGET snail #" + targetSnailNumber + " position " + targetPos);
        }

        // Set timeout
        scheduler.schedule(() -> {
            if (callSession.getState() == CallSession.CallState.RINGING) {
                handleCallTimeout(callSession);
            }
        }, VoiceChatConstants.getRingTimeoutMs(), TimeUnit.MILLISECONDS);
    }

    // =================== ENHANCED CALL ACCEPTANCE/REJECTION ===================

    public boolean acceptCall(ServerPlayer player, UUID callId) {
        CallSession callSession = activeCalls.get(callId);
        if (callSession == null || callSession.getState() != CallSession.CallState.RINGING) {
            return false;
        }

        try {
            // Find target snail for pickup sound
            for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
                if (!participant.hasActivePlayer() && participant.isBlock()) {
                    TransponderSnailBlockEntity targetBlock = getRegisteredSnailBlock(participant.getSnailNumber());
                    if (targetBlock != null) {
                        soundManager.playPickUpSoundAtSnail(player, targetBlock.getBlockPos());
                        System.out.println("TransponderCallManager: Played pick up sound at target snail #" + participant.getSnailNumber());
                    }
                    break;
                }
            }

            // Stop ringing
            stopRingingForCall(callSession);

            // Connect the call
            connectCall(callSession, player);

            player.sendSystemMessage(Component.literal("Call connected!"));
            return true;

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error accepting call: " + e.getMessage());
            return false;
        }
    }

    public boolean rejectCall(ServerPlayer player, UUID callId) {
        CallSession callSession = activeCalls.get(callId);
        if (callSession == null) {
            return false;
        }

        stopRingingForCall(callSession);
        notifyCallRejected(callSession);
        endCall(callId);
        return true;
    }

    // =================== ENHANCED CALL CONNECTION ===================

    private void connectCall(CallSession callSession, ServerPlayer acceptingPlayer) {
        callSession.setState(CallSession.CallState.CONNECTED);
        updateBlockEntitiesForCall(callSession);

        // Add accepting player to the call
        if (!callSession.isParticipant(acceptingPlayer.getUUID())) {
            // Find the participant without a player and add the player to it
            for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
                if (!participant.hasActivePlayer()) {
                    // Create new participant with player
                    CallSession.CallParticipant updatedParticipant = CallSession.CallParticipant.blockWithPlayer(
                            acceptingPlayer.getUUID(), participant.getSnailNumber(), participant.getBlockPosition());
                    callSession.removeParticipant(participant.getSnailNumber());
                    callSession.addParticipant(participant.getSnailNumber(), updatedParticipant);
                    break;
                }
            }
            playerToCallId.put(acceptingPlayer.getUUID(), callSession.getCallId());
        }

        // Add all participants to call icon tracking
        for (UUID playerId : callSession.getActivePlayerParticipants()) {
            addPlayerToCallIcon(playerId);
        }

        // Create audio channels
        createAudioChannels(callSession);

        // Play connection sounds
        playConnectionSounds(callSession);

        // Notify block entities with CallSession
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity block = getRegisteredSnailBlock(snailNumber);
            if (block != null) {
                block.onCallConnected(callSession.getCallId(), callSession);
            }
        }

        System.out.println("TransponderCallManager: Call connected - " + callSession.getParticipantCount() + " participants");
    }

    private void createAudioChannels(CallSession callSession) {
        for (BlockPos pos : callSession.getInvolvedBlockPositions()) {
            try {
                TransponderSnailBlockEntity block = null;
                for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
                    TransponderSnailBlockEntity testBlock = getRegisteredSnailBlock(snailNumber);
                    if (testBlock != null && testBlock.getBlockPos().equals(pos)) {
                        block = testBlock;
                        break;
                    }
                }

                if (block != null) {
                    LocationalAudioChannel channel = voiceChatApi.createLocationalAudioChannel(
                            UUID.randomUUID(),
                            voiceChatApi.fromServerLevel((ServerLevel) block.getLevel()),
                            voiceChatApi.createPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    );

                    if (channel != null) {
                        channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);
                        channel.setDistance((float) VoiceChatConstants.getLocationalSnailRange());
                        callSession.addProximityChannel(pos, channel);
                        System.out.println("TransponderCallManager: Created audio channel at " + pos);
                    }
                }

            } catch (Exception e) {
                System.err.println("TransponderCallManager: Failed to create audio channel: " + e.getMessage());
            }
        }
    }

    // =================== ENHANCED CALL TERMINATION ===================

    public void endCall(UUID callId) {
        CallSession callSession = activeCalls.remove(callId);
        if (callSession == null) {
            return;
        }

        try {
            System.out.println("TransponderCallManager: Ending call " + callId.toString().substring(0, 8));

            // Update state to ENDING
            callSession.setState(CallSession.CallState.ENDING);
            updateBlockEntitiesForCall(callSession);

            stopRingingForCall(callSession);
            cleanupCall(callSession);
            notifyCallEnded(callSession);

            // Brief delay, then set to ENDED
            scheduler.schedule(() -> {
                callSession.setState(CallSession.CallState.ENDED);
                updateBlockEntitiesForCall(callSession);

                // After another brief delay, clear the session completely
                scheduler.schedule(() -> {
                    clearCallSessionFromBlockEntities(callSession);
                }, 1000, TimeUnit.MILLISECONDS);
            }, 500, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error ending call: " + e.getMessage());
        }
    }

    public void endCall(ServerPlayer player) {
        UUID callId = playerToCallId.get(player.getUUID());
        if (callId != null) {
            CallSession callSession = activeCalls.get(callId);
            if (callSession != null) {
                // Play spatial hang up sound at the snail the player is near
                playHangUpSoundForPlayer(player, callSession);

                // Delay call termination to let hang up sound finish first
                scheduler.schedule(() -> {
                    endCall(callId);
                }, 800, TimeUnit.MILLISECONDS);
            } else {
                endCall(callId);
            }
        }
    }

    public void endCallBySnailNumber(int snailNumber) {
        UUID callId = snailToCallId.get(snailNumber);
        if (callId != null) {
            endCall(callId);
        }
    }

    private void cleanupCall(CallSession callSession) {
        // Remove from tracking maps
        for (UUID playerId : callSession.getActivePlayerParticipants()) {
            playerToCallId.remove(playerId);
            removePlayerFromCallIcon(playerId);
        }
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            snailToCallId.remove(snailNumber);
            ringingSnails.remove(snailNumber);
        }

        // Clear proximity channels
        callSession.getProximityChannels().clear();
    }

    // =================== RINGING MANAGEMENT ===================

    private void stopRingingForCall(CallSession callSession) {
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            if (ringingSnails.containsKey(snailNumber)) {
                ringingSnails.remove(snailNumber);
                TransponderSnailBlockEntity targetBlock = getRegisteredSnailBlock(snailNumber);
                if (targetBlock != null) {
                    BlockPos targetPos = targetBlock.getBlockPos();
                    soundManager.stopSnailPositionSounds(targetPos, CallSoundManager.SoundType.RING_TONE);
                    System.out.println("TransponderCallManager: Stopped ringing at snail #" + snailNumber + " position " + targetPos);
                }
            }
        }
    }

    private void stopRingingAtSnail(int snailNumber) {
        UUID callId = ringingSnails.remove(snailNumber);
        if (callId != null) {
            TransponderSnailBlockEntity snailBlock = getRegisteredSnailBlock(snailNumber);
            if (snailBlock != null) {
                soundManager.stopSnailPositionSounds(snailBlock.getBlockPos(), CallSoundManager.SoundType.RING_TONE);
                System.out.println("TransponderCallManager: Stopped ringing at snail #" + snailNumber);
            }
        }
    }

    // =================== SOUND MANAGEMENT ===================

    private void playConnectionSounds(CallSession callSession) {
        for (BlockPos pos : callSession.getInvolvedBlockPositions()) {
            List<ServerPlayer> nearbyPlayers = getPlayersNearSnail((ServerLevel) getWorldForPosition(pos), pos, VoiceChatConstants.getSnailInteractionRange());
            if (!nearbyPlayers.isEmpty()) {
                soundManager.playCallConnectedSoundAtSnail(nearbyPlayers.get(0), pos);
            }
        }
    }

    private void playHangUpSoundForPlayer(ServerPlayer player, CallSession callSession) {
        BlockPos playerPos = player.blockPosition();
        BlockPos closestSnailPos = null;
        double closestDistance = Double.MAX_VALUE;

        for (BlockPos pos : callSession.getInvolvedBlockPositions()) {
            double distance = playerPos.distSqr(pos);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestSnailPos = pos;
            }
        }

        if (closestSnailPos != null) {
            soundManager.playHangUpSoundAtSnail(player, closestSnailPos);
        }
    }

    private void handleTargetBusy(ServerPlayer caller, int callerSnailNumber, int targetSnailNumber) {
        TransponderSnailBlockEntity callerBlock = getRegisteredSnailBlock(callerSnailNumber);
        if (callerBlock != null) {
            soundManager.playBusySoundAtSnail(caller, callerBlock.getBlockPos());
        }
        caller.sendSystemMessage(Component.literal("Snail #" + targetSnailNumber + " is busy!"));
    }

    private void handleCallTimeout(CallSession callSession) {
        stopRingingForCall(callSession);

        for (UUID playerId : callSession.getActivePlayerParticipants()) {
            ServerPlayer player = getPlayerById(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("Call timed out - no answer"));
            }
        }
        endCall(callSession.getCallId());
    }

    private void notifyCallRejected(CallSession callSession) {
        for (UUID playerId : callSession.getActivePlayerParticipants()) {
            ServerPlayer player = getPlayerById(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("Call was rejected"));

                // Find caller's snail for rejection sound
                for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
                    if (participant.hasActivePlayer() && participant.getPlayerId().equals(playerId)) {
                        TransponderSnailBlockEntity callerBlock = getRegisteredSnailBlock(participant.getSnailNumber());
                        if (callerBlock != null) {
                            soundManager.playCallDisconnectedSoundAtSnail(player, callerBlock.getBlockPos());
                        }
                        break;
                    }
                }
            }
        }
    }

    private void notifyCallEnded(CallSession callSession) {
        System.out.println("TransponderCallManager: Notifying call ended for " + callSession.getActivePlayerParticipants().size() + " players");

        // Play spatial disconnected sounds at all participating snail locations
        for (BlockPos pos : callSession.getInvolvedBlockPositions()) {
            List<ServerPlayer> nearbyPlayers = getPlayersNearSnail((ServerLevel) getWorldForPosition(pos), pos, VoiceChatConstants.getSnailInteractionRange());
            if (!nearbyPlayers.isEmpty()) {
                soundManager.playCallDisconnectedSoundAtSnail(nearbyPlayers.get(0), pos);
            }
        }

        // Notify participants
        for (UUID playerId : callSession.getActivePlayerParticipants()) {
            ServerPlayer player = getPlayerById(playerId);
            if (player != null) {
                player.sendSystemMessage(Component.literal("Call ended"));
            }
        }

        // Notify block entities
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity block = getRegisteredSnailBlock(snailNumber);
            if (block != null) {
                block.onCallEnded(callSession.getCallId());
            }
        }
    }

    // =================== UTILITY METHODS ===================

    private boolean snailExists(int snailNumber) {
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        return registry != null && registry.isNumberAssigned(snailNumber);
    }

    @Nullable
    private ServerPlayer findPlayerNearSnail(int snailNumber) {
        TransponderSnailBlockEntity block = getRegisteredSnailBlock(snailNumber);
        return block != null ? block.findNearbyPlayer() : null;
    }

    private List<ServerPlayer> getPlayersAroundSnail(int snailNumber) {
        TransponderSnailBlockEntity block = getRegisteredSnailBlock(snailNumber);
        if (block != null) {
            return getPlayersNearSnail(
                    (ServerLevel) block.getLevel(),
                    block.getBlockPos(),
                    VoiceChatConstants.getSnailInteractionRange()
            );
        }
        return List.of();
    }

    @Nullable
    private ServerPlayer getPlayerById(UUID playerId) {
        return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerId);
    }

    public List<ServerPlayer> getPlayersNearSnail(ServerLevel level, BlockPos snailPos, double range) {
        List<ServerPlayer> nearbyPlayers = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            double distance = player.distanceToSqr(snailPos.getX() + 0.5, snailPos.getY() + 0.5, snailPos.getZ() + 0.5);
            if (distance <= range * range) {
                nearbyPlayers.add(player);
            }
        }
        return nearbyPlayers;
    }

    @Nullable
    private ServerLevel getWorldForPosition(BlockPos pos) {
        // Helper method to get world for a position - you may need to adapt this
        for (TransponderSnailBlockEntity block : registeredSnailBlocks.values()) {
            if (block.getBlockPos().equals(pos)) {
                return (ServerLevel) block.getLevel();
            }
        }
        return null;
    }

    private void cleanupInactiveCalls() {
        long currentTime = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (CallSession session : activeCalls.values()) {
            if (session.shouldAutoEnd()) {
                toRemove.add(session.getCallId());
            }
        }

        for (UUID callId : toRemove) {
            System.out.println("TransponderCallManager: Cleaning up inactive call " + callId.toString().substring(0, 8));
            endCall(callId);
        }
    }

    // =================== QUERY METHODS ===================

    public boolean isInCall(UUID playerId) {
        return playerToCallId.containsKey(playerId);
    }

    public boolean isSnailInCall(int snailNumber) {
        return snailToCallId.containsKey(snailNumber);
    }

    public boolean isCallActive(UUID callId) {
        CallSession session = activeCalls.get(callId);
        return session != null && session.getState() == CallSession.CallState.CONNECTED;
    }

    public boolean isCallConnected(UUID callId) {
        return isCallActive(callId);
    }

    @Nullable
    public UUID getPlayerCallId(UUID playerId) {
        return playerToCallId.get(playerId);
    }

    public Collection<CallSession> getActiveCalls() {
        return new ArrayList<>(activeCalls.values());
    }

    public CallSoundManager getSoundManager() {
        return soundManager;
    }

    // =================== CLEANUP METHODS ===================

    /**
     * Cleanup method called when server is shutting down
     */
    public void cleanup() {
        System.out.println("TransponderCallManager: Starting cleanup...");

        try {
            // End all active calls
            for (UUID callId : new HashSet<>(activeCalls.keySet())) {
                try {
                    endCall(callId);
                } catch (Exception e) {
                    System.err.println("TransponderCallManager: Error ending call " + callId + " during cleanup: " + e.getMessage());
                }
            }

            // Clear all collections
            activeCalls.clear();
            registeredSnailBlocks.clear();
            playerToCallId.clear();
            snailToCallId.clear();
            playersInCall.clear();
            ringingSnails.clear();

            // Cleanup sound manager if it exists
            if (soundManager != null) {
                try {
                    soundManager.cleanup();
                } catch (Exception e) {
                    System.err.println("TransponderCallManager: Error cleaning up sound manager: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("TransponderCallManager: Error during cleanup: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("TransponderCallManager: Cleanup completed");
    }

    public void shutdown() {
        System.out.println("TransponderCallManager: Shutting down...");

        List<UUID> activeCalls = new ArrayList<>(this.activeCalls.keySet());
        for (UUID callId : activeCalls) {
            endCall(callId);
        }

        playersInCall.clear();
        ringingSnails.clear();
        soundManager.cleanup();
        scheduler.shutdown();

        System.out.println("TransponderCallManager: Shutdown complete");
    }
}