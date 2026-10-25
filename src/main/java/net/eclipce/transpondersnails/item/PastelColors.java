package net.eclipce.transpondersnails.item;

import java.util.Random;

/**
 * The random pastel body colour a snail gets when it comes into being: a random hue, 30-60% saturation, 70-90% lightness - the same recipe the
 * numbered Transponder Snails use. Pure (no Minecraft classes), unit tested.
 */
public final class PastelColors {

    private PastelColors() {}

    /** A random pastel colour as 0xRRGGBB. */
    public static int random(Random random) {
        float hue = random.nextFloat();
        float saturation = 0.3F + random.nextFloat() * 0.3F;
        float lightness = 0.7F + random.nextFloat() * 0.2F;
        return hslToRgb(hue, saturation, lightness);
    }

    /**
     * The body colour for ONE placement: the colour the item carries (one crafted from a captured Den Den Mushi, or dropped by a placed snail), or - if
     * it carries none - a fresh random pastel. It never changes what the item carries, so a stack that is not used up (creative mode) gives every snail
     * placed from it its own colour, like the numbered Transponder Snails.
     */
    public static int forPlacement(Integer carried, Random random) {
        return carried != null ? carried : random(random);
    }

    /** HSL (each 0..1) to 0xRRGGBB. */
    public static int hslToRgb(float h, float s, float l) {
        float r, g, b;
        if (s == 0.0F) {
            r = g = b = l;
        } else {
            float q = l < 0.5F ? l * (1.0F + s) : l + s - l * s;
            float p = 2.0F * l - q;
            r = hueToRgb(p, q, h + 1.0F / 3.0F);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0F / 3.0F);
        }
        return (Math.round(r * 255.0F) << 16) | (Math.round(g * 255.0F) << 8) | Math.round(b * 255.0F);
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0.0F) t += 1.0F;
        if (t > 1.0F) t -= 1.0F;
        if (t < 1.0F / 6.0F) return p + (q - p) * 6.0F * t;
        if (t < 0.5F) return q;
        if (t < 2.0F / 3.0F) return p + (q - p) * (2.0F / 3.0F - t) * 6.0F;
        return p;
    }

    /** The saturation of 0xRRGGBB in HSL (for tests and tooling). */
    public static float saturation(int rgb) {
        float r = ((rgb >> 16) & 255) / 255.0F, g = ((rgb >> 8) & 255) / 255.0F, b = (rgb & 255) / 255.0F;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max == min) return 0.0F;
        float l = (max + min) / 2.0F;
        return l > 0.5F ? (max - min) / (2.0F - max - min) : (max - min) / (max + min);
    }

    /** The lightness of 0xRRGGBB in HSL (for tests and tooling). */
    public static float lightness(int rgb) {
        float r = ((rgb >> 16) & 255) / 255.0F, g = ((rgb >> 8) & 255) / 255.0F, b = (rgb & 255) / 255.0F;
        return (Math.max(r, Math.max(g, b)) + Math.min(r, Math.min(g, b))) / 2.0F;
    }
}
