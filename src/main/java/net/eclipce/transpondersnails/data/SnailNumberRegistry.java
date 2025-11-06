package net.eclipce.transpondersnails.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server-side registry that manages the assignment and tracking of unique snail numbers.
 * Each Transponder Snail gets assigned a unique 4-digit number (1000-9999) that persists
 * across server restarts and is tied to the snail's UUID, not the player.
 *
 * FIXED: Immediate save on assignment - numbers persist even if server crashes/is killed
 */
public class SnailNumberRegistry extends SavedData {
    private static final String DATA_NAME = "transponder_snails_registry";
    private static final int MIN_SNAIL_NUMBER = 1000;
    private static final int MAX_SNAIL_NUMBER = 9999;
    private static final int TOTAL_POSSIBLE_NUMBERS = MAX_SNAIL_NUMBER - MIN_SNAIL_NUMBER + 1; // 9000 total numbers

    // Main mappings
    private final Map<UUID, Integer> snailToNumber = new HashMap<>(); // UUID -> Snail Number
    private final Map<Integer, UUID> numberToSnail = new HashMap<>(); // Snail Number -> UUID (reverse lookup)
    private final Set<Integer> assignedNumbers = new HashSet<>(); // Quick lookup for assigned numbers

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
            System.out.println("SnailNumberRegistry: Cannot get instance - server not running");
            return null; // Server not running
        }

        if (instance == null) {
            System.out.println("SnailNumberRegistry: Creating new instance, loading from disk...");
            ServerLevel overworld = ServerLifecycleHooks.getCurrentServer().overworld();
            instance = overworld.getDataStorage().computeIfAbsent(
                    SnailNumberRegistry::load,
                    SnailNumberRegistry::new,
                    DATA_NAME
            );
            System.out.println("SnailNumberRegistry: Registry loaded with " +
                    instance.assignedNumbers.size() + " existing assignments");
        }

        return instance;
    }

    /**
     * Assigns a snail number to the given UUID if it doesn't already have one
     * FIXED: Now saves immediately to disk for crash resistance
     * @param snailUUID The unique identifier of the transponder snail
     * @return The assigned snail number, or -1 if assignment failed
     */
    public synchronized int assignNumberToSnail(@NotNull UUID snailUUID) {
        // Check if this snail already has a number
        if (snailToNumber.containsKey(snailUUID)) {
            int existingNumber = snailToNumber.get(snailUUID);
            System.out.println("SnailNumberRegistry: UUID " + snailUUID + " already has number #" + existingNumber);
            return existingNumber;
        }

        // Check if we've run out of available numbers
        if (assignedNumbers.size() >= TOTAL_POSSIBLE_NUMBERS) {
            System.err.println("SnailNumberRegistry: All snail numbers have been assigned! Cannot assign to UUID: " + snailUUID);
            return -1; // No more numbers available
        }

        // Generate a new unique number
        int newNumber = generateUniqueNumber();
        if (newNumber == -1) {
            System.err.println("SnailNumberRegistry: Failed to generate unique number for UUID: " + snailUUID);
            return -1;
        }

        // Assign the number
        snailToNumber.put(snailUUID, newNumber);
        numberToSnail.put(newNumber, snailUUID);
        assignedNumbers.add(newNumber);

        // Mark data as dirty to ensure it saves
        setDirty();

        // ⚡ CRITICAL FIX: Save immediately so data persists even if server crashes/is killed
        // This is the key change that fixes gradle task kill persistence issues
        System.out.println("SnailNumberRegistry: Assigned NEW number #" + newNumber + " to snail " + snailUUID);
        System.out.println("SnailNumberRegistry: Saving immediately to disk...");
        forceSave();
        System.out.println("SnailNumberRegistry: Assignment saved! Total: " + assignedNumbers.size() + "/" + TOTAL_POSSIBLE_NUMBERS);

        return newNumber;
    }

    /**
     * Gets the snail number for the given UUID
     * @param snailUUID The snail's unique identifier
     * @return The assigned number, or -1 if not found
     */
    public int getSnailNumber(@NotNull UUID snailUUID) {
        return snailToNumber.getOrDefault(snailUUID, -1);
    }

    /**
     * Gets the snail UUID for the given number
     * @param snailNumber The snail number to look up
     * @return The UUID of the snail with that number, or null if not found
     */
    @Nullable
    public UUID getSnailByNumber(int snailNumber) {
        return numberToSnail.get(snailNumber);
    }

    /**
     * Checks if a snail number is currently assigned
     * @param snailNumber The number to check
     * @return True if the number is assigned to a snail
     */
    public boolean isNumberAssigned(int snailNumber) {
        return assignedNumbers.contains(snailNumber);
    }

    /**
     * Gets the total number of assigned snail numbers
     * @return The count of currently assigned numbers
     */
    public int getAssignedCount() {
        return assignedNumbers.size();
    }

    /**
     * Gets the total number of available snail numbers
     * @return The count of unassigned numbers
     */
    public int getAvailableCount() {
        return TOTAL_POSSIBLE_NUMBERS - assignedNumbers.size();
    }

    /**
     * Removes a snail number assignment (for debugging/admin use)
     * FIXED: Now saves immediately after removal
     * WARNING: This should rarely be used as it can break existing calls
     * @param snailUUID The UUID of the snail to unassign
     * @return True if a number was removed, false if the UUID wasn't assigned
     */
    public synchronized boolean removeSnailAssignment(@NotNull UUID snailUUID) {
        Integer number = snailToNumber.remove(snailUUID);
        if (number != null) {
            numberToSnail.remove(number);
            assignedNumbers.remove(number);
            setDirty();

            // Save immediately after removal
            System.out.println("SnailNumberRegistry: Removed assignment of number " + number + " from snail " + snailUUID);
            forceSave();
            System.out.println("SnailNumberRegistry: Removal saved to disk");

            return true;
        }
        return false;
    }

    /**
     * Generates a unique snail number that isn't already assigned
     * @return A unique number between MIN_SNAIL_NUMBER and MAX_SNAIL_NUMBER, or -1 if none available
     */
    private int generateUniqueNumber() {
        // If we're getting close to capacity, use a more systematic approach
        if (assignedNumbers.size() > TOTAL_POSSIBLE_NUMBERS * 0.8) {
            // Find the first available number systematically
            for (int number = MIN_SNAIL_NUMBER; number <= MAX_SNAIL_NUMBER; number++) {
                if (!assignedNumbers.contains(number)) {
                    return number;
                }
            }
            return -1; // No numbers available
        }

        // Use random generation for better distribution when plenty of numbers available
        int attempts = 0;
        int maxAttempts = 1000; // Prevent infinite loops

        while (attempts < maxAttempts) {
            int number = ThreadLocalRandom.current().nextInt(MIN_SNAIL_NUMBER, MAX_SNAIL_NUMBER + 1);
            if (!assignedNumbers.contains(number)) {
                return number;
            }
            attempts++;
        }

        // Fallback to systematic search if random fails
        for (int number = MIN_SNAIL_NUMBER; number <= MAX_SNAIL_NUMBER; number++) {
            if (!assignedNumbers.contains(number)) {
                return number;
            }
        }

        return -1; // No numbers available
    }

    // SavedData implementation for persistence
    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag compound) {
        // Save the UUID -> Number mappings
        ListTag assignmentsList = new ListTag();
        for (Map.Entry<UUID, Integer> entry : snailToNumber.entrySet()) {
            CompoundTag assignmentTag = new CompoundTag();
            assignmentTag.putString("uuid", entry.getKey().toString());
            assignmentTag.putInt("number", entry.getValue());
            assignmentsList.add(assignmentTag);
        }
        compound.put("assignments", assignmentsList);

        // Save statistics for debugging
        compound.putInt("total_assigned", assignedNumbers.size());
        compound.putInt("registry_version", 1); // For future compatibility

        System.out.println("SnailNumberRegistry: Saved " + assignedNumbers.size() + " snail number assignments");
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
            System.out.println("SnailNumberRegistry: Found " + assignmentsList.size() + " assignments to load");

            for (int i = 0; i < assignmentsList.size(); i++) {
                CompoundTag assignmentTag = assignmentsList.getCompound(i);

                try {
                    UUID snailUUID = UUID.fromString(assignmentTag.getString("uuid"));
                    int number = assignmentTag.getInt("number");

                    // Validate the number is in valid range
                    if (number >= MIN_SNAIL_NUMBER && number <= MAX_SNAIL_NUMBER) {
                        registry.snailToNumber.put(snailUUID, number);
                        registry.numberToSnail.put(number, snailUUID);
                        registry.assignedNumbers.add(number);

                        // Log first few for verification
                        if (i < 5) {
                            System.out.println("  Loaded: UUID " + snailUUID + " -> #" + number);
                        }
                    } else {
                        System.err.println("SnailNumberRegistry: Loaded invalid snail number " + number + " for UUID " + snailUUID + ", skipping");
                    }

                } catch (IllegalArgumentException e) {
                    System.err.println("SnailNumberRegistry: Failed to load assignment entry at index " + i + ": " + e.getMessage());
                }
            }
        } else {
            System.out.println("SnailNumberRegistry: No existing assignments found - starting fresh");
        }

        System.out.println("SnailNumberRegistry: LOADED " + registry.assignedNumbers.size() + " snail number assignments from disk");
        return registry;
    }

    /**
     * Debug method to print current registry state
     */
    public void debugPrintState() {
        System.out.println("=== SnailNumberRegistry Debug Info ===");
        System.out.println("Assigned numbers: " + assignedNumbers.size() + "/" + TOTAL_POSSIBLE_NUMBERS);
        System.out.println("Available numbers: " + getAvailableCount());

        if (assignedNumbers.size() <= 20) { // Only print details if not too many
            System.out.println("Current assignments:");
            for (Map.Entry<UUID, Integer> entry : snailToNumber.entrySet()) {
                System.out.println("  " + entry.getKey() + " -> #" + entry.getValue());
            }
        }
        System.out.println("=====================================");
    }

    /**
     * Clears all snail number assignments (DANGEROUS - for admin use only)
     * FIXED: Now saves immediately after clearing
     * WARNING: This will break all existing calls and make all snails lose their numbers
     * @return The number of assignments that were cleared
     */
    public synchronized int clearAllAssignments() {
        int clearedCount = assignedNumbers.size();

        // Clear all mappings
        snailToNumber.clear();
        numberToSnail.clear();
        assignedNumbers.clear();

        // Mark data as dirty to ensure it saves
        setDirty();

        // Save immediately after clearing
        System.out.println("SnailNumberRegistry: CLEARED ALL ASSIGNMENTS - " + clearedCount + " numbers freed");
        forceSave();
        System.out.println("SnailNumberRegistry: Clear operation saved to disk");

        return clearedCount;
    }

    /**
     * Attempts to restore a snail assignment that was lost during loading
     * FIXED: Now saves immediately after restoration
     * This should only be used during world loading/validation
     * @param snailUUID The UUID of the snail to restore
     * @param preferredNumber The number the snail previously had
     * @return The restored number, or -1 if restoration failed
     */
    public synchronized int restoreSnailAssignment(@NotNull UUID snailUUID, int preferredNumber) {
        System.out.println("SnailNumberRegistry: Attempting to restore assignment for UUID " + snailUUID + " with preferred number #" + preferredNumber);

        // Check if the UUID is already assigned
        if (snailToNumber.containsKey(snailUUID)) {
            int existingNumber = snailToNumber.get(snailUUID);
            System.out.println("SnailNumberRegistry: UUID already has assignment #" + existingNumber);
            return existingNumber;
        }

        // Check if preferred number is available
        if (preferredNumber >= MIN_SNAIL_NUMBER && preferredNumber <= MAX_SNAIL_NUMBER) {
            if (!assignedNumbers.contains(preferredNumber)) {
                // Preferred number is available, restore it
                snailToNumber.put(snailUUID, preferredNumber);
                numberToSnail.put(preferredNumber, snailUUID);
                assignedNumbers.add(preferredNumber);
                setDirty();

                System.out.println("SnailNumberRegistry: Restored preferred assignment #" + preferredNumber + " to UUID " + snailUUID);
                forceSave();
                System.out.println("SnailNumberRegistry: Restoration saved to disk");

                return preferredNumber;
            } else {
                System.out.println("SnailNumberRegistry: Preferred number #" + preferredNumber + " is no longer available");
            }
        }

        // Preferred number not available, assign a new one
        int newNumber = generateUniqueNumber();
        if (newNumber != -1) {
            snailToNumber.put(snailUUID, newNumber);
            numberToSnail.put(newNumber, snailUUID);
            assignedNumbers.add(newNumber);
            setDirty();

            System.out.println("SnailNumberRegistry: Restored with new assignment #" + newNumber + " to UUID " + snailUUID);
            forceSave();
            System.out.println("SnailNumberRegistry: Restoration saved to disk");

            return newNumber;
        }

        System.err.println("SnailNumberRegistry: Failed to restore assignment for UUID " + snailUUID + " - no numbers available");
        return -1;
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
            // System.out.println("SnailNumberRegistry: ✓ Data written to disk");
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
                System.out.println("SnailNumberRegistry: DataStorage location: " + overworld.getDataStorage().toString());

                // Force a save and verify
                setDirty();
                overworld.getDataStorage().save();
                System.out.println("SnailNumberRegistry: Force save completed");
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
        if (instance != null) {
            System.out.println("SnailNumberRegistry: Resetting instance cache (" +
                    instance.assignedNumbers.size() + " assignments will be reloaded on next start)");
        } else {
            System.out.println("SnailNumberRegistry: Instance already null, nothing to reset");
        }
        instance = null;
    }
}