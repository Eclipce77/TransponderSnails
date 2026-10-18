package net.eclipce.transpondersnails.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntBinaryOperator;

/**
 * The snail number book: which snail (UUID) holds which 4-digit number, and which {@link SnailKind} it is. Phone snails and visual
 * snails draw from the SAME pool, so a number is always exactly one snail.
 *
 * Pure (no Minecraft classes): SnailNumberRegistry keeps one of these and only adds saving, revocation and the world around it.
 * All the allocation and scoped-removal logic lives here so it can be unit tested. Not thread safe on its own; the registry
 * synchronizes its writers.
 */
public final class SnailNumberTable {

    public static final int MIN_NUMBER = 1000;
    public static final int MAX_NUMBER = 9999;
    public static final int TOTAL_NUMBERS = MAX_NUMBER - MIN_NUMBER + 1; // 9000

    /** One line of the book, for saving. */
    public record Entry(UUID uuid, int number, SnailKind kind) { }

    private final Map<UUID, Integer> numberOf = new HashMap<>();
    private final Map<Integer, UUID> uuidOf = new HashMap<>();
    private final Map<UUID, SnailKind> kindOf = new HashMap<>();
    /** (min inclusive, max inclusive) -> a random number in between. Injectable so tests are repeatable. */
    private final IntBinaryOperator random;

    public SnailNumberTable() {
        this((lo, hi) -> ThreadLocalRandom.current().nextInt(lo, hi + 1));
    }

    public SnailNumberTable(IntBinaryOperator random) {
        this.random = random;
    }

    // ---------------- reading ----------------

    /** The number of this snail, or -1. */
    public int numberOf(UUID uuid) {
        return numberOf.getOrDefault(uuid, -1);
    }

    /** The snail that holds this number, or null. */
    public UUID uuidOf(int number) {
        return uuidOf.get(number);
    }

    public boolean isAssigned(int number) {
        return uuidOf.containsKey(number);
    }

    /** The kind of this snail, or null if it has no number. */
    public SnailKind kindOf(UUID uuid) {
        return kindOf.get(uuid);
    }

    /** The kind of the snail that holds this number, or null if the number is not assigned. */
    public SnailKind kindOfNumber(int number) {
        UUID uuid = uuidOf.get(number);
        return uuid == null ? null : kindOf.get(uuid);
    }

    public int size() {
        return uuidOf.size();
    }

    /** How many numbers of this scope are assigned. */
    public int size(SnailKind.Scope scope) {
        int count = 0;
        for (SnailKind kind : kindOf.values()) {
            if (scope.includes(kind)) count++;
        }
        return count;
    }

    public int available() {
        return TOTAL_NUMBERS - uuidOf.size();
    }

    /** A copy of the book: number -> snail, sorted by number, limited to a scope. */
    public TreeMap<Integer, UUID> snapshot(SnailKind.Scope scope) {
        TreeMap<Integer, UUID> copy = new TreeMap<>();
        for (Map.Entry<Integer, UUID> e : uuidOf.entrySet()) {
            if (scope.includes(kindOf.get(e.getValue()))) copy.put(e.getKey(), e.getValue());
        }
        return copy;
    }

    /** Every line of the book, sorted by number (for saving). */
    public List<Entry> entries() {
        List<Entry> list = new ArrayList<>();
        for (Map.Entry<Integer, UUID> e : new TreeMap<>(uuidOf).entrySet()) {
            list.add(new Entry(e.getValue(), e.getKey(), kindOf.get(e.getValue())));
        }
        return list;
    }

    // ---------------- writing ----------------

    /**
     * Gives this snail a number of its kind. A snail that already has one keeps it (and its kind: see {@link #setKind}).
     *
     * @return the number, or -1 if the pool is full
     */
    public int assign(UUID uuid, SnailKind kind) {
        int existing = numberOf(uuid);
        if (existing != -1) return existing;
        if (uuidOf.size() >= TOTAL_NUMBERS) return -1;

        int number = generateUniqueNumber();
        if (number == -1) return -1;
        put(uuid, number, kind);
        return number;
    }

    /**
     * Gives this snail its old number back if that is free, otherwise a new one.
     *
     * @return the number, or -1 if there is none to give
     */
    public int restore(UUID uuid, int preferred, SnailKind kind) {
        int existing = numberOf(uuid);
        if (existing != -1) return existing;

        if (preferred >= MIN_NUMBER && preferred <= MAX_NUMBER && !uuidOf.containsKey(preferred)) {
            put(uuid, preferred, kind);
            return preferred;
        }
        return assign(uuid, kind);
    }

    /** Puts a line of a saved book back. False (and nothing changes) if the number is out of range or the snail or number is taken. */
    public boolean load(UUID uuid, int number, SnailKind kind) {
        if (number < MIN_NUMBER || number > MAX_NUMBER) return false;
        if (numberOf.containsKey(uuid) || uuidOf.containsKey(number)) return false;
        put(uuid, number, kind);
        return true;
    }

    /**
     * Changes the kind of a snail that has a number. This heals a number that was recorded with the wrong kind (it keeps the number).
     *
     * @return true if the kind changed
     */
    public boolean setKind(UUID uuid, SnailKind kind) {
        SnailKind now = kindOf.get(uuid);
        if (now == null || now == kind) return false;
        kindOf.put(uuid, kind);
        return true;
    }

    /** Takes the number away from this snail. @return the number that was freed, or -1 if it had none */
    public int remove(UUID uuid) {
        Integer number = numberOf.remove(uuid);
        if (number == null) return -1;
        uuidOf.remove(number);
        kindOf.remove(uuid);
        return number;
    }

    /** Takes away every number of a scope. @return what was removed: number -> snail, sorted by number */
    public TreeMap<Integer, UUID> removeAll(SnailKind.Scope scope) {
        TreeMap<Integer, UUID> removed = snapshot(scope);
        for (UUID uuid : removed.values()) {
            numberOf.remove(uuid);
            kindOf.remove(uuid);
        }
        for (Integer number : removed.keySet()) {
            uuidOf.remove(number);
        }
        return removed;
    }

    public void clear() {
        numberOf.clear();
        uuidOf.clear();
        kindOf.clear();
    }

    private void put(UUID uuid, int number, SnailKind kind) {
        numberOf.put(uuid, number);
        uuidOf.put(number, uuid);
        kindOf.put(uuid, kind == null ? SnailKind.PHONE : kind);
    }

    /** A free number: random while there is plenty of room, in order once the pool is getting full. -1 if there is none. */
    private int generateUniqueNumber() {
        // Getting close to capacity: take the first free number
        if (uuidOf.size() > TOTAL_NUMBERS * 0.8) {
            return firstFree();
        }

        // Plenty of room: random numbers spread better
        for (int attempts = 0; attempts < 1000; attempts++) {
            int number = random.applyAsInt(MIN_NUMBER, MAX_NUMBER);
            if (!uuidOf.containsKey(number)) return number;
        }

        return firstFree();
    }

    private int firstFree() {
        for (int number = MIN_NUMBER; number <= MAX_NUMBER; number++) {
            if (!uuidOf.containsKey(number)) return number;
        }
        return -1;
    }
}
