package net.eclipce.transpondersnails.visual;

import net.eclipce.transpondersnails.config.ModConfig;

/**
 * The one place the video call code reads its limits from. Combines the SERVER limits (synced to every client, set by the
 * server operator) with the player's own CLIENT preferences: the lower of the two always wins.
 */
public final class VisualSettings {

    private VisualSettings() {}

    /** Highest resolution any feed may use on this client: min(server limit, player limit), as a resolution tier. */
    public static int maxFeedResolution() {
        return VisualQuality.floorTier(Math.min(ModConfig.getVisualMaxFeedResolution(), ModConfig.getMaxVideoResolution()));
    }

    /** Highest frame rate any feed may use (server limit). */
    public static int maxFeedFps() {
        return ModConfig.getVisualMaxFeedFps();
    }

    /** The most cameras relayed through the server at once; 0 = relay off (server limit). */
    public static int relayMaxSources() {
        return ModConfig.getVisualRelayMaxSources();
    }

    /** Frames a second of a relayed picture (server setting). */
    public static int relayFps() {
        return ModConfig.getVisualRelayFps();
    }

    /** The biggest relayed picture, pixels each way (server setting). */
    public static int relayMaxResolution() {
        return ModConfig.getVisualRelayMaxResolution();
    }

    /** The most a relayed camera may send, KB a second on average (server setting). */
    public static int relayMaxKbps() {
        return ModConfig.getVisualRelayMaxKbps();
    }

    /** The furthest any feed sees, in chunks (server limit). The cost of a feed grows with the square of this. */
    public static int maxFeedViewChunks() {
        return ModConfig.getVisualMaxFeedViewChunks();
    }

    /** Whether resolution / frame rate / view distance adapt automatically (player setting). */
    public static boolean adaptive() {
        return ModConfig.isAdaptiveVideoQuality();
    }

    /** How many feeds this client renders at once (player setting). */
    public static int maxActiveFeeds() {
        return ModConfig.getMaxActiveVideoFeeds();
    }

    /** The largest screen (blocks per side) a projector can show (server config, synced to every client). */
    public static int maxScreenSize() {
        return ModConfig.getVisualMaxScreenSize();
    }

    /** Most video calls that may run at the same time (server limit). */
    public static int maxConcurrentCalls() {
        return ModConfig.getVisualMaxConcurrentCalls();
    }
}
