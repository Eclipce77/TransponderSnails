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
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Custom stonecutter recipe that converts Transponder Snail back to Den Den Mushi
 * while preserving colors
 */
public class DenDenMushiStonecutterRecipe extends SingleItemRecipe {

    public DenDenMushiStonecutterRecipe(ResourceLocation id, String group, Ingredient ingredient, ItemStack result) {
        super(ModRecipeTypes.DEN_DEN_MUSHI_STONECUTTING.get(), ModRecipeSerializers.DEN_DEN_MUSHI_STONECUTTING.get(),
                id, group, ingredient, result);
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

                    System.out.println("DenDenMushiStonecutterRecipe: Transferred colors from Transponder Snail - Body: #" +
                            Integer.toHexString(bodyColor) + ", Shell: " + shellColor);
                }
                // Also check BlockEntityTag (for items that were placed as blocks)
                else if (inputNbt.contains("BlockEntityTag")) {
                    CompoundTag blockEntityTag = inputNbt.getCompound("BlockEntityTag");
                    if (blockEntityTag.contains("BodyColor") && blockEntityTag.contains("ShellColor")) {
                        int bodyColor = blockEntityTag.getInt("BodyColor");
                        int shellColor = blockEntityTag.getInt("ShellColor");

                        DenDenMushiItem.setColors(result, bodyColor, shellColor);
                        DenDenMushiItem.setCaptured(result, true);

                        System.out.println("DenDenMushiStonecutterRecipe: Transferred colors from BlockEntityTag - Body: #" +
                                Integer.toHexString(bodyColor) + ", Shell: " + shellColor);
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

                    System.out.println("DenDenMushiStonecutterRecipe: Transferred colors from Transponder Snail - Body: #" +
                            Integer.toHexString(bodyColor) + ", Shell: " + shellColor);
                }
                // Also check BlockEntityTag (for items that were placed as blocks)
                else if (inputNbt.contains("BlockEntityTag")) {
                    CompoundTag blockEntityTag = inputNbt.getCompound("BlockEntityTag");
                    if (blockEntityTag.contains("BodyColor") && blockEntityTag.contains("ShellColor")) {
                        int bodyColor = blockEntityTag.getInt("BodyColor");
                        int shellColor = blockEntityTag.getInt("ShellColor");

                        DenDenMushiItem.setColors(result, bodyColor, shellColor);
                        DenDenMushiItem.setCaptured(result, true);

                        System.out.println("DenDenMushiStonecutterRecipe: Transferred colors from BlockEntityTag - Body: #" +
                                Integer.toHexString(bodyColor) + ", Shell: " + shellColor);
                    }
                }
            }
        }

        // White Transponder Snail → White Den Den Mushi (shell color only — no body color)
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
                    System.out.println("DenDenMushiStonecutterRecipe: White TS → White DDM, shell=" + shellColor);
                }
            }
        }

        return result;
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        // Check if input is a Transponder Snail
        ItemStack input = container.getItem(0);
        return this.ingredient.test(input);
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
            // Use the standard SingleItemRecipe parser (same as stonecutter)
            String group = json.has("group") ? json.get("group").getAsString() : "";
            Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
            ItemStack result = net.minecraft.world.item.crafting.ShapedRecipe.itemStackFromJson(json.getAsJsonObject("result"));

            return new DenDenMushiStonecutterRecipe(recipeId, group, ingredient, result);
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