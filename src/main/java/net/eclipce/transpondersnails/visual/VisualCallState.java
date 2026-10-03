package net.eclipce.transpondersnails.visual;

/**
 * Call state of a single placed Visual Transponder Snail. Drives which of the four block models is shown:
 * idle / sound / call / active (see TransponderSnailBlockEntity#determineVisualModel).
 */
public enum VisualCallState {
    /** Not in a call. Idle model (or "sound" model while a ring/connect/disconnect sound plays). */
    IDLE,
    /** This snail started a call that has not been answered yet. Shows the idle model, like a normal caller. */
    CALLING_OUT,
    /** This snail is being called. Shows the "sound" model while ringing. */
    RINGING_IN,
    /** Call answered, audio ready, waiting for the video feeds to come up on both ends. "call" model. */
    CONNECTING,
    /** Fully connected (audio + video). "call" model, "active" model while audio is being received. */
    CONNECTED
}
