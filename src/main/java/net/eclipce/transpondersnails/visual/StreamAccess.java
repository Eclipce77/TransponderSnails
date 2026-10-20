package net.eclipce.transpondersnails.visual;

import java.util.Collection;
import java.util.List;

/**
 * Who may see a stream. A PUBLIC stream is seen by every Visual Transponder Snail in its dimension; a PRIVATE one is invite only: it is seen by
 * the snails whose numbers are on its invite list, and by nobody else (it does not even appear in their list). The invite list is of snail
 * numbers (1000-9999), in the order they were invited. Pure (no Minecraft classes), unit tested.
 */
public final class StreamAccess {

    private StreamAccess() {}

    /** The most numbers a stream can invite. */
    public static final int MAX_INVITES = 32;
    public static final int MIN_NUMBER = 1000;
    public static final int MAX_NUMBER = 9999;

    public enum InviteResult { ADDED, ALREADY_INVITED, FULL, INVALID_NUMBER }

    public static boolean validNumber(int number) {
        return number >= MIN_NUMBER && number <= MAX_NUMBER;
    }

    /** A number typed into the invite box: exactly four digits (spaces around it and a leading # are ignored); -1 if it is not one. */
    public static int parseNumber(String text) {
        if (text == null) return -1;
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1).trim();
        if (t.length() != 4) return -1;
        for (int i = 0; i < 4; i++) if (t.charAt(i) < '0' || t.charAt(i) > '9') return -1;
        int n = Integer.parseInt(t);
        return validNumber(n) ? n : -1;
    }

    /**
     * May the snail with this number see the stream? Public: always. Private: only if its number is invited (a snail with no number never is).
     */
    public static boolean canSee(boolean privateStream, Collection<Integer> invites, int snailNumber) {
        if (!privateStream) return true;
        return validNumber(snailNumber) && invites.contains(snailNumber);
    }

    /** Adds a number to the end of the list, unless it is not a number, already there, or the list is full. */
    public static InviteResult add(List<Integer> invites, int number) {
        if (!validNumber(number)) return InviteResult.INVALID_NUMBER;
        if (invites.contains(number)) return InviteResult.ALREADY_INVITED;
        if (invites.size() >= MAX_INVITES) return InviteResult.FULL;
        invites.add(number);
        return InviteResult.ADDED;
    }

    /** Takes a number off the list. @return true if it was there */
    public static boolean remove(List<Integer> invites, int number) {
        return invites.remove((Integer) number);
    }
}
