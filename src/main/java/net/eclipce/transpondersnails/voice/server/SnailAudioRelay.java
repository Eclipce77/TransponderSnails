package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.eclipce.transpondersnails.block.entity.TransponderSnailBlockEntity;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles real-time audio forwarding between Transponder Snails
 * Captures microphone input near one snail and forwards it to connected snails
 */
public class SnailAudioRelay {

    private final VoicechatServerApi voiceChatApi;
    private final TransponderCallManager callManager;

    // Track active audio players for each call session
    private final Map<UUID, Map<BlockPos, AudioPlayer>> activeAudioPlayers = new ConcurrentHashMap<>();

    // Track which players are currently transmitting
    private final Set<UUID> activeTransmitters = ConcurrentHashMap.newKeySet();

    // Performance optimization - cache nearby snails
    private final Map<UUID, NearbySnailCache> playerSnailCache = new ConcurrentHashMap<>();
    private static final long CACHE_TIMEOUT_MS = 1000; // 1 second cache

    // Audio buffering for forwarding
    private final Map<UUID, Queue<short[]>> audioBuffers = new ConcurrentHashMap<>();

    public SnailAudioRelay(VoicechatServerApi voiceChatApi, TransponderCallManager callManager) {
        this.voiceChatApi = voiceChatApi;
        this.callManager = callManager;

        System.out.println("SnailAudioRelay: Initialized microphone forwarding system");
    }

    /**
     * Main audio processing method - called when a player speaks into their microphone
     * This should be called from your VoiceChat event handler
     */
    public void onMicrophonePacket(MicrophonePacketEvent event) {
        try {
            // Get the VoiceChat ServerPlayer and convert to Minecraft ServerPlayer
            de.maxhenkel.voicechat.api.ServerPlayer vcSpeaker = event.getSenderConnection().getPlayer();
            if (vcSpeaker == null) {
                return;
            }

            // Convert to Minecraft ServerPlayer
            ServerPlayer speaker = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(vcSpeaker.getUuid());
            if (speaker == null) {
                return;
            }

            // Only process if player is in a call
            if (!callManager.isInCall(speaker.getUUID())) {
                return;
            }

            UUID callId = callManager.getPlayerCallId(speaker.getUUID());
            if (callId == null) {
                return;
            }

            // Get the active call session
            CallSession callSession = getCallSessionById(callId);
            if (callSession == null || callSession.getState() != CallSession.CallState.CONNECTED) {
                return;
            }

            // Find the snail the speaker is near
            TransponderSnailBlockEntity nearbySnail = findNearestSnailInCall(speaker, callSession);
            if (nearbySnail == null) {
                return;
            }

            // Calculate distance-based volume
            float volume = calculateVolumeByDistance(speaker, nearbySnail.getBlockPos());
            if (volume <= 0.0f) {
                return; // Too far away
            }

            // Get audio data from the event and decode it
            byte[] opusData = event.getPacket().getOpusEncodedData();
            if (opusData == null || opusData.length == 0) {
                return;
            }

            // Decode Opus audio to PCM for forwarding
            short[] pcmAudio = decodeOpusAudio(opusData);
            if (pcmAudio == null || pcmAudio.length == 0) {
                return;
            }

            // Apply volume to audio data
            short[] adjustedAudio = applyVolumeToAudio(pcmAudio, volume);

            // Buffer the audio for this speaker
            bufferAudioForPlayer(speaker.getUUID(), adjustedAudio);

            // Mark player as actively transmitting
            activeTransmitters.add(speaker.getUUID());

            // Start/update audio players for call participants if not already running
            ensureAudioPlayersForCall(callSession, nearbySnail, speaker.getUUID());

            // Update call activity
            callSession.updateActivity();

            System.out.println("SnailAudioRelay: Buffered audio from player " + speaker.getName().getString() +
                    " near snail #" + nearbySnail.getSnailNumber() + " (volume: " + volume + ")");

        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Error processing microphone packet: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Decode Opus audio data to PCM
     */
    @Nullable
    private short[] decodeOpusAudio(byte[] opusData) {
        try {
            OpusDecoder decoder = voiceChatApi.createDecoder();
            if (decoder == null) {
                return null;
            }

            short[] pcmData = decoder.decode(opusData);
            decoder.close(); // Important: close to prevent memory leaks
            return pcmData;
        } catch (Exception e) {
            System.err.println("SnailAudioRelay: Failed to decode Opus audio: " + e.getMessage());
            return null;
        }
    }

    /**
     * Apply volume adjustment to PCM audio data
     */
    private short[] applyVolumeToAudio(short[] originalAudio, float volume) {
        if (volume >= 1.0f) {
            return originalAudio; // No adjustment needed
        }

        short[] adjustedAudio = new short[originalAudio.length];
        for (int i = 0; i < originalAudio.length; i++) {
            // Apply volume and clamp to prevent overflow
            int adjusted = (int)(originalAudio[i] * volume);
            adjustedAudio[i] = (short)Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, adjusted));
        }
        return adjustedAudio;
    }

    /**
     * Buffer audio data for a specific player
     */
    private void bufferAudioForPlayer(UUID playerId, short[] audioData) {
        Queue<short[]> buffer = audioBuffers.computeIfAbsent(playerId, k -> new LinkedList<>());

        buffer.offer(audioData);

        // Keep only the last 10 audio frames to prevent memory buildup
        while (buffer.size() > 10) {
            buffer.poll();
        }
    }

    /**
     * Get buffered audio for a player (used by audio suppliers)
     */
    @Nullable
    private short[] getBufferedAudioForPlayer(UUID playerId) {
        Queue<short[]> buffer = audioBuffers.get(playerId);
        if (buffer == null || buffer.isEmpty()) {
            return new short[VoiceChatConstants.AUDIO_FRAME_SIZE]; // Return silence
        }

        short[] audio = buffer.poll();
        return audio != null ? audio : new short[VoiceChatConstants.AUDIO_FRAME_SIZE];
    }

    /**
     * Ensure audio players are running for all target snails in the call
     */
    private void ensureAudioPlayersForCall(CallSession callSession, TransponderSnailBlockEntity sourceSnail, UUID speakerPlayerId) {
        UUID callId = callSession.getCallId();
        Map<BlockPos, AudioPlayer> callPlayers = activeAudioPlayers.computeIfAbsent(callId, k -> new ConcurrentHashMap<>());

        // Get all snail positions involved in this call
        Set<BlockPos> targetPositions = callSession.getInvolvedBlockPositions();

        for (BlockPos targetPos : targetPositions) {
            // Don't send audio back to the source snail
            if (targetPos.equals(sourceSnail.getBlockPos())) {
                continue;
            }

            // Check if audio player already exists for this position
            if (callPlayers.containsKey(targetPos)) {
                continue; // Already have a player for this position
            }

            // Get the locational audio channel for this position
            LocationalAudioChannel channel = (LocationalAudioChannel) callSession.getProximityChannel(targetPos);
            if (channel == null) {
                continue;
            }

            try {
                // Create audio supplier that provides buffered audio
                OpusEncoder encoder = voiceChatApi.createEncoder();
                if (encoder == null) {
                    continue;
                }

                // Create audio player with supplier
                AudioPlayer player = voiceChatApi.createAudioPlayer(
                        channel,
                        encoder,
                        () -> getBufferedAudioForPlayer(speakerPlayerId)
                );

                if (player != null) {
                    callPlayers.put(targetPos, player);
                    player.startPlaying();

                    System.out.println("SnailAudioRelay: Started audio player for snail at " + targetPos);
                }

            } catch (Exception e) {
                System.err.println("SnailAudioRelay: Failed to create audio player for " + targetPos + ": " + e.getMessage());
            }
        }
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

        if (distance >= maxRangeSq) {
            return 0.0f; // Out of range
        }

        // Linear falloff from 1.0 at snail to 0.1 at max range (never completely silent)
        double normalizedDistance = Math.sqrt(distance) / maxRange;
        return Math.max(0.1f, (float)(1.0 - normalizedDistance));
    }

    /**
     * Find the nearest snail that's participating in the given call
     */
    @Nullable
    private TransponderSnailBlockEntity findNearestSnailInCall(ServerPlayer player, CallSession callSession) {
        // Check cache first
        UUID playerId = player.getUUID();
        NearbySnailCache cache = playerSnailCache.get(playerId);
        if (cache != null && cache.isValid()) {
            return cache.snail;
        }

        BlockPos playerPos = player.blockPosition();
        TransponderSnailBlockEntity closestSnail = null;
        double closestDistance = Double.MAX_VALUE;
        double maxRange = VoiceChatConstants.getSnailInteractionRange();
        double maxRangeSq = maxRange * maxRange;

        // Check all snails involved in this call
        for (Integer snailNumber : callSession.getParticipantSnailNumbers()) {
            TransponderSnailBlockEntity snail = callManager.getRegisteredSnailBlock(snailNumber);
            if (snail == null) {
                continue;
            }

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

        // Cache the result
        if (closestSnail != null) {
            playerSnailCache.put(playerId, new NearbySnailCache(closestSnail));
        }

        return closestSnail;
    }

    /**
     * Get call session by ID from the call manager
     */
    @Nullable
    private CallSession getCallSessionById(UUID callId) {
        return callManager.getActiveCalls().stream()
                .filter(call -> call.getCallId().equals(callId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Called when a player stops transmitting
     */
    public void onPlayerStoppedTransmitting(UUID playerId) {
        activeTransmitters.remove(playerId);

        // Clear audio buffer for this player
        audioBuffers.remove(playerId);
    }

    /**
     * Clean up when a call ends
     */
    public void onCallEnded(UUID callId) {
        // Stop and remove all audio players for this call
        Map<BlockPos, AudioPlayer> callPlayers = activeAudioPlayers.remove(callId);
        if (callPlayers != null) {
            for (AudioPlayer player : callPlayers.values()) {
                try {
                    if (player.isPlaying()) {
                        player.stopPlaying();
                    }
                } catch (Exception e) {
                    System.err.println("SnailAudioRelay: Error stopping audio player: " + e.getMessage());
                }
            }
        }

        // Remove audio buffers for players no longer in calls
        audioBuffers.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            return !callManager.isInCall(playerId);
        });

        // Clear transmission state
        activeTransmitters.removeIf(playerId -> !callManager.isInCall(playerId));

        System.out.println("SnailAudioRelay: Cleaned up audio relay for ended call " + callId.toString().substring(0, 8));
    }

    /**
     * Clean up cached data periodically
     */
    public void cleanupCaches() {
        long now = System.currentTimeMillis();

        // Remove expired snail caches
        playerSnailCache.entrySet().removeIf(entry -> !entry.getValue().isValid());

        // Remove offline players from tracking
        Set<UUID> onlinePlayerIds = new HashSet<>();
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()
                    .forEach(player -> onlinePlayerIds.add(player.getUUID()));
        }

        activeTransmitters.removeIf(playerId -> !onlinePlayerIds.contains(playerId));
        playerSnailCache.entrySet().removeIf(entry -> !onlinePlayerIds.contains(entry.getKey()));
        audioBuffers.entrySet().removeIf(entry -> !onlinePlayerIds.contains(entry.getKey()));
    }

    /**
     * Get debug information about active audio forwarding
     */
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("activeTransmitters", activeTransmitters.size());
        info.put("cachedSnailLookups", playerSnailCache.size());
        info.put("audioBuffers", audioBuffers.size());
        info.put("activeCallPlayers", activeAudioPlayers.size());

        // Count total audio players
        int totalPlayers = activeAudioPlayers.values().stream()
                .mapToInt(Map::size)
                .sum();
        info.put("totalAudioPlayers", totalPlayers);

        // List active transmitters
        List<String> transmitterNames = new ArrayList<>();
        for (UUID playerId : activeTransmitters) {
            ServerPlayer player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                transmitterNames.add(player.getName().getString());
            }
        }
        info.put("activeTransmitterNames", transmitterNames);

        return info;
    }

    // =================== HELPER CLASSES ===================

    /**
     * Cache for nearby snail lookups to improve performance
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