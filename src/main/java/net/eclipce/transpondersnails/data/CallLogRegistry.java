package net.eclipce.transpondersnails.data;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Permanent record of every call placed between Transponder Snails.
 *
 * Storage: one JSON object per line in <world>/data/transponder_snails_call_log.jsonl.
 * A call is appended when it finishes, so nothing is ever rewritten and a killed server
 * loses at most the calls that were still in progress. The whole file is also held in memory
 * so the /snailnumber commands can query it without touching the disk.
 *
 * Thread-safe: calls end on the voice-chat scheduler threads as well as on the server thread.
 * Logging must never break a call, so callers wrap their calls in try/catch.
 */
public final class CallLogRegistry {

    private static final String FILE_NAME = "transponder_snails_call_log.jsonl";
    private static final Gson GSON = new Gson();

    /** How a call turned out, from the point of view of "did the two snails ever talk". */
    public enum Outcome {
        ANSWERED,        // connected, and later hung up / ended
        MISSED,          // rang until the ring timeout, nobody answered
        REJECTED,        // the receiving side declined it
        CANCELLED,       // the caller hung up (or disconnected) while it was still ringing
        BUSY,            // the target was already in a call
        UNREACHABLE,     // the number exists but nobody could be rung (not loaded / not held)
        NO_SUCH_NUMBER,  // the number was not assigned to any snail
        FAILED           // ended before connecting for any other reason
    }

    /** Why a call that was in progress ended. */
    public enum EndReason {
        HANG_UP,         // a participant hung up
        REJECTED,
        RING_TIMEOUT,
        INACTIVITY,
        DISCONNECT,      // a participant left the server
        SNAIL_GONE,      // a placed snail was broken, unloaded or destroyed
        NUMBER_REMOVED,  // an admin removed the number (/snailnumber remove)
        SERVER_STOP,
        UNKNOWN
    }

    /**
     * One side of a call. type is BLOCK, HANDHELD or UNKNOWN. The location is where the snail
     * (block) or the player holding it (handheld) was; dimension is null when there is none.
     */
    public record Party(String type, UUID snailUuid, UUID playerId, String playerName,
                        String dimension, int x, int y, int z) {

        public static Party unknown() {
            return new Party("UNKNOWN", null, null, null, null, 0, 0, 0);
        }

        public boolean hasLocation() {
            return dimension != null;
        }

        /** Same snail, but records the player who actually picked up. Block snails keep their block location. */
        Party withAnsweringPlayer(UUID id, String name, String dim, int px, int py, int pz) {
            boolean keepBlockLocation = "BLOCK".equals(type) && hasLocation();
            return new Party(type, snailUuid, id, name,
                    keepBlockLocation ? dimension : dim,
                    keepBlockLocation ? x : px,
                    keepBlockLocation ? y : py,
                    keepBlockLocation ? z : pz);
        }
    }

    /** A place a snail was last seen, taken from the log. */
    public record Sighting(Party party, long timeMs, int otherNumber) { }

    public static final class Entry {
        private final UUID callId;
        private final long placedAt;
        private final int callerNumber;
        private final int calleeNumber;
        private Party caller;
        private Party callee;
        private long connectedAt;   // 0 = never connected
        private long endedAt;       // 0 = still in progress
        private Outcome outcome;    // null while in progress
        private EndReason endReason;
        private int endedBy = -1;   // snail number of whoever ended it, when known

        private Entry(UUID callId, long placedAt, int callerNumber, int calleeNumber, Party caller, Party callee) {
            this.callId = callId;
            this.placedAt = placedAt;
            this.callerNumber = callerNumber;
            this.calleeNumber = calleeNumber;
            this.caller = caller;
            this.callee = callee;
        }

        public UUID getCallId() { return callId; }
        public long getPlacedAt() { return placedAt; }
        public int getCallerNumber() { return callerNumber; }
        public int getCalleeNumber() { return calleeNumber; }
        public Party getCaller() { return caller; }
        public Party getCallee() { return callee; }
        public long getConnectedAt() { return connectedAt; }
        public long getEndedAt() { return endedAt; }
        /** Null while the call is still in progress. */
        public Outcome getOutcome() { return outcome; }
        @Nullable
        public EndReason getEndReason() { return endReason; }
        public int getEndedBy() { return endedBy; }

        public boolean isInProgress() { return outcome == null; }

        /** Time the two snails were actually connected. */
        public long getTalkTimeMs() {
            if (connectedAt <= 0) return 0;
            long end = endedAt > 0 ? endedAt : System.currentTimeMillis();
            return Math.max(0, end - connectedAt);
        }

        private Entry copy() {
            Entry e = new Entry(callId, placedAt, callerNumber, calleeNumber, caller, callee);
            e.connectedAt = connectedAt;
            e.endedAt = endedAt;
            e.outcome = outcome;
            e.endReason = endReason;
            e.endedBy = endedBy;
            return e;
        }

        private void finish(EndReason reason, int endedBySnail, long endedAtMs) {
            this.endReason = reason;
            this.endedBy = endedBySnail;
            this.endedAt = Math.max(endedAtMs, placedAt);
            if (connectedAt > 0) {
                this.outcome = Outcome.ANSWERED;
            } else {
                switch (reason) {
                    case REJECTED -> this.outcome = Outcome.REJECTED;
                    case RING_TIMEOUT -> this.outcome = Outcome.MISSED;
                    case HANG_UP, DISCONNECT -> this.outcome = Outcome.CANCELLED;
                    default -> this.outcome = Outcome.FAILED;
                }
            }
        }
    }

    // =================== INSTANCE MANAGEMENT ===================

    private static CallLogRegistry instance = null;
    private static MinecraftServer instanceServer = null;

    /**
     * The call log for the running server, or null when no server is running.
     * A new instance is loaded automatically whenever the server changes (single-player world switches).
     */
    @Nullable
    public static synchronized CallLogRegistry get() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        if (instance == null || instanceServer != server) {
            Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE_NAME);
            instance = new CallLogRegistry(file);
            instanceServer = server;
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        instance = null;
        instanceServer = null;
    }

    // =================== STATE ===================

    private final Path file;
    private final Object lock = new Object();      // guards entries + inFlight
    private final Object fileLock = new Object();  // guards writes to the file
    private final List<Entry> entries = new ArrayList<>();          // finished calls
    private final Map<UUID, Entry> inFlight = new HashMap<>();      // ringing / connected calls

    /** Loads the log from the given file (created on the first call that finishes). */
    public CallLogRegistry(@NotNull Path file) {
        this.file = file;
        load();
    }

    // =================== RECORDING ===================

    /** A call has started ringing. */
    public void recordPlaced(UUID callId, int callerNumber, Party caller, int calleeNumber, Party callee) {
        Entry entry = new Entry(callId, System.currentTimeMillis(), callerNumber, calleeNumber, caller, callee);
        synchronized (lock) {
            inFlight.put(callId, entry);
        }
    }

    /** The receiving side picked up. */
    public void recordConnected(UUID callId, UUID playerId, String playerName, String dimension, int x, int y, int z) {
        synchronized (lock) {
            Entry entry = inFlight.get(callId);
            if (entry == null) return;
            entry.connectedAt = System.currentTimeMillis();
            entry.callee = entry.callee.withAnsweringPlayer(playerId, playerName, dimension, x, y, z);
        }
    }

    /** A call has finished. Does nothing if the call was never recorded or was already finished. */
    public void recordEnded(UUID callId, EndReason reason, int endedBySnail, long endedAtMs) {
        Entry entry;
        synchronized (lock) {
            entry = inFlight.remove(callId);
            if (entry == null) return;
            entry.finish(reason, endedBySnail, endedAtMs);
            entries.add(entry);
        }
        append(entry);
    }

    /** A call attempt that never started ringing (busy, unreachable, unassigned number). */
    public void recordImmediate(Outcome outcome, int callerNumber, Party caller, int calleeNumber, Party callee) {
        long now = System.currentTimeMillis();
        Entry entry = new Entry(UUID.randomUUID(), now, callerNumber, calleeNumber, caller, callee);
        entry.outcome = outcome;
        entry.endedAt = now;
        synchronized (lock) {
            entries.add(entry);
        }
        append(entry);
    }

    // =================== QUERIES ===================

    /** Every call ever, oldest first. Calls still in progress are included (getOutcome() == null). */
    public List<Entry> getAll() {
        List<Entry> result;
        synchronized (lock) {
            result = new ArrayList<>(entries.size() + inFlight.size());
            result.addAll(entries);
            for (Entry e : inFlight.values()) {
                result.add(e.copy());
            }
        }
        result.sort(Comparator.comparingLong(Entry::getPlacedAt));
        return result;
    }

    /** Every call to or from the given number, oldest first. */
    public List<Entry> getForNumber(int number) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : getAll()) {
            if (e.callerNumber == number || e.calleeNumber == number) {
                result.add(e);
            }
        }
        return result;
    }

    /** The most recent place the log saw this number's snail, or null if it never appeared with a location. */
    @Nullable
    public Sighting getLastKnownLocation(int number) {
        List<Entry> all = getAll();
        for (int i = all.size() - 1; i >= 0; i--) {
            Entry e = all.get(i);
            if (e.callerNumber == number && e.caller.hasLocation()) {
                return new Sighting(e.caller, e.placedAt, e.calleeNumber);
            }
            if (e.calleeNumber == number && e.callee.hasLocation()) {
                return new Sighting(e.callee, e.placedAt, e.callerNumber);
            }
        }
        return null;
    }

    public int size() {
        synchronized (lock) {
            return entries.size() + inFlight.size();
        }
    }

    // =================== FILE I/O ===================

    private void load() {
        if (!Files.exists(file)) {
            return;
        }

        int skipped = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    Entry entry = fromJson(JsonParser.parseString(line).getAsJsonObject());
                    if (entry != null) {
                        entries.add(entry);
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
        } catch (IOException e) {
            System.err.println("CallLogRegistry: Could not read " + file + ": " + e.getMessage());
        }

        System.out.println("CallLogRegistry: Loaded " + entries.size() + " call log entries" +
                (skipped > 0 ? " (skipped " + skipped + " unreadable lines)" : ""));
    }

    private void append(Entry entry) {
        String line = GSON.toJson(toJson(entry)) + "\n";
        synchronized (fileLock) {
            try {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                System.err.println("CallLogRegistry: Could not write to " + file + ": " + e.getMessage());
            }
        }
    }

    // =================== JSON ===================

    private static JsonObject toJson(Entry e) {
        JsonObject o = new JsonObject();
        o.addProperty("callId", e.callId.toString());
        o.addProperty("placedAt", e.placedAt);
        o.addProperty("connectedAt", e.connectedAt);
        o.addProperty("endedAt", e.endedAt);
        o.addProperty("callerNumber", e.callerNumber);
        o.addProperty("calleeNumber", e.calleeNumber);
        o.add("caller", partyToJson(e.caller));
        o.add("callee", partyToJson(e.callee));
        o.addProperty("outcome", e.outcome != null ? e.outcome.name() : Outcome.FAILED.name());
        if (e.endReason != null) {
            o.addProperty("endReason", e.endReason.name());
        }
        o.addProperty("endedBy", e.endedBy);
        return o;
    }

    private static JsonObject partyToJson(Party p) {
        JsonObject o = new JsonObject();
        o.addProperty("type", p.type());
        if (p.snailUuid() != null) o.addProperty("snailUuid", p.snailUuid().toString());
        if (p.playerId() != null) o.addProperty("playerId", p.playerId().toString());
        if (p.playerName() != null) o.addProperty("playerName", p.playerName());
        if (p.dimension() != null) {
            o.addProperty("dimension", p.dimension());
            o.addProperty("x", p.x());
            o.addProperty("y", p.y());
            o.addProperty("z", p.z());
        }
        return o;
    }

    @Nullable
    private static Entry fromJson(JsonObject o) {
        UUID callId = uuidOrNull(str(o, "callId"));
        if (callId == null || !o.has("placedAt") || !o.has("callerNumber") || !o.has("calleeNumber")) {
            return null;
        }

        Entry e = new Entry(callId, o.get("placedAt").getAsLong(),
                o.get("callerNumber").getAsInt(), o.get("calleeNumber").getAsInt(),
                partyFromJson(o.has("caller") && o.get("caller").isJsonObject() ? o.getAsJsonObject("caller") : null),
                partyFromJson(o.has("callee") && o.get("callee").isJsonObject() ? o.getAsJsonObject("callee") : null));

        e.connectedAt = o.has("connectedAt") ? o.get("connectedAt").getAsLong() : 0;
        e.endedAt = o.has("endedAt") ? o.get("endedAt").getAsLong() : 0;
        e.endedBy = o.has("endedBy") ? o.get("endedBy").getAsInt() : -1;
        e.outcome = enumOrDefault(Outcome.class, str(o, "outcome"), Outcome.FAILED);
        String reason = str(o, "endReason");
        e.endReason = reason != null ? enumOrDefault(EndReason.class, reason, EndReason.UNKNOWN) : null;
        return e;
    }

    private static Party partyFromJson(@Nullable JsonObject o) {
        if (o == null) {
            return Party.unknown();
        }
        String dimension = str(o, "dimension");
        return new Party(
                str(o, "type") != null ? str(o, "type") : "UNKNOWN",
                uuidOrNull(str(o, "snailUuid")),
                uuidOrNull(str(o, "playerId")),
                str(o, "playerName"),
                dimension,
                dimension != null && o.has("x") ? o.get("x").getAsInt() : 0,
                dimension != null && o.has("y") ? o.get("y").getAsInt() : 0,
                dimension != null && o.has("z") ? o.get("z").getAsInt() : 0);
    }

    @Nullable
    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    @Nullable
    private static UUID uuidOrNull(@Nullable String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E enumOrDefault(Class<E> type, @Nullable String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}