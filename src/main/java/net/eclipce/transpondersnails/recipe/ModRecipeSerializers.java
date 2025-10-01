package net.eclipce.transpondersnails.recipe;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TransponderSnails.MOD_ID);

    public static final RegistryObject<RecipeSerializer<TransponderSnailColorRecipe>> TRANSPONDER_SNAIL_COLOR =
            RECIPE_SERIALIZERS.register("transponder_snail_color",
                    () -> new SimpleCraftingRecipeSerializer<>(TransponderSnailColorRecipe::new));

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}
