// Represents an active call with multiple participants
package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.audiochannel.StaticAudioChannel;
import de.maxhenkel.voicechat.api.audiosender.AudioSender;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@ForgeVoicechatPlugin
public class TransponderCall implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "transpondersnails";
    }

    @Override
    public void initialize(VoicechatApi api) {
        VoicechatPlugin.super.initialize(api);
    }

    private final UUID callId;
    private final CallType callType;
    private final VoicechatServerApi voicechatApi;
    private final String category;
    private final Map<UUID, CallParticipant> participants = new ConcurrentHashMap<>();

    // Audio management components
    private final AudioChannelManager audioChannelManager;
    private final PlayerAudioForwarder audioForwarder;
    private AudioChannel audioChannel;

    // Call location and settings
    private ServerLevel level;
    private BlockPos callLocation;
    private double hearingRange = 10.0;
    private final long creationTime;

    public TransponderCall(UUID callId, CallType callType, VoicechatServerApi api, String category,
                           AudioChannelManager channelManager, PlayerAudioForwarder forwarder) {
        this.callId = callId;
        this.callType = callType;
        this.voicechatApi = api;
        this.category = category;
        this.audioChannelManager = channelManager;
        this.audioForwarder = forwarder;
        this.creationTime = System.currentTimeMillis();

        updateHearingRange();
    }

    /**
     * Set hearing range based on call type
     */
    private void updateHearingRange() {
        switch (callType) {
            case LOCATIONAL:
                this.hearingRange = VoiceChatConstants.LOCATIONAL_SNAIL_RANGE;
                break;
            case HANDHELD:
                this.hearingRange = VoiceChatConstants.HANDHELD_SNAIL_RANGE;
                break;
            case PERSONAL:
            default:
                this.hearingRange = 0.0; // No proximity audio
                break;
        }
    }

    /**
     * Add a participant to the call with full audio setup
     */
    public boolean addParticipant(ServerPlayer player, ParticipantRole role) {
        UUID playerId = player.getUUID();

        try {
            // Check if player has voice chat
            VoicechatConnection connection = voicechatApi.getConnectionOf(playerId);
            if (connection == null) {
                player.sendSystemMessage(Component.literal("Voice chat not available!"));
                return false;
            }

            // Ensure audio channel exists
            if (audioChannel == null && !createAudioChannel(player.serverLevel())) {
                player.sendSystemMessage(Component.literal("Failed to create audio channel!"));
                return false;
            }

            // Set up player audio forwarding
            if (!audioForwarder.setupPlayerAudio(callId, player, audioChannel)) {
                player.sendSystemMessage(Component.literal("Failed to setup audio forwarding!"));
                return false;
            }

            // Add player to audio channel
            if (!audioChannelManager.addPlayerToChannel(callId, player)) {
                audioForwarder.removePlayer(callId, playerId);
                player.sendSystemMessage(Component.literal("Failed to join audio channel!"));
                return false;
            }

            // Create participant record
            CallParticipant participant = new CallParticipant(playerId, player.getName().getString(), role);
            participants.put(playerId, participant);

            // Start audio playback for this player
            audioForwarder.startPlayerAudio(playerId);

            // Notify other participants
            broadcastMessage(player.getName().getString() + " joined the call");

            System.out.println("TransponderCall: Successfully added participant " + player.getName().getString() + " to call " + callId);
            return true;

        } catch (Exception e) {
            System.err.println("TransponderCall: Failed to add participant " + player.getName().getString() + ": " + e.getMessage());
            e.printStackTrace();

            // Clean up on failure
            cleanupPlayer(playerId);
            return false;
        }
    }

    /**
     * Remove a participant from the call with full cleanup
     */
    public void removeParticipant(ServerPlayer player) {
        UUID playerId = player.getUUID();

        if (!participants.containsKey(playerId)) {
            return; // Player not in call
        }

        try {
            // Stop audio forwarding
            audioForwarder.stopPlayerAudio(playerId);
            audioForwarder.removePlayer(callId, playerId);

            // Remove from audio channel
            audioChannelManager.removePlayerFromChannel(callId, player);

            // Remove participant record
            participants.remove(playerId);

            player.sendSystemMessage(Component.literal("Left the call"));

            System.out.println("TransponderCall: Removed participant " + player.getName().getString() + " from call " + callId);

        } catch (Exception e) {
            System.err.println("TransponderCall: Error removing participant " + player.getName().getString() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create audio channel for this call
     */
    private boolean createAudioChannel(ServerLevel level) {
        if (audioChannel != null) {
            return true; // Already exists
        }

        try {
            // Use current player level if no specific level set
            ServerLevel channelLevel = this.level != null ? this.level : level;
            BlockPos channelLocation = this.callLocation != null ? this.callLocation : new BlockPos(0, 64, 0);

            audioChannel = audioChannelManager.createCallChannel(callId, callType, channelLevel, channelLocation);

            if (audioChannel != null) {
                System.out.println("TransponderCall: Created " + callType + " audio channel for call " + callId);
                return true;
            }

        } catch (Exception e) {
            System.err.println("TransponderCall: Failed to create audio channel: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Set the location for locational calls
     */
    public void setLocation(ServerLevel level, BlockPos pos) {
        this.level = level;
        this.callLocation = pos;
        updateHearingRange();

        // Update existing audio channel location if applicable
        if (audioChannel != null && (callType == CallType.LOCATIONAL || callType == CallType.HANDHELD)) {
            audioChannelManager.updateChannelLocation(callId, level, pos);
        }

        System.out.println("TransponderCall: Updated call location to " + pos + " for call " + callId);
    }

    /**
     * Update location for handheld calls (following a moving player)
     */
    public void updateHandheldLocation(ServerPlayer player) {
        if (callType == CallType.HANDHELD) {
            BlockPos playerPos = player.blockPosition();
            if (!playerPos.equals(callLocation)) {
                setLocation(player.serverLevel(), playerPos);
            }
        }
    }

    /**
     * Broadcast a message to all participants
     */
    public void broadcastMessage(String message) {
        Component messageComponent = Component.literal("[Transponder] " + message);

        for (UUID participantId : participants.keySet()) {
            ServerPlayer player = getPlayerById(participantId);
            if (player != null) {
                player.sendSystemMessage(messageComponent);
            }
        }
    }

    /**
     * Mute/unmute a participant
     */
    public boolean setParticipantMuted(UUID playerId, boolean muted) {
        CallParticipant participant = participants.get(playerId);
        if (participant == null) {
            return false;
        }

        participant.setMuted(muted);

        // Stop/start audio forwarding based on mute status
        if (muted) {
            audioForwarder.stopPlayerAudio(playerId);
        } else {
            audioForwarder.startPlayerAudio(playerId);
        }

        ServerPlayer player = getPlayerById(playerId);
        if (player != null) {
            String status = muted ? "muted" : "unmuted";
            broadcastMessage(player.getName().getString() + " is now " + status);
        }

        return true;
    }

    /**
     * Check if participant is muted
     */
    public boolean isParticipantMuted(UUID playerId) {
        CallParticipant participant = participants.get(playerId);
        return participant != null && participant.isMuted();
    }

    /**
     * Get all participants in the call
     */
    public Set<UUID> getParticipants() {
        return new HashSet<>(participants.keySet());
    }

    /**
     * Get participant count
     */
    public int getParticipantCount() {
        return participants.size();
    }

    /**
     * Check if call is empty
     */
    public boolean isEmpty() {
        return participants.isEmpty();
    }

    /**
     * Check if host left the call
     */
    public boolean hostLeft() {
        return participants.values().stream()
                .noneMatch(p -> p.getRole() == ParticipantRole.HOST);
    }

    /**
     * Get call type
     */
    public CallType getCallType() {
        return callType;
    }

    /**
     * Get call location (for locational calls)
     */
    public BlockPos getCallLocation() {
        return callLocation;
    }

    /**
     * Get hearing range
     */
    public double getHearingRange() {
        return hearingRange;
    }

    /**
     * Get call duration in milliseconds
     */
    public long getCallDuration() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * Get debug information about this call
     */
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("callId", callId.toString());
        info.put("callType", callType.toString());
        info.put("participantCount", participants.size());
        info.put("location", callLocation != null ? callLocation.toString() : "null");
        info.put("hearingRange", hearingRange);
        info.put("duration", getCallDuration() + "ms");
        info.put("hasAudioChannel", audioChannel != null);

        List<String> participantNames = new ArrayList<>();
        for (CallParticipant participant : participants.values()) {
            String status = participant.isMuted() ? " (muted)" : "";
            participantNames.add(participant.getPlayerName() + status);
        }
        info.put("participants", participantNames);

        return info;
    }

    /**
     * Full cleanup of the call and all resources
     */
    public void cleanup() {
        try {
            // Remove all participants
            Set<UUID> participantIds = new HashSet<>(participants.keySet());
            for (UUID participantId : participantIds) {
                ServerPlayer player = getPlayerById(participantId);
                if (player != null) {
                    removeParticipant(player);
                }
            }

            // Remove audio channel
            if (audioChannel != null) {
                audioChannelManager.removeChannel(callId);
                audioChannel = null;
            }

            // Remove call session from audio forwarder
            audioForwarder.removeCallSession(callId);

            participants.clear();

            System.out.println("TransponderCall: Cleaned up call " + callId);

        } catch (Exception e) {
            System.err.println("TransponderCall: Error during cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clean up resources for a specific player (used internally)
     */
    private void cleanupPlayer(UUID playerId) {
        try {
            audioForwarder.removePlayer(callId, playerId);
            // Note: Don't remove from audioChannelManager here as other participants might still be using it
        } catch (Exception e) {
            System.err.println("TransponderCall: Error during player cleanup: " + e.getMessage());
        }
    }

    /**
     * Get player by UUID
     */
    private ServerPlayer getPlayerById(UUID playerId) {
        return ServerLifecycleHooks.getCurrentServer()
                .getPlayerList().getPlayer(playerId);
    }
}