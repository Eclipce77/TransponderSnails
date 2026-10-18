package net.eclipce.transpondersnails.visual;

/**
 * Which parts of a snail glow, from what a live video link tells about it. Pure (no Minecraft classes), unit tested.
 *
 * <ul>
 *   <li>The PROJECTOR part of a Visual Transponder Snail glows while it is projecting a picture.</li>
 *   <li>The EYES glow while the snail is capturing: a camera snail (Visual Transmitter Transponder Snail) whenever its picture is
 *       shown somewhere, a projector snail when it is capturing AND projecting.</li>
 * </ul>
 *
 * A link says: the screen of snail S shows the picture of the camera of snail C. S is projecting, C is capturing - and when C is a
 * projector too, C shows S's camera as well, so both ends project AND capture. Working that out from one link means a viewer who only
 * sees one of the two links of a projector-to-projector call still sees both snails glow correctly.
 */
public final class VisualGlow {

    /** Index into a snail's flags: how far (0..1, the fade-in of the picture) it is projecting. */
    public static final int PROJECTING = 0;
    /** Index into a snail's flags: how far it is capturing. */
    public static final int CAPTURING = 1;

    private VisualGlow() {}

    /** Same, with one value for both: the picture and the capturing come on and go off together. */
    public static void apply(VisualSnailRole screenRole, VisualSnailRole cameraRole, float ease,
                             float[] screenFlags, float[] cameraFlags) {
        apply(screenRole, cameraRole, ease, ease, screenFlags, cameraFlags);
    }

    /**
     * Adds what a live link tells to the flags of its two snails (each {@code float[2]}, 0 = not at all; the larger value is kept).
     *
     * @param projectEase  how visible the picture of this link is, 0..1: it fades in, and out again when the call ends
     * @param captureEase  how far the camera of this link is capturing, 0..1: it comes on with the picture, but when the call ends it is
     *                     0 at once - the eyes turn off, they do not fade out (see ProjectorTransition.captureEase)
     */
    public static void apply(VisualSnailRole screenRole, VisualSnailRole cameraRole, float projectEase, float captureEase,
                             float[] screenFlags, float[] cameraFlags) {
        screenFlags[PROJECTING] = Math.max(screenFlags[PROJECTING], projectEase);
        cameraFlags[CAPTURING] = Math.max(cameraFlags[CAPTURING], captureEase);
        if (cameraRole.canReceive()) { // a projector at the other end shows OUR camera too: both ends project and capture
            cameraFlags[PROJECTING] = Math.max(cameraFlags[PROJECTING], projectEase);
            screenFlags[CAPTURING] = Math.max(screenFlags[CAPTURING], captureEase);
        }
    }

    /**
     * The state a snail's model is in, from its blockstate (has_sound / in_call): "idle", "sound" (ringing), "call" (in a call) or "active"
     * (in a call with somebody talking). The same mapping the block entity uses to pick the model; the glow of the eyes must use the model of
     * the SAME state, or the glowing eyes would look different from the snail's own - especially while the picture fades out and the snail is
     * already back to idle.
     */
    public static String modelState(boolean hasSound, boolean inCall) {
        if (inCall) return hasSound ? "active" : "call";
        return hasSound ? "sound" : "idle";
    }

    /** How much the eyes glow (0..1) for a snail with these flags. */
    public static float eyes(VisualSnailRole role, float[] flags) {
        if (role == VisualSnailRole.CAMERA) return flags[CAPTURING];
        return Math.min(flags[PROJECTING], flags[CAPTURING]); // a projector: capturing AND projecting
    }

    /** How much the projector part glows (0..1); only the projector snail has one. */
    public static float projector(VisualSnailRole role, float[] flags) {
        return role == VisualSnailRole.DUPLEX ? flags[PROJECTING] : 0.0F;
    }
}
