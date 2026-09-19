package net.eclipce.transpondersnails.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.eclipce.transpondersnails.recipe.ShellDyeRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shows {@link ShellDyeRecipe} in JEI's crafting tab. JEI hides CustomRecipes ("special" recipes) unless
 * an extension claims them, and this one is that extension.
 *
 * <p>Layout: shapeless, one dyeable snail + one dye. Both slots cycle, and the output is computed by the
 * real recipe's {@code assemble()} from whatever is currently displayed, so the result always matches
 * what the crafting table would give (correct NBT keys for every snail type, present and future).
 */
public class ShellDyeCraftingExtension implements ICraftingCategoryExtension {

    private final ShellDyeRecipe recipe;

    public ShellDyeCraftingExtension(ShellDyeRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
        List<ItemStack> snails = dyeableSnails();

        // width/height of 0 = shapeless.
        craftingGridHelper.createAndSetInputs(builder, List.of(snails, DyeDisplayHelper.allDyes()), 0, 0);

        // Stand-ins so JEI can find this recipe when you look up any snail (R / U). The output slot's
        // displayed stack is replaced with the real dyed result in onDisplayedIngredientsUpdate.
        craftingGridHelper.createAndSetOutputs(builder, snails);
    }

    @Override
    public void onDisplayedIngredientsUpdate(List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
        RegistryAccess registryAccess = DyeDisplayHelper.registryAccess();
        Optional<IRecipeSlotDrawable> outputSlot = recipeSlots.stream()
                .filter(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT)
                .findFirst();
        if (registryAccess == null || outputSlot.isEmpty()) {
            return;
        }

        // The two input slots that actually hold something: the dye, and the snail.
        IRecipeSlotDrawable snailSlot = null;
        IRecipeSlotDrawable dyeSlot = null;
        for (IRecipeSlotDrawable slot : recipeSlots) {
            if (slot.getRole() != RecipeIngredientRole.INPUT) {
                continue;
            }
            ItemStack shown = slot.getDisplayedItemStack().orElse(ItemStack.EMPTY);
            if (shown.isEmpty()) {
                continue;
            }
            if (shown.getItem() instanceof DyeItem) {
                dyeSlot = slot;
            } else {
                snailSlot = slot;
            }
        }
        if (snailSlot == null || dyeSlot == null) {
            return;
        }

        ItemStack snail = snailSlot.getDisplayedItemStack().orElse(ItemStack.EMPTY);
        ItemStack dye = dyeSlot.getDisplayedItemStack().orElse(ItemStack.EMPTY);

        // "Recipes for <snail>": keep the snail slot on the snail that was asked for, instead of
        // whichever one happens to be cycling.
        if (focuses.getFocuses(RecipeIngredientRole.OUTPUT).findAny().isPresent()) {
            ItemStack focused = outputSlot.get().getDisplayedItemStack().orElse(ItemStack.EMPTY);
            if (!focused.isEmpty()) {
                snail = new ItemStack(focused.getItem());
                DyeDisplayHelper.show(snailSlot, snail);
            }
        }

        CraftingContainer grid = DyeDisplayHelper.emptyGrid(2, 2);
        grid.setItem(0, snail.copy());
        grid.setItem(1, dye.copy());

        ItemStack result = recipe.assemble(grid, registryAccess);
        if (!result.isEmpty()) {
            DyeDisplayHelper.show(outputSlot.get(), result);
        }
    }

    @Nullable
    @Override
    public ResourceLocation getRegistryName() {
        return recipe.getId();
    }

    /** Every registered item the recipe itself accepts as a dyeable snail (so new snails are picked up automatically). */
    private List<ItemStack> dyeableSnails() {
        List<ItemStack> snails = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack stack = new ItemStack(item);
            if (recipe.isValidSnail(stack)) {
                snails.add(stack);
            }
        }
        return snails;
    }
}
