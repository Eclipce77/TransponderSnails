package net.eclipce.transpondersnails.visual;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which blocks are connected to each other by wire. The same walk the mod's wires already use (the lightning-rod and white-snail traces):
 * from a start block, go through wire blocks to the four horizontal neighbours - and to the block above and below each of them, as a wire
 * climbs - and report the target blocks reached. A target that is not a wire is where the walk stops (a snail does not conduct); only the
 * start block itself is walked out of whatever it is. Pure (no Minecraft classes), unit tested; the game supplies what a block is.
 */
public final class WireTrace {

    private WireTrace() {}

    /** A block position. */
    public record Pos(int x, int y, int z) {
        Pos offset(int dx, int dy, int dz) {
            return new Pos(x + dx, y + dy, z + dz);
        }
    }

    private static final int[][] HORIZONTAL = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
    private static final int[] LEVELS = {0, 1, -1};

    /**
     * @param isWire       is this block a wire?
     * @param isTarget     is this block one we are looking for (a snail)?
     * @param maxVisited   the most blocks to look at (the mod's wires use 64 * 64)
     * @return every target reachable from {@code start} through wires, not counting {@code start} itself
     */
    public static Set<Pos> findTargets(Pos start, Predicate<Pos> isWire, Predicate<Pos> isTarget, int maxVisited) {
        Set<Pos> found = new HashSet<>();
        Set<Pos> visited = new HashSet<>();
        ArrayDeque<Pos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty() && visited.size() < maxVisited) {
            Pos current = queue.poll();
            if (!current.equals(start) && isTarget.test(current)) found.add(current);
            if (!current.equals(start) && !isWire.test(current)) continue;   // a snail or a rod ends the walk
            for (int[] d : HORIZONTAL) {
                for (int dy : LEVELS) {                                      // same level, above, below
                    Pos next = current.offset(d[0], dy, d[1]);
                    if (visited.contains(next)) continue;
                    if (isWire.test(next) || isTarget.test(next)) {
                        visited.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        return found;
    }
}
