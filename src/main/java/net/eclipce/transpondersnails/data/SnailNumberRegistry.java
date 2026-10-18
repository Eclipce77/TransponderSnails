package net.eclipce.transpondersnails.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side registry that manages the assignment and tracking of unique snail numbers.
 * Each numbered snail gets assigned a unique 4-digit number (1000-9999) that persists
 * across server restarts and is tied to the snail's UUID, not the player.
 *
 * There are two kinds of numbered snail (see {@link SnailKind}): Transponder Snails (phones) and Visual Transponder Snails.
 * They share ONE pool of numbers, so a number is always exactly one snail, and each number remembers which kind it is.
 * The numbers and their kinds are kept in a {@link SnailNumberTable}; this class adds saving, revocation and the server.
 *
 * FIXED: Immediate save on assignment - numbers persist even if server crashes/is killed
 */
public class SnailNumberRegistry extends SavedData {
    private static final String DATA_NAME = "transponder_snails_registry";

    // The numbers, who holds them and of which kind
    private final SnailNumberTable table = new SnailNumberTable();

    // UUIDs of snails whose number an admin removed (/snailnumber remove). Such a snail must never come
    // back to life with its old identity: items and block entities check this before trusting their
    // cached data, and drop the identity so the snail registers a brand new number on its next use.
    // Concurrent set because item code can read it from the client thread in single-player.
    private final Set<UUID> revokedSnails = ConcurrentHashMap.newKeySet();

    // Cached instance for quick access
    private static SnailNumberRegistry instance = null;

    // Private constructor - use getInstance() instead
    private SnailNumberRegistry() {
        super();
    }

    /**
     * Gets the global instance of the SnailNumberRegistry
     * @return The registry instance, or null if server isn't running
     */
    @Nullable
    public static SnailNumberRegistry getInstance() {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            return null; // Server not running
        }

        if (instance == null) {
            ServerLevel overworld = ServerLifecycleHooks.getCurrentServer().overworld();
            instance = overworld.getDataStorage().computeIfAbsent(
                    SnailNumberRegistry::load,
                    SnailNumberRegistry::new,
                    DATA_NAME
            );
        }

        return instance;
    }

    /**
     * Assigns a snail number to the given UUID if it doesn't already have one - as a PHONE snail.
     * @param snailUUID The unique identifier of the transponder snail
     * @return The assigned snail number, or -1 if assignment failed
     */
    public synchronized int assignNumberToSnail(@NotNull UUID snailUUID) {
        return assignNumberToSnail(snailUUID, SnailKind.PHONE);
    }

    /**
     * Assigns a snail number of the given kind to the given UUID if it doesn't already have one.
     * FIXED: Now saves immediately to disk for crash resistance
     * @param snailUUID The unique identifier of the snail
     * @param kind      Which kind of snail it is; both kinds draw from the same pool of numbers
     * @return The assigned snail number (the existing one if it already had one), or -1 if assignment failed
     */
    public synchronized int assignNumberToSnail(@NotNull UUID snailUUID, @NotNull SnailKind kind) {
        // A revoked snail may not be re-registered under its old identity - callers must wipe it first
        if (revokedSnails.contains(snailUUID)) {
            System.err.println("SnailNumberRegistry: Refusing to assign a number to revoked snail " + snailUUID);
            return -1;
        }

        // Check if this snail already has a number
        int existingNumber = table.numberOf(snailUUID);
        if (existingNumber != -1) {
            return existingNumber;
        }

        int newNumber = table.assign(snailUUID, kind);
        if (newNumber == -1) {
            System.err.println("SnailNumberRegistry: Cannot assign a number to UUID " + snailUUID + " - all snail numbers are in use");
            return -1;
        }

        // Mark data as dirty to ensure it saves
        setDirty();

        // ⚡ CRITICAL FIX: Save immediately so data persists even if server crashes/is killed
        // This is the key change that fixes gradle task kill persistence issues
        forceSave();

        return newNumber;
    }

    /**
     * Gets the snail number for the given UUID
     * @param snailUUID The snail's unique identifier
     * @return The assigned number, or -1 if not found
     */
    public int getSnailNumber(@NotNull UUID snailUUID) {
        return table.numberOf(snailUUID);
    }

    /**
     * Gets the snail UUID for the given number
     * @param snailNumber The snail number to look up
     * @return The UUID of the snail with that number, or null if not found
     */
    @Nullable
    public UUID getSnailByNumber(int snailNumber) {
        return table.uuidOf(snailNumber);
    }

    /**
     * Which kind of snail holds this number.
     * @return PHONE or VISUAL, or null if the number is not assigned
     */
    @Nullable
    public SnailKind getKindOfNumber(int snailNumber) {
        return table.kindOfNumber(snailNumber);
    }

    /**
     * Which kind this snail is.
     * @return PHONE or VISUAL, or null if the snail has no number
     */
    @Nullable
    public SnailKind getKind(@NotNull UUID snailUUID) {
        return table.kindOf(snailUUID);
    }

    /**
     * Corrects the recorded kind of a snail that has a number (it keeps the number). A placed snail calls this when it checks its
     * identity, so a number that was recorded with the wrong kind is healed instead of making the snail dialable by the wrong network.
     * @return True if the kind was changed
     */
    public synchronized boolean setKind(@NotNull UUID snailUUID, @NotNull SnailKind kind) {
        if (!table.setKind(snailUUID, kind)) {
            return false;
        }
        System.out.println("SnailNumberRegistry: Snail " + snailUUID + " (#" + table.numberOf(snailUUID) + ") is now recorded as a " + kind.label() + " snail");
        setDirty();
        forceSave();
        return true;
    }

    /**
     * Checks if a snail number is currently assigned
     * @param snailNumber The number to check
     * @return True if the number is assigned to a snail
     */
    public boolean isNumberAssigned(int snailNumber) {
        return table.isAssigned(snailNumber);
    }

    /**
     * Gets the total number of assigned snail numbers
     * @return The count of currently assigned numbers
     */
    public int getAssignedCount() {
        return table.size();
    }

    /**
     * Gets the number of assigned snail numbers of a scope (all, only phone snails, only visual snails)
     */
    public int getAssignedCount(@NotNull SnailKind.Scope scope) {
        return table.size(scope);
    }

    /**
     * Gets the total number of available snail numbers
     * @return The count of unassigned numbers
     */
    public int getAvailableCount() {
        return table.available();
    }

    /**
     * Removes a snail number assignment (for debugging/admin use)
     * FIXED: Now saves immediately after removal
     * WARNING: This should rarely be used as it can break existing calls
     * @param snailUUID The UUID of the snail to unassign
     * @return True if a number was removed, false if the UUID wasn't assigned
     */
    public synchronized boolean removeSnailAssignment(@NotNull UUID snailUUID) {
        if (table.remove(snailUUID) != -1) {
            setDirty();

            // Save immediately after removal
            forceSave();

            return true;
        }
        return false;
    }

    /**
     * Gets every assignment, sorted by number. The returned map is a copy.
     * @return snail number -> snail UUID
     */
    public synchronized Map<Integer, UUID> getAllAssignments() {
        return table.snapshot(SnailKind.Scope.ALL);
    }

    /**
     * Gets the assignments of a scope (all, only phone snails, only visual snails), sorted by number. The returned map is a copy.
     * @return snail number -> snail UUID
     */
    public synchronized Map<Integer, UUID> getAllAssignments(@NotNull SnailKind.Scope scope) {
        return table.snapshot(scope);
    }

    /**
     * Removes a snail's number AND revokes its identity: the number becomes free again, and the snail
     * (item or placed block) is forced to register a new number the next time it is used.
     * Saves immediately.
     *
     * @param snailUUID The UUID of the snail
     * @return The number that was removed, or -1 if the UUID had no number
     */
    public synchronized int revokeSnail(@NotNull UUID snailUUID) {
        int number = table.remove(snailUUID);
        if (number == -1) {
            return -1;
        }

        revokedSnails.add(snailUUID);

        setDirty();
        forceSave();
        return number;
    }

    /**
     * Removes EVERY number and revokes every snail identity (see revokeSnail). Saves immediately.
     *
     * @return The assignments that were removed (snail number -> snail UUID), sorted by number
     */
    public synchronized Map<Integer, UUID> revokeAllSnails() {
        return revokeAllSnails(SnailKind.Scope.ALL);
    }

    /**
     * Removes the numbers of a scope - every number, or only the phone snails', or only the visual snails' - and revokes those snail
     * identities (see revokeSnail). The other kind is not touched. Saves immediately.
     *
     * @param scope Which numbers to remove
     * @return The assignments that were removed (snail number -> snail UUID), sorted by number
     */
    public synchronized Map<Integer, UUID> revokeAllSnails(@NotNull SnailKind.Scope scope) {
        Map<Integer, UUID> removed = table.removeAll(scope);
        revokedSnails.addAll(removed.values());

        setDirty();
        forceSave();
        return removed;
    }

    /**
     * @return True if an admin removed this snail's number, so its old identity must not be used again
     */
    public boolean isRevoked(@NotNull UUID snailUUID) {
        return revokedSnails.contains(snailUUID);
    }

    /**
     * @return How many snail identities have been revoked
     */
    public int getRevokedCount() {
        return revokedSnails.size();
    }

    // SavedData implementation for persistence
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag compound) {
        // Save the UUID -> Number mappings, with the kind of each snail
        ListTag assignmentsList = new ListTag();
        for (SnailNumberTable.Entry entry : table.entries()) {
            CompoundTag assignmentTag = new CompoundTag();
            assignmentTag.putString("uuid", entry.uuid().toString());
            assignmentTag.putInt("number", entry.number());
            assignmentTag.putString("kind", entry.kind().name());
            assignmentsList.add(assignmentTag);
        }
        compound.put("assignments", assignmentsList);

        // Revoked snail identities (see revokeSnail) - these must survive restarts
        ListTag revokedList = new ListTag();
        for (UUID revoked : revokedSnails) {
            revokedList.add(NbtUtils.createUUID(revoked));
        }
        compound.put("revoked", revokedList);

        // Save statistics for debugging
        compound.putInt("total_assigned", table.size());
        compound.putInt("registry_version", 3); // For future compatibility (2 = adds "revoked", 3 = adds the "kind" of each number)

        return compound;
    }

    /**
     * Loads the registry data from NBT
     */
    public static SnailNumberRegistry load(CompoundTag compound) {
        SnailNumberRegistry registry = new SnailNumberRegistry();

        // Load assignments
        if (compound.contains("assignments", Tag.TAG_LIST)) {
            ListTag assignmentsList = compound.getList("assignments", Tag.TAG_COMPOUND);

            for (int i = 0; i < assignmentsList.size(); i++) {
                CompoundTag assignmentTag = assignmentsList.getCompound(i);

                try {
                    UUID snailUUID = UUID.fromString(assignmentTag.getString("uuid"));
                    int number = assignmentTag.getInt("number");
                    // Registries saved before visual snails had numbers have no kind: every one of those numbers is a phone
                    SnailKind kind = SnailKind.fromSaved(assignmentTag.getString("kind"));

                    // Validates the range, and that neither the number nor the snail is already taken
                    if (!registry.table.load(snailUUID, number, kind)) {
                        System.err.println("SnailNumberRegistry: Skipped invalid or duplicate snail number " + number + " for UUID " + snailUUID);
                    }

                } catch (IllegalArgumentException e) {
                    System.err.println("SnailNumberRegistry: Failed to load assignment entry at index " + i + ": " + e.getMessage());
                }
            }
        }

        // Load revoked snail identities (absent in registries saved before this feature)
        if (compound.contains("revoked", Tag.TAG_LIST)) {
            ListTag revokedList = compound.getList("revoked", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < revokedList.size(); i++) {
                try {
                    registry.revokedSnails.add(NbtUtils.loadUUID(revokedList.get(i)));
                } catch (IllegalArgumentException e) {
                    System.err.println("SnailNumberRegistry: Failed to load revoked entry at index " + i + ": " + e.getMessage());
                }
            }
        }

        return registry;
    }

    /**
     * Debug method to print current registry state
     */
    public void debugPrintState() {

        if (table.size() <= 20) { // Only print details if not too many
            for (SnailNumberTable.Entry entry : table.entries());
        }
    }

    /**
     * Clears all snail number assignments (DANGEROUS - for admin use only)
     * FIXED: Now saves immediately after clearing
     * WARNING: This will break all existing calls and make all snails lose their numbers
     * @return The number of assignments that were cleared
     */
    public synchronized int clearAllAssignments() {
        int clearedCount = table.size();

        // Clear all mappings
        table.clear();

        // Mark data as dirty to ensure it saves
        setDirty();

        // Save immediately after clearing
        forceSave();
        return clearedCount;
    }

    /**
     * Attempts to restore a snail assignment that was lost during loading - as a PHONE snail.
     * This should only be used during world loading/validation
     * @param snailUUID The UUID of the snail to restore
     * @param preferredNumber The number the snail previously had
     * @return The restored number, or -1 if restoration failed
     */
    public synchronized int restoreSnailAssignment(@NotNull UUID snailUUID, int preferredNumber) {
        return restoreSnailAssignment(snailUUID, preferredNumber, SnailKind.PHONE);
    }

    /**
     * Attempts to restore a snail assignment that was lost during loading
     * FIXED: Now saves immediately after restoration
     * This should only be used during world loading/validation
     * @param snailUUID The UUID of the snail to restore
     * @param preferredNumber The number the snail previously had
     * @param kind Which kind of snail it is
     * @return The restored number, or -1 if restoration failed
     */
    public synchronized int restoreSnailAssignment(@NotNull UUID snailUUID, int preferredNumber, @NotNull SnailKind kind) {
        if (revokedSnails.contains(snailUUID)) {
            System.err.println("SnailNumberRegistry: Refusing to restore revoked snail " + snailUUID);
            return -1;
        }

        // Check if the UUID is already assigned
        int existingNumber = table.numberOf(snailUUID);
        if (existingNumber != -1) {
            return existingNumber;
        }

        // The old number if it is free, otherwise a new one
        int number = table.restore(snailUUID, preferredNumber, kind);
        if (number == -1) {
            System.err.println("SnailNumberRegistry: Failed to restore assignment for UUID " + snailUUID + " - no numbers available");
            return -1;
        }

        setDirty();
        forceSave();
        return number;
    }

    /**
     * Forces the registry to save immediately
     * ⚡ KEY METHOD for crash resistance
     * Useful for ensuring data is written before server shutdown or after critical changes
     */
    public void forceSave() {
        if (ServerLifecycleHooks.getCurrentServer() == null) {
            System.err.println("SnailNumberRegistry: Cannot force save - server not available");
            return;
        }

        try {
            // CRITICAL: Mark as dirty before saving
            setDirty();

            ServerLevel overworld = ServerLifecycleHooks.getCurrentServer().overworld();
            overworld.getDataStorage().save();

            // Success message only if not spamming
        } catch (Exception e) {
            System.err.println("SnailNumberRegistry: Error during force save: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Enhanced debug method with save validation
     */
    public void debugPrintStateWithSave() {
        debugPrintState();

        // Also check if data is being saved properly
        try {
            if (ServerLifecycleHooks.getCurrentServer() != null) {
                ServerLevel overworld = ServerLifecycleHooks.getCurrentServer().overworld();

                // Force a save and verify
                setDirty();
                overworld.getDataStorage().save();
            }
        } catch (Exception e) {
            System.err.println("SnailNumberRegistry: Error during save validation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Resets the instance cache - MUST be called when server stops
     */
    public static void resetInstance() {
        instance = null;
    }
}
