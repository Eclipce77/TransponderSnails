package net.eclipce.transpondersnails.voice.server;

public enum CallState {
    INITIATING("Initiating call..."),    // Call just created, sending invite
    RINGING("Ringing..."),               // Waiting for recipient to answer
    CONNECTING("Connecting..."),         // Recipient accepted, setting up audio
    CONNECTED("Connected"),              // Active call in progress
    ENDING("Ending call..."),            // Call termination in progress
    ENDED("Call ended"),                 // Call completed/terminated
    TIMEOUT("Call timeout"),             // Call timed out (no answer)
    FAILED("Call failed");               // Call failed to establish

    private final String displayText;

    CallState(String displayText) {
        this.displayText = displayText;
    }

    public String getDisplayText() {
        return displayText;
    }

    // Helper methods for state checking
    public boolean isActive() {
        return this == CONNECTED;
    }

    public boolean isInProgress() {
        return this == INITIATING || this == RINGING || this == CONNECTING || this == CONNECTED;
    }

    public boolean isTerminated() {
        return this == ENDED || this == TIMEOUT || this == FAILED;
    }

    public boolean canAccept() {
        return this == RINGING;
    }

    public boolean canEnd() {
        return this == RINGING || this == CONNECTING || this == CONNECTED;
    }
}