package net.eclipce.transpondersnails.visual;

/**
 * What a right click on a placed visual snail does. Pure (no Minecraft classes), unit tested.
 *
 * <pre>
 *                        click                  crouch + click
 *   projector, idle      the dialing GUI        the projector SETTINGS (the only way to them)
 *   projector, in call   answer / hang up       the dialing GUI (no settings)
 *   camera (any state)   starts / stops the     the STATUS menu (stream active or idle, viewers, its name)
 *                        stream
 * </pre>
 * "In a call" for a projector includes ringing and connecting, and being tuned to a stream: anything but idle. A projector that is
 * wired to a Black Transponder Snail ("tapped") has neither: plain click is refused, crouch + click still opens the settings. A camera does not care
 * whether anyone is watching: a click toggles its stream, and the status menu is there at any time, also while streaming.
 */
public final class VisualInteraction {

    private VisualInteraction() {}

    public enum Action {
        /** The projector settings screen. */
        OPEN_SETTINGS,
        /** The dialing GUI (the same as a phone's, with the stream tuner beside it). */
        OPEN_DIALER,
        /** VisualCallManager#onInteract: answer, cancel or hang up a call. */
        CALL,
        /** A camera: start its stream, or stop it if it is streaming. */
        STREAM_TOGGLE,
        /** A camera: the status menu. */
        OPEN_STATUS,
        /** A projector wired to a Black Transponder Snail: it only shows what the black snail intercepts, so the click does nothing (it says so). */
        TAP_BLOCKED
    }

    public static Action decide(boolean projector, boolean crouching, boolean inCall) {
        return decide(projector, crouching, inCall, false);
    }

    /**
     * @param tapped the projector is wired to a Black Transponder Snail: it is the black snail's display. It cannot call, be called or tune to
     *               a stream, so there is no dialing GUI and nothing to answer - a plain click is refused - but its settings (the size and place of
     *               the picture) stay reachable with crouch + click, as always.
     */
    public static Action decide(boolean projector, boolean crouching, boolean inCall, boolean tapped) {
        if (!projector) return crouching ? Action.OPEN_STATUS : Action.STREAM_TOGGLE;
        if (tapped) return crouching ? Action.OPEN_SETTINGS : Action.TAP_BLOCKED;
        if (!inCall) return crouching ? Action.OPEN_SETTINGS : Action.OPEN_DIALER;
        return crouching ? Action.OPEN_DIALER : Action.CALL;
    }
}
