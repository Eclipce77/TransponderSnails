package net.eclipce.transpondersnails.client;

/**
 * Maths of a slider that picks one of a fixed list of values (e.g. the resolutions 256, 512, 1024, 2048, or 1..16 feeds).
 * Pure (no Minecraft classes), unit tested.
 */
public final class StepSliderMath {

    private StepSliderMath() {}

    /** Index of the value closest to {@code value} (a tie goes to the lower one). Always a valid index of a non-empty array. */
    public static int indexOf(int[] values, int value) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < values.length; i++) {
            long distance = Math.abs((long) values[i] - (long) value);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** Slider position 0..1 of an index (a slider with a single value sits at 0). */
    public static double position(int index, int count) {
        if (count <= 1) return 0.0;
        return clampIndex(index, count) / (double) (count - 1);
    }

    /** The index a slider position 0..1 points at (nearest step). NaN and out-of-range positions are made safe. */
    public static int indexAt(double position, int count) {
        if (count <= 1 || Double.isNaN(position)) return 0;
        double p = Math.max(0.0, Math.min(1.0, position));
        return clampIndex((int) Math.round(p * (count - 1)), count);
    }

    public static int clampIndex(int index, int count) {
        return Math.max(0, Math.min(Math.max(0, count - 1), index));
    }
}
