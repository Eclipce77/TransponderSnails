package net.eclipce.transpondersnails.visual;

/**
 * What the projector does when a call starts and when it ends, as a timeline (no Minecraft classes, unit tested). The same three things
 * happen at both ends, like an old CRT television being switched on and off:
 *
 * <ul>
 *   <li><b>Fade</b>: the picture fades in over FEED_FADE_IN_MS and, when the call ends, out over FEED_FADE_OUT_MS (frozen on its last frame).</li>
 *   <li><b>Static</b>: TV snow. When the projector turns on it is at full strength at once and dies away as the picture comes in; when it
 *       turns off it swells while the picture dissolves and is gone when the picture is.</li>
 *   <li><b>Pop</b>: a flash of light. A sharp one just after the projector turns on, and one in the middle of the fade-out, as the picture
 *       dissolves - strong at its peak, then gone.</li>
 * </ul>
 *
 * Times are in ms. {@code sinceStart} is the time since the first picture of the call (negative = not started yet), {@code sinceEnd} the
 * time since the call ended (negative = not ended). In the middle of a call (started long ago, not ended) there is no static and no pop, and
 * the visibility is exactly 1.
 */
public final class ProjectorTransition {

    private ProjectorTransition() {}

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }

    private static double smoothstep(double t) {
        double x = clamp01(t);
        return x * x * (3.0 - 2.0 * x);
    }

    /** 0..1: how far the picture has faded in. 0 before the call starts. */
    public static double fadeIn(long sinceStartMs) {
        if (sinceStartMs < 0L) return 0.0;
        return smoothstep(sinceStartMs / (double) VisualCallConstants.FEED_FADE_IN_MS);
    }

    /** 1..0: how much of the picture is left. 1 until the call ends. */
    public static double fadeOut(long sinceEndMs) {
        if (sinceEndMs < 0L) return 1.0;
        return 1.0 - smoothstep(sinceEndMs / (double) VisualCallConstants.FEED_FADE_OUT_MS);
    }

    /** How visible the picture, the glow and the ray are: faded in and not yet faded out. */
    public static double visibility(long sinceStartMs, long sinceEndMs) {
        return fadeIn(sinceStartMs) * fadeOut(sinceEndMs);
    }

    /** The fade-out has played completely: the screen can go. */
    public static boolean finished(long sinceEndMs) {
        return sinceEndMs >= VisualCallConstants.FEED_FADE_OUT_MS;
    }

    /** Opacity of the TV static (0..PROJECTOR_STATIC_PEAK). */
    public static double staticAmount(long sinceStartMs, long sinceEndMs) {
        double in = 0.0;
        if (sinceStartMs >= 0L && sinceStartMs < VisualCallConstants.PROJECTOR_STATIC_IN_MS) {
            in = 1.0 - smoothstep(sinceStartMs / (double) VisualCallConstants.PROJECTOR_STATIC_IN_MS); // full at once, dying away
        }
        double out = 0.0;
        if (sinceEndMs >= 0L && sinceEndMs < VisualCallConstants.FEED_FADE_OUT_MS) {
            out = Math.sin(Math.PI * (sinceEndMs / (double) VisualCallConstants.FEED_FADE_OUT_MS)); // swells, then gone with the picture
        }
        return VisualCallConstants.PROJECTOR_STATIC_PEAK * Math.max(in, out);
    }

    private static double popIn(long sinceStartMs) {
        if (sinceStartMs < 0L) return 0.0;
        long rise = VisualCallConstants.PROJECTOR_POP_RISE_MS;
        if (sinceStartMs < rise) return sinceStartMs / (double) rise; // a sharp rise to the peak...
        return Math.exp(-(sinceStartMs - rise) / (double) VisualCallConstants.PROJECTOR_POP_DECAY_MS); // ...then it dies away quickly
    }

    private static double popOut(long sinceEndMs) {
        if (sinceEndMs < 0L || sinceEndMs > VisualCallConstants.FEED_FADE_OUT_MS) return 0.0;
        double x = sinceEndMs / (double) VisualCallConstants.FEED_FADE_OUT_MS;
        double d = (x - VisualCallConstants.PROJECTOR_POP_END_AT) / VisualCallConstants.PROJECTOR_POP_END_WIDTH;
        return Math.exp(-d * d); // a bump in the middle of the fade-out
    }

    /** The pop of light, 0..1 (1 at its peak). */
    public static double pop(long sinceStartMs, long sinceEndMs) {
        return Math.max(popIn(sinceStartMs), popOut(sinceEndMs));
    }

    /**
     * The picture's brightness flickering while the projector warms up and while it dies: 1 in the middle of a call, down to 0.65 at the very
     * beginning and the very end (scaled by {@code noise01}, a random 0..1 that the caller makes up).
     */
    public static double flicker(long sinceStartMs, long sinceEndMs, double noise01) {
        double startPart = sinceStartMs < 0L ? 1.0 : 1.0 - clamp01(sinceStartMs / (double) VisualCallConstants.FEED_FADE_IN_MS);
        double endPart = sinceEndMs < 0L ? 0.0 : clamp01(sinceEndMs / (double) VisualCallConstants.FEED_FADE_OUT_MS);
        return 1.0 - Math.max(startPart, endPart) * 0.35 * clamp01(noise01);
    }
}
