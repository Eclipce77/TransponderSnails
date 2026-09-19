package net.eclipce.transpondersnails.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.eclipce.transpondersnails.TransponderSnails;
import net.eclipce.transpondersnails.recipe.ModRecipeTypes;
import net.eclipce.transpondersnails.recipe.PortableSnailDyeRecipe;
import net.eclipce.transpondersnails.recipe.ShellDyeRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.List;

/**
 * JEI integration for Transponder Snails (Minecraft 1.20.1 / Forge).
 *
 * <p>JEI finds this class through {@link JeiPlugin} and only then loads it, so nothing outside this
 * package should ever reference it. That keeps JEI a purely optional dependency at runtime.
 *
 * <p>The shaped crafting recipes need no JEI code: they are 3x3 shaped recipes (see UntrimmedPattern),
 * so JEI draws them exactly as written. The two dye recipes are CustomRecipes, which JEI hides unless
 * an extension claims them, so those are registered below.
 */
@JeiPlugin
public class TransponderSnailsJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID = new ResourceLocation(TransponderSnails.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    /**
     * JEI skips CustomRecipes ("special" recipes) because it has no idea how to draw them. Registering an
     * extension for the exact recipe class makes JEI treat them as handled and draw them our way.
     * Needs JEI 15.16.2+ (for ICraftingCategoryExtension#onDisplayedIngredientsUpdate).
     */
    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getCraftingCategory().addCategoryExtension(ShellDyeRecipe.class, ShellDyeCraftingExtension::new);
        registration.getCraftingCategory().addCategoryExtension(PortableSnailDyeRecipe.class, PortableSnailDyeCraftingExtension::new);
    }

    /**
     * The Den Den Mushi stonecutting recipes use our own RecipeType, and JEI only reads vanilla's
     * {@code RecipeType.STONECUTTING}, so they never appeared in JEI at all. Show them in JEI's
     * regular Stonecutting tab by handing it display-only copies (same id, ingredient and result).
     * The real recipes, and the color-transferring assemble(), are untouched.
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        RecipeManager recipeManager = level.getRecipeManager();
        RegistryAccess registryAccess = level.registryAccess();

        List<StonecutterRecipe> displayRecipes = recipeManager
                .getAllRecipesFor(ModRecipeTypes.DEN_DEN_MUSHI_STONECUTTING.get())
                .stream()
                .map(recipe -> new StonecutterRecipe(
                        recipe.getId(),
                        recipe.getGroup(),
                        recipe.getIngredients().get(0),
                        recipe.getResultItem(registryAccess)))
                .toList();

        registration.addRecipes(RecipeTypes.STONECUTTING, displayRecipes);
    }
}