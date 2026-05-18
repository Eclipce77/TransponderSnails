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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * TIER 2 + HANDHELD SUPPORT + WHITE SNAIL PROTECTION: Complete audio relay system
 * - Direct Opus transmission (Tier 1)
 * - Smart caching and optimizations (Tier 2)
 * - Full handheld snail audio forwarding
 * - Phone audio filtering for immersive call quality
 * - ✨ REFACTORED: White Transponder Snail protection - looping static via CallSoundManager
 */
public class SnailAudioRelay {

    private final VoicechatServerApi voiceChatApi;
    private final TransponderCallManager callManager;

    // Phone audio filter - PER-SPEAKER to avoid cross-contamination
    private final Map<UUID, PhoneAudioFilter> speakerFilters = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFilterActivity = new ConcurrentHashMap<>();
    private static final long FILTER_CLEANUP_TIMEOUT_MS = 10000; // 10 seconds

    // ✅ CRITICAL FIX: PER-SPEAKER CODECS to prevent simultaneous audio corruption
    // Opus codecs are NOT thread-safe. When multiple people talk at once,
    // they MUST have separate encoder/decoder instances to prevent state corruption
    private final Map<UUID, OpusDecoder> speakerDecoders = new ConcurrentHashMap<>();
    private final Map<UUID, OpusEncoder> speakerEncoders = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCodecActivity = new ConcurrentHashMap<>();

    // Interception manager reference
    private CallInterceptionManager interceptionManager;

    // TIER 2: Simplified cache - only cache what we need
    private final Map<UUID, CallSessionCache> playerSessionCache = new ConcurrentHashMap<>();
    private static final long CACHE_TIMEOUT_MS = 2000;

    // TIER 2: Event-driven blockstate tracking with improved cleanup
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(2,
            r -> {
                Thread t = new Thread(r, "SnailAudioRelay-Worker");
                t.setDaemon(true);
                return t;
            });
    private final Map<BlockPos, BlockstateActivity> blockstateActivity = new ConcurrentHashMap<>();
    private static final long AUDIO_TIMEOUT_MS = 500;

    public SnailAudioRelay(VoicechatServerApi voiceChatApi, TransponderCallManager callManager) {
        this.voiceChatApi = voiceChatApi;
        this.callManager = callManager;

        // Codecs and filters are created per-speaker on demand (not shared!)

        // TIER 2: Less frequent cleanup for better performance
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredActivity, 200, 200, TimeUnit.MILLISECONDS);

        // Cleanup old speaker filters every 5 seconds
        cleanupExecutor.scheduleAtFixedRate(this::cleanupOldFilters, 5000, 5000, TimeUnit.MILLISECONDS);

        // ✅ Cleanup old speaker codecs every 5 seconds
        cleanupExecutor.scheduleAtFixedRate(this::cleanupOldCodecs, 5000, 5000, TimeUnit.MILLISECONDS);

    }

    /**
     * Set the interception manager
     * Called by TransponderCallManager after both are initialized
     */
    public void setInterceptionManager(CallInterceptionManager interceptionManager) {
        this.interceptionManager = interceptionManager;
    }

    // =================== AUDIO PROCESSING ===================

    /**
     * TIER 2 + HANDHELD + WHITE SNAIL: Complete audio processing with protection
     * ✨ REFACTORED: Static is now handled by looping sound via CallSoundManager
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
            // PERFORMANCE: use cached set from CallSessionCache (avoids new HashSet<> per packet)
            Set<BlockPos> targetPositions = sessionCache.cachedBlockPositions;
            for (BlockPos targetPos : targetPositions) {
                // Skip if this is the transmitting block snail
                if (transmittingPos == null || !targetPos.equals(transmittingPos)) {
                    forwardOpusToSnail(targetPos, opusData, callSession, speaker.getUUID());
                    updateAudioActivity(targetPos);
                }
            }

            // =================== FORWARD TO HANDHELD SNAILS ===================
            // PERFORMANCE: use cached set from CallSessionCache (avoids new HashSet<> per packet)
            Set<UUID> handheldParticipants = sessionCache.cachedHandheldParticipants;
            for (UUID handheldPlayerId : handheldParticipants) {
                // Don't echo to self
                if (!handheldPlayerId.equals(speaker.getUUID())) {
                    forwardOpusToHandheld(handheldPlayerId, opusData, callSession, speaker.getUUID());
                }
            }

            // =================== ✨ FORWARD TO INTERCEPTORS (WITH WHITE SNAIL PROTECTION) ===================
            if (interceptionManager != null) {
                Set<UUID> interceptors = interceptionManager.getInterceptorsForCall(callSession.getCallId());

                if (!interceptors.isEmpty()) {
                    // ✨ Check if the SPEAKER is protected by a White Snail
                    boolean speakerIsProtected = isSpeakerProtected(speaker, callSession, sessionCache);

                    for (UUID interceptorId : interceptors) {
                        if (speakerIsProtected) {
                            // ✨ WHITE SNAIL PROTECTION: Speaker is protected
                            // Static is already playing via CallSoundManager
                            // DON'T forward audio, DON'T mark activity
                            // Let updateCallStates() keep Black Snail in CALL state (intercepting but no audio)

                            // Do nothing - interceptor only hears static, no visual feedback for blocked audio
                        } else {
                            // ✨ Speaker is NOT protected - forward actual audio
                            // Static continues playing in background via CallSoundManager
                            AudioChannel interceptorChannel = interceptionManager.getInterceptorChannel(interceptorId);
                            if (interceptorChannel != null) {
                                forwardOpusToInterceptor(interceptorChannel, opusData, speaker.getUUID());
                            }

                            // PERFORMANCE: markAudioActivityAndSync only sends a packet
                            // on the CALL→ACTIVE transition, not every ~50Hz audio frame.
                            ServerPlayer interceptorPlayer = callManager.getPlayerById(interceptorId);
                            if (interceptorPlayer != null) {
                                interceptionManager.markAudioActivityAndSync(interceptorId, interceptorPlayer);
                            } else {
                                interceptionManager.markAudioActivity(interceptorId);
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error processing microphone packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =================== WHITE SNAIL PROTECTION METHODS ===================

    /**
     * ✨ Check if the speaking player is protected by a White Transponder Snail
     */
    private boolean isSpeakerProtected(ServerPlayer speaker, CallSession callSession, CallSessionCache sessionCache) {
        // Get the participant info for the speaker
        CallSession.CallParticipant speakerParticipant = callSession.getParticipantByPlayer(speaker.getUUID());

        if (speakerParticipant == null) {
            return false;
        }

        // Handheld snails cannot be protected
        if (speakerParticipant.isHandheld()) {
            return false;
        }

        // Check if the speaker's snail block is protected
        if (speakerParticipant.isBlock() && sessionCache.transmittingSnail != null) {
            return WhiteSnailProtectionManager.getInstance().isParticipantProtected(
                    sessionCache.transmittingSnail.getLevel(),
                    sessionCache.transmittingSnail.getBlockPos()
            );
        }

        return false;
    }

    /**
     * ✨ Get all White Snails protecting any participant in a call
     */
    private Set<BlockPos> getProtectingWhiteSnails(CallSession callSession) {
        Set<BlockPos> whiteSnails = new HashSet<>();

        for (CallSession.CallParticipant participant : callSession.getAllParticipants()) {
            if (participant.isBlock() && participant.getBlockPosition() != null) {
                TransponderSnailBlockEntity blockEntity =
                        callManager.getRegisteredSnailBlock(participant.getSnailNumber());

                if (blockEntity != null && blockEntity.getLevel() != null) {
                    BlockPos whiteSnailPos = WhiteSnailProtectionManager.getInstance()
                            .getProtectingWhiteSnail(blockEntity.getLevel(), participant.getBlockPosition());

                    if (whiteSnailPos != null) {
                        whiteSnails.add(whiteSnailPos);
                    }
                }
            }
        }

        return whiteSnails;
    }

    // =================== AUDIO PROCESSING METHODS ===================

    /**
     * Process audio through phone filter (if enabled)
     * Uses per-speaker filters AND codecs to avoid cross-contamination artifacts
     *
     * ✅ CRITICAL: Each speaker gets their own Opus decoder/encoder to prevent
     * state corruption when multiple people talk simultaneously
     *
     * @param opusData The Opus-encoded audio data
     * @param speakerId The UUID of the player speaking
     * @return Filtered Opus audio data
     */
    private byte[] processAudioWithFilter(byte[] opusData, UUID speakerId) {
        if (!ModConfig.isPhoneFilterEnabled()) {
            return opusData;
        }

        try {
            // ✅ Get or create decoder for THIS speaker (prevents state corruption)
            OpusDecoder decoder = speakerDecoders.computeIfAbsent(speakerId,
                    id -> {
                        OpusDecoder newDecoder = voiceChatApi.createDecoder();
                        id.toString().substring(0, 8);
                        return newDecoder;
                    });

            // ✅ Get or create encoder for THIS speaker (prevents state corruption)
            OpusEncoder encoder = speakerEncoders.computeIfAbsent(speakerId,
                    id -> {
                        OpusEncoder newEncoder = voiceChatApi.createEncoder();
                        id.toString().substring(0, 8);
                        return newEncoder;
                    });

            // Decode using speaker's dedicated decoder
            short[] pcmSamples = decoder.decode(opusData);

            if (pcmSamples == null || pcmSamples.length == 0) {
                return opusData;
            }

            // Get or create filter for this speaker
            PhoneAudioFilter filter = speakerFilters.computeIfAbsent(speakerId,
                    id -> {
                        PhoneAudioFilter newFilter = new PhoneAudioFilter();
                        id.toString().substring(0, 8);
                        return newFilter;
                    });

            // Track activity for cleanup
            lastFilterActivity.put(speakerId, System.currentTimeMillis());
            lastCodecActivity.put(speakerId, System.currentTimeMillis());

            // Process through speaker's dedicated filter
            short[] filteredSamples = filter.process(pcmSamples);

            // Encode using speaker's dedicated encoder
            byte[] filteredOpus = encoder.encode(filteredSamples);

            if (filteredOpus == null || filteredOpus.length == 0) {
                return opusData;
            }

            return filteredOpus;

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error applying phone filter for speaker " +
                    speakerId.toString().substring(0, 8) + ": " + e.getMessage());
            e.printStackTrace();
            return opusData;
        }
    }

    /**
     * Forward Opus bytes directly to block snail audio channel
     *
     * @param targetPos Position of the target snail
     * @param opusData Original Opus audio data
     * @param callSession The call session
     * @param speakerId UUID of the player speaking
     */
    private void forwardOpusToSnail(BlockPos targetPos, byte[] opusData, CallSession callSession, UUID speakerId) {
        try {
            byte[] processedAudio = processAudioWithFilter(opusData, speakerId);

            LocationalAudioChannel channel = (LocationalAudioChannel) callSession.getProximityChannel(targetPos);
            if (channel != null) {
                channel.send(processedAudio);
            }
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Failed to forward opus to " + targetPos + ": " + e.getMessage());
        }
    }

    /**
     * Forward Opus bytes to handheld snail participant
     *
     * @param playerId UUID of the receiving player
     * @param opusData Original Opus audio data
     * @param callSession The call session
     * @param speakerId UUID of the player speaking
     */
    private void forwardOpusToHandheld(UUID playerId, byte[] opusData, CallSession callSession, UUID speakerId) {
        try {
            byte[] processedAudio = processAudioWithFilter(opusData, speakerId);

            AudioChannel channel = callSession.getHandheldChannel(playerId);
            if (channel != null) {
                channel.send(processedAudio);
            } else {
                System.err.println("SnailAudioRelay: No handheld channel found for player " +
                        playerId.toString().substring(0, 8));
            }
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Failed to forward opus to handheld " +
                    playerId.toString().substring(0, 8) + ": " + e.getMessage());
        }
    }

    /**
     * Forward audio to an interceptor (when NOT protected)
     */
    /**
     * Forward Opus bytes to interceptor channel
     *
     * @param interceptorChannel The interceptor's audio channel
     * @param opusData Original Opus audio data
     * @param speakerId UUID of the player speaking
     */
    private void forwardOpusToInterceptor(AudioChannel interceptorChannel, byte[] opusData, UUID speakerId) {
        try {
            byte[] processedAudio = processAudioWithFilter(opusData, speakerId);
            interceptorChannel.send(processedAudio);
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Failed to forward opus to interceptor: " + e.getMessage());
        }
    }

    // =================== BLOCKSTATE TRACKING ===================

    /**
     * Event-driven audio activity tracking
     */
    private void updateAudioActivity(BlockPos pos) {
        long now = System.currentTimeMillis();

        BlockstateActivity activity = blockstateActivity.get(pos);
        if (activity == null) {
            activity = new BlockstateActivity(pos);
            blockstateActivity.put(pos, activity);
            updateSnailBlockstate(pos, true);
        } else {
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
     * Cleanup expired activity
     */
    private void cleanupExpiredActivity() {
        try {
            long now = System.currentTimeMillis();
            List<BlockPos> toRemove = new ArrayList<>();

            for (BlockstateActivity activity : blockstateActivity.values()) {
                if (now - activity.lastActivityTime > AUDIO_TIMEOUT_MS) {
                    toRemove.add(activity.position);
                }
            }

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

    // =================== UTILITY METHODS ===================

    /**
     * Find nearest snail in call
     */
    @Nullable
    private TransponderSnailBlockEntity findNearestSnailInCall(ServerPlayer player, CallSession callSession) {
        TransponderSnailBlockEntity closestSnail = null;
        double closestDistance = Double.MAX_VALUE;
        double maxRangeSq = VoiceChatConstants.getSnailInteractionRange() *
                VoiceChatConstants.getSnailInteractionRange();

        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity snail = callManager.getRegisteredSnailBlock(snailNumber);
            if (snail == null) continue;

            double distance = player.distanceToSqr(
                    snail.getBlockPos().getX() + 0.5,
                    snail.getBlockPos().getY() + 0.5,
                    snail.getBlockPos().getZ() + 0.5);

            if (distance <= maxRangeSq && distance < closestDistance) {
                closestDistance = distance;
                closestSnail = snail;
            }
        }

        if (closestSnail == null) {
            CallSession.CallParticipant participant = callSession.getParticipantByPlayer(player.getUUID());
            if (participant != null && participant.isHandheld()) {
                participant.getSnailNumber();
            }
        }

        return closestSnail;
    }

    /**
     * Find snail number at position
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
     * Get call session by ID
     */
    @Nullable
    private CallSession getCallSessionById(UUID callId) {
        return callManager.getActiveCalls().stream()
                .filter(call -> call.getCallId().equals(callId))
                .findFirst()
                .orElse(null);
    }

    // =================== LIFECYCLE METHODS ===================

    /**
     * Called when a player leaves a call
     */
    public void onPlayerLeftCall(UUID playerId) {
        playerSessionCache.remove(playerId);

    }

    /**
     * Called when a call ends
     */
    public void onCallEnded(UUID callId) {
        CallSession callSession = getCallSessionById(callId);
        if (callSession != null) {
            for (UUID playerId : callSession.getActivePlayerParticipants()) {
                playerSessionCache.remove(playerId);
            }

            Set<BlockPos> involvedPositions = callSession.getInvolvedBlockPositions();
            for (BlockPos pos : involvedPositions) {
                blockstateActivity.remove(pos);
                updateSnailBlockstate(pos, false);
            }
        }

    }

    /**
     * Cleanup old speaker filters that haven't been used recently
     * Prevents memory leaks from players who disconnect or stop talking
     */
    private void cleanupOldFilters() {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : lastFilterActivity.entrySet()) {
            if (now - entry.getValue() > FILTER_CLEANUP_TIMEOUT_MS) {
                toRemove.add(entry.getKey());
            }
        }

        if (!toRemove.isEmpty()) {
            for (UUID speakerId : toRemove) {
                PhoneAudioFilter filter = speakerFilters.remove(speakerId);
                if (filter != null) {
                    filter.reset(); // Clean up filter state
                }
                lastFilterActivity.remove(speakerId);
            }
        }
    }

    /**
     * Cleanup old speaker codecs that haven't been used recently
     * ✅ CRITICAL: Properly close Opus codecs to prevent resource leaks
     */
    private void cleanupOldCodecs() {
        long now = System.currentTimeMillis();
        List<UUID> toRemove = new ArrayList<>();

        for (Map.Entry<UUID, Long> entry : lastCodecActivity.entrySet()) {
            if (now - entry.getValue() > FILTER_CLEANUP_TIMEOUT_MS) {
                toRemove.add(entry.getKey());
            }
        }

        if (!toRemove.isEmpty()) {
            for (UUID speakerId : toRemove) {
                // Close decoder
                OpusDecoder decoder = speakerDecoders.remove(speakerId);
                if (decoder != null) {
                    try {
                        decoder.close();
                    } catch (Exception e) {
                        System.err.println("Error closing decoder for speaker " +
                                speakerId.toString().substring(0, 8) + ": " + e.getMessage());
                    }
                }

                // Close encoder
                OpusEncoder encoder = speakerEncoders.remove(speakerId);
                if (encoder != null) {
                    try {
                        encoder.close();
                    } catch (Exception e) {
                        System.err.println("Error closing encoder for speaker " +
                                speakerId.toString().substring(0, 8) + ": " + e.getMessage());
                    }
                }

                lastCodecActivity.remove(speakerId);
            }
        }
    }

    /**
     * Shutdown cleanup
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

        // ✅ Close all per-speaker codecs
        for (OpusDecoder decoder : speakerDecoders.values()) {
            try {
                decoder.close();
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Error closing decoder: " + e.getMessage());
            }
        }

        for (OpusEncoder encoder : speakerEncoders.values()) {
            try {
                encoder.close();
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Error closing encoder: " + e.getMessage());
            }
        }

        playerSessionCache.clear();
        blockstateActivity.clear();

        // Clean up speaker filters and codecs
        speakerFilters.clear();
        lastFilterActivity.clear();
        speakerDecoders.clear();
        speakerEncoders.clear();
        lastCodecActivity.clear();

    }

    // =================== DATA STRUCTURES ===================

    /**
     * Cache for call session data
     */
    private static class CallSessionCache {
        final CallSession callSession;
        final TransponderSnailBlockEntity transmittingSnail;
        final long timestamp;

        // PERFORMANCE: Cache sets that would otherwise be allocated fresh on every
        // audio packet (~50Hz). getInvolvedBlockPositions() and getHandheldParticipantIds()
        // both do `new HashSet<>()` on every call. Caching here avoids ~100 short-lived
        // allocations per second per active call.
        final java.util.Set<net.minecraft.core.BlockPos> cachedBlockPositions;
        final java.util.Set<java.util.UUID> cachedHandheldParticipants;

        CallSessionCache(CallSession callSession, @Nullable TransponderSnailBlockEntity transmittingSnail) {
            this.callSession = callSession;
            this.transmittingSnail = transmittingSnail;
            this.timestamp = System.currentTimeMillis();
            this.cachedBlockPositions = callSession.getInvolvedBlockPositions();
            this.cachedHandheldParticipants = callSession.getHandheldParticipantIds();
        }

        boolean isValid() {
            return (System.currentTimeMillis() - timestamp) < CACHE_TIMEOUT_MS &&
                    callSession.getState() == CallSession.CallState.CONNECTED;
        }
    }

    /**
     * Blockstate activity tracker
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