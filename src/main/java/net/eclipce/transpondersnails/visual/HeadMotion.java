package net.eclipce.transpondersnails.visual;

/**
 * How the projector's head follows its target: it does not jump to a new angle, it glides - each moment it covers the same share of what is left (an exponential ease), so a
 * click on an arrow gives a short smooth turn and holding one gives a steady follow. Because it is a share of what is left, two short steps make exactly the same turn as one
 * long step: how fast it moves does not depend on the frame rate. Pure (no Minecraft classes), unit tested.
 */
public final class HeadMotion {

    private HeadMotion() {}

    /** After a pause (the game was frozen, or the head was out of sight) it does not glide for longer than this: at most this much time passes in one step. */
    public static final double MAX_STEP_SECONDS = 0.25;
    /** Closer than this (degrees) to the target it just arrives. */
    public static final double ARRIVE_DEGREES = 0.02;

    /** One angle, {@code dtSeconds} later; {@code speed} is VisualCallConstants.PROJECTOR_HEAD_TURN_SPEED. */
    public static double step(double current, double target, double dtSeconds, double speed) {
        if (Double.isNaN(current) || Double.isNaN(target)) return Double.isNaN(target) ? 0.0 : target;
        if (!(dtSeconds > 0.0) || !(speed > 0.0)) return current;
        double share = 1.0 - Math.exp(-speed * Math.min(dtSeconds, MAX_STEP_SECONDS));
        double next = current + (target - current) * share;
        return Math.abs(target - next) < ARRIVE_DEGREES ? target : next;
    }

    /** Both angles of a pose, {@code dtSeconds} later (the lift is worked out again for the new angles). */
    public static ProjectorHeadAim.Pose step(ProjectorHeadAim.Pose current, ProjectorHeadAim.Pose target, double dtSeconds, double speed) {
        return ProjectorHeadAim.pose(step(current.yaw(), target.yaw(), dtSeconds, speed), step(current.pitch(), target.pitch(), dtSeconds, speed));
    }
}
