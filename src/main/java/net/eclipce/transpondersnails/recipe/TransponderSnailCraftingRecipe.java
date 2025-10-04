package net.eclipce.transpondersnails.recipe;

import com.google.gson.JsonObject;
import net.eclipce.transpondersnails.item.DenDenMushiItem;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

/**
 * Custom crafting recipe that transfers colors from Den Den Mushi to Transponder Snail
 */
public class TransponderSnailCraftingRecipe extends ShapedRecipe {

    public TransponderSnailCraftingRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                                          int width, int height,
                                          net.minecraft.core.NonNullList<net.minecraft.world.item.crafting.Ingredient> ingredients,
                                          ItemStack result) {
        super(id, group, category, width, height, ingredients, result);
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingContainer container, @NotNull RegistryAccess registryAccess) {
        // Get the base result
        ItemStack result = super.assemble(container, registryAccess);

        // Find the Den Den Mushi in the crafting grid
        ItemStack denDenMushi = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() == ModItems.DEN_DEN_MUSHI.get()) {
                denDenMushi = stack;
                break;
            }
        }

        // Transfer colors if Den Den Mushi was found
        if (!denDenMushi.isEmpty() && DenDenMushiItem.isCaptured(denDenMushi)) {
            int bodyColor = DenDenMushiItem.getBodyColor(denDenMushi);
            int shellColor = DenDenMushiItem.getShellColor(denDenMushi);

            CompoundTag nbt = result.getOrCreateTag();
            nbt.putInt("body_color", bodyColor);
            nbt.putInt("shell_color", shellColor);

            // Also add to BlockEntityTag for placement
            CompoundTag blockEntityTag = nbt.getCompound("BlockEntityTag");
            blockEntityTag.putInt("BodyColor", bodyColor);
            blockEntityTag.putInt("ShellColor", shellColor);
            blockEntityTag.putBoolean("ColorsInitialized", true);
            nbt.put("BlockEntityTag", blockEntityTag);

            System.out.println("TransponderSnailCraftingRecipe: Transferred colors - Body: #" +
                    Integer.toHexString(bodyColor) + ", Shell: " + shellColor);
        }

        return result;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.TRANSPONDER_SNAIL_CRAFTING.get();
    }

    /**
     * Custom serializer for the Transponder Snail crafting recipe
     */
    public static class Serializer implements RecipeSerializer<TransponderSnailCraftingRecipe> {

        @Override
        public @NotNull TransponderSnailCraftingRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
            // Use the standard ShapedRecipe parser
            ShapedRecipe baseRecipe = RecipeSerializer.SHAPED_RECIPE.fromJson(recipeId, json);

            // Create our custom recipe using the parsed data
            return new TransponderSnailCraftingRecipe(
                    recipeId,
                    baseRecipe.getGroup(),
                    baseRecipe.category(),
                    baseRecipe.getWidth(),
                    baseRecipe.getHeight(),
                    baseRecipe.getIngredients(),
                    baseRecipe.getResultItem(null)
            );
        }

        @Override
        public @NotNull TransponderSnailCraftingRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
            // Use the standard ShapedRecipe network parser
            ShapedRecipe baseRecipe = RecipeSerializer.SHAPED_RECIPE.fromNetwork(recipeId, buffer);

            if (baseRecipe == null) {
                throw new IllegalStateException("Failed to read Transponder Snail recipe from network");
            }

            return new TransponderSnailCraftingRecipe(
                    recipeId,
                    baseRecipe.getGroup(),
                    baseRecipe.category(),
                    baseRecipe.getWidth(),
                    baseRecipe.getHeight(),
                    baseRecipe.getIngredients(),
                    baseRecipe.getResultItem(null)
            );
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull TransponderSnailCraftingRecipe recipe) {
            // Use the standard ShapedRecipe network writer
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}