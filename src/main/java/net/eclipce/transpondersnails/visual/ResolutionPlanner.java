package net.eclipce.transpondersnails.visual;

/**
 * Decides WHEN a feed changes resolution. Changing it re-creates the render target (a small hitch), so it must not flap
 * when the viewer walks back and forth: a different resolution is only applied after it has been wanted continuously for a
 * while, and never more often than a minimum interval. Limits that got lower (server / player settings, frame governor) are
 * obeyed at once. Pure logic (one instance per feed), unit tested.
 */
public final class ResolutionPlanner {

    private int pending = 0;
    private long pendingSinceMs = 0L;
    private long lastResizeMs = Long.MIN_VALUE / 2;

    /**
     * @param current 0 if the feed has no render target yet, otherwise its resolution
     * @param wanted  the tier the screens would like (any value; clamped to [min, cap])
     * @param cap     highest allowed tier right now
     * @param min     lowest allowed tier
     * @return the resolution to render the next capture at
     */
    public int choose(int current, int wanted, int cap, int min, long nowMs) {
        int target = Math.max(min, Math.min(cap, wanted));

        if (current == 0) {                 // first allocation: no render target to protect yet
            pending = 0;
            lastResizeMs = nowMs;
            return target;
        }
        if (current > cap) {                // a limit got lower: obey at once
            pending = 0;
            lastResizeMs = nowMs;
            return cap;
        }
        if (target == current) {
            pending = 0;
            return current;
        }
        if (pending != target) {            // a new wish: start the clock
            pending = target;
            pendingSinceMs = nowMs;
            return current;
        }
        long delay = target > current ? VisualCallConstants.LOD_UP_DELAY_MS : VisualCallConstants.LOD_DOWN_DELAY_MS;
        if (nowMs - pendingSinceMs >= delay && nowMs - lastResizeMs >= VisualCallConstants.LOD_MIN_RESIZE_INTERVAL_MS) {
            pending = 0;
            lastResizeMs = nowMs;
            return target;
        }
        return current;
    }
}
