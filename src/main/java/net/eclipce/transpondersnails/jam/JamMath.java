package net.eclipce.transpondersnails.jam;

/**
 * The arithmetic of jamming. A jammer does not cut calls off: it fills them with static, the more the nearer you are. Pure (no Minecraft
 * classes), unit tested; the jam manager only feeds it positions and the clock.
 */
public final class JamMath {

    private JamMath() {}

    /** Within this many blocks of a jammer the jam is total. */
    public static final double FULL_WITHIN = 1.0;

    /**
     * How strongly a jammer with this range jams something {@code distance} blocks away: 0 at the edge of its range (and beyond), growing in a
     * straight line to 1 (all static) at 1 block or nearer. 20 blocks of range: 20 away = 0, 10 away = 0.5, 1 away or less = 1.
     */
    public static double factor(double distance, double range) {
        if (!(range > 0.0) || Double.isNaN(distance)) return 0.0;
        if (distance <= FULL_WITHIN) return 1.0;
        if (distance >= range) return 0.0;
        return Math.max(0.0, Math.min(1.0, 1.0 - distance / range));
    }

    /**
     * The range of a placed Horned Transponder Snail: {@code base} with no lightning rods; with rods, {@code perRod} more for each one, but never
     * more than {@code max} (the same shape as a Black Transponder Snail's tower, whose rods are counted the same way).
     */
    public static double rangeForRods(int rods, double base, double perRod, double max) {
        double cap = Math.max(0.0, max);
        if (rods <= 0) return Math.min(Math.max(0.0, base), cap);
        return Math.min(cap, Math.max(0.0, base) + rods * Math.max(0.0, perRod));
    }

    /** Moves {@code current} towards {@code target} by at most {@code maxStep}. */
    public static double approach(double current, double target, double maxStep) {
        double step = Math.max(0.0, maxStep);
        if (target > current) return Math.min(target, current + step);
        return Math.max(target, current - step);
    }

    /** How much the jam may change in one server tick if a full swing (0 to 1) is to take {@code fadeSeconds}. */
    public static double stepPerTick(double fadeSeconds) {
        return 1.0 / (Math.max(0.05, fadeSeconds) * 20.0);
    }

    /** The jam level as one byte (0..255), for the network. */
    public static int quantize(double level) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, level)) * 255.0);
    }

    public static double dequantize(int quantized) {
        return Math.max(0, Math.min(255, quantized)) / 255.0;
    }

    /**
     * Should a changed jam level be sent to the screens that show it? When it has moved enough to see, when it has just reached or left zero, or
     * (while there is any) every so often, so that a screen that has just started to show it learns it too.
     */
    public static boolean shouldSend(double lastSent, double now, int ticksSinceSent, int resendTicks) {
        if (quantize(lastSent) == quantize(now)) return now > 0.0 && ticksSinceSent >= resendTicks;
        if ((lastSent <= 0.0) != (now <= 0.0)) return true;
        return Math.abs(now - lastSent) >= 0.02 || ticksSinceSent >= resendTicks;
    }
}
