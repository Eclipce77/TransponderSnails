package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.*;
import de.maxhenkel.voicechat.api.audiochannel.*;
import net.eclipce.transpondersnails.voice.VoiceChatConstants;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages audio channels for Transponder Snail calls
 * Handles creation, management, and cleanup of voice chat channels
 */
public class AudioChannelManager {

    private final VoicechatServerApi voicechatApi;
    private final Map<UUID, ChannelInfo> activeChannels = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> playerChannels = new ConcurrentHashMap<>(); // playerId -> channel IDs

    public AudioChannelManager(VoicechatServerApi api) {
        this.voicechatApi = api;
    }

    /**
     * Creates an audio channel for a call based on call type
     */
    public AudioChannel createCallChannel(UUID callId, CallType callType, ServerLevel level, BlockPos location) {
        try {
            AudioChannel channel = null;

            switch (callType) {
                case LOCATIONAL:
                    channel = createLocationalChannel(callId, level, location, VoiceChatConstants.LOCATIONAL_SNAIL_RANGE);
                    break;
                case HANDHELD:
                    channel = createLocationalChannel(callId, level, location, VoiceChatConstants.HANDHELD_SNAIL_RANGE);
                    break;
                case PERSONAL:
                    // For personal calls, we create a static channel
                    channel = createStaticChannel(callId, level);
                    break;
            }

            if (channel != null) {
                ChannelInfo info = new ChannelInfo(callId, callType, channel, level, location);
                activeChannels.put(callId, info);

                System.out.println("AudioChannelManager: Created " + callType + " channel for call " + callId);
                return channel;
            }

        } catch (Exception e) {
            System.err.println("AudioChannelManager: Failed to create channel for call " + callId + ": " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Creates a locational audio channel at a specific position
     */
    private LocationalAudioChannel createLocationalChannel(UUID callId, ServerLevel level, BlockPos location, double range) {
        try {
            UUID channelId = UUID.randomUUID();

            // Convert BlockPos to Position
            Position position = voicechatApi.createPosition(
                    location.getX() + 0.5,
                    location.getY() + 0.5,
                    location.getZ() + 0.5
            );

            // Create the locational channel
            LocationalAudioChannel channel = voicechatApi.createLocationalAudioChannel(
                    channelId,
                    voicechatApi.fromServerLevel(level),
                    position
            );

            if (channel != null) {
                // Configure the channel
                channel.setDistance((float) range);
                channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);

                System.out.println("AudioChannelManager: Created locational channel at " + location + " with range " + range);
            }

            return channel;

        } catch (Exception e) {
            System.err.println("AudioChannelManager: Failed to create locational channel: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Creates a static audio channel for personal calls
     * Static channels don't use addConnection/removeConnection - they work differently
     */
    private StaticAudioChannel createStaticChannel(UUID callId, ServerLevel level) {
        try {
            UUID channelId = UUID.randomUUID();

            // Create a basic static channel
            // We'll handle participant management through the call system itself
            StaticAudioChannel channel = voicechatApi.createStaticAudioChannel(
                    channelId,
                    voicechatApi.fromServerLevel(level),
                    null // No specific connection - we'll manage participants differently
            );

            if (channel != null) {
                channel.setCategory(VoiceChatConstants.SNAIL_VOLUME_CATEGORY);
                System.out.println("AudioChannelManager: Created static channel for call " + callId);
            }

            return channel;

        } catch (Exception e) {
            System.err.println("AudioChannelManager: Failed to create static channel: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Adds a player to an audio channel
     * For locational channels, this just tracks the player
     * For static channels, we use the voice chat group system instead
     */
    public boolean addPlayerToChannel(UUID callId, ServerPlayer player) {
        ChannelInfo channelInfo = activeChannels.get(callId);
        if (channelInfo == null) {
            System.err.println("AudioChannelManager: No channel found for call " + callId);
            return false;
        }

        try {
            VoicechatConnection connection = voicechatApi.getConnectionOf(player.getUUID());
            if (connection == null) {
                System.err.println("AudioChannelManager: No voice chat connection for player " + player.getName().getString());
                return false;
            }

            // Track that this player is using this channel
            playerChannels.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(callId);
            channelInfo.participants.add(player.getUUID());

            System.out.println("AudioChannelManager: Added player " + player.getName().getString() + " to call " + callId);
            return true;

        } catch (Exception e) {
            System.err.println("AudioChannelManager: Failed to add player to channel: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Removes a player from an audio channel
     */
    public boolean removePlayerFromChannel(UUID callId, ServerPlayer player) {
        ChannelInfo channelInfo = activeChannels.get(callId);
        if (channelInfo == null) {
            return false;
        }

        try {
            // Remove tracking
            Set<UUID> playerChannelSet = playerChannels.get(player.getUUID());
            if (playerChannelSet != null) {
                playerChannelSet.remove(callId);
                if (playerChannelSet.isEmpty()) {
                    playerChannels.remove(player.getUUID());
                }
            }

            channelInfo.participants.remove(player.getUUID());

            System.out.println("AudioChannelManager: Removed player " + player.getName().getString() + " from call " + callId);
            return true;

        } catch (Exception e) {
            System.err.println("AudioChannelManager: Failed to remove player from channel: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Updates the location of a locational channel (for handheld calls)
     * Note: Simple Voice Chat API doesn't support updating location dynamically
     * We'll need to recreate the channel at the new location
     */
    public boolean updateChannelLocation(UUID callId, ServerLevel level, BlockPos newLocation) {
        ChannelInfo channelInfo = activeChannels.get(callId);
        if (channelInfo == null || !(channelInfo.channel instanceof LocationalAudioChannel)) {
            return false;
        }

        try {
            // Store the old participants
            Set<UUID> oldParticipants = new HashSet<>(channelInfo.participants);

            // Remove the old channel
            removeChannelInternal(callId);

            // Create a new channel at the new location
            AudioChannel newChannel = createCallChannel(callId, channelInfo.callType, level, newLocation);

            if (newChannel != null) {
                // Re-add all participants to the new channel
                ChannelInfo newChannelInfo = activeChannels.get(callId);
                if (newChannelInfo != null) {
                    newChannelInfo.participants.addAll(oldParticipants);

                    // Update player channel mappings
                    for (UUID participantId : oldParticipants) {
                        playerChannels.computeIfAbsent(participantId, k -> new HashSet<>()).add(callId);
                    }
                }

                System.out.println("AudioChannelManager: Recreated channel at new location " + newLocation);
                return true;
            }

        } catch (Exception e) {
            System.err.println("AudioChannelManager: Failed to update channel location: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Removes an audio channel and cleans up resources
     */
    public boolean removeChannel(UUID callId) {
        return removeChannelInternal(callId);
    }

    /**
     * Internal method to remove a channel without recreating
     */
    private boolean removeChannelInternal(UUID callId) {
        ChannelInfo channelInfo = activeChannels.remove(callId);
        if (channelInfo == null) {
            return false;
        }

        try {
            // The Simple Voice Chat API automatically handles channel cleanup
            // We don't need to call a specific remove method - channels are cleaned up
            // when they go out of scope or when the server shuts down

            // Clean up player tracking
            for (UUID participantId : channelInfo.participants) {
                Set<UUID> playerChannelSet = playerChannels.get(participantId);
                if (playerChannelSet != null) {
                    playerChannelSet.remove(callId);
                    if (playerChannelSet.isEmpty()) {
                        playerChannels.remove(participantId);
                    }
                }
            }

            System.out.println("AudioChannelManager: Removed channel for call " + callId);
            return true;

        } catch (Exception e) {
            System.err.println("AudioChannelManager: Failed to remove channel: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Gets channel information for a call
     */
    public ChannelInfo getChannelInfo(UUID callId) {
        return activeChannels.get(callId);
    }

    /**
     * Gets all channels a player is participating in
     */
    public Set<UUID> getPlayerChannels(UUID playerId) {
        return playerChannels.getOrDefault(playerId, new HashSet<>());
    }

    /**
     * Cleans up all channels (for server shutdown)
     */
    public void cleanup() {
        // Clean up all channel tracking
        Set<UUID> callIds = new HashSet<>(activeChannels.keySet());
        for (UUID callId : callIds) {
            removeChannelInternal(callId);
        }

        activeChannels.clear();
        playerChannels.clear();
        System.out.println("AudioChannelManager: Cleaned up all channels");
    }

    /**
     * Gets debug information about active channels
     */
    public Map<UUID, String> getChannelDebugInfo() {
        Map<UUID, String> info = new HashMap<>();

        for (Map.Entry<UUID, ChannelInfo> entry : activeChannels.entrySet()) {
            ChannelInfo channelInfo = entry.getValue();
            String debugString = channelInfo.callType + " channel with " +
                    channelInfo.participants.size() + " participants at " +
                    channelInfo.location;
            info.put(entry.getKey(), debugString);
        }

        return info;
    }

    /**
     * Inner class to store channel information
     */
    public static class ChannelInfo {
        public final UUID callId;
        public final CallType callType;
        public final AudioChannel channel;
        public final ServerLevel level;
        public BlockPos location;
        public final Set<UUID> participants = new HashSet<>();
        public final long creationTime;

        public ChannelInfo(UUID callId, CallType callType, AudioChannel channel, ServerLevel level, BlockPos location) {
            this.callId = callId;
            this.callType = callType;
            this.channel = channel;
            this.level = level;
            this.location = location;
            this.creationTime = System.currentTimeMillis();
        }

        public boolean isLocational() {
            return callType == CallType.LOCATIONAL || callType == CallType.HANDHELD;
        }

        public double getRange() {
            return switch (callType) {
                case LOCATIONAL -> VoiceChatConstants.LOCATIONAL_SNAIL_RANGE;
                case HANDHELD -> VoiceChatConstants.HANDHELD_SNAIL_RANGE;
                case PERSONAL -> 0.0; // No range limit for personal calls
            };
        }
    }
}