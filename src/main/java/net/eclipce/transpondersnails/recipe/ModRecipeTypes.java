package net.eclipce.transpondersnails.recipe;

import net.eclipce.transpondersnails.TransponderSnails;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModRecipeTypes {
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, TransponderSnails.MOD_ID);

    public static final RegistryObject<RecipeType<DenDenMushiStonecutterRecipe>> DEN_DEN_MUSHI_STONECUTTING =
            RECIPE_TYPES.register("den_den_mushi_stonecutting",
                    () -> new RecipeType<DenDenMushiStonecutterRecipe>() {
                        @Override
                        public String toString() {
                            return "den_den_mushi_stonecutting";
                        }
                    });

    public static void register(IEventBus eventBus) {
        RECIPE_TYPES.register(eventBus);
    }
}