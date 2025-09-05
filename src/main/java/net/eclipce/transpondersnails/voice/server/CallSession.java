package net.eclipce.transpondersnails.voice.server;

import de.maxhenkel.voicechat.api.audiochannel.AudioChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents an active call session between transponder snails
 * Handles both 1-to-1 calls and multi-participant conference calls
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
    private final Map<UUID, Integer> participantSnailNumbers = new ConcurrentHashMap<>();  // Player UUID -> Snail Number
    private final Map<Integer, CallParticipant> snailParticipants = new ConcurrentHashMap<>(); // Snail Number -> Participant Info

    // Audio channels
    private AudioChannel primaryChannel;        // Main call audio
    private final Map<BlockPos, AudioChannel> proximityChannels = new ConcurrentHashMap<>(); // Location-based eavesdropping

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

        // Add initiator as first participant
        addParticipant(initiatorSnailNumber, initiator);
    }

    /**
     * Represents a call participant - either a player with handheld snail or a placed snail block
     */
    public static class CallParticipant {
        private final UUID playerId;           // Player UUID (null for unattended blocks)
        private final int snailNumber;         // The snail's assigned number
        private final ParticipantType type;    // HANDHELD or BLOCK
        private final BlockPos blockPosition; // Position if it's a block snail (null for handheld)

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

    /**
     * Add a participant to the call
     */
    public void addParticipant(int snailNumber, CallParticipant participant) {
        snailParticipants.put(snailNumber, participant);
        if (participant.hasActivePlayer()) {
            participantSnailNumbers.put(participant.getPlayerId(), snailNumber);
        }
        updateActivity();
    }

    /**
     * Remove a participant from the call
     */
    public void removeParticipant(int snailNumber) {
        CallParticipant participant = snailParticipants.remove(snailNumber);
        if (participant != null && participant.hasActivePlayer()) {
            participantSnailNumbers.remove(participant.getPlayerId());
        }
        updateActivity();
    }

    /**
     * Remove participant by player UUID
     */
    public void removeParticipantByPlayer(UUID playerId) {
        Integer snailNumber = participantSnailNumbers.remove(playerId);
        if (snailNumber != null) {
            snailParticipants.remove(snailNumber);
        }
        updateActivity();
    }

    /**
     * Get participant by snail number
     */
    @Nullable
    public CallParticipant getParticipant(int snailNumber) {
        return snailParticipants.get(snailNumber);
    }

    /**
     * Get participant by player UUID
     */
    @Nullable
    public CallParticipant getParticipantByPlayer(UUID playerId) {
        Integer snailNumber = participantSnailNumbers.get(playerId);
        return snailNumber != null ? snailParticipants.get(snailNumber) : null;
    }

    /**
     * Check if player is a participant
     */
    public boolean isParticipant(UUID playerId) {
        return participantSnailNumbers.containsKey(playerId);
    }

    /**
     * Check if snail is a participant
     */
    public boolean isParticipant(int snailNumber) {
        return snailParticipants.containsKey(snailNumber);
    }

    /**
     * Get all participant snail numbers
     */
    public Set<Integer> getParticipantSnailNumbers() {
        return new HashSet<>(snailParticipants.keySet());
    }

    /**
     * Get all active player participants
     */
    public Set<UUID> getActivePlayerParticipants() {
        return new HashSet<>(participantSnailNumbers.keySet());
    }

    /**
     * Get all participants
     */
    public Collection<CallParticipant> getAllParticipants() {
        return new ArrayList<>(snailParticipants.values());
    }

    /**
     * Get participant count
     */
    public int getParticipantCount() {
        return snailParticipants.size();
    }

    // =================== PROXIMITY LISTENER MANAGEMENT ===================

    /**
     * Add a proximity listener (player near a snail block in the call)
     */
    public void addProximityListener(UUID playerId) {
        if (proximityListeners.add(playerId)) {
            updateActivity();
        }
    }

    /**
     * Remove a proximity listener
     */
    public void removeProximityListener(UUID playerId) {
        proximityListeners.remove(playerId);
    }

    /**
     * Check if player is a proximity listener
     */
    public boolean isProximityListener(UUID playerId) {
        return proximityListeners.contains(playerId);
    }

    /**
     * Get all proximity listeners
     */
    public Set<UUID> getProximityListeners() {
        return new HashSet<>(proximityListeners);
    }

    /**
     * Get all players who should hear this call (participants + proximity listeners)
     */
    public Set<UUID> getAllAudioParticipants() {
        Set<UUID> allParticipants = new HashSet<>(participantSnailNumbers.keySet());
        allParticipants.addAll(proximityListeners);
        return allParticipants;
    }

    // =================== AUDIO CHANNEL MANAGEMENT ===================

    /**
     * Set the primary audio channel for call participants
     */
    public void setPrimaryChannel(AudioChannel channel) {
        this.primaryChannel = channel;
    }

    /**
     * Get the primary audio channel
     */
    @Nullable
    public AudioChannel getPrimaryChannel() {
        return primaryChannel;
    }

    /**
     * Add a proximity channel for a specific location
     */
    public void addProximityChannel(BlockPos position, AudioChannel channel) {
        proximityChannels.put(position, channel);
    }

    /**
     * Remove proximity channel for a location
     */
    public void removeProximityChannel(BlockPos position) {
        proximityChannels.remove(position);
    }

    /**
     * Get proximity channel for a location
     */
    @Nullable
    public AudioChannel getProximityChannel(BlockPos position) {
        return proximityChannels.get(position);
    }

    /**
     * Get all proximity channels
     */
    public Map<BlockPos, AudioChannel> getProximityChannels() {
        return new HashMap<>(proximityChannels);
    }

    // =================== CALL STATE MANAGEMENT ===================

    /**
     * Update call state
     */
    public void setState(CallState newState) {
        this.state = newState;
        updateActivity();
    }

    /**
     * Get current call state
     */
    public CallState getState() {
        return state;
    }

    /**
     * Update last activity time (called when audio is transmitted or other activity occurs)
     */
    public void updateActivity() {
        this.lastActivityTime = System.currentTimeMillis();
    }

    /**
     * Check if call has been inactive too long
     */
    public boolean hasTimedOut() {
        return (System.currentTimeMillis() - lastActivityTime) > INACTIVITY_TIMEOUT;
    }

    /**
     * Get time since last activity in milliseconds
     */
    public long getTimeSinceLastActivity() {
        return System.currentTimeMillis() - lastActivityTime;
    }

    /**
     * Check if call should be automatically ended
     */
    public boolean shouldAutoEnd() {
        return hasTimedOut() || getParticipantCount() == 0;
    }

    // =================== UTILITY METHODS ===================

    /**
     * Get all block positions involved in this call
     */
    public Set<BlockPos> getInvolvedBlockPositions() {
        Set<BlockPos> positions = new HashSet<>();
        for (CallParticipant participant : snailParticipants.values()) {
            if (participant.isBlock() && participant.getBlockPosition() != null) {
                positions.add(participant.getBlockPosition());
            }
        }
        return positions;
    }

    /**
     * Check if this call involves a specific block position
     */
    public boolean involvesBlockPosition(BlockPos position) {
        return snailParticipants.values().stream()
                .anyMatch(p -> p.isBlock() && position.equals(p.getBlockPosition()));
    }

    /**
     * Get call duration in milliseconds
     */
    public long getCallDuration() {
        return System.currentTimeMillis() - creationTime;
    }

    /**
     * Create a summary string for debugging
     */
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
        return String.format("CallSession{id=%s, state=%s, participants=%d}",
                callId.toString().substring(0, 8), state, getParticipantCount());
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