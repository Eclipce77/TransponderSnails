package net.eclipce.transpondersnails.gary;

/**
 * Which sounds have a Gary version. A snail that looks like Gary rings, connects and disconnects with Gary's own sounds; every other sound a snail makes
 * (busy, pick up, hang up, the dial buttons, the static) has no Gary version and stays what it is. Pure (no Minecraft classes), unit tested.
 */
public final class GarySounds {

    private GarySounds() {}

    /**
     * The sound (its name, without the mod id) to play for a snail that is or is not Gary.
     *
     * @param normalPath the normal sound, e.g. {@code snail_ringing}
     * @return {@code gary_ringing} for a Gary snail; the same sound for anything else
     */
    public static String variantPath(String normalPath, boolean gary) {
        if (!gary || normalPath == null) return normalPath;
        return switch (normalPath) {
            case "snail_ringing" -> "gary_ringing";
            case "snail_connected" -> "gary_connected";
            case "snail_disconnected" -> "gary_disconnected";
            default -> normalPath;
        };
    }
}
