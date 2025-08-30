package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoderMode;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles player audio forwarding for Transponder Snail calls using AudioSenders and AudioPlayers
 * This creates a two-way audio bridge between players in a call
 */
public class PlayerAudioForwarder {

    private final VoicechatServerApi voicechatApi;
    private final Map<UUID, PlayerAudioInfo> playerAudioInfo = new ConcurrentHashMap<>();
    private final Map<UUID, CallAudioSession> callSessions = new ConcurrentHashMap<>();

    public PlayerAudioForwarder(VoicechatServerApi api) {
        this.voicechatApi = api;
    }

    /**
     * Sets up audio forwarding for a player in a call
     * This creates an AudioSender for the player to simulate their voice to other participants
     */
    public boolean setupPlayerAudio(UUID callId, ServerPlayer player, AudioChannel channel) {
        try {
            UUID playerId = player.getUUID();

            // Get voice chat connection
            VoicechatConnection connection = voicechatApi.getConnectionOf(playerId);
            if (connection == null) {
                System.err.println("PlayerAudioForwarder: No voice chat connection for " + player.getName().getString());
                return false;
            }

            // Create audio sender for this player (simulates them speaking to others)
            AudioSender audioSender = voicechatApi.createAudioSender(connection);
            if (audioSender == null) {
                System.err.println("PlayerAudioForwarder: Failed to create AudioSender for " + player.getName().getString());
                return false;
            }

            // Register the audio sender
            if (!voicechatApi.registerAudioSender(audioSender)) {
                System.err.println("PlayerAudioForwarder: Failed to register AudioSender for " + player.getName().getString());
                return false;
            }

            // Create encoder for audio processing
            OpusEncoder encoder = voicechatApi.createEncoder(OpusEncoderMode.AUDIO);
            if (encoder == null) {
                System.err.println("PlayerAudioForwarder: Failed to create OpusEncoder for " + player.getName().getString());
                voicechatApi.unregisterAudioSender(audioSender);
                return false;
            }

            // Create AudioPlayer to play other participants' voices through the channel
            AudioPlayer audioPlayer = voicechatApi.createAudioPlayer(channel, encoder, new short[0]);
            if (audioPlayer == null) {
                System.err.println("PlayerAudioForwarder: Failed to create AudioPlayer for " + player.getName().getString());
                voicechatApi.unregisterAudioSender(audioSender);
                return false;
            }

            // Store audio components
            PlayerAudioInfo audioInfo = new PlayerAudioInfo(
                    playerId,
                    connection,
                    audioSender,
                    encoder,
                    audioPlayer,
                    System.currentTimeMillis()
            );

            playerAudioInfo.put(playerId, audioInfo);

            // Add to call session
            CallAudioSession session = callSessions.computeIfAbsent(callId, k -> new CallAudioSession(callId));
            session.addPlayer(playerId, audioInfo);

            System.out.println("PlayerAudioForwarder: Set up audio forwarding for " + player.getName().getString() + " in call " + callId);
            return true;

        } catch (Exception e) {
            System.err.println("PlayerAudioForwarder: Failed to setup audio for player " + player.getName().getString() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Simulates sending audio data from one player to others in the call
     * In a real implementation, this would capture the player's actual microphone input
     */
    public void simulatePlayerAudio(UUID playerId, short[] audioData) {
        PlayerAudioInfo audioInfo = playerAudioInfo.get(playerId);
        if (audioInfo == null || !audioInfo.isActive()) {
            return;
        }

        try {
            // Encode the audio data to Opus format
            byte[] opusData = audioInfo.encoder.encode(audioData);
            if (opusData != null && audioInfo.audioSender.canSend()) {
                // Send the audio through the AudioSender
                audioInfo.audioSender.send(opusData);
            }
        } catch (Exception e) {
            System.err.println("PlayerAudioForwarder: Error simulating audio for player " + playerId + ": " + e.getMessage());
        }
    }

    /**
     * Plays audio data to a specific player through their AudioPlayer
     * This would be used to play other participants' voices
     */
    public void playAudioToPlayer(UUID playerId, short[] audioData) {
        PlayerAudioInfo audioInfo = playerAudioInfo.get(playerId);
        if (audioInfo == null || !audioInfo.isActive()) {
            return;
        }

        try {
            // Add audio data to the player's buffer for playback
            audioInfo.addAudioData(audioData);

            // If the audio player is not currently playing, start it
            if (!audioInfo.audioPlayer.isPlaying() && !audioInfo.audioPlayer.isStopped()) {
                audioInfo.audioPlayer.startPlaying();
            }
        } catch (Exception e) {
            System.err.println("PlayerAudioForwarder: Error playing audio to player " + playerId + ": " + e.getMessage());
        }
    }

    /**
     * Forwards audio between all participants in a call
     * This simulates the two-way audio communication
     */
    public void forwardAudioInCall(UUID callId, UUID senderPlayerId, short[] audioData) {
        CallAudioSession session = callSessions.get(callId);
        if (session == null) {
            return;
        }

        // Forward audio to all other participants in the call
        for (UUID participantId : session.getParticipants()) {
            if (!participantId.equals(senderPlayerId)) {
                playAudioToPlayer(participantId, audioData);
            }
        }
    }

    /**
     * Starts audio processing for a player
     */
    public boolean startPlayerAudio(UUID playerId) {
        PlayerAudioInfo audioInfo = playerAudioInfo.get(playerId);
        if (audioInfo == null) {
            return false;
        }

        try {
            audioInfo.setActive(true);

            // Start the audio player if it's not already playing
            if (!audioInfo.audioPlayer.isPlaying() && !audioInfo.audioPlayer.isStopped()) {
                audioInfo.audioPlayer.startPlaying();
            }

            System.out.println("PlayerAudioForwarder: Started audio processing for player " + playerId);
            return true;
        } catch (Exception e) {
            System.err.println("PlayerAudioForwarder: Failed to start audio processing: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stops audio processing for a player
     */
    public boolean stopPlayerAudio(UUID playerId) {
        PlayerAudioInfo audioInfo = playerAudioInfo.get(playerId);
        if (audioInfo == null) {
            return false;
        }

        try {
            audioInfo.setActive(false);

            // Stop the audio player
            if (audioInfo.audioPlayer.isPlaying()) {
                audioInfo.audioPlayer.stopPlaying();
            }

            System.out.println("PlayerAudioForwarder: Stopped audio processing for player " + playerId);
            return true;
        } catch (Exception e) {
            System.err.println("PlayerAudioForwarder: Failed to stop audio processing: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes a player from audio forwarding and cleans up resources
     */
    public boolean removePlayer(UUID callId, UUID playerId) {
        PlayerAudioInfo audioInfo = playerAudioInfo.remove(playerId);
        if (audioInfo == null) {
            return false;
        }

        try {
            // Stop audio processing
            audioInfo.setActive(false);

            // Stop audio player
            if (audioInfo.audioPlayer.isPlaying()) {
                audioInfo.audioPlayer.stopPlaying();
            }

            // Unregister audio sender
            voicechatApi.unregisterAudioSender(audioInfo.audioSender);

            // Remove from call session
            CallAudioSession session = callSessions.get(callId);
            if (session != null) {
                session.removePlayer(playerId);
                if (session.isEmpty()) {
                    callSessions.remove(callId);
                }
            }

            System.out.println("PlayerAudioForwarder: Removed player " + playerId + " from audio forwarding");
            return true;

        } catch (Exception e) {
            System.err.println("PlayerAudioForwarder: Error removing player from audio forwarding: " + e.getMessage());
            return false;
        }
    }

    /**
     * Removes a call session and cleans up all associated audio resources
     */
    public boolean removeCallSession(UUID callId) {
        CallAudioSession session = callSessions.remove(callId);
        if (session == null) {
            return false;
        }

        // Remove all players from this call
        Set<UUID> participants = new HashSet<>(session.getParticipants());
        for (UUID playerId : participants) {
            removePlayer(callId, playerId);
        }

        System.out.println("PlayerAudioForwarder: Removed call session " + callId);
        return true;
    }

    /**
     * Checks if a player has active audio forwarding
     */
    public boolean hasPlayerAudio(UUID playerId) {
        PlayerAudioInfo audioInfo = playerAudioInfo.get(playerId);
        return audioInfo != null && audioInfo.isActive();
    }

    /**
     * Gets debug information about active audio sessions
     */
    public Map<UUID, String> getAudioDebugInfo() {
        Map<UUID, String> info = new HashMap<>();

        for (Map.Entry<UUID, CallAudioSession> entry : callSessions.entrySet()) {
            CallAudioSession session = entry.getValue();
            info.put(entry.getKey(), "Call with " + session.getParticipants().size() + " participants");
        }

        return info;
    }

    /**
     * Cleanup all audio resources (for server shutdown)
     */
    public void cleanup() {
        // Remove all call sessions
        Set<UUID> callIds = new HashSet<>(callSessions.keySet());
        for (UUID callId : callIds) {
            removeCallSession(callId);
        }

        // Clean up any remaining player audio info
        Set<UUID> playerIds = new HashSet<>(playerAudioInfo.keySet());
        for (UUID playerId : playerIds) {
            PlayerAudioInfo audioInfo = playerAudioInfo.remove(playerId);
            if (audioInfo != null) {
                try {
                    audioInfo.setActive(false);
                    if (audioInfo.audioPlayer.isPlaying()) {
                        audioInfo.audioPlayer.stopPlaying();
                    }
                    voicechatApi.unregisterAudioSender(audioInfo.audioSender);
                } catch (Exception e) {
                    System.err.println("PlayerAudioForwarder: Error during cleanup: " + e.getMessage());
                }
            }
        }

        System.out.println("PlayerAudioForwarder: Cleaned up all audio resources");
    }

    /**
     * Inner class to store player audio information
     */
    private static class PlayerAudioInfo {
        final UUID playerId;
        final VoicechatConnection connection;
        final AudioSender audioSender;
        final OpusEncoder encoder;
        final AudioPlayer audioPlayer;
        final long creationTime;

        private boolean active = false;
        private final Queue<short[]> audioBuffer = new LinkedList<>();
        private final int maxBufferSize = VoiceChatConstants.AUDIO_BUFFER_SIZE;

        public PlayerAudioInfo(UUID playerId, VoicechatConnection connection, AudioSender audioSender,
                               OpusEncoder encoder, AudioPlayer audioPlayer, long creationTime) {
            this.playerId = playerId;
            this.connection = connection;
            this.audioSender = audioSender;
            this.encoder = encoder;
            this.audioPlayer = audioPlayer;
            this.creationTime = creationTime;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public boolean isActive() {
            return active;
        }

        public synchronized void addAudioData(short[] audioData) {
            audioBuffer.offer(audioData);
            // Keep buffer size manageable
            while (audioBuffer.size() > maxBufferSize) {
                audioBuffer.poll();
            }
        }

        public synchronized short[] getLatestAudioData() {
            return audioBuffer.poll(); // Get and remove the oldest audio data
        }
    }

    /**
     * Inner class to manage audio session for a call
     */
    private static class CallAudioSession {
        final UUID callId;
        final Set<UUID> participants = ConcurrentHashMap.newKeySet();
        final long creationTime;

        public CallAudioSession(UUID callId) {
            this.callId = callId;
            this.creationTime = System.currentTimeMillis();
        }

        public void addPlayer(UUID playerId, PlayerAudioInfo audioInfo) {
            participants.add(playerId);
        }

        public void removePlayer(UUID playerId) {
            participants.remove(playerId);
        }

        public Set<UUID> getParticipants() {
            return new HashSet<>(participants);
        }

        public boolean isEmpty() {
            return participants.isEmpty();
        }
    }
}