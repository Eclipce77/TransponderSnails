package net.eclipce.transpondersnails.compat.jei;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.ICraftingGridHelper;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.extensions.vanilla.crafting.ICraftingCategoryExtension;
import net.eclipce.transpondersnails.item.ModItems;
import net.eclipce.transpondersnails.recipe.PortableSnailDyeRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shows {@link PortableSnailDyeRecipe} in JEI's crafting tab (JEI hides CustomRecipes unless an
 * extension claims them).
 *
 * <pre>
 *   [shell] [shell] [shell]
 *   [shell] [snail] [shell]
 *   [band ] [band ] [band ]
 * </pre>
 *
 * The real recipe needs all shell dyes to be one color and all band dyes to be one color, so the
 * display keeps every shell slot on the same dye and every band slot on the same dye, then asks the
 * real {@code assemble()} for the result. (In the game each group is optional; JEI shows both filled.)
 */
public class PortableSnailDyeCraftingExtension implements ICraftingCategoryExtension {

    private static final int[] SHELL_CELLS = {0, 1, 2, 3, 5};
    private static final int CENTER_CELL = 4;
    private static final int[] BAND_CELLS = {6, 7, 8};

    /** Shell and band cycle in step, so offset the band list to show they can be different colors. */
    private static final int BAND_COLOR_OFFSET = 8;

    private final PortableSnailDyeRecipe recipe;

    public PortableSnailDyeCraftingExtension(PortableSnailDyeRecipe recipe) {
        this.recipe = recipe;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ICraftingGridHelper craftingGridHelper, IFocusGroup focuses) {
        ItemStack snail = new ItemStack(ModItems.PORTABLE_BLACK_TRANSPONDER_SNAIL.get());
        List<ItemStack> shellDyes = DyeDisplayHelper.allDyes();
        List<ItemStack> bandDyes = DyeDisplayHelper.rotated(shellDyes, BAND_COLOR_OFFSET);

        List<List<ItemStack>> cells = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            cells.add(List.of());
        }
        for (int cell : SHELL_CELLS) {
            cells.set(cell, shellDyes);
        }
        cells.set(CENTER_CELL, List.of(snail));
        for (int cell : BAND_CELLS) {
            cells.set(cell, bandDyes);
        }

        // A full 3x3, so JEI places every cell exactly where we put it.
        craftingGridHelper.createAndSetInputs(builder, cells, 3, 3);
        craftingGridHelper.createAndSetOutputs(builder, List.of(snail));
    }

    @Override
    public void onDisplayedIngredientsUpdate(List<IRecipeSlotDrawable> recipeSlots, IFocusGroup focuses) {
        RegistryAccess registryAccess = DyeDisplayHelper.registryAccess();
        List<IRecipeSlotDrawable> inputs = recipeSlots.stream()
                .filter(slot -> slot.getRole() == RecipeIngredientRole.INPUT)
                .toList();
        Optional<IRecipeSlotDrawable> outputSlot = recipeSlots.stream()
                .filter(slot -> slot.getRole() == RecipeIngredientRole.OUTPUT)
                .findFirst();
        if (registryAccess == null || outputSlot.isEmpty() || inputs.size() != 9) {
            return;
        }

        // The first shell slot and first band slot lead; the rest are forced to match them.
        ItemStack shellDye = inputs.get(SHELL_CELLS[0]).getDisplayedItemStack().orElse(ItemStack.EMPTY);
        ItemStack bandDye = inputs.get(BAND_CELLS[0]).getDisplayedItemStack().orElse(ItemStack.EMPTY);
        ItemStack snail = inputs.get(CENTER_CELL).getDisplayedItemStack().orElse(ItemStack.EMPTY);
        if (shellDye.isEmpty() || bandDye.isEmpty() || snail.isEmpty()) {
            return;
        }

        CraftingContainer grid = DyeDisplayHelper.emptyGrid(3, 3);
        grid.setItem(CENTER_CELL, snail.copy());

        for (int i = 0; i < SHELL_CELLS.length; i++) {
            if (i > 0) {
                DyeDisplayHelper.show(inputs.get(SHELL_CELLS[i]), shellDye);
            }
            grid.setItem(SHELL_CELLS[i], shellDye.copy());
        }
        for (int i = 0; i < BAND_CELLS.length; i++) {
            if (i > 0) {
                DyeDisplayHelper.show(inputs.get(BAND_CELLS[i]), bandDye);
            }
            grid.setItem(BAND_CELLS[i], bandDye.copy());
        }

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

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }
}
