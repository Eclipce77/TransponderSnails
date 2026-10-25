package net.eclipce.transpondersnails.gary;

import java.util.Locale;

/**
 * The secret: a Den Den Mushi or a Transponder Snail named "Gary" or "Gary the Snail" (any capitals) turns into Gary. This is the one place that decides
 * whether a name counts, so the creature, the placed block and the item all agree. Pure (no Minecraft classes), unit tested.
 */
public final class GaryName {

    private GaryName() {}

    private static final String SHORT = "gary";
    private static final String LONG = "gary the snail";
    private static final java.util.regex.Pattern SPACES = java.util.regex.Pattern.compile("\\s+");   // compiled once: the creature is asked every frame
    /** Names are at most 50 characters in the game; anything this long cannot be Gary's (and is not worth looking at, every frame). */
    private static final int LONGEST_WORTH_CHECKING = 128;

    /**
     * Is this name Gary's? Capitals do not matter, nor do spaces around the name or doubled up inside it; nothing else is allowed
     * ("Garys", "Gary the Snail!", "Gary the" are all just names).
     */
    public static boolean matches(String name) {
        if (name == null || name.length() > LONGEST_WORTH_CHECKING) return false;
        String normalized = SPACES.matcher(name.trim()).replaceAll(" ").toLowerCase(Locale.ROOT);
        return normalized.equals(SHORT) || normalized.equals(LONG);
    }
}
