package net.eclipce.transpondersnails.visual;

/**
 * What a placed Visual snail can do in a call. Pure logic (no Minecraft classes) so it can be unit tested; the block ->
 * role lookup is in {@link VisualRoles}.
 *
 * <ul>
 *   <li>{@link #DUPLEX} - Visual Transponder Snail: films and shows, picks up and plays audio. Two of them make a video call.</li>
 *   <li>{@link #CAMERA} - Visual Transmitter Transponder Snail: transmit only. Films and picks up sound, but has no screen and
 *       no speaker. It starts a call to the nearest idle DUPLEX snail in range and can never be the one that is called.</li>
 * </ul>
 */
public enum VisualSnailRole {

    DUPLEX(VisualCallConstants.DUPLEX_SNAIL_ID, true, true,
            VisualCallConstants.FEED_RESOLUTION, VisualCallConstants.FEED_FPS,
            VisualCallConstants.FEED_VIEW_CHUNKS, VisualCallConstants.FEED_VIEW_SECTIONS_VERTICAL),

    CAMERA(VisualCallConstants.CAMERA_SNAIL_ID, true, false,
            VisualCallConstants.CAMERA_FEED_RESOLUTION, VisualCallConstants.CAMERA_FEED_FPS,
            VisualCallConstants.CAMERA_FEED_VIEW_CHUNKS, VisualCallConstants.CAMERA_FEED_VIEW_SECTIONS_VERTICAL);

    private final String registryPath;
    private final boolean canTransmit;
    private final boolean canReceive;
    private final int feedResolution;
    private final int feedFps;
    private final int feedViewChunks;
    private final int feedViewSectionsVertical;

    VisualSnailRole(String registryPath, boolean canTransmit, boolean canReceive,
                    int feedResolution, int feedFps, int feedViewChunks, int feedViewSectionsVertical) {
        this.registryPath = registryPath;
        this.canTransmit = canTransmit;
        this.canReceive = canReceive;
        this.feedResolution = feedResolution;
        this.feedFps = feedFps;
        this.feedViewChunks = feedViewChunks;
        this.feedViewSectionsVertical = feedViewSectionsVertical;
    }

    public String registryPath() { return registryPath; }
    /** Its camera and microphone can be used by the other end. */
    public boolean canTransmit() { return canTransmit; }
    /** It can show the other end's picture and play its audio. */
    public boolean canReceive() { return canReceive; }

    /** Quality of the picture THIS snail films (used by whoever shows it). */
    public int feedResolution() { return feedResolution; }
    public int feedFps() { return feedFps; }
    public int feedViewChunks() { return feedViewChunks; }
    public int feedViewSectionsVertical() { return feedViewSectionsVertical; }

    /** @return the role for a block's registry path (without the namespace), or null if it is not a Visual snail */
    public static VisualSnailRole ofPath(String registryPath) {
        if (registryPath == null) return null;
        for (VisualSnailRole role : values()) {
            if (role.registryPath.equals(registryPath)) return role;
        }
        return null;
    }

    /** A call may only be placed to a snail that can show a picture / play audio. Cameras are never called. */
    public static boolean canCall(VisualSnailRole caller, VisualSnailRole callee) {
        return callee.canReceive;
    }

    /** In a call between {@code self} and {@code other}: does {@code self} show the other's picture and play its audio? */
    public static boolean receives(VisualSnailRole self, VisualSnailRole other) {
        return self.canReceive && other.canTransmit;
    }

    /** In a call between {@code self} and {@code other}: are {@code self}'s camera and microphone used by the other end? */
    public static boolean transmits(VisualSnailRole self, VisualSnailRole other) {
        return self.canTransmit && other.canReceive;
    }
}
