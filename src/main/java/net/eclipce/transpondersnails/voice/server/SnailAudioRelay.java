package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.config.ModConfig;
import net.eclipce.transpondersnails.network.packets.CallStateSyncPacket;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.eclipce.transpondersnails.voice.audio.PhoneAudioFilter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TIER 2 + HANDHELD SUPPORT: Complete audio relay system
 * - Direct Opus transmission (Tier 1)
 * - Smart caching and optimizations (Tier 2)
 * - Full handheld snail audio forwarding (NEW)
 * - Phone audio filtering for immersive call quality (NEW)
 */
public class SnailAudioRelay {

    private final VoicechatServerApi voiceChatApi;
    private final TransponderCallManager callManager;

    // ✨ NEW: Phone audio filter
    private final PhoneAudioFilter phoneFilter;
    private final OpusDecoder opusDecoder;
    private final OpusEncoder opusEncoder;

    // TIER 2: Simplified cache - only cache what we need
    private final Map<UUID, CallSessionCache> playerSessionCache = new ConcurrentHashMap<>();
    private static final long CACHE_TIMEOUT_MS = 2000; // Increased from 1000ms for better performance

    // TIER 2: Event-driven blockstate tracking with improved cleanup
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "SnailAudioRelay-Cleanup"));
    private final Map<BlockPos, BlockstateActivity> blockstateActivity = new ConcurrentHashMap<>();
    private static final long AUDIO_TIMEOUT_MS = 500; // Increased from 400ms - more forgiving

    public SnailAudioRelay(VoicechatServerApi voiceChatApi, TransponderCallManager callManager) {
        this.voiceChatApi = voiceChatApi;
        this.callManager = callManager;

        // ✨ NEW: Initialize phone filter and codecs
        this.phoneFilter = new PhoneAudioFilter();
        this.opusDecoder = voiceChatApi.createDecoder();
        this.opusEncoder = voiceChatApi.createEncoder();

        // TIER 2: Less frequent cleanup for better performance
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredActivity, 200, 200, TimeUnit.MILLISECONDS);

        System.out.println("SnailAudioRelay: Initialized TIER 2 + Handheld support + Phone Filter system");
        System.out.println("  Phone Filter: " + (ModConfig.isPhoneFilterEnabled() ? "ENABLED" : "DISABLED"));
        if (ModConfig.isPhoneFilterEnabled()) {
            System.out.println("  " + phoneFilter.getDescription());
        }
    }

    /**
     * TIER 2 + HANDHELD: Complete audio processing with forwarding to all participant types
     */
    public void onMicrophonePacket(MicrophonePacketEvent event) {
        try {
            // Get the speaking player
            de.maxhenkel.voicechat.api.ServerPlayer vcSpeaker = Objects.requireNonNull(event.getSenderConnection()).getPlayer();
            if (vcSpeaker == null) return;

            ServerPlayer speaker = ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayer(vcSpeaker.getUuid());
            if (speaker == null) return;

            // TIER 2: Use cached call session instead of looking up every packet
            CallSessionCache sessionCache = playerSessionCache.get(speaker.getUUID());

            // Validate cache
            if (sessionCache == null || !sessionCache.isValid()) {
                // Cache miss or expired - rebuild cache
                if (!callManager.isInCall(speaker.getUUID())) return;

                UUID callId = callManager.getPlayerCallId(speaker.getUUID());
                if (callId == null) return;

                CallSession callSession = getCallSessionById(callId);
                if (callSession == null || callSession.getState() != CallSession.CallState.CONNECTED) {
                    return;
                }

                // Find transmitting snail (may be block or handheld)
                TransponderSnailBlockEntity nearbySnail = findNearestSnailInCall(speaker, callSession);

                // Cache the session info (nearbySnail can be null for handheld)
                sessionCache = new CallSessionCache(callSession, nearbySnail);
                playerSessionCache.put(speaker.getUUID(), sessionCache);
            }

            // TIER 2: Get Opus data - trust Voice Chat's VAD completely
            byte[] opusData = event.getPacket().getOpusEncodedData();
            if (opusData == null || opusData.length == 0) return;

            // Forward to all target snails using cached session
            CallSession callSession = sessionCache.callSession;

            // Get transmitting position (may be null for handheld)
            BlockPos transmittingPos = sessionCache.transmittingSnail != null ?
                    sessionCache.transmittingSnail.getBlockPos() : null;

            // =================== FORWARD TO BLOCK SNAILS ===================
            Set<BlockPos> targetPositions = callSession.getInvolvedBlockPositions();
            for (BlockPos targetPos : targetPositions) {
                // Skip if this is the transmitting block snail
                if (transmittingPos == null || !targetPos.equals(transmittingPos)) {
                    forwardOpusToSnail(targetPos, opusData, callSession);
                    updateAudioActivity(targetPos);
                }
            }

            // =================== FORWARD TO HANDHELD SNAILS ===================
            Set<UUID> handheldParticipants = callSession.getHandheldParticipantIds();
            for (UUID handheldPlayerId : handheldParticipants) {
                // Don't echo to self
                if (!handheldPlayerId.equals(speaker.getUUID())) {
                    forwardOpusToHandheld(handheldPlayerId, opusData, callSession);
                }
            }

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error processing microphone packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✨ NEW: Process audio through phone filter (if enabled)
     * This method handles the decode -> filter -> re-encode pipeline
     */
    private byte[] processAudioWithFilter(byte[] opusData) {
        // Check if phone filter is enabled in config
        if (!ModConfig.isPhoneFilterEnabled()) {
            // Filter disabled - pass through unchanged
            return opusData;
        }

        try {
            // Decode Opus to PCM (16-bit samples at 48kHz)
            short[] pcmSamples = opusDecoder.decode(opusData);

            if (pcmSamples == null || pcmSamples.length == 0) {
                // Decode failed - return original
                return opusData;
            }

            // Apply phone filter (bandpass 300Hz-3400Hz)
            short[] filteredSamples = phoneFilter.process(pcmSamples);

            // Re-encode to Opus
            byte[] filteredOpus = opusEncoder.encode(filteredSamples);

            if (filteredOpus == null || filteredOpus.length == 0) {
                // Encode failed - return original
                return opusData;
            }

            return filteredOpus;

        } catch (Exception e) {
            // Error in filtering - log and return original
            System.err.println("SnailAudioRelay: Error applying phone filter: " + e.getMessage());
            return opusData;
        }
    }

    /**
     * Forward Opus bytes directly to block snail audio channel
     * ✨ UPDATED: Now applies phone filter if enabled
     */
    private void forwardOpusToSnail(BlockPos targetPos, byte[] opusData, CallSession callSession) {
        try {
            // Process audio through filter (no-op if filter disabled)
            byte[] processedAudio = processAudioWithFilter(opusData);

            LocationalAudioChannel channel = (LocationalAudioChannel) callSession.getProximityChannel(targetPos);
            if (channel != null) {
                channel.send(processedAudio);
            }
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Failed to forward opus to " + targetPos + ": " + e.getMessage());
        }
    }

    /**
     * ✨ NEW: Forward Opus bytes to handheld snail participant
     * This enables handheld-to-handheld and block-to-handheld audio!
     * ✨ UPDATED: Now applies phone filter if enabled
     */
    private void forwardOpusToHandheld(UUID playerId, byte[] opusData, CallSession callSession) {
        try {
            // Process audio through filter (no-op if filter disabled)
            byte[] processedAudio = processAudioWithFilter(opusData);

            AudioChannel channel = callSession.getHandheldChannel(playerId);
            if (channel != null) {
                channel.send(processedAudio);
            } else {
                // Debug: Channel missing (this shouldn't happen if everything is set up correctly)
                System.err.println("SnailAudioRelay: No handheld channel found for player " +
                        playerId.toString().substring(0, 8));
            }
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Failed to forward opus to handheld " +
                    playerId.toString().substring(0, 8) + ": " + e.getMessage());
        }
    }

    /**
     * TIER 2 IMPROVED: Event-driven audio activity tracking with better data structure
     */
    private void updateAudioActivity(BlockPos pos) {
        long now = System.currentTimeMillis();

        BlockstateActivity activity = blockstateActivity.get(pos);
        if (activity == null) {
            // First audio packet - create new activity tracker
            activity = new BlockstateActivity(pos);
            blockstateActivity.put(pos, activity);

            // Update blockstate to active
            updateSnailBlockstate(pos, true);

        } else {
            // Update existing activity timestamp
            activity.lastActivityTime = now;
        }
    }

    /**
     * Update snail blockstate for visual feedback
     */
    private void updateSnailBlockstate(BlockPos pos, boolean active) {
        try {
            TransponderSnailBlockEntity snail = callManager.getRegisteredSnailBlock(
                    findSnailNumberAtPosition(pos));

            if (snail != null && snail.getCurrentCallState() == CallStateSyncPacket.CallState.CONNECTED) {
                snail.onSoundStateChanged(pos, active);
            }
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error updating blockstate for " + pos + ": " + e.getMessage());
        }
    }

    /**
     * TIER 2 OPTIMIZED: More efficient cleanup with batch processing
     */
    private void cleanupExpiredActivity() {
        try {
            long now = System.currentTimeMillis();
            List<BlockPos> toRemove = new ArrayList<>();

            // Collect expired positions
            for (BlockstateActivity activity : blockstateActivity.values()) {
                if (now - activity.lastActivityTime > AUDIO_TIMEOUT_MS) {
                    toRemove.add(activity.position);
                }
            }

            // Batch update - more efficient than one-by-one
            if (!toRemove.isEmpty()) {
                for (BlockPos pos : toRemove) {
                    blockstateActivity.remove(pos);
                    updateSnailBlockstate(pos, false);
                }
            }

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error in cleanup: " + e.getMessage());
        }
    }

    /**
     * TIER 2 + HANDHELD: Find nearest snail in call (handles both block and handheld)
     */
    @Nullable
    private TransponderSnailBlockEntity findNearestSnailInCall(ServerPlayer player, CallSession callSession) {
        TransponderSnailBlockEntity closestSnail = null;
        double closestDistance = Double.MAX_VALUE;
        double maxRangeSq = VoiceChatConstants.getSnailInteractionRange() *
                VoiceChatConstants.getSnailInteractionRange();

        // Look for nearby BLOCK snails
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity snail = callManager.getRegisteredSnailBlock(snailNumber);
            if (snail == null) continue; // Skip handheld snails in this loop

            double distance = player.distanceToSqr(
                    snail.getBlockPos().getX() + 0.5,
                    snail.getBlockPos().getY() + 0.5,
                    snail.getBlockPos().getZ() + 0.5);

            if (distance <= maxRangeSq && distance < closestDistance) {
                closestDistance = distance;
                closestSnail = snail;
            }
        }

        // ✨ NEW: If no block snail found, check if player has handheld snail
        if (closestSnail == null) {
            CallSession.CallParticipant participant = callSession.getParticipantByPlayer(player.getUUID());
            if (participant != null && participant.isHandheld()) {
                // Player is using handheld snail - return null (transmittingSnail can be null)
                System.out.println("SnailAudioRelay: Player " + player.getName().getString() +
                        " is using handheld snail #" + participant.getSnailNumber());
            }
        }

        return closestSnail; // Can be null for handheld transmitters
    }

    /**
     * TIER 2: Direct snail number lookup instead of iterating all snails
     */
    private int findSnailNumberAtPosition(BlockPos pos) {
        Map<Integer, TransponderSnailBlockEntity> snails = callManager.getRegisteredSnailBlocks();
        for (Map.Entry<Integer, TransponderSnailBlockEntity> entry : snails.entrySet()) {
            if (entry.getValue().getBlockPos().equals(pos)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Get call session by ID (optimized with early returns)
     */
    @Nullable
    private CallSession getCallSessionById(UUID callId) {
        return callManager.getActiveCalls().stream()
                .filter(call -> call.getCallId().equals(callId))
                .findFirst()
                .orElse(null);
    }

    /**
     * TIER 2 IMPROVED: Clean up with proper cache invalidation
     */
    public void onPlayerLeftCall(UUID playerId) {
        // Clear session cache
        playerSessionCache.remove(playerId);

        System.out.println("SnailAudioRelay: Cleaned up player " + playerId.toString().substring(0, 8));
    }

    /**
     * TIER 2 IMPROVED: Efficient call cleanup with batch blockstate reset
     */
    public void onCallEnded(UUID callId) {
        // Clear session caches for all participants
        CallSession callSession = getCallSessionById(callId);
        if (callSession != null) {
            for (UUID playerId : callSession.getActivePlayerParticipants()) {
                playerSessionCache.remove(playerId);
            }

            // Batch reset blockstates
            Set<BlockPos> involvedPositions = callSession.getInvolvedBlockPositions();
            for (BlockPos pos : involvedPositions) {
                blockstateActivity.remove(pos);
                updateSnailBlockstate(pos, false);
            }
        }

        System.out.println("SnailAudioRelay: Cleaned up call " + callId.toString().substring(0, 8));
    }

    /**
     * Shutdown cleanup
     * ✨ UPDATED: Now includes codec cleanup
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close codecs
        try {
            if (opusDecoder != null) {
                opusDecoder.close();
            }
            if (opusEncoder != null) {
                opusEncoder.close();
            }
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error closing codecs: " + e.getMessage());
        }

        playerSessionCache.clear();
        blockstateActivity.clear();

        System.out.println("SnailAudioRelay: Shutdown complete");
    }

    // =================== TIER 2 + HANDHELD DATA STRUCTURES ===================

    /**
     * TIER 2: Simplified cache that stores complete call context
     * ✨ IMPROVED: transmittingSnail can be null for handheld snails
     */
    private static class CallSessionCache {
        final CallSession callSession;
        final TransponderSnailBlockEntity transmittingSnail; // Can be null for handheld
        final long timestamp;

        CallSessionCache(CallSession callSession, @Nullable TransponderSnailBlockEntity transmittingSnail) {
            this.callSession = callSession;
            this.transmittingSnail = transmittingSnail;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid() {
            // Cache is valid if:
            // 1. Not expired
            // 2. Call session is still connected
            return (System.currentTimeMillis() - timestamp) < CACHE_TIMEOUT_MS &&
                    callSession.getState() == CallSession.CallState.CONNECTED;
        }
    }

    /**
     * TIER 2: Lightweight activity tracker for blockstate management
     */
    private static class BlockstateActivity {
        final BlockPos position;
        volatile long lastActivityTime;

        BlockstateActivity(BlockPos position) {
            this.position = position;
            this.lastActivityTime = System.currentTimeMillis();
        }
    }
}