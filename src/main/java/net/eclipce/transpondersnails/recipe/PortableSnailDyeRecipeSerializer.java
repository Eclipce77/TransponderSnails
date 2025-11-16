package net.eclipce.transpondersnails.recipe;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

/**
 * Serializer for the Portable Snail Dye Recipe
 */
public class PortableSnailDyeRecipeSerializer implements RecipeSerializer<PortableSnailDyeRecipe> {

    @Override
    public @NotNull PortableSnailDyeRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
        // This recipe doesn't need any JSON data, it's pattern-based
        CraftingBookCategory category = CraftingBookCategory.MISC;

        if (json.has("category")) {
            category = CraftingBookCategory.CODEC.byName(
                    json.get("category").getAsString(),
                    CraftingBookCategory.MISC
            );
        }

        return new PortableSnailDyeRecipe(recipeId, category);
    }

    @Override
    public @NotNull PortableSnailDyeRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
        CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
        return new PortableSnailDyeRecipe(recipeId, category);
    }

    @Override
    public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull PortableSnailDyeRecipe recipe) {
        buffer.writeEnum(recipe.category());
    }
}