package net.eclipce.transpondersnails.visual;

import java.util.Locale;

/**
 * Names of streams (a Visual Transmitter's broadcast). Pure (no Minecraft classes), unit tested.
 * A stream that has not been named shows its owner: "Steve's Stream".
 */
public final class VisualStreamNames {

    private VisualStreamNames() {}

    public static final int MAX_LENGTH = 32;

    /**
     * A name as it may be stored: no control characters and no formatting codes, runs of spaces collapsed to one, no space at the ends,
     * at most MAX_LENGTH characters. Blank if nothing is left (or the input is null).
     */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length());
        boolean pendingSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c) || c == '\u00A7') continue;
            if (Character.isWhitespace(c)) {
                pendingSpace = out.length() > 0;
                continue;
            }
            if (pendingSpace) {
                out.append(' ');
                pendingSpace = false;
            }
            out.append(c);
        }
        String s = out.toString();
        return s.length() > MAX_LENGTH ? s.substring(0, MAX_LENGTH).trim() : s;
    }

    /** What the stream is called: its own name, or "Steve's Stream" after its owner, or "Visual Stream" if there is neither. */
    public static String displayName(String customName, String ownerName) {
        String custom = sanitize(customName);
        if (!custom.isEmpty()) return custom;
        String owner = sanitize(ownerName);
        return owner.isEmpty() ? "Visual Stream" : owner + "'s Stream";
    }

    /** Order of streams in a list: by name, ignoring case, then exactly (so the order never flips between two refreshes). */
    public static int compare(String a, String b) {
        int c = a.toLowerCase(Locale.ROOT).compareTo(b.toLowerCase(Locale.ROOT));
        return c != 0 ? c : a.compareTo(b);
    }
}
