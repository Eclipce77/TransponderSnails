package net.eclipce.transpondersnails.recipe;

import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.item.PortableBlackTransponderSnailItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Custom crafting recipe for dyeing Portable Black Transponder Snails
 *
 * Layout:
 * [Shell] [Shell] [Shell]
 * [Shell] [Snail] [Shell]
 * [ Band] [ Band] [ Band]
 *
 * Where:
 * - Shell = Top-left, Top-center, Top-right, Middle-left, Middle-right (5 slots)
 * - Snail = Center (the portable snail being dyed)
 * - Band = Bottom-left, Bottom-center, Bottom-right (3 slots)
 */
public class PortableSnailDyeRecipe extends CustomRecipe {

    public PortableSnailDyeRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(@NotNull CraftingContainer container, @NotNull Level level) {
        // Must be 3x3 grid
        if (container.getWidth() != 3 || container.getHeight() != 3) {
            return false;
        }

        // Check center (slot 4) is a Portable Black Transponder Snail
        ItemStack centerStack = container.getItem(4);
        if (centerStack.getItem() != ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get()) {
            return false;
        }

        // At least one dye must be present
        boolean hasShellDye = false;
        boolean hasBandDye = false;

        // Check shell slots (0, 1, 2, 3, 5)
        int[] shellSlots = {0, 1, 2, 3, 5};
        DyeColor firstShellColor = null;
        for (int slot : shellSlots) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                if (stack.getItem() instanceof DyeItem dyeItem) {
                    DyeColor color = dyeItem.getDyeColor();
                    if (firstShellColor == null) {
                        firstShellColor = color;
                        hasShellDye = true;
                    } else if (firstShellColor != color) {
                        return false; // All shell dyes must be same color
                    }
                } else {
                    return false; // Non-dye item in shell slot
                }
            }
        }

        // Check band slots (6, 7, 8)
        int[] bandSlots = {6, 7, 8};
        DyeColor firstBandColor = null;
        for (int slot : bandSlots) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                if (stack.getItem() instanceof DyeItem dyeItem) {
                    DyeColor color = dyeItem.getDyeColor();
                    if (firstBandColor == null) {
                        firstBandColor = color;
                        hasBandDye = true;
                    } else if (firstBandColor != color) {
                        return false; // All band dyes must be same color
                    }
                } else {
                    return false; // Non-dye item in band slot
                }
            }
        }

        // Must have at least one dye
        return hasShellDye || hasBandDye;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingContainer container, @NotNull RegistryAccess registryAccess) {
        // Get the center snail
        ItemStack centerStack = container.getItem(4);
        if (centerStack.getItem() != ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get()) {
            return ItemStack.EMPTY;
        }

        // Create result stack (copy of input)
        ItemStack result = centerStack.copy();

        // Determine shell dye color
        int[] shellSlots = {0, 1, 2, 3, 5};
        DyeColor shellColor = null;
        for (int slot : shellSlots) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof DyeItem dyeItem) {
                shellColor = dyeItem.getDyeColor();
                break;
            }
        }

        // Determine band dye color
        int[] bandSlots = {6, 7, 8};
        DyeColor bandColor = null;
        for (int slot : bandSlots) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() instanceof DyeItem dyeItem) {
                bandColor = dyeItem.getDyeColor();
                break;
            }
        }

        // Apply dyes to result
        if (shellColor != null) {
            PortableBlackTransponderSnailItem.setShellColor(result, shellColor);
        }
        if (bandColor != null) {
            PortableBlackTransponderSnailItem.setBandColor(result, bandColor);
        }

        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.PORTABLE_SNAIL_DYE_SERIALIZER.get();
    }
}