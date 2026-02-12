package net.eclipce.transpondersnails.compat;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * Wrapper to make Curios completely optional.
 * Checks if Curios is loaded before attempting any Curios operations.
 *
 * This allows the mod to work perfectly fine without Curios installed.
 * When Curios is present, players get the wrist slot feature.
 * When Curios is absent, players use snails normally (just no wrist slot).
 */
public class CuriosCompat {

    private static final String CURIOS_MOD_ID = "curios";
    private static Boolean curiosLoaded = null;

    /**
     * Check if Curios API is loaded
     * @return true if Curios is present, false otherwise
     */
    public static boolean isCuriosLoaded() {
        if (curiosLoaded == null) {
            curiosLoaded = ModList.get().isLoaded(CURIOS_MOD_ID);
            if (curiosLoaded) {
                System.out.println("TransponderSnails: Curios API detected - enabling wrist slot features");
            } else {
                System.out.println("TransponderSnails: Curios API not found - wrist slot features disabled");
            }
        }
        return curiosLoaded;
    }

    /**
     * Check if an item is equipped in a Curios slot
     * Returns false if Curios is not loaded
     *
     * @param player The player to check
     * @param item The item to look for
     * @return true if item is in a Curios slot, false otherwise
     */
    public static boolean isEquippedInCuriosSlot(Player player, Item item) {
        if (!isCuriosLoaded()) {
            return false;
        }

        try {
            // Use helper class to avoid loading Curios classes when not present
            return CuriosHelper.isEquippedInCuriosSlot(player, item);
        } catch (Exception e) {
            System.err.println("Error checking Curios slot: " + e.getMessage());
            return false;
        }
    }

    /**
     * Get an ItemStack from a Curios slot
     * Returns empty stack if Curios is not loaded
     *
     * @param player The player to check
     * @param item The item type to look for
     * @return The ItemStack if found, otherwise ItemStack.EMPTY
     */
    public static ItemStack getEquippedCuriosItem(Player player, Item item) {
        if (!isCuriosLoaded()) {
            return ItemStack.EMPTY;
        }

        try {
            return CuriosHelper.getEquippedCuriosItem(player, item);
        } catch (Exception e) {
            System.err.println("Error getting Curios item: " + e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    /**
     * Internal helper class that uses Curios API directly.
     * This class is only loaded if Curios is present, preventing NoClassDefFoundError.
     *
     * FIXED FOR 1.20.1 FORGE: Uses LazyOptional.resolve() which returns Optional<T>
     */
    private static class CuriosHelper {

        public static boolean isEquippedInCuriosSlot(Player player, Item item) {
            try {
                // Access Curios API - getCuriosInventory returns LazyOptional
                var curiosInventoryLazy = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);

                // In 1.20.1, LazyOptional.resolve() returns Optional<T>
                var curiosInventoryOpt = curiosInventoryLazy.resolve();

                if (curiosInventoryOpt.isPresent()) {
                    var handler = curiosInventoryOpt.get();

                    // getCurios() returns Map<String, ICurioStacksHandler>
                    var curiosMap = handler.getCurios();

                    // Check all curio slot types
                    for (var entry : curiosMap.entrySet()) {
                        var stacksHandler = entry.getValue();
                        var itemHandler = stacksHandler.getStacks(); // Returns IItemHandlerModifiable

                        // Check each slot in this curio type
                        for (int i = 0; i < itemHandler.getSlots(); i++) {
                            ItemStack stack = itemHandler.getStackInSlot(i);
                            if (!stack.isEmpty() && stack.is(item)) {
                                return true;
                            }
                        }
                    }
                }

                return false;
            } catch (Exception e) {
                // If anything goes wrong, assume not equipped
                System.err.println("CuriosCompat: Error in isEquippedInCuriosSlot: " + e.getMessage());
                return false;
            }
        }

        public static ItemStack getEquippedCuriosItem(Player player, Item item) {
            try {
                // Access Curios API - getCuriosInventory returns LazyOptional
                var curiosInventoryLazy = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player);

                // In 1.20.1, LazyOptional.resolve() returns Optional<T>
                var curiosInventoryOpt = curiosInventoryLazy.resolve();

                if (curiosInventoryOpt.isPresent()) {
                    var handler = curiosInventoryOpt.get();

                    // getCurios() returns Map<String, ICurioStacksHandler>
                    var curiosMap = handler.getCurios();

                    // Check all curio slot types
                    for (var entry : curiosMap.entrySet()) {
                        var stacksHandler = entry.getValue();
                        var itemHandler = stacksHandler.getStacks(); // Returns IItemHandlerModifiable

                        // Check each slot in this curio type
                        for (int i = 0; i < itemHandler.getSlots(); i++) {
                            ItemStack stack = itemHandler.getStackInSlot(i);
                            if (!stack.isEmpty() && stack.is(item)) {
                                return stack; // Found it!
                            }
                        }
                    }
                }

                return ItemStack.EMPTY;
            } catch (Exception e) {
                System.err.println("CuriosCompat: Error in getEquippedCuriosItem: " + e.getMessage());
                return ItemStack.EMPTY;
            }
        }
    }
}