package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.network.packets.CallStateSyncPacket;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
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
 * Fixed audio relay system for Transponder Snails
 * Addresses stuttering and blockstate update issues
 */
public class SnailAudioRelay {

    private final VoicechatServerApi voiceChatApi;
    private final TransponderCallManager callManager;

    // Audio management
    private final Map<UUID, OpusDecoder> playerDecoders = new ConcurrentHashMap<>();
    private final Map<UUID, Map<BlockPos, AudioConnection>> activeConnections = new ConcurrentHashMap<>();

    // Performance optimization - cache nearby snails
    private final Map<UUID, NearbySnailCache> playerSnailCache = new ConcurrentHashMap<>();
    private static final long CACHE_TIMEOUT_MS = 1000;

    // FIX 3: Restored blockstate tracking with proper cleanup
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "SnailAudioRelay-Cleanup"));
    private final Map<BlockPos, Long> lastAudioActivity = new ConcurrentHashMap<>();
    private final Set<BlockPos> snailsWithActiveAudio = ConcurrentHashMap.newKeySet();
    private static final long AUDIO_TIMEOUT_MS = 400; // 400ms timeout

    // FIX 2: Pre-allocated silence buffer to prevent stuttering
    private static final short[] SILENCE_BUFFER = new short[VoiceChatConstants.AUDIO_FRAME_SIZE];

    public SnailAudioRelay(VoicechatServerApi voiceChatApi, TransponderCallManager callManager) {
        this.voiceChatApi = voiceChatApi;
        this.callManager = callManager;

        // FIX 3: Start cleanup task for blockstate management
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredActivity, 100, 100, TimeUnit.MILLISECONDS);

        System.out.println("SnailAudioRelay: Initialized fixed audio relay system");
    }

    /**
     * Main audio processing with stuttering fixes
     */
    public void onMicrophonePacket(MicrophonePacketEvent event) {
        try {
            // Get the speaking player
            de.maxhenkel.voicechat.api.ServerPlayer vcSpeaker = Objects.requireNonNull(event.getSenderConnection()).getPlayer();
            if (vcSpeaker == null) return;

            ServerPlayer speaker = ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayer(vcSpeaker.getUuid());
            if (speaker == null) return;

            // Only process if player is in a call
            if (!callManager.isInCall(speaker.getUUID())) return;

            UUID callId = callManager.getPlayerCallId(speaker.getUUID());
            if (callId == null) return;

            CallSession callSession = getCallSessionById(callId);
            if (callSession == null || callSession.getState() != CallSession.CallState.CONNECTED) {
                return;
            }

            // Find the snail the speaker is near
            TransponderSnailBlockEntity nearbySnail = findNearestSnailInCall(speaker, callSession);
            if (nearbySnail == null) return;

            // Calculate volume based on distance
            float volume = calculateVolumeByDistance(speaker, nearbySnail.getBlockPos());
            if (volume <= 0.0f) return;

            // Get audio data
            byte[] opusData = event.getPacket().getOpusEncodedData();
            if (opusData == null || opusData.length == 0) return;

            // Decode the audio once
            OpusDecoder decoder = getOrCreateDecoder(speaker.getUUID());
            if (decoder == null) return;

            short[] pcmAudio = decoder.decode(opusData);
            if (pcmAudio == null || pcmAudio.length == 0) return;

            // Apply volume adjustment if needed
            if (volume < 1.0f) {
                pcmAudio = applyVolume(pcmAudio, volume);
            }

            // Check if this is actual audio content
            boolean hasAudio = hasAudioContent(pcmAudio);

            // Forward to all target snails in the call
            Set<BlockPos> targetPositions = callSession.getInvolvedBlockPositions();
            for (BlockPos targetPos : targetPositions) {
                if (!targetPos.equals(nearbySnail.getBlockPos())) {
                    // FIX 2: Always send audio (even if silence) to prevent stuttering
                    forwardAudioToSnail(speaker.getUUID(), targetPos, pcmAudio, callSession);

                    // FIX 3: Update blockstate activity if there's actual audio
                    if (hasAudio) {
                        updateAudioActivity(targetPos);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error processing microphone packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * FIX 2: Improved audio forwarding that prevents stuttering
     */
    private void forwardAudioToSnail(UUID speakerId, BlockPos targetPos, short[] audioData, CallSession callSession) {
        try {
            // Get or create audio connection for this target
            Map<BlockPos, AudioConnection> playerConnections = activeConnections.computeIfAbsent(
                    speakerId, k -> new ConcurrentHashMap<>());

            AudioConnection connection = playerConnections.get(targetPos);
            if (connection == null || !connection.isValid()) {
                // Create new connection
                LocationalAudioChannel channel = (LocationalAudioChannel) callSession.getProximityChannel(targetPos);
                if (channel == null) return;

                OpusEncoder encoder = voiceChatApi.createEncoder();
                if (encoder == null) return;

                // FIX 2: Create continuous audio supplier that never returns null
                ContinuousAudioHolder audioHolder = new ContinuousAudioHolder();
                AudioPlayer player = voiceChatApi.createAudioPlayer(channel, encoder, audioHolder::getCurrentAudio);

                if (player != null) {
                    connection = new AudioConnection(encoder, player, audioHolder);
                    playerConnections.put(targetPos, connection);
                    player.startPlaying();
                    System.out.println("SnailAudioRelay: Created audio connection for " + targetPos);
                }
            }

            // Send audio through the connection
            if (connection != null && connection.isValid()) {
                connection.sendAudio(audioData);
            }

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Failed to forward audio to " + targetPos + ": " + e.getMessage());
        }
    }

    /**
     * Improved audio content detection with better threshold
     */
    private boolean hasAudioContent(short[] audioData) {
        if (audioData == null || audioData.length == 0) return false;

        // Quick RMS calculation on a subset of samples for performance
        long sumSquares = 0;
        int checkSamples = Math.min(audioData.length, 240); // Check first 5ms worth for speed

        for (int i = 0; i < checkSamples; i++) {
            long sample = audioData[i];
            sumSquares += sample * sample;
        }

        double rms = Math.sqrt((double)sumSquares / checkSamples);
        return rms > 80.0; // Balanced threshold - not too sensitive
    }

    /**
     * FIX 3: Proper audio activity tracking for blockstates
     */
    private void updateAudioActivity(BlockPos pos) {
        long now = System.currentTimeMillis();
        lastAudioActivity.put(pos, now);

        // Only update blockstate if not already active (prevents spam)
        if (!snailsWithActiveAudio.contains(pos)) {
            snailsWithActiveAudio.add(pos);
            updateSnailBlockstate(pos, true);
            System.out.println("SnailAudioRelay: Started audio activity at " + pos);
        }
    }

    /**
     * FIX 3: Improved blockstate update method
     */
    private void updateSnailBlockstate(BlockPos pos, boolean active) {
        try {
            TransponderSnailBlockEntity snail = findSnailAtPosition(pos);
            if (snail != null && snail.getCurrentCallState() == CallStateSyncPacket.CallState.CONNECTED) {
                snail.onSoundStateChanged(pos, active);
                System.out.println("SnailAudioRelay: Updated blockstate for " + pos + " - active: " + active);
            }
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error updating blockstate for " + pos + ": " + e.getMessage());
        }
    }

    /**
     * FIX 3: Scheduled cleanup of expired audio activity
     */
    private void cleanupExpiredActivity() {
        try {
            long now = System.currentTimeMillis();

            // Find expired positions
            Set<BlockPos> expiredPositions = new HashSet<>();
            Iterator<Map.Entry<BlockPos, Long>> iterator = lastAudioActivity.entrySet().iterator();

            while (iterator.hasNext()) {
                Map.Entry<BlockPos, Long> entry = iterator.next();
                if (now - entry.getValue() > AUDIO_TIMEOUT_MS) {
                    expiredPositions.add(entry.getKey());
                    iterator.remove();
                }
            }

            // Update blockstates for expired positions
            for (BlockPos pos : expiredPositions) {
                if (snailsWithActiveAudio.remove(pos)) {
                    updateSnailBlockstate(pos, false);
                    System.out.println("SnailAudioRelay: Stopped audio activity at " + pos + " (timeout)");
                }
            }

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error in cleanup: " + e.getMessage());
        }
    }

    /**
     * Simple volume application
     */
    private short[] applyVolume(short[] audio, float volume) {
        short[] result = new short[audio.length];
        for (int i = 0; i < audio.length; i++) {
            float adjusted = audio[i] * volume;
            result[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, Math.round(adjusted)));
        }
        return result;
    }

    /**
     * Calculate volume based on distance
     */
    private float calculateVolumeByDistance(ServerPlayer player, BlockPos snailPos) {
        double distance = player.distanceToSqr(
                snailPos.getX() + 0.5, snailPos.getY() + 0.5, snailPos.getZ() + 0.5);

        double maxRange = VoiceChatConstants.getSnailInteractionRange();
        double maxRangeSq = maxRange * maxRange;

        if (distance >= maxRangeSq) return 0.0f;

        double normalizedDistance = Math.sqrt(distance) / maxRange;
        return Math.max(0.1f, (float)(1.0 - normalizedDistance));
    }

    /**
     * Find nearest snail in call
     */
    @Nullable
    private TransponderSnailBlockEntity findNearestSnailInCall(ServerPlayer player, CallSession callSession) {
        UUID playerId = player.getUUID();
        NearbySnailCache cache = playerSnailCache.get(playerId);
        if (cache != null && cache.isValid()) {
            return cache.snail;
        }

        TransponderSnailBlockEntity closestSnail = null;
        double closestDistance = Double.MAX_VALUE;
        double maxRangeSq = VoiceChatConstants.getSnailInteractionRange() * VoiceChatConstants.getSnailInteractionRange();

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

        if (closestSnail != null) {
            playerSnailCache.put(playerId, new NearbySnailCache(closestSnail));
        }

        return closestSnail;
    }

    /**
     * Find snail at position
     */
    @Nullable
    private TransponderSnailBlockEntity findSnailAtPosition(BlockPos pos) {
        Map<Integer, TransponderSnailBlockEntity> registeredSnails = callManager.getRegisteredSnailBlocks();
        for (TransponderSnailBlockEntity snail : registeredSnails.values()) {
            if (snail.getBlockPos().equals(pos)) {
                return snail;
            }
        }
        return null;
    }

    /**
     * Get or create decoder for player
     */
    private OpusDecoder getOrCreateDecoder(UUID playerId) {
        return playerDecoders.computeIfAbsent(playerId, k -> {
            try {
                return voiceChatApi.createDecoder();
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Failed to create decoder: " + e.getMessage());
                return null;
            }
        });
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

    /**
     * Clean up when player leaves call
     */
    public void onPlayerLeftCall(UUID playerId) {
        // Clean up decoder
        OpusDecoder decoder = playerDecoders.remove(playerId);
        if (decoder != null) {
            try {
                decoder.close();
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Error closing decoder: " + e.getMessage());
            }
        }

        // Clean up connections
        Map<BlockPos, AudioConnection> connections = activeConnections.remove(playerId);
        if (connections != null) {
            for (AudioConnection connection : connections.values()) {
                connection.cleanup();
            }
        }

        // Clear cache
        playerSnailCache.remove(playerId);
    }

    /**
     * Clean up when call ends
     */
    public void onCallEnded(UUID callId) {
        // Find all connections for this call and clean them up
        CallSession callSession = getCallSessionById(callId);
        if (callSession != null) {
            Set<BlockPos> involvedPositions = callSession.getInvolvedBlockPositions();

            // FIX 3: Properly reset blockstates for involved snails
            for (BlockPos pos : involvedPositions) {
                snailsWithActiveAudio.remove(pos);
                lastAudioActivity.remove(pos);
                updateSnailBlockstate(pos, false);
            }
        }

        // Clean up any remaining connections
        for (Map<BlockPos, AudioConnection> connections : activeConnections.values()) {
            Iterator<Map.Entry<BlockPos, AudioConnection>> iterator = connections.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, AudioConnection> entry = iterator.next();
                entry.getValue().cleanup();
                iterator.remove();
            }
        }

        System.out.println("SnailAudioRelay: Cleaned up call " + callId.toString().substring(0, 8));
    }

    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        // FIX 3: Shutdown cleanup executor
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clean up all decoders
        for (OpusDecoder decoder : playerDecoders.values()) {
            try {
                decoder.close();
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Error closing decoder during shutdown");
            }
        }
        playerDecoders.clear();

        // Clean up all connections
        for (Map<BlockPos, AudioConnection> connections : activeConnections.values()) {
            for (AudioConnection connection : connections.values()) {
                connection.cleanup();
            }
        }
        activeConnections.clear();

        playerSnailCache.clear();
        lastAudioActivity.clear();
        snailsWithActiveAudio.clear();

        System.out.println("SnailAudioRelay: Shutdown complete");
    }

    // =================== AUDIO TRANSMISSION READINESS CHECKS ===================

    /**
     * Check if a snail is ready to transmit/receive audio
     */
    private boolean isSnailReadyForAudio(TransponderSnailBlockEntity snail) {
        if (snail == null) {
            return false;
        }

        // Must be in CONNECTED call state
        if (snail.getCurrentCallState() != CallStateSyncPacket.CallState.CONNECTED) {
            System.out.println("SnailAudioRelay: Snail not ready - call state: " + snail.getCurrentCallState());
            return false;
        }

        // Must show "in call" visual state (transponder_snail_call or transponder_snail_active)
        if (!snail.getCurrentVisualCallState()) {
            System.out.println("SnailAudioRelay: Snail not ready - visual call state not active");
            return false;
        }

        // Must be audio ready (call fully connected with audio channels)
        if (!snail.isAudioReady()) {
            System.out.println("SnailAudioRelay: Snail not ready - audio not ready");
            return false;
        }

        return true;
    }

    /**
     * Process audio transmission after readiness checks pass
     */
    private void processAudioTransmission(ServerPlayer speaker, CallSession callSession,
                                          TransponderSnailBlockEntity transmittingSnail,
                                          MicrophonePacket microphonePacket) {

        // Relay audio to all other snails in the call
        for (BlockPos snailPos : callSession.getInvolvedBlockPositions()) {
            if (snailPos.equals(transmittingSnail.getBlockPos())) {
                continue; // Don't relay to the transmitting snail
            }

            // Get the proximity channel for this position
            AudioChannel proximityChannel = callSession.getProximityChannel(snailPos);
            if (proximityChannel != null) {
                try {
                    // Send audio to this snail's location
                    proximityChannel.send(microphonePacket.getOpusEncodedData());

                    System.out.println("SnailAudioRelay: Relayed audio to snail at " + snailPos);

                } catch (Exception e) {
                    System.err.println("SnailAudioRelay: Error relaying audio to " + snailPos + ": " + e.getMessage());
                }
            }
        }

        // Update last activity time for the call session
        callSession.updateActivity();
    }

    // =================== HELPER CLASSES ===================

    /**
     * FIX 2: Continuous audio holder that never returns null (prevents stuttering)
     */
    private static class ContinuousAudioHolder {
        private volatile short[] currentAudio = SILENCE_BUFFER;
        private volatile long lastUpdateTime = System.currentTimeMillis();
        private static final long SILENCE_TIMEOUT = 50; // 50ms timeout to silence

        void setCurrentAudio(short[] audio) {
            if (audio != null && audio.length > 0) {
                this.currentAudio = audio;
                this.lastUpdateTime = System.currentTimeMillis();
            }
        }

        short[] getCurrentAudio() {
            // FIX 2: Return silence if no recent audio update (prevents stuttering on old data)
            long timeSinceUpdate = System.currentTimeMillis() - lastUpdateTime;
            if (timeSinceUpdate > SILENCE_TIMEOUT) {
                return SILENCE_BUFFER;
            }
            return currentAudio;
        }
    }

    /**
     * Manages a single audio connection to a snail
     */
    private static class AudioConnection {
        final OpusEncoder encoder;
        final AudioPlayer player;
        final ContinuousAudioHolder audioHolder;
        private volatile boolean valid = true;

        AudioConnection(OpusEncoder encoder, AudioPlayer player, ContinuousAudioHolder audioHolder) {
            this.encoder = encoder;
            this.player = player;
            this.audioHolder = audioHolder;
        }

        void sendAudio(short[] audioData) {
            if (valid && player.isPlaying()) {
                audioHolder.setCurrentAudio(audioData);
            }
        }

        boolean isValid() {
            return valid && player.isPlaying();
        }

        void cleanup() {
            valid = false;
            try {
                if (player.isPlaying()) {
                    player.stopPlaying();
                }
                encoder.close();
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Error cleaning up connection: " + e.getMessage());
            }
        }
    }

    /**
     * Cache for nearby snail lookups
     */
    private static class NearbySnailCache {
        final TransponderSnailBlockEntity snail;
        final long timestamp;

        NearbySnailCache(TransponderSnailBlockEntity snail) {
            this.snail = snail;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid() {
            return (System.currentTimeMillis() - timestamp) < CACHE_TIMEOUT_MS;
        }
    }
}