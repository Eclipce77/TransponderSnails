package net.eclipce.transpondersnails.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

/**
 * Serializer for the Shell Dye Recipe
 */
public class ShellDyeRecipeSerializer implements RecipeSerializer<ShellDyeRecipe> {

    @Override
    public @NotNull ShellDyeRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        // This recipe doesn't need any JSON data, it's shapeless pattern-based
        CraftingBookCategory category = CraftingBookCategory.MISC;

        if (json.has("category")) {
            category = CraftingBookCategory.CODEC.byName(
                    json.get("category").getAsString(),
                    CraftingBookCategory.MISC
            );
        }

        return new ShellDyeRecipe(recipeId, category);
    }

    @Override
    public @NotNull ShellDyeRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
        return new ShellDyeRecipe(recipeId, category);
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull ShellDyeRecipe recipe) {
        buffer.writeEnum(recipe.category());
    }
}