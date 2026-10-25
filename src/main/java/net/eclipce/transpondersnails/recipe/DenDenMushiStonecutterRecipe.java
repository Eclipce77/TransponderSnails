package net.eclipce.transpondersnails.recipe;

import com.google.gson.JsonObject;
import net.eclipce.transpondersnails.item.DenDenMushiItem;
import net.eclipce.transpondersnails.item.ModItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Custom stonecutter recipe that converts Transponder Snail back to Den Den Mushi
 * while preserving colors
 */
public class DenDenMushiStonecutterRecipe extends StonecutterRecipe {

    public DenDenMushiStonecutterRecipe(ResourceLocation id, String group, Ingredient ingredient, ItemStack result) {
        // StonecutterRecipe's own constructor always stores RecipeSerializer.STONECUTTER
        // internally, but getSerializer() below overrides what's actually returned/used for
        // JSON parsing and network sync - this is still needed so JEI's stonecutting category,
        // which casts every RecipeType.STONECUTTING recipe straight to StonecutterRecipe, can
        // handle this one without a ClassCastException.
        super(id, group, ingredient, result);
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        return !level.isClientSide && this.ingredient.test(container.getItem(0));
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess registryAccess) {
        // Get the base result (Den Den Mushi)
        ItemStack result = this.getResultItem(registryAccess).copy();

        // Get the input Transponder Snail
        ItemStack input = container.getItem(0);

        if (!input.isEmpty() && input.getItem() == ModItems.TRANSPONDER_SNAIL.get()) {
            CompoundTag inputNbt = input.getTag();

            if (inputNbt != null) {
                // Check for colors in top-level NBT
                if (inputNbt.contains("body_color") && inputNbt.contains("shell_color")) {
                    int bodyColor = inputNbt.getInt("body_color");
                    int shellColor = inputNbt.getInt("shell_color");

                    // Transfer colors to Den Den Mushi
                    DenDenMushiItem.setColors(result, bodyColor, shellColor);
                    DenDenMushiItem.setCaptured(result, true);

                }
                // Also check BlockEntityTag (for items that were placed as blocks)
                else if (inputNbt.contains("BlockEntityTag")) {
                    CompoundTag blockEntityTag = inputNbt.getCompound("BlockEntityTag");
                    if (blockEntityTag.contains("BodyColor") && blockEntityTag.contains("ShellColor")) {
                        int bodyColor = blockEntityTag.getInt("BodyColor");
                        int shellColor = blockEntityTag.getInt("ShellColor");

                        DenDenMushiItem.setColors(result, bodyColor, shellColor);
                        DenDenMushiItem.setCaptured(result, true);
                    }
                }
            }
        }

        if (!input.isEmpty() && input.getItem() == ModItems.TRANSPONDER_SNAIL_TRANSMITTER.get()) {
            CompoundTag inputNbt = input.getTag();

            if (inputNbt != null) {
                // Check for colors in top-level NBT
                if (inputNbt.contains("body_color") && inputNbt.contains("shell_color")) {
                    int bodyColor = inputNbt.getInt("body_color");
                    int shellColor = inputNbt.getInt("shell_color");

                    // Transfer colors to Den Den Mushi
                    DenDenMushiItem.setColors(result, bodyColor, shellColor);
                    DenDenMushiItem.setCaptured(result, true);
                }
                // Also check BlockEntityTag (for items that were placed as blocks)
                else if (inputNbt.contains("BlockEntityTag")) {
                    CompoundTag blockEntityTag = inputNbt.getCompound("BlockEntityTag");
                    if (blockEntityTag.contains("BodyColor") && blockEntityTag.contains("ShellColor")) {
                        int bodyColor = blockEntityTag.getInt("BodyColor");
                        int shellColor = blockEntityTag.getInt("ShellColor");

                        DenDenMushiItem.setColors(result, bodyColor, shellColor);
                        DenDenMushiItem.setCaptured(result, true);
                    }
                }
            }
        }

        // Visual Transponder Snail → Den Den Mushi (body + shell color, same as TRANSPONDER_SNAIL)
        if (!input.isEmpty() && (input.getItem() == ModItems.VISUAL_TRANSPONDER_SNAIL.get()
                || input.getItem() == ModItems.VISUAL_TRANSPONDER_SNAIL_TRANSMITTER.get())) {
            CompoundTag inputNbt = input.getTag();

            if (inputNbt != null) {
                // Check for colors in top-level NBT
                if (inputNbt.contains("body_color") && inputNbt.contains("shell_color")) {
                    int bodyColor = inputNbt.getInt("body_color");
                    int shellColor = inputNbt.getInt("shell_color");

                    DenDenMushiItem.setColors(result, bodyColor, shellColor);
                    DenDenMushiItem.setCaptured(result, true);
                }
                // Also check BlockEntityTag (for items that were placed as blocks)
                else if (inputNbt.contains("BlockEntityTag")) {
                    CompoundTag blockEntityTag = inputNbt.getCompound("BlockEntityTag");
                    if (blockEntityTag.contains("BodyColor") && blockEntityTag.contains("ShellColor")) {
                        int bodyColor = blockEntityTag.getInt("BodyColor");
                        int shellColor = blockEntityTag.getInt("ShellColor");

                        DenDenMushiItem.setColors(result, bodyColor, shellColor);
                        DenDenMushiItem.setCaptured(result, true);
                    }
                }
            }
        }

        // Amplified Transponder Snail → Den Den Mushi. The Amplified block only ever *renders*
        // body color (it has no compatible shell), but TransponderSnailCraftingRecipe always
        // writes body_color and shell_color together from the source Den Den Mushi, so the same
        // both-present check applies here - this just hands back the original mushi's full colors.
        if (!input.isEmpty() && input.getItem() == ModItems.AMPLIFIED_TRANSPONDER_SNAIL.get()) {
            CompoundTag inputNbt = input.getTag();

            if (inputNbt != null) {
                // Check for colors in top-level NBT
                if (inputNbt.contains("body_color") && inputNbt.contains("shell_color")) {
                    int bodyColor = inputNbt.getInt("body_color");
                    int shellColor = inputNbt.getInt("shell_color");

                    DenDenMushiItem.setColors(result, bodyColor, shellColor);
                    DenDenMushiItem.setCaptured(result, true);
                }
                // Also check BlockEntityTag (for items that were placed as blocks)
                else if (inputNbt.contains("BlockEntityTag")) {
                    CompoundTag blockEntityTag = inputNbt.getCompound("BlockEntityTag");
                    if (blockEntityTag.contains("BodyColor") && blockEntityTag.contains("ShellColor")) {
                        int bodyColor = blockEntityTag.getInt("BodyColor");
                        int shellColor = blockEntityTag.getInt("ShellColor");

                        DenDenMushiItem.setColors(result, bodyColor, shellColor);
                        DenDenMushiItem.setCaptured(result, true);
                    }
                }
            }
        }

        // White Transponder Snail → White Den Den Mushi (shell color only — no body color)
        // The camera snails (Visual Transmission Transponder Snail, with and without a Transmitter) and the Horned Transponder Snail hand on their colours too,
        // so that a recipe written for them keeps the body colour instead of making a colourless Den Den Mushi.
        if (!input.isEmpty() && (input.getItem() == ModItems.VISUAL_TRANSMISSION_TRANSPONDER_SNAIL.get()
                || input.getItem() == ModItems.VISUAL_TRANSMISSION_TRANSPONDER_SNAIL_TRANSMITTER.get()
                || input.getItem() == ModItems.HORNED_TRANSPONDER_SNAIL.get())) {
            int[] colours = RecipeColors.read(input);
            if (colours != null) {
                DenDenMushiItem.setColors(result, colours[0], colours[1]);
                DenDenMushiItem.setCaptured(result, true);
            }
        }

        if (!input.isEmpty() && input.getItem() == net.eclipce.transpondersnails.item.ModItems.WHITE_TRANSPONDER_SNAIL.get()) {
            // Override result to be WHITE_DEN_DEN_MUSHI
            result = new ItemStack(net.eclipce.transpondersnails.item.ModItems.WHITE_DEN_DEN_MUSHI.get());
            CompoundTag inputNbt = input.getTag();
            if (inputNbt != null) {
                int shellColor = -1;
                // Check top-level shell_color first (item NBT)
                if (inputNbt.contains("shell_color")) {
                    shellColor = inputNbt.getInt("shell_color");
                }
                // Fall back to BlockEntityTag (placed-then-broken item)
                else if (inputNbt.contains("BlockEntityTag")) {
                    CompoundTag beTag = inputNbt.getCompound("BlockEntityTag");
                    if (beTag.contains("ShellColor")) {
                        shellColor = beTag.getInt("ShellColor");
                    }
                }
                if (shellColor >= 0) {
                    result.getOrCreateTag().putInt("ShellColor", shellColor);
                }
            }
        }

        return result;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.DEN_DEN_MUSHI_STONECUTTING.get();
    }

    /**
     * Custom serializer for the Den Den Mushi stonecutter recipe
     */
    public static class Serializer implements RecipeSerializer<DenDenMushiStonecutterRecipe> {

        @Override
        public @NotNull DenDenMushiStonecutterRecipe fromJson(@NotNull ResourceLocation recipeId, @NotNull JsonObject json) {
            // The same shape as the vanilla stonecutter recipe, and it accepts BOTH ways of writing the result:
            //   "result": { "item": "namespace:item", "count": 1 }      (an object, like a crafting recipe)
            //   "result": "namespace:item", "count": 1                    (a string with the count beside it, like vanilla stonecutting)
            // Anything malformed is reported as a JSON error, which the recipe manager logs and skips; any other exception (a ClassCastException, say)
            // is not caught by it and would stop the recipes - and so the world - from loading at all.
            try {
                String group = json.has("group") ? json.get("group").getAsString() : "";
                Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
                com.google.gson.JsonElement resultJson = json.get("result");
                ItemStack result;
                if (resultJson != null && resultJson.isJsonObject()) {
                    result = net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(resultJson.getAsJsonObject());
                } else if (resultJson != null && resultJson.isJsonPrimitive()) {
                    ResourceLocation itemId = new ResourceLocation(resultJson.getAsString());
                    net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId)
                            .orElseThrow(() -> new com.google.gson.JsonSyntaxException("Unknown item '" + itemId + "' in recipe " + recipeId));
                    result = new ItemStack(item, net.minecraft.util.GsonHelper.getAsInt(json, "count", 1));
                } else {
                    throw new com.google.gson.JsonSyntaxException("Recipe " + recipeId + " needs a \"result\" (an item id, or an object with an item)");
                }
                return new DenDenMushiStonecutterRecipe(recipeId, group, ingredient, result);
            } catch (ClassCastException | IllegalStateException | NullPointerException | net.minecraft.ResourceLocationException e) {
                throw new com.google.gson.JsonSyntaxException("Malformed Den Den Mushi stonecutting recipe " + recipeId + ": " + e.getMessage(), e);
            }
        }

        @Override
        public @NotNull DenDenMushiStonecutterRecipe fromNetwork(@NotNull ResourceLocation recipeId, @NotNull FriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            ItemStack result = buffer.readItem();

            return new DenDenMushiStonecutterRecipe(recipeId, group, ingredient, result);
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull DenDenMushiStonecutterRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            recipe.ingredient.toNetwork(buffer);
            buffer.writeItem(recipe.getResultItem(null));
        }
    }
}