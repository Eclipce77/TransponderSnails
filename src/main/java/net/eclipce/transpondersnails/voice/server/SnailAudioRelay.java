package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.network.packets.CallStateSyncPacket;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Improved audio relay system for Transponder Snails
 * Uses persistent audio streams and proper buffering
 */
public class SnailAudioRelay {

    private final VoicechatServerApi voiceChatApi;
    private final TransponderCallManager callManager;

    // Persistent decoders/encoders per connection
    private final Map<UUID, OpusDecoder> playerDecoders = new ConcurrentHashMap<>();
    private final Map<UUID, Map<BlockPos, EncoderPlayerPair>> callAudioStreams = new ConcurrentHashMap<>();

    // Improved audio buffering with timing
    private final Map<UUID, AudioStreamBuffer> audioStreams = new ConcurrentHashMap<>();

    // Performance optimization - cache nearby snails
    private final Map<UUID, NearbySnailCache> playerSnailCache = new ConcurrentHashMap<>();
    private static final long CACHE_TIMEOUT_MS = 1000;

    // Audio processing thread
    private final ScheduledExecutorService audioProcessor = Executors.newSingleThreadScheduledExecutor(
            r -> new Thread(r, "SnailAudioRelay-Processor"));

    // Audio activity tracking for blockstate updates only
    private final Map<BlockPos, Long> activeAudioAtPosition = new ConcurrentHashMap<>();
    private final Set<BlockPos> snailsReceivingAudio = ConcurrentHashMap.newKeySet();
    private static final long AUDIO_ACTIVITY_TIMEOUT_MS = 500; // 500ms timeout for audio activity

    public SnailAudioRelay(VoicechatServerApi voiceChatApi, TransponderCallManager callManager) {
        this.voiceChatApi = voiceChatApi;
        this.callManager = callManager;

        // Start audio processing loop
        audioProcessor.scheduleAtFixedRate(this::processAudioStreams, 0, 20, TimeUnit.MILLISECONDS);

        System.out.println("SnailAudioRelay: Initialized improved audio relay system");
    }

    /**
     * Find a snail block entity at a specific position
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
     * Main audio processing - now just captures and buffers
     */
    public void onMicrophonePacket(MicrophonePacketEvent event) {
        try {
            de.maxhenkel.voicechat.api.ServerPlayer vcSpeaker = event.getSenderConnection().getPlayer();
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

            // Calculate volume
            float volume = calculateVolumeByDistance(speaker, nearbySnail.getBlockPos());
            if (volume <= 0.0f) return;

            // Get or create decoder for this player
            OpusDecoder decoder = getOrCreateDecoder(speaker.getUUID());
            if (decoder == null) return;

            // Decode audio
            byte[] opusData = event.getPacket().getOpusEncodedData();
            if (opusData == null || opusData.length == 0) return;

            short[] pcmAudio = decoder.decode(opusData);
            if (pcmAudio == null || pcmAudio.length == 0) return;

            // Apply volume
            short[] adjustedAudio = applyVolumeToAudio(pcmAudio, volume);

            // Add to audio stream buffer
            AudioStreamBuffer streamBuffer = getOrCreateAudioStream(speaker.getUUID(), callSession, nearbySnail);
            streamBuffer.addAudioFrame(adjustedAudio, System.currentTimeMillis());

            // ONLY NEW LINE: Track audio activity for blockstate updates
            trackAudioActivity(callSession, nearbySnail, adjustedAudio);

            // Ensure audio streams are set up for all target snails
            ensureAudioStreamsForCall(callSession, nearbySnail, speaker.getUUID());

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error processing microphone packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get or create a persistent decoder for a player
     */
    private OpusDecoder getOrCreateDecoder(UUID playerId) {
        return playerDecoders.computeIfAbsent(playerId, k -> {
            try {
                return voiceChatApi.createDecoder();
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Failed to create decoder for player " + k);
                return null;
            }
        });
    }

    /**
     * Get or create audio stream buffer for a player
     */
    private AudioStreamBuffer getOrCreateAudioStream(UUID playerId, CallSession callSession,
                                                     TransponderSnailBlockEntity sourceSnail) {
        return audioStreams.computeIfAbsent(playerId, k ->
                new AudioStreamBuffer(playerId, callSession.getCallId(), sourceSnail.getBlockPos()));
    }

    /**
     * Process audio streams on dedicated thread - maintains consistent timing
     */
    private void processAudioStreams() {
        try {
            // ONLY NEW LINE: Clean up expired audio activity for blockstates
            cleanupExpiredAudioActivity();

            long currentTime = System.currentTimeMillis();

            for (AudioStreamBuffer stream : audioStreams.values()) {
                if (!stream.isActive()) continue;

                // Get target positions for this stream's call
                CallSession callSession = getCallSessionById(stream.callId);
                if (callSession == null) continue;

                Set<BlockPos> targetPositions = callSession.getInvolvedBlockPositions();
                targetPositions.remove(stream.sourcePosition); // Don't send to source

                // Get audio data to send (maybe silence if no recent audio)
                short[] audioToSend = stream.getNextAudioFrame(currentTime);
                if (audioToSend == null) continue;

                // Send to all target positions
                Map<BlockPos, EncoderPlayerPair> streamPairs = callAudioStreams.get(stream.callId);
                if (streamPairs == null) continue;

                for (BlockPos targetPos : targetPositions) {
                    EncoderPlayerPair pair = streamPairs.get(targetPos);
                    if (pair != null && pair.isValid()) {
                        pair.sendAudio(audioToSend);
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error in audio processing loop: " + e.getMessage());
        }
    }

    /**
     * Ensure persistent audio streams exist for all target snails in the call
     */
    private void ensureAudioStreamsForCall(CallSession callSession, TransponderSnailBlockEntity sourceSnail,
                                           UUID speakerPlayerId) {
        UUID callId = callSession.getCallId();
        Map<BlockPos, EncoderPlayerPair> streamPairs = callAudioStreams.computeIfAbsent(callId,
                k -> new ConcurrentHashMap<>());

        Set<BlockPos> targetPositions = callSession.getInvolvedBlockPositions();

        for (BlockPos targetPos : targetPositions) {
            if (targetPos.equals(sourceSnail.getBlockPos())) continue;
            if (streamPairs.containsKey(targetPos)) continue;

            // Get the locational audio channel for this position
            LocationalAudioChannel channel = (LocationalAudioChannel) callSession.getProximityChannel(targetPos);
            if (channel == null) continue;

            try {
                OpusEncoder encoder = voiceChatApi.createEncoder();
                if (encoder == null) continue;

                // Create audio supplier that reads from our stream buffer
                AudioPlayer player = voiceChatApi.createAudioPlayer(
                        channel,
                        encoder,
                        () -> {
                            AudioStreamBuffer stream = audioStreams.get(speakerPlayerId);
                            if (stream != null && stream.hasAudio()) {
                                return stream.getCurrentAudioFrame();
                            }
                            return new short[VoiceChatConstants.AUDIO_FRAME_SIZE]; // Silence
                        }
                );

                if (player != null) {
                    EncoderPlayerPair pair = new EncoderPlayerPair(encoder, player);
                    streamPairs.put(targetPos, pair);
                    player.startPlaying();

                    System.out.println("SnailAudioRelay: Created persistent audio stream for snail at " + targetPos);
                }

            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Failed to create audio stream for " + targetPos + ": " + e.getMessage());
            }
        }
    }

    /**
     * Check if audio contains audible content (not just silence/noise)
     */
    private boolean hasAudibleContent(short[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return false;
        }

        // Calculate RMS (Root Mean Square) to detect actual audio content
        long sumSquares = 0;
        for (short sample : audioData) {
            sumSquares += (long)sample * sample;
        }

        double rms = Math.sqrt((double)sumSquares / audioData.length);

        // Threshold for detecting actual audio vs silence/noise
        double threshold = 100.0; // Adjust if needed

        return rms > threshold;
    }

    /**
     * Apply volume adjustment to PCM audio data
     */
    private short[] applyVolumeToAudio(short[] originalAudio, float volume) {
        if (volume >= 1.0f) return originalAudio;

        short[] adjustedAudio = new short[originalAudio.length];
        for (int i = 0; i < originalAudio.length; i++) {
            int adjusted = (int)(originalAudio[i] * volume);
            adjustedAudio[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, adjusted));
        }
        return adjustedAudio;
    }

    /**
     * Calculate volume based on player's distance from snail
     */
    private float calculateVolumeByDistance(ServerPlayer player, BlockPos snailPos) {
        double distance = player.distanceToSqr(
                snailPos.getX() + 0.5,
                snailPos.getY() + 0.5,
                snailPos.getZ() + 0.5
        );

        double maxRange = VoiceChatConstants.getSnailInteractionRange();
        double maxRangeSq = maxRange * maxRange;

        if (distance >= maxRangeSq) return 0.0f;

        double normalizedDistance = Math.sqrt(distance) / maxRange;
        return Math.max(0.1f, (float)(1.0 - normalizedDistance));
    }

    /**
     * Find the nearest snail that's participating in the given call
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
        double maxRange = VoiceChatConstants.getSnailInteractionRange();
        double maxRangeSq = maxRange * maxRange;

        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity snail = callManager.getRegisteredSnailBlock(snailNumber);
            if (snail == null) continue;

            double distance = player.distanceToSqr(
                    snail.getBlockPos().getX() + 0.5,
                    snail.getBlockPos().getY() + 0.5,
                    snail.getBlockPos().getZ() + 0.5
            );

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
     * Update a snail's blockstate for audio activity
     */
    private void updateSnailBlockstateForAudio(BlockPos snailPos, boolean receivingAudio) {
        TransponderSnailBlockEntity snail = findSnailAtPosition(snailPos);
        if (snail == null) {
            return;
        }

        if (snail.getCurrentCallState() != CallStateSyncPacket.CallState.CONNECTED) {
            return;
        }

        try {
            snail.onSoundStateChanged(snailPos, receivingAudio);
            System.out.println("SnailAudioRelay: Updated blockstate for snail at " + snailPos +
                    " - receiving audio: " + receivingAudio);
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error updating blockstate: " + e.getMessage());
        }
    }

    /**
     * Track audio activity for blockstate updates only
     */
    private void trackAudioActivity(CallSession callSession, TransponderSnailBlockEntity sourceSnail, short[] audioData) {
        // Only track if audio contains actual content
        if (!hasAudibleContent(audioData)) {
            return;
        }

        Set<BlockPos> targetPositions = callSession.getInvolvedBlockPositions();
        long currentTime = System.currentTimeMillis();

        for (BlockPos targetPos : targetPositions) {
            if (!targetPos.equals(sourceSnail.getBlockPos())) { // Don't update source snail
                activeAudioAtPosition.put(targetPos, currentTime);

                // If not already receiving audio, mark as receiving and update blockstate
                if (!snailsReceivingAudio.contains(targetPos)) {
                    snailsReceivingAudio.add(targetPos);
                    updateSnailBlockstateForAudio(targetPos, true);
                }
            }
        }
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
     * Clean up when a player leaves a call
     */
    public void onPlayerLeftCall(UUID playerId) {
        // Clean up decoder
        OpusDecoder decoder = playerDecoders.remove(playerId);
        if (decoder != null) {
            try {
                decoder.close();
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Error closing decoder for player " + playerId);
            }
        }

        // Clean up audio stream
        AudioStreamBuffer stream = audioStreams.remove(playerId);
        if (stream != null) {
            stream.cleanup();
        }

        // Clear cache
        playerSnailCache.remove(playerId);
    }

    /**
     * Clean up when a call ends
     */
    public void onCallEnded(UUID callId) {
        // Stop and remove all audio streams for this call
        Map<BlockPos, EncoderPlayerPair> streamPairs = callAudioStreams.remove(callId);
        if (streamPairs != null) {
            // NEW: Reset blockstates for snails that were in this call
            for (BlockPos pos : streamPairs.keySet()) {
                if (snailsReceivingAudio.remove(pos)) {
                    updateSnailBlockstateForAudio(pos, false);
                }
                activeAudioAtPosition.remove(pos);
            }

            for (EncoderPlayerPair pair : streamPairs.values()) {
                pair.cleanup();
            }
        }

        // Clean up audio streams for this call
        audioStreams.entrySet().removeIf(entry -> {
            AudioStreamBuffer stream = entry.getValue();
            if (stream.callId.equals(callId)) {
                stream.cleanup();
                return true;
            }
            return false;
        });

        System.out.println("SnailAudioRelay: Cleaned up audio relay for ended call " + callId.toString().substring(0, 8));
    }

    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        audioProcessor.shutdown();

        // Clean up all decoders
        for (OpusDecoder decoder : playerDecoders.values()) {
            try {
                decoder.close();
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Error closing decoder during shutdown");
            }
        }
        playerDecoders.clear();

        // Clean up all audio streams
        for (Map<BlockPos, EncoderPlayerPair> streamPairs : callAudioStreams.values()) {
            for (EncoderPlayerPair pair : streamPairs.values()) {
                pair.cleanup();
            }
        }
        callAudioStreams.clear();

        // Clean up stream buffers
        for (AudioStreamBuffer stream : audioStreams.values()) {
            stream.cleanup();
        }
        audioStreams.clear();
    }

    // =================== HELPER CLASSES ===================

    /**
     * Manages audio buffering with proper timing
     */
    private static class AudioStreamBuffer {
        final UUID playerId;
        final UUID callId;
        final BlockPos sourcePosition;
        private final Queue<TimestampedAudioFrame> audioFrames = new ConcurrentLinkedQueue<>();
        private long lastActivityTime;
        private short[] currentFrame = new short[VoiceChatConstants.AUDIO_FRAME_SIZE];

        AudioStreamBuffer(UUID playerId, UUID callId, BlockPos sourcePosition) {
            this.playerId = playerId;
            this.callId = callId;
            this.sourcePosition = sourcePosition;
            this.lastActivityTime = System.currentTimeMillis();
        }

        void addAudioFrame(short[] audioData, long timestamp) {
            audioFrames.offer(new TimestampedAudioFrame(audioData, timestamp));
            lastActivityTime = timestamp;

            // Keep only recent frames (200ms worth)
            while (audioFrames.size() > 10) {
                audioFrames.poll();
            }
        }

        short[] getNextAudioFrame(long currentTime) {
            TimestampedAudioFrame frame = audioFrames.poll();
            if (frame != null && (currentTime - frame.timestamp) < 100) { // Within 100ms
                currentFrame = frame.audioData;
                return currentFrame;
            }

            // No recent audio - return silence but maintain timing
            if ((currentTime - lastActivityTime) < 1000) { // Keep stream active for 1 second
                return new short[VoiceChatConstants.AUDIO_FRAME_SIZE]; // Silence
            }

            return null; // Stream inactive
        }

        short[] getCurrentAudioFrame() {
            return currentFrame;
        }

        boolean hasAudio() {
            return !audioFrames.isEmpty();
        }

        boolean isActive() {
            return (System.currentTimeMillis() - lastActivityTime) < 2000; // 2 second timeout
        }

        void cleanup() {
            audioFrames.clear();
        }

        private static class TimestampedAudioFrame {
            final short[] audioData;
            final long timestamp;

            TimestampedAudioFrame(short[] audioData, long timestamp) {
                this.audioData = audioData;
                this.timestamp = timestamp;
            }
        }
    }

    /**
     * Manages encoder and audio player pairs
     */
    private static class EncoderPlayerPair {
        final OpusEncoder encoder;
        final AudioPlayer player;
        private boolean valid = true;

        EncoderPlayerPair(OpusEncoder encoder, AudioPlayer player) {
            this.encoder = encoder;
            this.player = player;
        }

        void sendAudio(short[] audioData) {
            if (!valid || !player.isPlaying()) return;

            try {
                // The audio supplier will be called by the player automatically
                // We don't manually send here, just ensure the player is running
            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Error in audio stream");
                valid = false;
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
                System.err.println("SnailAudioRelay: Error cleaning up encoder/player pair");
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

    /**
     * Clean up expired audio activity for blockstate updates
     */
    private void cleanupExpiredAudioActivity() {
        long currentTime = System.currentTimeMillis();

        // Find expired positions
        Set<BlockPos> expiredPositions = new HashSet<>();
        activeAudioAtPosition.entrySet().removeIf(entry -> {
            boolean expired = (currentTime - entry.getValue()) > AUDIO_ACTIVITY_TIMEOUT_MS;
            if (expired) {
                expiredPositions.add(entry.getKey());
            }
            return expired;
        });

        // Update blockstates for positions that are no longer receiving audio
        for (BlockPos pos : expiredPositions) {
            if (snailsReceivingAudio.remove(pos)) {
                updateSnailBlockstateForAudio(pos, false);
            }
        }
    }
}