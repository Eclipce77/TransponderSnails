package net.eclipce.transpondersnails.visual;

/**
 * The arithmetic of the scrolling list of streams, the flashing of the preset boxes and double-clicking. Pure (no Minecraft classes),
 * unit tested; the client widgets only draw and pass the mouse on.
 */
public final class StreamListMath {

    private StreamListMath() {}

    /** The most the list can be scrolled: how many rows do not fit. */
    public static int maxScroll(int total, int visible) {
        return Math.max(0, total - visible);
    }

    public static int clampScroll(int scroll, int total, int visible) {
        return Math.max(0, Math.min(scroll, maxScroll(total, visible)));
    }

    /** The row (index into the whole list) under a mouse y, or -1 if there is none. */
    public static int rowAt(int mouseY, int top, int rowHeight, int scroll, int total, int visible) {
        if (mouseY < top || rowHeight <= 0) return -1;
        int slot = (mouseY - top) / rowHeight;
        if (slot >= visible) return -1;
        int row = scroll + slot;
        return row < total ? row : -1;
    }

    /** A scroll that brings a row into view with the least movement. */
    public static int ensureVisible(int scroll, int row, int total, int visible) {
        if (row < scroll) scroll = row;
        else if (row >= scroll + visible) scroll = row - visible + 1;
        return clampScroll(scroll, total, visible);
    }

    /** The scrollbar's thumb: {top, height} in the track, as tall as the visible part of the list is of the whole. At least 6 high. */
    public static int[] thumb(int trackTop, int trackHeight, int total, int visible, int scroll) {
        if (total <= visible || trackHeight <= 0) return new int[]{trackTop, Math.max(0, trackHeight)};
        int height = Math.max(6, Math.min(trackHeight, trackHeight * visible / total));
        int travel = trackHeight - height;
        int max = maxScroll(total, visible);
        int top = trackTop + (max == 0 ? 0 : travel * clampScroll(scroll, total, visible) / max);
        return new int[]{top, height};
    }

    /** The scroll for a mouse y on the scrollbar (the thumb is centred on the mouse). */
    public static int scrollForY(int mouseY, int trackTop, int trackHeight, int total, int visible) {
        int max = maxScroll(total, visible);
        if (max == 0) return 0;
        int height = thumb(trackTop, trackHeight, total, visible, 0)[1];
        int travel = Math.max(1, trackHeight - height);
        double t = (mouseY - trackTop - height / 2.0) / travel;
        return clampScroll((int) Math.round(t * max), total, visible);
    }

    /** 0..1, a smooth pulse (the preset boxes flash with it), once every PERIOD_MS. */
    public static final long FLASH_PERIOD_MS = 900L;

    public static double flash(long nowMs) {
        double phase = (nowMs % FLASH_PERIOD_MS) / (double) FLASH_PERIOD_MS;
        return 0.5 - 0.5 * Math.cos(phase * 2.0 * Math.PI);
    }

    /** Tells a double click from two single ones: the same row clicked again within DOUBLE_CLICK_MS. */
    public static final class ClickTracker {
        public static final long DOUBLE_CLICK_MS = 300L;
        private long lastMs = Long.MIN_VALUE / 2;
        private int lastRow = -1;

        /** @return true if this click is the second of a double click (and then forgets it, so a third click starts again) */
        public boolean click(long nowMs, int row) {
            boolean dbl = row >= 0 && row == lastRow && nowMs - lastMs <= DOUBLE_CLICK_MS;
            if (dbl) {
                lastRow = -1;
                lastMs = Long.MIN_VALUE / 2;
            } else {
                lastRow = row;
                lastMs = nowMs;
            }
            return dbl;
        }
    }
}
