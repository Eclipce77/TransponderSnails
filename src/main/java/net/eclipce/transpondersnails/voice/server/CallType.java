package net.eclipce.transpondersnails.voice.server;

public enum CallType{
    PERSONAL,    // Direct player-to-player call (no proximity)
    LOCATIONAL,  // Call through placed snail (spatial audio, longer range)
    HANDHELD     // Call through handheld snail (spatial audio, shorter range)
}
