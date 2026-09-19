package net.eclipce.transpondersnails.compat.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Small helpers shared by the dye-recipe displays. Client/JEI only.
 */
final class DyeDisplayHelper {

    /**
     * TransientCraftingContainer needs a menu to notify when its contents change. We only use the
     * container to ask the real recipe "what would this produce?", so a menu that does nothing is fine.
     */
    private static final AbstractContainerMenu NO_MENU = new AbstractContainerMenu(null, -1) {
        @Override
        public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return false;
        }
    };

    private DyeDisplayHelper() {
    }

    /** One stack of every vanilla dye, in vanilla color order. */
    static List<ItemStack> allDyes() {
        List<ItemStack> dyes = new ArrayList<>(DyeColor.values().length);
        for (DyeColor color : DyeColor.values()) {
            dyes.add(new ItemStack(DyeItem.byColor(color)));
        }
        return dyes;
    }

    /** Copy of the list shifted left by {@code by}, so two slots cycling in step show different items. */
    static List<ItemStack> rotated(List<ItemStack> list, int by) {
        List<ItemStack> copy = new ArrayList<>(list);
        Collections.rotate(copy, -by);
        return copy;
    }

    /** An empty crafting grid we can fill in and hand to a recipe's assemble(). */
    static CraftingContainer emptyGrid(int width, int height) {
        return new TransientCraftingContainer(NO_MENU, width, height);
    }

    static @Nullable RegistryAccess registryAccess() {
        Level level = Minecraft.getInstance().level;
        return level == null ? null : level.registryAccess();
    }

    /**
     * Makes a slot show exactly this stack until JEI next cycles. Clears first because
     * createDisplayOverrides() reuses the existing consumer, so repeated updates would pile up.
     */
    static void show(IRecipeSlotDrawable slot, ItemStack stack) {
        slot.clearDisplayOverrides();
        slot.createDisplayOverrides().addItemStack(stack);
    }
}
