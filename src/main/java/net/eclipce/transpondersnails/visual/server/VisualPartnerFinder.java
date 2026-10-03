package net.eclipce.transpondersnails.visual.server;

import java.util.List;

/** Pure partner-selection logic ("any two Visual Snails within range auto-connect"). No Minecraft classes. */
public final class VisualPartnerFinder {

    private VisualPartnerFinder() {}

    public record Candidate(double x, double y, double z, boolean idle) {}

    public enum Outcome {
        /** An idle snail is in range - {@link Result#index()} is the nearest one. */
        FOUND,
        /** Snails are in range but every one of them is already in a call. */
        BUSY,
        /** No other snail in range at all. */
        NONE
    }

    public record Result(Outcome outcome, int index) {}

    public static Result find(double ox, double oy, double oz, List<Candidate> candidates, double range) {
        double rangeSq = range * range;
        int best = -1;
        double bestDist = Double.MAX_VALUE;
        boolean anyInRange = false;

        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            double dx = c.x() - ox;
            double dy = c.y() - oy;
            double dz = c.z() - oz;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > rangeSq) {
                continue;
            }
            anyInRange = true;
            if (c.idle() && distSq < bestDist) {
                bestDist = distSq;
                best = i;
            }
        }

        if (best >= 0) return new Result(Outcome.FOUND, best);
        return new Result(anyInRange ? Outcome.BUSY : Outcome.NONE, -1);
    }
}
