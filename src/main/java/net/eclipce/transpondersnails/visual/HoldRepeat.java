package net.eclipce.transpondersnails.visual;

/**
 * Holding an arrow of the projector settings menu keeps moving the screen: after a short wait it repeats, and the longer it is held the faster. The click itself makes the first
 * step; this says how many MORE are due. Pure (no Minecraft classes), unit tested.
 */
public final class HoldRepeat {

    /** Nothing repeats for this long after the click, so a normal click moves one step. */
    public static final long INITIAL_DELAY_MS = 400L;
    public static final long SLOW_INTERVAL_MS = 120L;
    /** Held this long, it speeds up... */
    public static final long FAST_AFTER_MS = 1500L;
    /** ...to a step this often. */
    public static final long FAST_INTERVAL_MS = 60L;
    /** After a stall (a lag spike) at most this many steps are made at once, so a screen that was frozen does not fly to the edge. */
    public static final int MAX_STEPS_AT_ONCE = 3;

    private long pressedAt = -1L;
    private long nextStepAt = 0L;

    /** The button went down at {@code nowMs} (the click made the first step). */
    public void press(long nowMs) {
        pressedAt = nowMs;
        nextStepAt = nowMs + INITIAL_DELAY_MS;
    }

    public void release() {
        pressedAt = -1L;
    }

    public boolean holding() {
        return pressedAt >= 0L;
    }

    /** How many more steps are due at {@code nowMs}; each one is counted once, however often this is asked. */
    public int poll(long nowMs) {
        if (pressedAt < 0L) return 0;
        int due = 0;
        while (nowMs >= nextStepAt) {
            due++;
            long held = nextStepAt - pressedAt;
            nextStepAt += held >= FAST_AFTER_MS ? FAST_INTERVAL_MS : SLOW_INTERVAL_MS;
            if (due >= MAX_STEPS_AT_ONCE) {      // a stall: drop what is overdue, carry on from now
                nextStepAt = Math.max(nextStepAt, nowMs + 1L);
                break;
            }
        }
        return due;
    }
}
