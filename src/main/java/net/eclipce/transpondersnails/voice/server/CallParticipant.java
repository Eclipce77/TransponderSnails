package net.eclipce.transpondersnails.voice.server;

import java.util.UUID;

public class CallParticipant {
    private final UUID playerId;
    private final String playerName;
    private final ParticipantRole role;
    private boolean muted = false;

    public CallParticipant(UUID playerId, String playerName, ParticipantRole role) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.role = role;
    }

    // Getters
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public ParticipantRole getRole() { return role; }
    public boolean isMuted() { return muted; }
    public void setMuted(boolean muted) { this.muted = muted; }
}
