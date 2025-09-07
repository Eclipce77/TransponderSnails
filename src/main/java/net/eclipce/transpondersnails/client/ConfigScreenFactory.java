package net.eclipce.transpondersnails.client;

import net.eclipce.transpondersnails.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

/**
 * Registers the config screen for in-game configuration GUI
 * Routes to different screens based on whether player is in a world or main menu
 */
public class ConfigScreenFactory {

    /**
     * Call this method during client setup to register the config screen
     */
    public static void register() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(ConfigScreenFactory::createConfigScreen)
        );
    }

    /**
     * Creates the appropriate config screen based on current game state
     */
    private static Screen createConfigScreen(Screen parent) {
        Minecraft minecraft = Minecraft.getInstance();

        // Check if we're currently in a world (single player or multiplayer)
        if (minecraft.level != null && minecraft.player != null) {
            // In-world: Use the regular per-world config screen
            return new ModConfigScreen(parent, ModConfig.SERVER_SPEC, "transpondersnails-server.toml");
        } else {
            // Main menu: Use the defaults config screen
            return new MainMenuConfigScreen(parent, ModConfig.SERVER_SPEC);
        }
    }
}