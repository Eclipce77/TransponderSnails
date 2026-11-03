package net.eclipce.transpondersnails.entity.custom;

import java.util.function.IntUnaryOperator;

/**
 * When the Black Den Den Mushi lets a few bubbles go: "every once in a while". Pure arithmetic with no Minecraft types, so it can be tested on its own.
 * The wait between two bursts is a random number of ticks between {@link #MIN_TICKS} and {@link #MAX_TICKS} (about 5 to 12 seconds), and a burst is
 * {@link #MIN_BUBBLES} to {@link #MAX_BUBBLES} bubbles. The random source is passed as "a function from a bound to a number below it" so both
 * {@code java.util.Random::nextInt} and Minecraft's {@code RandomSource::nextInt} fit.
 */
public final class BubbleSchedule {

    public static final int MIN_TICKS = 100;
    public static final int MAX_TICKS = 240;
    public static final int MIN_BUBBLES = 3;
    public static final int MAX_BUBBLES = 6;

    private BubbleSchedule() {}

    /** Ticks to wait until the next burst: MIN_TICKS to MAX_TICKS inclusive. */
    public static int nextDelay(IntUnaryOperator nextInt) {
        return MIN_TICKS + nextInt.applyAsInt(MAX_TICKS - MIN_TICKS + 1);
    }

    /** How many bubbles one burst has: MIN_BUBBLES to MAX_BUBBLES inclusive. */
    public static int burstSize(IntUnaryOperator nextInt) {
        return MIN_BUBBLES + nextInt.applyAsInt(MAX_BUBBLES - MIN_BUBBLES + 1);
    }

    /** True on the tick the countdown has run out: a burst should happen now. */
    public static boolean isDue(int countdown) {
        return countdown <= 1;
    }

    /** The countdown after one more tick: one less, or a freshly drawn delay (always at least MIN_TICKS) once it had run out. */
    public static int tick(int countdown, IntUnaryOperator nextInt) {
        return isDue(countdown) ? nextDelay(nextInt) : countdown - 1;
    }
}
