package net.eclipce.transpondersnails.recipe;

import net.eclipce.transpondersnails.item.DenDenMushiItem;
import net.eclipce.transpondersnails.block.ModBlocks;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public class TransponderSnailColorRecipe extends CustomRecipe {

    public TransponderSnailColorRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        boolean hasDenDenMushi = false;
        boolean hasOtherIngredients = false;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                if (stack.getItem() instanceof DenDenMushiItem) {
                    if (hasDenDenMushi) {
                        return false; // Only one Den Den Mushi allowed
                    }
                    hasDenDenMushi = true;
                } else {
                    // Check if this is a valid transponder snail crafting ingredient
                    // You'll need to modify this based on your actual recipe
                    hasOtherIngredients = true;
                }
            }
        }

        // Must have exactly one Den Den Mushi and other required ingredients
        return hasDenDenMushi && hasOtherIngredients;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack denDenMushiStack = ItemStack.EMPTY;

        // Find the Den Den Mushi in the crafting grid
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.getItem() instanceof DenDenMushiItem) {
                denDenMushiStack = stack;
                break;
            }
        }

        // Create the transponder snail item
        ItemStack result = new ItemStack(ModBlocks.TRANSPONDER_SNAIL.get());

        // Transfer colors if the Den Den Mushi has them
        if (!denDenMushiStack.isEmpty() && DenDenMushiItem.isCaptured(denDenMushiStack)) {
            CompoundTag nbt = result.getOrCreateTag();

            // Store colors in the item NBT for block placement
            nbt.putInt("body_color", DenDenMushiItem.getBodyColor(denDenMushiStack));
            nbt.putInt("shell_color", DenDenMushiItem.getShellColor(denDenMushiStack));

            // Also store in BlockEntityTag for proper block entity loading
            CompoundTag blockEntityTag = nbt.getCompound("BlockEntityTag");
            blockEntityTag.putInt("BodyColor", DenDenMushiItem.getBodyColor(denDenMushiStack));
            blockEntityTag.putInt("ShellColor", DenDenMushiItem.getShellColor(denDenMushiStack));
            blockEntityTag.putBoolean("ColorsInitialized", true);
            nbt.put("BlockEntityTag", blockEntityTag);
        }

        return result;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 2 && height >= 2; // Adjust based on your recipe size
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeSerializers.TRANSPONDER_SNAIL_COLOR.get(); // You'll need to register this
    }
}