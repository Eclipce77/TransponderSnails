package net.eclipce.transpondersnails.gary;

import java.util.function.BooleanSupplier;

/**
 * How a sound decides whether a placed snail is a Gary: the snail's own block entity knows (it holds its own block state, so asking it touches no part of
 * the world and is right on ANY thread), and only a snail that is not registered anywhere falls back to reading the world - which vanilla only answers on
 * the server thread (getChunkNow, getBlockEntity and getBlockState from another thread give nothing or make the caller wait). The call manager ends calls
 * and plays their sounds from its own scheduler thread as often as from the server's, so a world read alone says "no" there, wrongly.
 * Pure (no Minecraft classes), unit tested.
 */
public final class GaryLookup {

    private GaryLookup() {}

    /**
     * @param registered what the snail's own block entity says (null if no registered block entity is at that place)
     * @param worldRead  the fallback, asked only when nothing is registered
     */
    public static boolean resolve(Boolean registered, BooleanSupplier worldRead) {
        if (registered != null) return registered;
        return worldRead != null && worldRead.getAsBoolean();
    }
}
