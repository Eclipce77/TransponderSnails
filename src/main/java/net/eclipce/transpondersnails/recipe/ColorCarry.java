package net.eclipce.transpondersnails.recipe;

/**
 * The one rule for what colours an ingredient snail hands on to what a recipe makes. A snail item keeps its colours in up to four places: the
 * top-level {@code body_color} / {@code shell_color} (or the Den Den Mushi's {@code BodyColor} / {@code ShellColor}), and inside its
 * {@code BlockEntityTag}. The body colour is what makes a snail "that" snail, so without one there is nothing to carry (a snail that was only
 * dyed has a shell but no body, and the one that is made from it gets a fresh random body when it is placed); with one, the shell comes along,
 * white if there is none. Pure (no Minecraft classes), unit tested.
 */
public final class ColorCarry {

    private ColorCarry() {}

    /**
     * @return {body, shell} to carry, or null if the ingredient has no body colour to hand on
     */
    public static int[] resolve(Integer topBody, Integer topShell, Integer blockEntityBody, Integer blockEntityShell) {
        Integer body = topBody != null ? topBody : blockEntityBody;
        if (body == null) return null;
        Integer shell = topShell != null ? topShell : blockEntityShell;
        return new int[]{body, shell != null ? shell : 0};
    }
}
