package net.eclipce.transpondersnails.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

/**
 * A normal shaped recipe that keeps the blank rows/columns of its pattern (see {@link UntrimmedPattern}).
 * Use it as {@code "type": "transpondersnails:untrimmed_shaped"} for any recipe that has no special
 * crafting logic of its own. Recipes that do (like {@link TransponderSnailCraftingRecipe}) call
 * {@link UntrimmedPattern#restore} from their own serializer instead.
 *
 * <p>The network format is vanilla's shaped-recipe format: the client just receives an ordinary
 * 3x3 shaped recipe.
 */
public class UntrimmedShapedRecipe extends ShapedRecipe {

    public UntrimmedShapedRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                                 int width, int height, NonNullList<Ingredient> ingredients, ItemStack result) {
        super(id, group, category, width, height, ingredients, result);
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.UNTRIMMED_SHAPED.get();
    }

    public static class Serializer implements RecipeSerializer<UntrimmedShapedRecipe> {

        @Override
        public @NotNull UntrimmedShapedRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
            // getResultItem(null): ShapedRecipe ignores that argument (same as TransponderSnailCraftingRecipe)
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromJson(recipeId, json);
            UntrimmedPattern.Result pattern = UntrimmedPattern.restore(json, base);

            return new UntrimmedShapedRecipe(
                    recipeId,
                    base.getGroup(),
                    base.category(),
                    pattern.width(),
                    pattern.height(),
                    pattern.ingredients(),
                    base.getResultItem(null)
            );
        }

        @Override
        public @NotNull UntrimmedShapedRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromNetwork(recipeId, buffer);
            if (base == null) {
                throw new IllegalStateException("Failed to read untrimmed shaped recipe " + recipeId + " from network");
            }

            return new UntrimmedShapedRecipe(
                    recipeId,
                    base.getGroup(),
                    base.category(),
                    base.getWidth(),
                    base.getHeight(),
                    base.getIngredients(),
                    base.getResultItem(null)
            );
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull UntrimmedShapedRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
