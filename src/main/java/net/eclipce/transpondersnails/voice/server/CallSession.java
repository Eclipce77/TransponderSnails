package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an active call session between transponder snails
 * NOW WITH FULL HANDHELD SUPPORT for audio forwarding
 */
public class CallSession {

    public enum CallState {
        INITIATING,     // Call being set up
        RINGING,        // Ringing at target
        CONNECTED,      // Call active
        ENDING,         // Call terminating
        ENDED           // Call finished
    }

    // Basic call information
    private final UUID callId;
    private final long creationTime;
    private CallState state;

    // Participants and their snail numbers
    private final Map<UUID, Integer> participantSnailNumbers = new ConcurrentHashMap<>();
    private final Map<Integer, CallParticipant> snailParticipants = new ConcurrentHashMap<>();

    // Audio channels - NOW INCLUDES HANDHELD!
    private AudioChannel primaryChannel;
    private final Map<BlockPos, AudioChannel> proximityChannels = new ConcurrentHashMap<>();

    // ✨ NEW: Handheld participant audio channels
    private final Map<UUID, AudioChannel> handheldChannels = new ConcurrentHashMap<>();

    // Proximity listeners (players near snail blocks who can hear the call)
    private final Set<UUID> proximityListeners = ConcurrentHashMap.newKeySet();

    // Call activity tracking
    private long lastActivityTime;
    private static final long INACTIVITY_TIMEOUT = 10 * 60 * 1000; // 10 minutes

    public CallSession(UUID callId, int initiatorSnailNumber, CallParticipant initiator) {
        this.callId = callId;
        this.creationTime = System.currentTimeMillis();
        this.state = CallState.INITIATING;
        this.lastActivityTime = creationTime;

        addParticipant(initiatorSnailNumber, initiator);
    }

    /**
     * Represents a call participant - either a player with handheld snail or a placed snail block
     */
    public static class CallParticipant {
        private final UUID playerId;
        private final int snailNumber;
        private final ParticipantType type;
        private final BlockPos blockPosition;

        public enum ParticipantType {
            HANDHELD,   // Player holding a snail
            BLOCK       // Placed snail block
        }

        public CallParticipant(@Nullable UUID playerId, int snailNumber, ParticipantType type, @Nullable BlockPos blockPosition) {
            this.playerId = playerId;
            this.snailNumber = snailNumber;
            this.type = type;
            this.blockPosition = blockPosition;
        }

        // Static factory methods
        public static CallParticipant handheld(UUID playerId, int snailNumber) {
            return new CallParticipant(playerId, snailNumber, ParticipantType.HANDHELD, null);
        }

        public static CallParticipant block(int snailNumber, BlockPos position) {
            return new CallParticipant(null, snailNumber, ParticipantType.BLOCK, position);
        }

        public static CallParticipant blockWithPlayer(UUID playerId, int snailNumber, BlockPos position) {
            return new CallParticipant(playerId, snailNumber, ParticipantType.BLOCK, position);
        }

        // Getters
        @Nullable public UUID getPlayerId() { return playerId; }
        public int getSnailNumber() { return snailNumber; }
        public ParticipantType getType() { return type; }
        @Nullable public BlockPos getBlockPosition() { return blockPosition; }

        public boolean isHandheld() { return type == ParticipantType.HANDHELD; }
        public boolean isBlock() { return type == ParticipantType.BLOCK; }
        public boolean hasActivePlayer() { return playerId != null; }

        @Override
        public String toString() {
            return String.format("CallParticipant{snail=#%d, type=%s, player=%s, pos=%s}",
                    snailNumber, type, playerId != null ? playerId.toString().substring(0, 8) : "none", blockPosition);
        }
    }

    // =================== PARTICIPANT MANAGEMENT ===================

    public void addParticipant(int snailNumber, CallParticipant participant) {
        snailParticipants.put(snailNumber, participant);
        if (participant.hasActivePlayer()) {
            participantSnailNumbers.put(participant.getPlayerId(), snailNumber);
        }
        updateActivity();
    }

    public void removeParticipant(int snailNumber) {
        CallParticipant participant = snailParticipants.remove(snailNumber);
        if (participant != null && participant.hasActivePlayer()) {
            participantSnailNumbers.remove(participant.getPlayerId());
        }
        updateActivity();
    }

    public void removeParticipantByPlayer(UUID playerId) {
        Integer snailNumber = participantSnailNumbers.remove(playerId);
        if (snailNumber != null) {
            snailParticipants.remove(snailNumber);
        }
        updateActivity();
    }

    @Nullable
    public CallParticipant getParticipant(int snailNumber) {
        return snailParticipants.get(snailNumber);
    }

    @Nullable
    public CallParticipant getParticipantByPlayer(UUID playerId) {
        Integer snailNumber = participantSnailNumbers.get(playerId);
        return snailNumber != null ? snailParticipants.get(snailNumber) : null;
    }

    public boolean isParticipant(UUID playerId) {
        return participantSnailNumbers.containsKey(playerId);
    }

    public boolean isParticipant(int snailNumber) {
        return snailParticipants.containsKey(snailNumber);
    }

    public Set<Integer> getParticipantSnailNumbers() {
        return new HashSet<>(snailParticipants.keySet());
    }

    public Set<UUID> getActivePlayerParticipants() {
        return new HashSet<>(participantSnailNumbers.keySet());
    }

    public Collection<CallParticipant> getAllParticipants() {
        return new ArrayList<>(snailParticipants.values());
    }

    public int getParticipantCount() {
        return snailParticipants.size();
    }

    // =================== PROXIMITY LISTENER MANAGEMENT ===================

    public void addProximityListener(UUID playerId) {
        if (proximityListeners.add(playerId)) {
            updateActivity();
        }
    }

    public void removeProximityListener(UUID playerId) {
        proximityListeners.remove(playerId);
    }

    public boolean isProximityListener(UUID playerId) {
        return proximityListeners.contains(playerId);
    }

    public Set<UUID> getProximityListeners() {
        return new HashSet<>(proximityListeners);
    }

    public Set<UUID> getAllAudioParticipants() {
        Set<UUID> allParticipants = new HashSet<>(participantSnailNumbers.keySet());
        allParticipants.addAll(proximityListeners);
        return allParticipants;
    }

    // =================== AUDIO CHANNEL MANAGEMENT ===================

    public void setPrimaryChannel(AudioChannel channel) {
        this.primaryChannel = channel;
    }

    @Nullable
    public AudioChannel getPrimaryChannel() {
        return primaryChannel;
    }

    // Block channels
    public void addProximityChannel(BlockPos position, AudioChannel channel) {
        proximityChannels.put(position, channel);
    }

    public void removeProximityChannel(BlockPos position) {
        proximityChannels.remove(position);
    }

    @Nullable
    public AudioChannel getProximityChannel(BlockPos position) {
        return proximityChannels.get(position);
    }

    public Map<BlockPos, AudioChannel> getProximityChannels() {
        return new HashMap<>(proximityChannels);
    }

    // ✨ NEW: Handheld channels
    public void addHandheldChannel(UUID playerId, AudioChannel channel) {
        handheldChannels.put(playerId, channel);
        System.out.println("CallSession: Added handheld channel for player " + playerId.toString().substring(0, 8));
    }

    public void removeHandheldChannel(UUID playerId) {
        handheldChannels.remove(playerId);
        System.out.println("CallSession: Removed handheld channel for player " + playerId.toString().substring(0, 8));
    }

    @Nullable
    public AudioChannel getHandheldChannel(UUID playerId) {
        return handheldChannels.get(playerId);
    }

    public Map<UUID, AudioChannel> getHandheldChannels() {
        return new HashMap<>(handheldChannels);
    }

    public boolean hasHandheldChannel(UUID playerId) {
        return handheldChannels.containsKey(playerId);
    }

    // ✨ NEW: Get all audio channels (block + handheld)
    public Collection<AudioChannel> getAllAudioChannels() {
        List<AudioChannel> allChannels = new ArrayList<>();
        allChannels.addAll(proximityChannels.values());
        allChannels.addAll(handheldChannels.values());
        if (primaryChannel != null) {
            allChannels.add(primaryChannel);
        }
        return allChannels;
    }

    // =================== CALL STATE MANAGEMENT ===================

    public void setState(CallState newState) {
        this.state = newState;
        updateActivity();
    }

    public CallState getState() {
        return state;
    }

    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    public boolean hasTimedOut() {
        return (System.currentTimeMillis() - lastActivityTime) > INACTIVITY_TIMEOUT;
    }

    public long getTimeSinceLastActivity() {
        return System.currentTimeMillis() - lastActivityTime;
    }

    public boolean shouldAutoEnd() {
        return hasTimedOut() || getParticipantCount() == 0;
    }

    // =================== UTILITY METHODS ===================

    public Set<BlockPos> getInvolvedBlockPositions() {
        Set<BlockPos> positions = new HashSet<>();
        for (CallParticipant participant : snailParticipants.values()) {
            if (participant.isBlock() && participant.getBlockPosition() != null) {
                positions.add(participant.getBlockPosition());
            }
        }
        return positions;
    }

    // ✨ NEW: Get all handheld participant player IDs
    public Set<UUID> getHandheldParticipantIds() {
        Set<UUID> handheldPlayers = new HashSet<>();
        for (CallParticipant participant : snailParticipants.values()) {
            if (participant.isHandheld() && participant.hasActivePlayer()) {
                handheldPlayers.add(participant.getPlayerId());
            }
        }
        return handheldPlayers;
    }

    public boolean involvesBlockPosition(BlockPos position) {
        return snailParticipants.values().stream()
                .anyMatch(p -> p.isBlock() && position.equals(p.getBlockPosition()));
    }

    public long getCallDuration() {
        return System.currentTimeMillis() - creationTime;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CallSession{id=%s, state=%s, duration=%ds, participants=%d, proximity=%d}",
                callId.toString().substring(0, 8), state, getCallDuration() / 1000,
                getParticipantCount(), proximityListeners.size()));

        sb.append("\n  Participants:");
        for (CallParticipant participant : snailParticipants.values()) {
            sb.append("\n    ").append(participant.toString());
        }

        if (!proximityListeners.isEmpty()) {
            sb.append("\n  Proximity Listeners: ").append(proximityListeners.size());
        }

        if (!handheldChannels.isEmpty()) {
            sb.append("\n  Handheld Channels: ").append(handheldChannels.size());
        }

        return sb.toString();
    }

    // =================== GETTERS ===================

    public UUID getCallId() {
        return callId;
    }

    public long getCreationTime() {
        return creationTime;
    }

    public long getLastActivityTime() {
        return lastActivityTime;
    }

    @Override
    public String toString() {
        return String.format("CallSession{id=%s, state=%s, participants=%d, handheld=%d}",
                callId.toString().substring(0, 8), state, getParticipantCount(), handheldChannels.size());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CallSession that = (CallSession) o;
        return Objects.equals(callId, that.callId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callId);
    }
}