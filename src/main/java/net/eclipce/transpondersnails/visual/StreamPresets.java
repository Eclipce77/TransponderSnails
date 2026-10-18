package net.eclipce.transpondersnails.visual;

import java.util.UUID;

/**
 * The three saved streams ("presets") of a Visual Transponder Snail. A preset remembers which stream it is (the stream's id, which
 * stays the same when the stream is off) and the name it had, so it can still be shown while the stream is not active.
 * Pure (no Minecraft classes), unit tested; the block entity saves and loads it.
 */
public final class StreamPresets {

    public static final int SLOTS = 3;

    public record Preset(UUID id, String name) { }

    private final Preset[] slots = new Preset[SLOTS];

    public static boolean validSlot(int slot) {
        return slot >= 0 && slot < SLOTS;
    }

    /** The preset in a slot, or null if it is empty (or the slot does not exist). */
    public Preset get(int slot) {
        return validSlot(slot) ? slots[slot] : null;
    }

    public boolean isEmpty(int slot) {
        return get(slot) == null;
    }

    /** Saves a stream into a slot, replacing what was there. False (nothing changes) for a bad slot or a missing id. */
    public boolean set(int slot, UUID id, String name) {
        if (!validSlot(slot) || id == null) return false;
        slots[slot] = new Preset(id, VisualStreamNames.sanitize(name));
        return true;
    }

    public void clear(int slot) {
        if (validSlot(slot)) slots[slot] = null;
    }

    /** Keeps the remembered names up to date when a stream is renamed. @return true if any preset changed */
    public boolean rename(UUID id, String name) {
        String clean = VisualStreamNames.sanitize(name);
        boolean changed = false;
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] != null && slots[i].id().equals(id) && !slots[i].name().equals(clean)) {
                slots[i] = new Preset(id, clean);
                changed = true;
            }
        }
        return changed;
    }

    /** The first slot that holds this stream, or -1. */
    public int slotOf(UUID id) {
        for (int i = 0; i < SLOTS; i++) {
            if (slots[i] != null && slots[i].id().equals(id)) return i;
        }
        return -1;
    }

    public void clearAll() {
        for (int i = 0; i < SLOTS; i++) slots[i] = null;
    }
}
