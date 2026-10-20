package net.eclipce.transpondersnails.visual;

/**
 * Ids that were renamed. The camera snail was "visual_transmitter_transponder_snail"; it is now the Visual Transmission Transponder Snail, and
 * the one that was is the one WITH a Transmitter. A world saved before still names the old id: it is mapped to the new one, so nothing placed
 * or carried disappears. Pure (no Minecraft classes), unit tested; LegacyIdRemapper applies it.
 */
public final class LegacyIds {

    private LegacyIds() {}

    public static final String OLD_CAMERA = "visual_transmitter_transponder_snail";
    public static final String NEW_CAMERA_WITH_TRANSMITTER = "visual_transmission_transponder_snail_transmitter";

    /** The current id (path) for an old one, or null if it was not renamed. */
    public static String current(String oldPath) {
        return OLD_CAMERA.equals(oldPath) ? NEW_CAMERA_WITH_TRANSMITTER : null;
    }
}
