package net.eclipce.transpondersnails.commands;

import net.eclipce.transpondersnails.data.SnailKind;

import java.util.Locale;

/**
 * What the player typed after "/snailnumber remove", as one piece of text. Pure (no Minecraft classes), unit tested.
 *
 * <pre>
 *   1234                    one number
 *   all | *                 every number (phone and visual)
 *   all phone | * phone     only the phone snails' numbers          (also: phone, phones)
 *   all visual | * visual   only the visual snails' numbers         (also: visual, visuals)
 *   all all                 same as all
 *   confirm | cancel        answer the question that "all ..." asks
 * </pre>
 * The type word may come before or after all / *. Any case. These words are parsed here and not registered as command nodes, so tab
 * completion only ever offers the number.
 */
public final class RemoveArgument {

    private RemoveArgument() {}

    public enum Type { NUMBER, ALL, CONFIRM, CANCEL, INVALID }

    /**
     * @param type    what it is
     * @param number  the snail number (NUMBER only)
     * @param scope   which numbers (ALL only: all, phone or visual)
     * @param problem what to tell the player (INVALID only)
     * @param text    what was typed, trimmed and in lower case
     */
    public record Parsed(Type type, int number, SnailKind.Scope scope, String problem, String text) { }

    private static final String USAGE = "Enter a snail number: /snailnumber remove <number>";

    public static Parsed parse(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return invalid(USAGE, text);
        String[] words = text.split("\\s+");

        if (words.length == 1 && words[0].matches("\\d{1,9}")) {
            int number = Integer.parseInt(words[0]);
            if (number < 1000 || number > 9999) return invalid("Snail numbers are 1000-9999.", text);
            return new Parsed(Type.NUMBER, number, null, null, text);
        }

        if (words.length == 1 && words[0].equals("confirm")) return new Parsed(Type.CONFIRM, 0, null, null, text);
        if (words.length == 1 && words[0].equals("cancel")) return new Parsed(Type.CANCEL, 0, null, null, text);

        SnailKind.Scope first = SnailKind.Scope.parse(words[0]);
        if (first == null) return invalid(USAGE, text);
        if (words.length == 1) return all(first, text);

        if (words.length == 2) {
            SnailKind.Scope second = SnailKind.Scope.parse(words[1]);
            if (second == null) return invalid("Unknown snail type \"" + words[1] + "\" - use phone, visual or all.", text);
            if (first == SnailKind.Scope.ALL) return all(second, text);            // all visual, * phone, all all
            if (second == SnailKind.Scope.ALL) return all(first, text);            // visual all
            return invalid("Pick one type: phone or visual (or all for both).", text); // visual phone
        }

        return invalid(USAGE, text);
    }

    private static Parsed all(SnailKind.Scope scope, String text) {
        return new Parsed(Type.ALL, 0, scope, null, text);
    }

    private static Parsed invalid(String problem, String text) {
        return new Parsed(Type.INVALID, 0, null, problem, text);
    }
}
