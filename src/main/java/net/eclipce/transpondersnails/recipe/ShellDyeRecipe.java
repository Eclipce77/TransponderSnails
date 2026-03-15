package net.eclipce.transpondersnails.recipe;

import net.eclipce.transpondersnails.item.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Custom shapeless crafting recipe for dyeing snail shells
 * Works with: Den Den Mushi, Baby Den Den Mushi, Black Transponder Snail,
 *             Baby Black Transponder Snail, White Den Den Mushi,
 *             Transponder Snail blocks, White Transponder Snail block
 *
 * Recipe: 1 Snail + 1 Dye = Snail with new shell color
 *
 * FIXED: Ensures colors persist when placing/picking up entities
 */
public class ShellDyeRecipe extends CustomRecipe {

    public ShellDyeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(@NotNull CraftingContainer container, @NotNull Level level) {
        ItemStack snailStack = ItemStack.EMPTY;
        ItemStack dyeStack = ItemStack.EMPTY;
        int itemCount = 0;

        // Find exactly 1 snail and 1 dye
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                itemCount++;
                if (isValidSnail(stack)) {
                    if (!snailStack.isEmpty()) {
                        return false; // More than one snail
                    }
                    snailStack = stack;
                } else if (stack.getItem() instanceof DyeItem) {
                    if (!dyeStack.isEmpty()) {
                        return false; // More than one dye
                    }
                    dyeStack = stack;
                } else {
                    return false; // Invalid item
                }
            }
        }

        // Must have exactly 1 snail and 1 dye
        return itemCount == 2 && !snailStack.isEmpty() && !dyeStack.isEmpty();
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingContainer container, @NotNull RegistryAccess registryAccess) {
        ItemStack snailStack = ItemStack.EMPTY;
        DyeColor dyeColor = null;

        // Find snail and dye
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                if (isValidSnail(stack)) {
                    snailStack = stack;
                } else if (stack.getItem() instanceof DyeItem dyeItem) {
                    dyeColor = dyeItem.getDyeColor();
                }
            }
        }

        if (snailStack.isEmpty() || dyeColor == null) {
            return ItemStack.EMPTY;
        }

        // Create result with new shell color
        ItemStack result = snailStack.copy();
        applyShellColor(result, dyeColor);

        return result;
    }

    /**
     * Check if an item is a valid snail that can have its shell dyed
     */
    private boolean isValidSnail(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof DenDenMushiItem
                || item instanceof BabyDenDenMushiItem
                || item instanceof BlackTransponderSnailItem
                || item instanceof BabyBlackTransponderSnailItem
                || item instanceof WhiteDenDenMushiItem
                || item instanceof TransponderSnailItem
                || item instanceof net.eclipce.transpondersnails.item.HornedDenDenMushiItem
                || item instanceof BlockItem blockItem && (
                blockItem.getBlock() == net.eclipce.transpondersnails.block.ModBlocks.TRANSPONDER_SNAIL.get()
                        || blockItem.getBlock() == net.eclipce.transpondersnails.block.ModBlocks.TRANSPONDER_SNAIL_TRANSMITTER.get()
                        || blockItem.getBlock() == net.eclipce.transpondersnails.block.ModBlocks.WHITE_TRANSPONDER_SNAIL.get()
        );
    }

    /**
     * Apply shell color to the appropriate snail type.
     *
     * Key map (MUST match each item's own SHELL_COLOR_TAG constant):
     *   DenDenMushiItem          → "ShellColor"  (capital)
     *   BabyDenDenMushiItem      → "ShellColor"  (capital)
     *   WhiteDenDenMushiItem     → "ShellColor"  (capital)
     *   HornedDenDenMushiItem    → "ShellColor"  (capital)
     *   BlackTransponderSnailItem     → "shell_color" (lowercase)
     *   BabyBlackTransponderSnailItem → "shell_color" (lowercase)
     *   TransponderSnailItem     → "shell_color" (lowercase)
     */
    private void applyShellColor(ItemStack stack, DyeColor dyeColor) {
        Item item = stack.getItem();
        int colorId = dyeColor.getId();

        // Den Den Mushi (regular) — preserves existing body color
        if (item instanceof DenDenMushiItem) {
            CompoundTag nbt = stack.getOrCreateTag();
            int bodyColor = nbt.contains("BodyColor") ? nbt.getInt("BodyColor") : 0xF5E6A3;
            nbt.putInt("BodyColor", bodyColor);
            nbt.putInt("ShellColor", colorId);
        }
        // Baby Den Den Mushi — preserves existing body color
        else if (item instanceof BabyDenDenMushiItem) {
            CompoundTag nbt = stack.getOrCreateTag();
            int bodyColor = nbt.contains("BodyColor") ? nbt.getInt("BodyColor") : 0xF5E6A3;
            nbt.putInt("BodyColor", bodyColor);
            nbt.putInt("ShellColor", colorId);
        }
        // White Den Den Mushi
        else if (item instanceof WhiteDenDenMushiItem) {
            stack.getOrCreateTag().putInt("ShellColor", colorId);
        }
        // Horned Den Den Mushi — preserves existing body color
        else if (item instanceof net.eclipce.transpondersnails.item.HornedDenDenMushiItem) {
            CompoundTag nbt = stack.getOrCreateTag();
            // Preserve BodyColor; only update ShellColor
            nbt.putInt("ShellColor", colorId);
        }
        // Black Transponder Snail — lowercase key to match SHELL_COLOR_TAG
        else if (item instanceof BlackTransponderSnailItem) {
            stack.getOrCreateTag().putInt("shell_color", colorId);
        }
        // Baby Black Transponder Snail — lowercase key to match SHELL_COLOR_TAG
        else if (item instanceof BabyBlackTransponderSnailItem) {
            stack.getOrCreateTag().putInt("shell_color", colorId);
        }
        // Transponder Snail block item — lowercase key + BlockEntityTag for placement
        else if (item instanceof TransponderSnailItem) {
            CompoundTag nbt = stack.getOrCreateTag();
            nbt.putInt("shell_color", colorId);
            CompoundTag beTag = nbt.getCompound("BlockEntityTag");
            beTag.putInt("ShellColor", colorId);
            nbt.put("BlockEntityTag", beTag);
        }
        // White Transponder Snail block item
        else if (item instanceof BlockItem blockItem) {
            if (blockItem.getBlock() == net.eclipce.transpondersnails.block.ModBlocks.WHITE_TRANSPONDER_SNAIL.get()) {
                CompoundTag nbt = stack.getOrCreateTag();
                nbt.putInt("shell_color", colorId);
                CompoundTag beTag = nbt.getCompound("BlockEntityTag");
                beTag.putInt("ShellColor", colorId);
                nbt.put("BlockEntityTag", beTag);
            }
        }
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2; // Need at least 2 slots
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.SHELL_DYE_SERIALIZER.get();
    }
}