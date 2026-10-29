package net.eclipce.transpondersnails.voice.server;

/**
 * When a call goes on and when it ends as a snail moves between being carried and being placed. The one idea: a snail being placed, dropped or broken does not end a
 * call - the person on that side is still there. The call ends only when that person is gone (they died, or left the game), or when nobody was on that side.
 * Pure (no Minecraft classes), unit tested; TransponderCallManager makes the decisions with it.
 */
public final class CallContinuity {

    private CallContinuity() {}

    /** How a snail takes part in a call. */
    public enum Kind { BLOCK, HANDHELD, NONE }

    /**
     * A PLACED snail that is in a call has been broken (or blown up, replaced...). Does the call carry on, with the player on that side taking over as a handheld one?
     *
     * @param kind          how that snail takes part in its call
     * @param hasPlayer     a player was on that side (the one who placed it from their hand, or answered at it)
     * @param playerPresent that player is online and alive
     * @param callEnding    the call is already being ended
     */
    public static boolean keepCallAfterBreak(Kind kind, boolean hasPlayer, boolean playerPresent, boolean callEnding) {
        return kind == Kind.BLOCK && hasPlayer && playerPresent && !callEnding;
    }

    /**
     * A player died. A snail they were CARRYING drops with the rest of what they had, and with them gone nobody is left on that side, so the call ends. A player at a
     * placed snail leaves the snail where it is, so it does not.
     */
    public static boolean endCallWhenPlayerDies(Kind kind) {
        return kind == Kind.HANDHELD;
    }

    /**
     * Has a snail that was carried just been PLACED successfully, so that its call moves to the block? The server answers a successful placement with CONSUME, and only
     * the client gets SUCCESS (that is what swings the arm), so what counts is that the action was consumed - the check used to be for SUCCESS, which the server never returns.
     */
    public static boolean placementTakesOver(boolean actionConsumed, boolean blockEntityThere) {
        return actionConsumed && blockEntityThere;
    }
}
