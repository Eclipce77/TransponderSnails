package net.eclipce.transpondersnails.recipe;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TransponderSnails.MOD_ID);

    public static final RegistryObject<RecipeSerializer<TransponderSnailCraftingRecipe>> TRANSPONDER_SNAIL_CRAFTING =
            RECIPE_SERIALIZERS.register("transponder_snail_crafting",
                    () -> new TransponderSnailCraftingRecipe.Serializer());

    public static final RegistryObject<RecipeSerializer<DenDenMushiStonecutterRecipe>> DEN_DEN_MUSHI_STONECUTTING =
            RECIPE_SERIALIZERS.register("den_den_mushi_stonecutting",
                    DenDenMushiStonecutterRecipe.Serializer::new);

    public static final RegistryObject<RecipeSerializer<PortableSnailDyeRecipe>> PORTABLE_SNAIL_DYE_SERIALIZER =
            RECIPE_SERIALIZERS.register("portable_snail_dye",
                    PortableSnailDyeRecipeSerializer::new);

    public static final RegistryObject<RecipeSerializer<ShellDyeRecipe>> SHELL_DYE_SERIALIZER =
            RECIPE_SERIALIZERS.register("shell_dye",
                    ShellDyeRecipeSerializer::new);

    public static void register(IEventBus eventBus) {
        RECIPE_SERIALIZERS.register(eventBus);
    }
}