package net.eclipce.transpondersnails.data;

import java.util.Locale;

/**
 * Which kind of snail a snail number belongs to. Both kinds draw their number from the same pool (4 digits, 1000-9999, so a
 * number is always ONE snail) and get it the same way (on first use, see TransponderSnailBlockEntity.ensureSnailNumberAssigned),
 * but they are separate telephone networks: a snail can only dial numbers of its own kind (see {@link #canDial}).
 *
 * Pure (no Minecraft classes), unit tested.
 */
public enum SnailKind {

    /** A Transponder Snail: placed or handheld, makes voice calls. */
    PHONE("phone", "Transponder Snail"),
    /** A Visual Transponder Snail (the projector): makes video calls. The Visual Transmitter (camera) has no number at all. */
    VISUAL("visual", "Visual Transponder Snail");

    private final String label;
    private final String displayName;

    SnailKind(String label, String displayName) {
        this.label = label;
        this.displayName = displayName;
    }

    /** Lower case word for commands and logs: "phone", "visual". */
    public String label() {
        return label;
    }

    /** The name of the snail for messages to players: "Transponder Snail", "Visual Transponder Snail". */
    public String displayName() {
        return displayName;
    }

    /**
     * The kind stored in a save file. Anything unknown or missing is a phone: every number saved before visual snails had numbers
     * is a phone number.
     */
    public static SnailKind fromSaved(String name) {
        if (name != null) {
            for (SnailKind kind : values()) {
                if (kind.name().equalsIgnoreCase(name.trim())) return kind;
            }
        }
        return PHONE;
    }

    /**
     * Whether a snail of kind {@code caller} may dial the number of a snail of kind {@code target}: only its own kind. A phone cannot
     * dial a visual snail and a visual snail cannot dial a phone; when the call is refused it is bounced back to the caller.
     * A missing kind counts as a phone (the same rule as {@link #fromSaved}).
     */
    public static boolean canDial(SnailKind caller, SnailKind target) {
        return (caller == null ? PHONE : caller) == (target == null ? PHONE : target);
    }

    /**
     * Which numbers a bulk command (remove all) applies to: every number, or only the phone or only the visual snails' numbers.
     */
    public enum Scope {
        ALL("all", null),
        PHONE("phone", SnailKind.PHONE),
        VISUAL("visual", SnailKind.VISUAL);

        private final String label;
        private final SnailKind only;

        Scope(String label, SnailKind only) {
            this.label = label;
            this.only = only;
        }

        public String label() {
            return label;
        }

        /** True if a number of this kind is covered. */
        public boolean includes(SnailKind kind) {
            return only == null || only == (kind == null ? SnailKind.PHONE : kind);
        }

        /** The scope for a command word: all, *, phone(s), visual(s) - in any case. Null if the word is none of them. */
        public static Scope parse(String word) {
            if (word == null) return null;
            switch (word.trim().toLowerCase(Locale.ROOT)) {
                case "all":
                case "*":
                    return ALL;
                case "phone":
                case "phones":
                    return PHONE;
                case "visual":
                case "visuals":
                    return VISUAL;
                default:
                    return null;
            }
        }
    }
}
