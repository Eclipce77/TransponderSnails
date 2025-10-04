package net.eclipce.transpondersnails.data;

import net.eclipce.transpondersnails.data.SnailNumberRegistry;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Utility class for handling NBT data storage and retrieval for Transponder Snails.
 * Manages UUID assignment, caching, and tooltip integration.
 */
public class SnailNBTHandler {

    // NBT tag keys
    private static final String SNAIL_UUID_TAG = "snail_uuid";
    private static final String CACHED_NUMBER_TAG = "cached_snail_number";
    private static final String ACTIVATION_TIMESTAMP_TAG = "activation_time";
    private static final String ACTIVATION_PLAYER_TAG = "activated_by";
    private static final String SNAIL_TYPE_TAG = "snail_type";

    // Snail types for tracking
    public enum SnailType {
        BLOCK,      // Placed Transponder Snail block
        HANDHELD    // Transponder Snail item in inventory/hand
    }

    /**
     * Determines the type of Transponder Snail from the ItemStack
     * @param stack The ItemStack to check
     * @return The SnailType, or null if not a valid snail
     */
    @Nullable
    private static SnailType getSnailType(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        Item item = stack.getItem();

        // Check if it's the Transponder Snail block item
        if (item instanceof BlockItem blockItem) {
            if (blockItem.getBlock() == ModBlocks.TRANSPONDER_SNAIL.get()) {
                return SnailType.BLOCK;
            }
        }

        // Check if it's a handheld Transponder Snail item
        // TODO: Replace with actual handheld item check when implemented
        // For now, assume if it's not a block item but has snail NBT, it's handheld
        if (hasSnailNBT(stack)) {
            return SnailType.HANDHELD;
        }

        // Could add more specific checks here for different snail item types
        // Example: if (item instanceof TransponderSnailItem) return SnailType.HANDHELD;

        return null; // Not a recognized snail type
    }

    /**
     * Checks if an ItemStack has any snail-related NBT data
     * @param stack The ItemStack to check
     * @return True if it has snail NBT data
     */
    private static boolean hasSnailNBT(@NotNull ItemStack stack) {
        CompoundTag nbt = stack.getTag();
        return nbt != null && (nbt.hasUUID(SNAIL_UUID_TAG) || nbt.contains(CACHED_NUMBER_TAG));
    }

    /**
     * Gets or generates a UUID for the given Transponder Snail ItemStack.
     * If the snail doesn't have a UUID, generates one and assigns a snail number.
     *
     * @param stack The Transponder Snail ItemStack
     * @param activatingPlayerUUID The UUID of the player activating the snail (for tracking)
     * @return The snail's UUID, or null if something went wrong or not a valid snail
     */
    @Nullable
    public static UUID getOrCreateSnailUUID(@NotNull ItemStack stack, @Nullable UUID activatingPlayerUUID) {
        if (stack.isEmpty()) {
            return null;
        }

        // Verify this is a valid Transponder Snail
        SnailType snailType = getSnailType(stack);
        if (snailType == null) {
            System.err.println("SnailNBTHandler: Attempted to get UUID for non-snail item: " + stack.getItem());
            return null;
        }

        CompoundTag nbt = stack.getOrCreateTag();

        // Check if UUID already exists
        if (nbt.hasUUID(SNAIL_UUID_TAG)) {
            UUID existingUUID = nbt.getUUID(SNAIL_UUID_TAG);

            // Ensure snail type is stored (for backward compatibility)
            if (!nbt.contains(SNAIL_TYPE_TAG)) {
                nbt.putString(SNAIL_TYPE_TAG, snailType.name());
            }

            return existingUUID;
        }

        // Generate new UUID and assign number
        UUID newUUID = UUID.randomUUID();

        // Try to assign a snail number
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry != null) {
            int snailNumber = registry.assignNumberToSnail(newUUID);

            if (snailNumber != -1) {
                // Successfully assigned - store everything
                nbt.putUUID(SNAIL_UUID_TAG, newUUID);
                nbt.putInt(CACHED_NUMBER_TAG, snailNumber);
                nbt.putLong(ACTIVATION_TIMESTAMP_TAG, System.currentTimeMillis());
                nbt.putString(SNAIL_TYPE_TAG, snailType.name());

                if (activatingPlayerUUID != null) {
                    nbt.putUUID(ACTIVATION_PLAYER_TAG, activatingPlayerUUID);
                }

                System.out.println("SnailNBTHandler: Created new " + snailType.name().toLowerCase() + " snail with UUID " + newUUID + " and number #" + snailNumber);
                return newUUID;
            } else {
                // Failed to assign number - registry might be full
                System.err.println("SnailNBTHandler: Failed to assign number to new " + snailType.name().toLowerCase() + " snail UUID " + newUUID);
                return null;
            }
        } else {
            System.err.println("SnailNBTHandler: Registry not available for UUID assignment");
            return null;
        }
    }

    /**
     * Gets the UUID of a Transponder Snail without creating one.
     *
     * @param stack The Transponder Snail ItemStack
     * @return The snail's UUID, or null if it doesn't have one or isn't a valid snail
     */
    @Nullable
    public static UUID getSnailUUID(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        // Verify this is a valid Transponder Snail (allow any type since we're just reading)
        if (getSnailType(stack) == null && !hasSnailNBT(stack)) {
            return null;
        }

        CompoundTag nbt = stack.getTag();
        if (nbt != null) {
            // Check top-level NBT first
            if (nbt.hasUUID(SNAIL_UUID_TAG)) {
                return nbt.getUUID(SNAIL_UUID_TAG);
            }

            // ALSO check BlockEntityTag (for items that were placed as blocks then broken)
            if (nbt.contains("BlockEntityTag")) {
                CompoundTag blockEntityTag = nbt.getCompound("BlockEntityTag");
                if (blockEntityTag.hasUUID("SnailUUID")) {
                    return blockEntityTag.getUUID("SnailUUID");
                }
            }
        }

        return null;
    }

    /**
     * Gets the cached snail number from the ItemStack.
     * If not cached, looks it up in the registry and caches it.
     *
     * @param stack The Transponder Snail ItemStack
     * @return The snail number, or -1 if not assigned or not a valid snail
     */
    public static int getSnailNumber(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }

        // Allow reading from any item with snail NBT (for backward compatibility)
        if (getSnailType(stack) == null && !hasSnailNBT(stack)) {
            return -1;
        }

        CompoundTag nbt = stack.getOrCreateTag();

        // Check cached number first (top level)
        if (nbt.contains(CACHED_NUMBER_TAG)) {
            return nbt.getInt(CACHED_NUMBER_TAG);
        }

        // Check BlockEntityTag for AssignedNumber
        if (nbt.contains("BlockEntityTag")) {
            CompoundTag blockEntityTag = nbt.getCompound("BlockEntityTag");
            if (blockEntityTag.contains("AssignedNumber")) {
                return blockEntityTag.getInt("AssignedNumber");
            }
        }

        // No cached number - try to look up by UUID
        UUID snailUUID = getSnailUUID(stack);
        if (snailUUID != null) {
            SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
            if (registry != null) {
                int number = registry.getSnailNumber(snailUUID);
                if (number != -1) {
                    // Cache the number for future lookups
                    nbt.putInt(CACHED_NUMBER_TAG, number);
                    return number;
                }
            }
        }

        return -1; // No number assigned
    }

    /**
     * Gets the stored snail type from NBT, if available.
     *
     * @param stack The Transponder Snail ItemStack
     * @return The stored SnailType, or null if not stored or not a valid snail
     */
    @Nullable
    public static SnailType getStoredSnailType(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains(SNAIL_TYPE_TAG)) {
            try {
                return SnailType.valueOf(nbt.getString(SNAIL_TYPE_TAG));
            } catch (IllegalArgumentException e) {
                System.err.println("SnailNBTHandler: Invalid snail type in NBT: " + nbt.getString(SNAIL_TYPE_TAG));
                return null;
            }
        }

        return null;
    }

    /**
     * Gets the effective snail type - either from NBT or by detecting from the item.
     *
     * @param stack The Transponder Snail ItemStack
     * @return The SnailType, or null if not a valid snail
     */
    @Nullable
    public static SnailType getEffectiveSnailType(@NotNull ItemStack stack) {
        // Try stored type first
        SnailType storedType = getStoredSnailType(stack);
        if (storedType != null) {
            return storedType;
        }

        // Fall back to detection
        return getSnailType(stack);
    }

    /**
     * Gets the timestamp when this snail was first activated.
     *
     * @param stack The Transponder Snail ItemStack
     * @return The activation timestamp in milliseconds, or 0 if not activated
     */
    public static long getActivationTimestamp(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }

        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.contains(ACTIVATION_TIMESTAMP_TAG)) {
            return nbt.getLong(ACTIVATION_TIMESTAMP_TAG);
        }

        return 0;
    }

    /**
     * Gets the UUID of the player who first activated this snail.
     *
     * @param stack The Transponder Snail ItemStack
     * @return The activating player's UUID, or null if not recorded
     */
    @Nullable
    public static UUID getActivatingPlayer(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }

        CompoundTag nbt = stack.getTag();
        if (nbt != null && nbt.hasUUID(ACTIVATION_PLAYER_TAG)) {
            return nbt.getUUID(ACTIVATION_PLAYER_TAG);
        }

        return null;
    }

    /**
     * Checks if a Transponder Snail has been activated (has UUID and number).
     *
     * @param stack The Transponder Snail ItemStack
     * @return True if the snail is activated, false otherwise
     */
    public static boolean isSnailActivated(@NotNull ItemStack stack) {
        return getSnailUUID(stack) != null && getSnailNumber(stack) != -1;
    }

    /**
     * Updates the cached snail number. Use this if the registry assignment changes.
     *
     * @param stack The Transponder Snail ItemStack
     * @param newNumber The new snail number to cache
     */
    public static void updateCachedNumber(@NotNull ItemStack stack, int newNumber) {
        if (stack.isEmpty()) {
            return;
        }

        CompoundTag nbt = stack.getOrCreateTag();
        if (newNumber == -1) {
            nbt.remove(CACHED_NUMBER_TAG);
        } else {
            nbt.putInt(CACHED_NUMBER_TAG, newNumber);
        }
    }

    /**
     * Copies snail data from one ItemStack to another.
     * Useful when snails are crafted, renamed, or otherwise duplicated.
     *
     * @param source The source ItemStack with snail data
     * @param target The target ItemStack to copy data to
     */
    public static void copySnailData(@NotNull ItemStack source, @NotNull ItemStack target) {
        if (source.isEmpty() || target.isEmpty()) {
            return;
        }

        CompoundTag sourceNbt = source.getTag();
        if (sourceNbt == null) {
            return; // Source has no data to copy
        }

        CompoundTag targetNbt = target.getOrCreateTag();

        // Copy all snail-related tags
        if (sourceNbt.hasUUID(SNAIL_UUID_TAG)) {
            targetNbt.putUUID(SNAIL_UUID_TAG, sourceNbt.getUUID(SNAIL_UUID_TAG));
        }

        if (sourceNbt.contains(CACHED_NUMBER_TAG)) {
            targetNbt.putInt(CACHED_NUMBER_TAG, sourceNbt.getInt(CACHED_NUMBER_TAG));
        }

        if (sourceNbt.contains(ACTIVATION_TIMESTAMP_TAG)) {
            targetNbt.putLong(ACTIVATION_TIMESTAMP_TAG, sourceNbt.getLong(ACTIVATION_TIMESTAMP_TAG));
        }

        if (sourceNbt.hasUUID(ACTIVATION_PLAYER_TAG)) {
            targetNbt.putUUID(ACTIVATION_PLAYER_TAG, sourceNbt.getUUID(ACTIVATION_PLAYER_TAG));
        }

        if (sourceNbt.contains(SNAIL_TYPE_TAG)) {
            targetNbt.putString(SNAIL_TYPE_TAG, sourceNbt.getString(SNAIL_TYPE_TAG));
        }
    }

    /**
     * Creates a new "blank" Transponder Snail with no UUID or number assigned.
     * The snail will get its identity when first activated by a player.
     *
     * @param stack The ItemStack to prepare
     */
    public static void prepareBlankSnail(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        // Remove any existing snail data to ensure it's truly blank
        CompoundTag nbt = stack.getTag();
        if (nbt != null) {
            nbt.remove(SNAIL_UUID_TAG);
            nbt.remove(CACHED_NUMBER_TAG);
            nbt.remove(ACTIVATION_TIMESTAMP_TAG);
            nbt.remove(ACTIVATION_PLAYER_TAG);
            nbt.remove(SNAIL_TYPE_TAG);
        }
    }

    /**
     * Validates that the snail's cached data matches the registry.
     * Useful for detecting corrupted or out-of-sync data.
     *
     * @param stack The Transponder Snail ItemStack
     * @return True if data is valid and consistent, false if there are issues
     */
    public static boolean validateSnailData(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return true; // Empty stacks are "valid"
        }

        UUID snailUUID = getSnailUUID(stack);
        if (snailUUID == null) {
            // No UUID means it's a blank snail - that's valid
            return true;
        }

        // Check if the UUID exists in the registry
        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            // Can't validate without registry - assume valid
            return true;
        }

        int registryNumber = registry.getSnailNumber(snailUUID);
        int cachedNumber = getSnailNumber(stack);

        if (registryNumber == -1) {
            // UUID not in registry but snail has UUID - data corruption
            System.err.println("SnailNBTHandler: Validation failed - UUID " + snailUUID + " not found in registry");
            return false;
        }

        if (cachedNumber != -1 && cachedNumber != registryNumber) {
            // Cached number doesn't match registry - data inconsistency
            System.err.println("SnailNBTHandler: Validation failed - cached number " + cachedNumber + " doesn't match registry number " + registryNumber + " for UUID " + snailUUID);
            return false;
        }

        return true;
    }

    /**
     * Repairs corrupted snail data by syncing with the registry.
     *
     * @param stack The Transponder Snail ItemStack to repair
     * @return True if repairs were made, false if no repairs needed or impossible
     */
    public static boolean repairSnailData(@NotNull ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        UUID snailUUID = getSnailUUID(stack);
        if (snailUUID == null) {
            // No UUID - nothing to repair
            return false;
        }

        SnailNumberRegistry registry = SnailNumberRegistry.getInstance();
        if (registry == null) {
            // Can't repair without registry
            return false;
        }

        int registryNumber = registry.getSnailNumber(snailUUID);
        if (registryNumber == -1) {
            // UUID not in registry - can't repair, might need manual intervention
            System.err.println("SnailNBTHandler: Cannot repair snail with UUID " + snailUUID + " - not found in registry");
            return false;
        }

        // Update cached number to match registry
        updateCachedNumber(stack, registryNumber);
        System.out.println("SnailNBTHandler: Repaired snail data - updated cached number to " + registryNumber + " for UUID " + snailUUID);
        return true;
    }

    /**
     * Adds tooltip information to Transponder Snail items.
     * Call this from your item's appendHoverText method.
     *
     * @param stack The ItemStack being displayed
     * @param level The current level (can be null)
     * @param tooltip The tooltip list to add to
     * @param flag The tooltip flag
     */
    public static void addSnailTooltip(@NotNull ItemStack stack, @Nullable Level level,
                                       @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {

        if (stack.isEmpty()) {
            return;
        }

        int snailNumber = getSnailNumber(stack);

        if (snailNumber != -1) {
            // Show the snail number
            Component numberTooltip = Component.literal("#: " + snailNumber)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
            tooltip.add(numberTooltip);

            // In advanced tooltip mode, show additional info
            if (flag.isAdvanced()) {
                UUID snailUUID = getSnailUUID(stack);
                if (snailUUID != null) {
                    Component uuidTooltip = Component.literal("UUID: " + snailUUID.toString().substring(0, 8) + "...")
                            .withStyle(ChatFormatting.DARK_GRAY);
                    tooltip.add(uuidTooltip);
                }

                SnailType snailType = getEffectiveSnailType(stack);
                if (snailType != null) {
                    Component typeTooltip = Component.literal("Type: " + snailType.name().toLowerCase())
                            .withStyle(ChatFormatting.DARK_GRAY);
                    tooltip.add(typeTooltip);
                }

                long activationTime = getActivationTimestamp(stack);
                if (activationTime > 0) {
                    Component timeTooltip = Component.literal("Activated: " + new java.util.Date(activationTime))
                            .withStyle(ChatFormatting.DARK_GRAY);
                    tooltip.add(timeTooltip);
                }
            }
        } else {
            // Snail not activated yet
            Component inactiveTooltip = Component.literal("Right-click to register")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            tooltip.add(inactiveTooltip);
        }
    }
}