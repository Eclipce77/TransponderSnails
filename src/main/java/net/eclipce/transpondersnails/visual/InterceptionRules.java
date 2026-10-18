package net.eclipce.transpondersnails.visual;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The rules of intercepting a VISUAL signal with a Black Transponder Snail, which follow the phone snail's: only what is within range is
 * found, the nearest first, and a player who has already tapped some signals is offered the others before those again when switching.
 * Pure (no Minecraft classes), unit tested.
 */
public final class InterceptionRules {

    private InterceptionRules() {}

    public enum Kind { CALL, STREAM }

    /** A visual call or stream that could be intercepted. {@code id} is stable for as long as the signal lasts. */
    public record Signal(Kind kind, String id, double distance) { }

    /** What a black snail found when it opened: the nearer of a phone call and a visual signal, if any. */
    public enum Source { NONE, PHONE, VISUAL }

    /** The signals within range, nearest first (a signal at exactly the range is out of it, as for phone calls). */
    public static List<Signal> inRange(List<Signal> all, double range) {
        List<Signal> out = new ArrayList<>();
        for (Signal s : all) if (s.distance() < range) out.add(s);
        out.sort((a, b) -> {
            int c = Double.compare(a.distance(), b.distance());
            return c != 0 ? c : a.id().compareTo(b.id());
        });
        return out;
    }

    /** The nearest signal within range, or null. */
    public static Signal nearest(List<Signal> all, double range) {
        List<Signal> near = inRange(all, range);
        return near.isEmpty() ? null : near.get(0);
    }

    /**
     * The signal to switch to: the nearest one not yet tapped, or, if all have been, the one after the current (in the order nearest first,
     * wrapping round). null if there is nothing else to switch to.
     */
    public static Signal next(List<Signal> all, double range, String currentId, Set<String> tapped) {
        List<Signal> near = inRange(all, range);
        for (Signal s : near) if (!s.id().equals(currentId) && !tapped.contains(s.id())) return s;
        int at = -1;
        for (int i = 0; i < near.size(); i++) if (near.get(i).id().equals(currentId)) at = i;
        for (int step = 1; step <= near.size(); step++) {
            Signal s = near.get((at + step + near.size()) % near.size());
            if (!s.id().equals(currentId)) return s;
        }
        return null;
    }

    /**
     * Phone call or visual signal? The nearer one; the phone call if they are equally near (it was there first). A negative distance means
     * "none found".
     */
    public static Source choose(double phoneDistance, double visualDistance) {
        boolean phone = phoneDistance >= 0.0;
        boolean visual = visualDistance >= 0.0;
        if (!phone && !visual) return Source.NONE;
        if (!visual) return Source.PHONE;
        if (!phone) return Source.VISUAL;
        return visualDistance < phoneDistance ? Source.VISUAL : Source.PHONE;
    }

    /**
     * What each half of an intercepted call's split screen shows: the picture, or static if that end is protected by a White Transponder Snail
     * (protection is per participant, as for phone calls: an unprotected end is still seen).
     */
    public static boolean[] staticHalves(boolean firstProtected, boolean secondProtected) {
        return new boolean[]{firstProtected, secondProtected};
    }
}
