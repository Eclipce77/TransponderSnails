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
