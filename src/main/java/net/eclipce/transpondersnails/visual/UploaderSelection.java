package net.eclipce.transpondersnails.visual;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Which player's client renders a camera's view for the viewers that cannot: one that is near the camera (so its client has the area
 * loaded). The nearest one within range - but the one that is doing it keeps doing it while it stays near, so the job does not jump between
 * two players who stand about as far. Pure (no Minecraft classes), unit tested.
 */
public final class UploaderSelection {

    private UploaderSelection() {}

    /**
     * @param candidates players in the camera's dimension
     * @param distanceSq squared distance of a player to the camera
     * @param maxRange   the farthest a new uploader may be (blocks)
     * @param current    who does it now (may be null, or no longer among the candidates)
     * @param stickyExtra how much farther than maxRange the current uploader may go before it is replaced (blocks)
     * @return the uploader, or null if nobody is near enough
     */
    public static <P> P choose(List<P> candidates, ToDoubleFunction<P> distanceSq, double maxRange, P current, double stickyExtra) {
        if (current != null && candidates.contains(current)) {
            double limit = maxRange + Math.max(0.0, stickyExtra);
            if (distanceSq.applyAsDouble(current) <= limit * limit) return current;
        }
        P best = null;
        double bestSq = maxRange * maxRange;
        for (P candidate : candidates) {
            double d = distanceSq.applyAsDouble(candidate);
            if (d <= bestSq) {
                bestSq = d;
                best = candidate;
            }
        }
        return best;
    }
}
