// Fixed Client Setup - matching your exact package structure
package net.eclipce.transpondersnails.client;

// Import your exact classes from the screen package
import net.eclipce.transpondersnails.entity.ModEntities;
import net.eclipce.transpondersnails.entity.client.DenDenMushiRenderer;
import net.eclipce.transpondersnails.entity.client.WhiteDenDenMushiRenderer;
import net.eclipce.transpondersnails.screen.DialingScreen;
import net.eclipce.transpondersnails.screen.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "transpondersnails", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Register the screen for the menu type
            MenuScreens.register(ModMenuTypes.DIALING_MENU.get(), DialingScreen::new);
        });

        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parentScreen) -> new ClientConfigScreen(parentScreen)
                )
        );

        EntityRenderers.register(ModEntities.DEN_DEN_MUSHI.get(), DenDenMushiRenderer::new);
        EntityRenderers.register(ModEntities.WHITE_DEN_DEN_MUSHI.get(), WhiteDenDenMushiRenderer::new);

        System.out.println("Registered entity renderers");

    }
}